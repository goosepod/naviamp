#!/usr/bin/env python3
"""Use only endpoints published by this run's already-bound disposable fixture."""
import argparse
import json
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request


def read_manifest(path):
    text = Path(path).read_text()
    fields = dict(line.split('=', 1) for line in text.splitlines() if '=' in line)
    ports = [int(fields[key]) for key in ('controlPort', 'mutualPort', 'privateCaPort', 'wrongHostPort')]
    if len(set(ports)) != 4 or not all(1 <= port <= 65535 for port in ports):
        raise ValueError('Invalid fixture endpoints')
    return text, fields, ports


def request(port, route, timeout=5):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(f'http://127.0.0.1:{port}{route}', timeout=timeout) as response:
        return response.read(1_048_577).decode()


def await_ready(bootstrap, report_path, timeout=180):
    """Provision a healthy owned dependency after boot; never retries a native assertion."""
    if not 0 < timeout <= 180:
        raise ValueError('Fixture readiness must stay within its existing 180-second setup budget')
    if report_path.exists():
        raise ValueError('Refusing to overwrite prior fixture-readiness evidence')
    text, _, ports = read_manifest(bootstrap)
    started = time.monotonic()
    report = {'budgetSeconds': timeout, 'status': 'failed', 'attempts': []}
    try:
        while True:
            remaining = timeout - (time.monotonic() - started)
            if remaining <= 0:
                raise TimeoutError('Owned fixture did not become healthy after simulator boot')
            attempt_started = time.monotonic()
            attempt = {'startedSeconds': attempt_started - started}
            try:
                response = request(ports[0], '/fixture', timeout=min(5, remaining))
                if response != text:
                    attempt['outcome'] = 'wrong identity'
                    raise ValueError('Fixture identity does not match this run; refusing readiness')
                attempt['outcome'] = 'ready'
                report['status'] = 'ready'
                return
            except (OSError, urllib.error.URLError) as error:
                attempt.update(outcome='transport unavailable', errorType=type(error).__name__)
            finally:
                attempt['seconds'] = time.monotonic() - attempt_started
                report['attempts'].append(attempt)
            remaining = timeout - (time.monotonic() - started)
            if remaining > 0:
                time.sleep(min(0.25, remaining))
    finally:
        report['seconds'] = time.monotonic() - started
        report_path.write_text(json.dumps(report, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('ready', 'await-ready', 'ports', 'control-port', 'stop'))
    parser.add_argument('bootstrap')
    parser.add_argument('--readiness-report', type=Path)
    args = parser.parse_args()
    if args.action == 'await-ready':
        if args.readiness_report is None:
            parser.error('Post-boot readiness requires a fresh evidence report')
        await_ready(args.bootstrap, args.readiness_report)
        return
    text, fields, ports = read_manifest(args.bootstrap)
    if args.action == 'ports':
        print('\n'.join(map(str, ports)))
    elif args.action == 'control-port':
        print(ports[0])
    else:
        if request(ports[0], '/fixture') != text:
            raise ValueError('Fixture identity does not match this run')
        if args.action == 'stop':
            request(ports[0], '/stop?token=' + urllib.parse.quote(fields['stopToken'], safe=''))


if __name__ == '__main__':
    main()
