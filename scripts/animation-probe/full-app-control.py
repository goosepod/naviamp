#!/usr/bin/env python3
"""Send a test command to FullAppObserver; reply files retain reproducible evidence."""
import argparse
from pathlib import Path
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', type=Path)
parser.add_argument('command', choices=['dump', 'action', 'focus', 'measure', 'minimize', 'restore', 'resize', 'state', 'trace', 'bridge'])
parser.add_argument('arguments', nargs='*')
args = parser.parse_args()
identifier = str(time.time_ns())
args.directory.mkdir(parents=True, exist_ok=True)
with (args.directory / 'commands.txt').open('a') as commands:
    commands.write('\t'.join([identifier, args.command, *args.arguments]) + '\n')
deadline = time.monotonic() + (int(args.arguments[1]) if args.command == 'measure' else 0) + 30
reply = args.directory / f'{identifier}.reply'
while not reply.exists():
    if time.monotonic() > deadline:
        raise SystemExit('Observer did not reply; inspect its app.log')
    time.sleep(.2)
result = reply.read_text()
print(result, end='')
if result.startswith('ERROR'):
    raise SystemExit(1)
