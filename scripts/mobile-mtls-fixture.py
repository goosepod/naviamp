#!/usr/bin/env python3
"""Use only endpoints published by this run's already-bound disposable fixture."""
import argparse
from pathlib import Path
import urllib.parse
import urllib.request


def read_manifest(path):
    text = Path(path).read_text()
    fields = dict(line.split('=', 1) for line in text.splitlines() if '=' in line)
    ports = [int(fields[key]) for key in ('controlPort', 'mutualPort', 'privateCaPort', 'wrongHostPort')]
    if len(set(ports)) != 4 or not all(1 <= port <= 65535 for port in ports):
        raise ValueError('Invalid fixture endpoints')
    return text, fields, ports


def request(port, route):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(f'http://127.0.0.1:{port}{route}', timeout=5) as response:
        return response.read(1_048_577).decode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('ready', 'ports', 'control-port', 'stop'))
    parser.add_argument('bootstrap')
    args = parser.parse_args()
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
