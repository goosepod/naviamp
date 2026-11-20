#!/usr/bin/env python3
"""Build reviewed source snapshots into NEW local repositories; never uploads or reseals existing outputs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import runpy
import shutil
import subprocess
import time
import sys


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--resume-from', type=Path, help='Explicit prior evidence summary with completed UI/plugin stages; bytes and inputs are revalidated')
    p.add_argument('--sources', type=Path, required=True, help='Fresh output of prepare-tvos-compose-sources.py')
    p.add_argument('--gradle-home', type=Path, required=True, help='Dependency/compiler cache; never an artifact source')
    p.add_argument('--native-home', type=Path, required=True, help='Dedicated source-producer Kotlin/Native cache')
    a = p.parse_args()
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        p.error('The producer requires an ARM64 Mac with Xcode and JDK 21')
    ownership = runpy.run_path(str(Path(__file__).with_name('tvos-build-lock.py')))
    with ownership['cancellation_signals'](), ownership['exclusive_build'](Path(__file__).resolve().parents[1]):
        run_producer(a)


def run_producer(a):
    root = Path(__file__).resolve().parents[1]
    source = a.sources.resolve()
    preparation = runpy.run_path(str(root/'scripts/prepare-tvos-compose-sources.py'))
    recipe, files = preparation['load_bundle'](root/'build-support/tvos-compose/sources.zip')
    report = json.loads((source/'source-replay.json').read_text())
    if not report['passed'] or report['bundleSha256'] != preparation['BUNDLE_SHA256']:
        raise ValueError('Sources have not passed reviewed replay')
    for name, entry in recipe['producers'].items():
        path = source/name
        if preparation['git'](path, 'write-tree').decode().strip() != entry['tree']:
            raise ValueError('Producer index differs from the reviewed tree')
        preparation['git'](path, 'diff', '--exit-code')
        # Build scripts must not pick up unreviewed source additions.
        if preparation['git'](path, 'ls-files', '--others', '--exclude-standard').strip():
            raise ValueError('Producer contains untracked inputs')
    plugin = preparation['read_zip'](files['settings-plugin.zip'])
    for name, data in plugin.items():
        if (source/'settings-plugin'/name.removeprefix('build-logic/compose-tvos/')).read_bytes() != data:
            raise ValueError('Settings plugin differs from reviewed sources')
    prior = json.loads(a.resume_from.read_text()) if a.resume_from else None
    out = source/'producer-evidence'
    if prior:
        number = 1
        while (source/('producer-evidence-resume-'+str(number))).exists(): number += 1
        out = source/('producer-evidence-resume-'+str(number))
    out.mkdir(exist_ok=False)
    ui, resources = source/'ui-repository', source/'resources-repository'
    if not prior and (ui.exists() or resources.exists()): raise ValueError('Artifact repositories must be fresh')
    identity = {'bundleSha256': preparation['BUNDLE_SHA256'], 'architecture': platform.machine(),
                'xcode': subprocess.check_output(['xcodebuild', '-version'], text=True).strip(),
                'sdk': subprocess.check_output(['xcrun', '--sdk', 'appletvos', '--show-sdk-build-version'], text=True).strip(),
                'java': subprocess.check_output(['java', '-version'], stderr=subprocess.STDOUT, text=True).strip(),
                'deploymentTarget': '18.5', 'scripts': {name: hashlib.sha256((root/'scripts'/name).read_bytes()).hexdigest()
                    for name in ('build-tvos-compose.py', 'probe-compose-tvos-source-graph.init.gradle',
                        'probe-compose-tvos-local-publication.init.gradle', 'probe-compose-tvos-deployment.init.gradle',
                        'probe-compose-tvos-resources-publication.init.gradle')}}
    if 'version \"21.' not in identity['java']:
        raise ValueError('Use JDK 21 for the reviewed producer')
    identity['simulatorSdk'] = subprocess.check_output(['xcrun', '--sdk', 'appletvsimulator', '--show-sdk-build-version'], text=True).strip()
    identity['wrappers'] = {name: hashlib.sha256((source/name/path).read_bytes()).hexdigest() for name,path in
        [('ui','gradle/wrapper/gradle-wrapper.properties'), ('resources','gradle-plugins/gradle/wrapper/gradle-wrapper.properties')]}
    identity['cacheKey'] = hashlib.sha256(json.dumps(identity, sort_keys=True).encode()).hexdigest()
    native = a.native_home.resolve()/identity['cacheKey']
    if prior:
        previous_identity = prior['identity']
        def comparable(value):
            value = json.loads(json.dumps(value))
            value.pop('cacheKey', None)
            value['scripts'].pop('build-tvos-compose.py', None)
            return value
        if comparable(identity) != comparable(previous_identity):
            raise ValueError('Resume source/toolchain/deployment/publication inputs changed')
        if [s['name'] for s in prior['stages'] if s['returncode'] == 0] != ['ui', 'plugin']:
            raise ValueError('Only completed UI+plugin stages can be resumed')
        native = a.native_home.resolve()/previous_identity['cacheKey']
    env = os.environ.copy()
    env.update(GRADLE_USER_HOME=str(a.gradle_home.resolve()), KONAN_DATA_DIR=str(native))
    evidence = {'passed': False, 'identity': identity, 'stages': [], 'nativeHome': str(native)}
    retained = None
    if prior:
        evidence['resumeSummarySha256'] = hashlib.sha256(a.resume_from.read_bytes()).hexdigest()
        evidence['resumeSummary'] = str(a.resume_from.resolve())
        integrity = runpy.run_path(str(root/'scripts/tvos-compose-artifacts.py'))
        subset = {c: owner for c,owner in integrity['expected_coordinates']().items()
                  if owner == 'ui' or c[1] == 'compose-gradle-plugin'}
        retained = integrity['inventory']({'ui': ui, 'resources': resources}, subset)
        evidence['retainedFiles'] = retained

    def run(name, cwd, command):
        start = time.monotonic()
        with (out/(name+'.log')).open('x') as log:
            process = subprocess.Popen(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT)
            try:
                process.wait(timeout=3600)
            except BaseException:
                runpy.run_path(str(root/'scripts/tvos-build-lock.py'))['terminate_tree']([process.pid])
                process.wait(timeout=10)
                raise
            result = process
        evidence['stages'].append({'name': name, 'cwd': str(cwd), 'command': list(map(str,command)), 'returncode': result.returncode,
                                    'seconds': round(time.monotonic()-start,3)})
        if result.returncode: raise RuntimeError(name+' failed; inspect '+str(out/(name+'.log')))
        print(name+' passed', flush=True)
    try:
        base = ['--no-daemon', '--max-workers=2', '--no-build-cache', '--no-configuration-cache', '--no-configure-on-demand']
        command = ['./gradlew', *base, '-Dorg.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8',
            '-Pcompose.platforms=KotlinMultiplatform,Android,IosArm64,IosSimulatorArm64,TvosArm64,TvosSimulatorArm64',
            '-Pjetbrains.publication.version.COMPOSE=1.12.2', '-Pjetbrains.publication.version.COMPOSE_MATERIAL3=1.9.0',
            '-Pjetbrains.publication.version.NAVIGATION_EVENT=1.1.0', '-Ppublication.coordinateRoot=app.naviamp.tvosprobe',
            '-Pnaviamp.tvos.probeDeploymentTarget=18.5', '-Pnaviamp.tvos.probeRepository='+str(ui)]
        for script in ('source-graph', 'local-publication', 'deployment'):
            command += ['--init-script', str(root/'scripts'/('probe-compose-tvos-'+script+'.init.gradle'))]
        command += ['verifyTvosCommonSourceGraph', 'verifyTvosProbePublicationCoordinates']
        coordinates = runpy.run_path(str(root/'scripts/tvos-compose-artifacts.py'))['expected_coordinates']()
        for group, module, version in coordinates:
            if coordinates[(group,module,version)] != 'ui' or module.endswith(('-tvosarm64','-tvossimulatorarm64')): continue
            owner = ':navigationevent:'+module if 'navigationevent' in group else ':compose:'+group.split('.')[-1]+':'+module
            for pub in ('KotlinMultiplatform','TvosArm64','TvosSimulatorArm64'):
                command.append(owner+':publish'+pub+'PublicationToMavenRepository')
        if prior:
            if prior['stages'][0]['command'] != list(map(str, command)):
                raise ValueError('Completed UI command differs; rebuild from fresh sources')
            evidence['stages'].append(dict(prior['stages'][0], reused=True))
        else:
            run('ui', source/'ui', command)
        command = ['./gradlew', *base, '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8']
        cwd = source/'resources/gradle-plugins'
        plugin_command = command+['-Ppublication.groupId=app.naviamp.tvosprobe.compose', ':compose:publishPluginMavenPublicationToLocalDirRepository']
        if prior:
            if prior['stages'][1]['command'] != plugin_command:
                raise ValueError('Completed plugin command differs; rebuild from fresh sources')
            evidence['stages'].append(dict(prior['stages'][1], reused=True))
        else:
            run('plugin', cwd, plugin_command)
            shutil.copytree(cwd/'build/repo/app', resources/'app')
        run('resources', source/'resources/components', [str(cwd/'gradlew'), *command[1:], '--init-script', str(root/'scripts/probe-compose-tvos-resources-publication.init.gradle'),
            '-Ppublication.groupId=app.naviamp.tvosprobe.compose.components',
            '-Pnaviamp.tvos.probePluginBuild='+str(source/'settings-plugin'),
            '-Pnaviamp.tvos.probeRepository='+str(ui), '-Pnaviamp.tvos.resourcesProbeRepository='+str(resources),
            *[':resources:library:publish'+pub+'PublicationToOwnedProbeRepository' for pub in ('KotlinMultiplatform','TvosArm64','TvosSimulatorArm64')]])
        if retained:
            current = runpy.run_path(str(root/'scripts/tvos-compose-artifacts.py'))['inventory']({'ui': ui, 'resources': resources})
            if any(current.get(name) != record for name,record in retained.items()):
                raise ValueError('Completed artifact bytes changed during resume')
        provenance = out/'provenance.json'
        provenance.write_text(json.dumps({'identity': identity, 'resumedFrom': evidence.get('resumeSummarySha256'),
            'retainedFiles': retained}, indent=2)+'\n')
        run('seal', root, ['python3', 'scripts/tvos-compose-artifacts.py', 'seal', '--ui-repository', str(ui),
            '--resources-repository', str(resources), '--manifest', str(out/'manifest.json'), '--provenance', str(provenance)])
        evidence['manifestSha256'] = hashlib.sha256((out/'manifest.json').read_bytes()).hexdigest()
        evidence['passed'] = True
    finally:
        (out/'summary.json').write_text(json.dumps(evidence, indent=2)+'\n')


if __name__ == '__main__':
    main()
