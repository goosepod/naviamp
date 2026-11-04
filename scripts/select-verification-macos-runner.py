#!/usr/bin/env python3
"""Only explicit owner selection may opt into an individually owned local runner."""
import json
import os
import re


def select_runner(event, label, local_pr_authorized=False):
    if event == 'pull_request' and local_pr_authorized and not label:
        raise ValueError('Owner-selected local PR verification requires its per-PR runner variable')
    if (event != 'workflow_dispatch' and not (event == 'pull_request' and local_pr_authorized)) or not label:
        return 'macos-latest'
    if not re.fullmatch(r'naviamp-local-[a-f0-9]{32}', label):
        raise ValueError('Local runner label must be naviamp-local- followed by a UUID hex value')
    return ['self-hosted', 'macOS', 'ARM64', label]


if __name__ == '__main__':
    print('macos_runner=' + json.dumps(select_runner(
        os.environ.get('GITHUB_EVENT_NAME', ''), os.environ.get('MACOS_RUNNER_LABEL', ''),
        os.environ.get('LOCAL_PR_AUTHORIZED', '') == 'true')))
