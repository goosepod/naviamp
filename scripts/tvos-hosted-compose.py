#!/usr/bin/env python3
"""Stage candidates or fetch explicitly approved hosted Compose artifacts; never promotes a pin."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import runpy
import selectors
import shutil
import stat
import subprocess
import tempfile
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DELIVERY = runpy.run_path(str(ROOT/'scripts/tvos-compose-delivery.py'))
SOURCE_SHA = runpy.run_path(str(ROOT/'scripts/prepare-tvos-compose-sources.py'))['BUNDLE_SHA256']
REPOSITORY = 'goosepod/naviamp'
WORKFLOW = '.github/workflows/tvos-compose-producer.yml'
MAX_DOWNLOAD = DELIVERY['MAX_BYTES'] + 1_000_000
PIN_KEYS = {'schemaVersion', 'status', 'repository', 'repositoryId', 'workflowPath', 'producerCommit',
            'runId', 'runAttempt', 'artifactId', 'artifactName', 'artifactSha256', 'bundleSha256',
            'manifestSha256', 'sourceBundleSha256'}


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def validate_pin(pin, *, approved=True):
    if pin.get('status') != ('approved' if approved else 'candidate'):
        raise ValueError('No approved hosted Compose artifact; review and promote a completed producer candidate first')
    if set(pin) != PIN_KEYS or type(pin['schemaVersion']) is not int or pin['schemaVersion'] != 1:
        raise ValueError('Unsupported hosted artifact pin schema')
    if pin['repository'] != REPOSITORY or pin['workflowPath'] != WORKFLOW or pin['sourceBundleSha256'] != SOURCE_SHA:
        raise ValueError('Unreviewed producer repository/workflow/source bundle')
    for name in ('repositoryId', 'runId', 'runAttempt', 'artifactId'):
        if type(pin[name]) is not int or pin[name] <= 0:
            raise ValueError('Invalid pinned integer: ' + name)
    for name in ('artifactSha256', 'bundleSha256', 'manifestSha256', 'producerCommit'):
        if not isinstance(pin[name], str) or not re.fullmatch('[0-9a-f]{'+('40' if name == 'producerCommit' else '64')+'}', pin[name]):
            raise ValueError('Invalid pinned digest: ' + name)
    if pin['artifactName'] != f'tvos-compose-candidate-{pin["runId"]}-{pin["runAttempt"]}':
        raise ValueError('Artifact name does not bind the pinned run attempt')
    return pin


def validate_metadata(pin, run, artifact):
    def repository_matches(value):
        return value.get('id') == pin['repositoryId'] and value.get('full_name') == REPOSITORY
    if (run.get('id') != pin['runId'] or run.get('run_attempt') != pin['runAttempt'] or
            run.get('event') != 'workflow_dispatch' or run.get('path') != WORKFLOW or
            run.get('head_branch') != 'main' or run.get('head_sha') != pin['producerCommit'] or
            run.get('status') != 'completed' or run.get('conclusion') != 'success' or
            not repository_matches(run.get('repository', {})) or
            not repository_matches(run.get('head_repository', {}))):
        raise ValueError('Producer run is not the pinned successful accepted-main writer')
    origin = artifact.get('workflow_run', {})
    if (artifact.get('id') != pin['artifactId'] or artifact.get('name') != pin['artifactName'] or
            artifact.get('expired') is not False or
            artifact.get('digest') != 'sha256:'+pin['artifactSha256'] or
            type(artifact.get('size_in_bytes')) is not int or not 0 < artifact['size_in_bytes'] <= MAX_DOWNLOAD or
            origin.get('id') != pin['runId'] or origin.get('head_sha') != pin['producerCommit'] or
            origin.get('head_branch') != 'main' or origin.get('repository_id') != pin['repositoryId'] or
            origin.get('head_repository_id') != pin['repositoryId']):
        raise ValueError('Artifact is expired, replaced, or not owned by the pinned producer run')


def gh_json(endpoint):
    return json.loads(subprocess.check_output(['gh', 'api', '--hostname', 'github.com', endpoint], text=True, timeout=60,
                                             env=dict(os.environ, GH_HOST='github.com')))


def gh_download(endpoint, target):
    # gh follows GitHub's artifact redirect; no untrusted metadata URL or token enters argv.
    with target.open('xb') as stream, subprocess.Popen(['gh', 'api', '--hostname', 'github.com', endpoint],
            stdout=subprocess.PIPE, env=dict(os.environ, GH_HOST='github.com')) as process:
        try:
            total = 0
            deadline = time.monotonic()+600
            with selectors.DefaultSelector() as ready:
                ready.register(process.stdout, selectors.EVENT_READ)
                while True:
                    if not ready.select(max(0, deadline-time.monotonic())):
                        raise TimeoutError('Hosted artifact download exceeded 600 seconds')
                    chunk = os.read(process.stdout.fileno(), 1024*1024)
                    if not chunk:
                        break
                    total += len(chunk)
                    if total > MAX_DOWNLOAD:
                        raise ValueError('Hosted artifact download exceeds its size bound')
                    stream.write(chunk)
            if process.wait(timeout=30):
                raise RuntimeError('GitHub artifact download failed')
        except BaseException:
            process.kill()
            process.wait(timeout=10)
            raise


def attestation_command(bundle, pin):
    return ['gh', 'attestation', 'verify', str(bundle), '--repo', REPOSITORY,
            '--signer-workflow', REPOSITORY+'/'+WORKFLOW, '--signer-digest', pin['producerCommit'],
            '--source-ref', 'refs/heads/main', '--source-digest', pin['producerCommit'],
            '--deny-self-hosted-runners', '--cert-oidc-issuer', 'https://token.actions.githubusercontent.com',
            '--predicate-type', 'https://slsa.dev/provenance/v1', '--format', 'json']


def verify_attestation(bundle, pin, evidence):
    with (evidence/'attestation.json').open('x') as stream:
        subprocess.run(attestation_command(bundle, pin), stdout=stream, check=True, timeout=120,
                       env=dict(os.environ, GH_HOST='github.com'))


def unpack_delivery(archive, target, pin):
    if sha(archive) != pin['artifactSha256']:
        raise ValueError('Downloaded GitHub archive digest differs from reviewed pin')
    with zipfile.ZipFile(archive) as zipped:
        members = zipped.infolist()
        if len(members) != 1:
            raise ValueError('Hosted archive must contain exactly compose.zip')
        info = members[0]
        if (info.orig_filename != 'compose.zip' or info.filename != 'compose.zip' or
                stat.S_IFMT(info.external_attr >> 16) not in (0, stat.S_IFREG) or
                not 0 < info.file_size <= DELIVERY['MAX_BYTES']):
            raise ValueError('Unsafe hosted delivery member')
        with zipped.open(info) as source, target.open('xb') as output:
            shutil.copyfileobj(source, output)
    if sha(target) != pin['bundleSha256']:
        raise ValueError('Delivered bundle differs from separately reviewed identity')


def fetch(pin, destination, evidence, *, api=gh_json, download=gh_download, attest=verify_attestation):
    if destination.exists() or destination.is_symlink():
        raise ValueError('Hosted install destination must be fresh')
    evidence.mkdir(parents=True, exist_ok=False)
    report = {'passed': False, 'pin': pin}
    try:
        validate_pin(pin)  # Before network, archive selection, or repository installation.
        prefix = 'repos/'+REPOSITORY+'/actions/'
        run = api(prefix+'runs/'+str(pin['runId']))
        artifact = api(prefix+'artifacts/'+str(pin['artifactId']))
        (evidence/'run.json').write_text(json.dumps(run, indent=2)+'\n')
        (evidence/'artifact.json').write_text(json.dumps(artifact, indent=2)+'\n')
        validate_metadata(pin, run, artifact)
        with tempfile.TemporaryDirectory(prefix='.hosted-compose-', dir=evidence) as temporary:
            temporary = Path(temporary)
            archive, bundle = temporary/'github.zip', temporary/'compose.zip'
            download(prefix+'artifacts/'+str(pin['artifactId'])+'/zip', archive)
            if archive.stat().st_size != artifact['size_in_bytes']:
                raise ValueError('Downloaded archive size differs from pinned artifact metadata')
            unpack_delivery(archive, bundle, pin)
            attest(bundle, pin, evidence)  # Mandatory cryptographic verification before repository use.
            DELIVERY['install'](bundle, pin['bundleSha256'], pin['manifestSha256'], destination)
        report['passed'] = True
    except BaseException as error:
        report['error'] = str(error)
        raise
    finally:
        (evidence/'summary.json').write_text(json.dumps(report, indent=2)+'\n')
    return report


def stage(sources, output):
    summary = json.loads((sources/'producer-evidence/summary.json').read_text())
    if not summary['passed'] or summary['identity']['bundleSha256'] != SOURCE_SHA:
        raise ValueError('Producer is incomplete or uses different reviewed sources')
    manifest = sources/'producer-evidence/manifest.json'
    if sha(manifest) != summary['manifestSha256']:
        raise ValueError('Producer summary does not match sealed manifest')
    output.mkdir(parents=True, exist_ok=False)
    bundle = output/'compose.zip'
    identity = DELIVERY['pack'](sources/'ui-repository', sources/'resources-repository', manifest,
                                summary['manifestSha256'], bundle)
    receipt = {'bundleSha256': identity, 'manifestSha256': summary['manifestSha256'], 'sourceBundleSha256': SOURCE_SHA}
    (output/'candidate.json').write_text(json.dumps(receipt, indent=2)+'\n')
    return receipt


def promotion_from_environment():
    # Values come only from the accepted workflow's build job and immutable upload outputs.
    pin = {'schemaVersion': 1, 'status': 'candidate', 'repository': REPOSITORY, 'workflowPath': WORKFLOW,
           'sourceBundleSha256': SOURCE_SHA, 'producerCommit': os.environ['GITHUB_SHA']}
    for name, variable in [('repositoryId', 'GITHUB_REPOSITORY_ID'), ('runId', 'GITHUB_RUN_ID'),
                           ('runAttempt', 'GITHUB_RUN_ATTEMPT'), ('artifactId', 'COMPOSE_ARTIFACT_ID')]:
        pin[name] = int(os.environ[variable])
    pin['artifactName'] = f'tvos-compose-candidate-{pin["runId"]}-{pin["runAttempt"]}'
    for name, variable in [('artifactSha256', 'COMPOSE_ARTIFACT_SHA256'), ('bundleSha256', 'COMPOSE_BUNDLE_SHA256'),
                           ('manifestSha256', 'COMPOSE_MANIFEST_SHA256')]:
        pin[name] = os.environ[variable]
    validate_pin(pin, approved=False)
    return pin


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    p = commands.add_parser('stage')
    p.add_argument('--sources', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p = commands.add_parser('fetch')
    p.add_argument('--pin', type=Path, required=True)
    p.add_argument('--destination', type=Path, required=True)
    p.add_argument('--evidence', type=Path, required=True)
    p = commands.add_parser('promotion')
    p.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.command == 'stage':
        print(json.dumps(stage(args.sources, args.output)))
    elif args.command == 'fetch':
        fetch(json.loads(args.pin.read_text()), args.destination, args.evidence)
    else:
        with args.output.open('x') as stream:
            stream.write(json.dumps(promotion_from_environment(), indent=2)+'\n')


if __name__ == '__main__':
    main()
