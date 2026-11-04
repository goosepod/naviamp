#!/usr/bin/env python3
"""Only an explicit manual dispatch may opt into an individually owned local runner."""
import json
import os
import re


def select_runner(event, label):
    if event != 'workflow_dispatch' or not label:
        return 'macos-latest'
    if not re.fullmatch(r'naviamp-local-[a-f0-9]{32}', label):
        raise ValueError('Local runner label must be naviamp-local- followed by a UUID hex value')
    return ['self-hosted', 'macOS', 'ARM64', label]


if __name__ == '__main__':
    print('macos_runner=' + json.dumps(select_runner(
        os.environ.get('GITHUB_EVENT_NAME', ''), os.environ.get('MACOS_RUNNER_LABEL', ''))))
