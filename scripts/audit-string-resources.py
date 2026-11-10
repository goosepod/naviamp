#!/usr/bin/env python3
"""Inventory string consumers and review candidates; never delete resources automatically.

Run at the repository root. --catalog-ref reads catalogs from a Git revision while
checking consumers in the working tree, so a removal inventory is reproducible.
Names in comments/imports count conservatively as references. A missing name is a
review candidate, not proof of non-use: inspect dynamic/native/generated consumers.
"""
import argparse
import json
from pathlib import Path
import plistlib
import re
import subprocess
import xml.etree.ElementTree as ET

TYPES = {'string', 'plurals', 'string-array'}
CONSUMER_SUFFIXES = {
    '.kt', '.kts', '.java', '.swift', '.m', '.mm', '.h', '.c', '.cpp',
    '.xml', '.plist', '.strings', '.stringsdict', '.xcstrings', '.json',
    '.py', '.sh', '.ps1', '.yml', '.yaml', '.properties', '.pbxproj',
}
TOKEN = re.compile(r'\b[A-Za-z_][A-Za-z_0-9]*\b')


def resource_names(text):
    root = ET.fromstring(text)
    if root.tag != 'resources':
        return set()
    return {node.attrib['name'] for node in root if node.tag in TYPES}


def references(keys, sources):
    result = {key: [] for key in keys}
    for path, text in sources:
        for key in set(TOKEN.findall(text)) & keys:
            result[key].append(path)
    return result


def audit(repo, catalog_ref=None):
    paths = subprocess.check_output(['git', 'ls-files', '-z'], cwd=repo).decode().split('\0')
    catalogs = {}
    sources = []
    for relative in filter(None, paths):
        path = repo / relative
        if path.suffix not in CONSUMER_SUFFIXES:
            continue
        text = (plistlib.dumps(plistlib.loads(path.read_bytes())).decode()
                if path.suffix == '.plist' else path.read_text(encoding='utf-8'))
        catalog_text = text
        if catalog_ref and path.suffix == '.xml':
            catalog_text = subprocess.check_output(
                ['git', 'show', f'{catalog_ref}:{relative}'], cwd=repo).decode()
        names = resource_names(catalog_text) if path.suffix == '.xml' else set()
        if names:
            catalogs[relative] = names
            # Values can reference other resources. Definitions are not roots.
            root = ET.fromstring(catalog_text)
            sources.append((relative, ' '.join(root.itertext())))
        else:
            sources.append((relative, text))
    keys = set().union(*catalogs.values())
    usages = references(keys, sources)
    return {
        'catalog_ref': catalog_ref or 'working tree',
        'catalogs': {path: len(keys) for path, keys in sorted(catalogs.items())},
        'native_catalogs': sorted(p for p in paths if Path(p).suffix in {
            '.strings', '.stringsdict', '.xcstrings'}),
        'resource_count': len(keys),
        'candidates': sorted(key for key, paths in usages.items() if not paths),
        'references': {key: sorted(paths) for key, paths in sorted(usages.items()) if paths},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path.cwd())
    parser.add_argument('--catalog-ref')
    args = parser.parse_args()
    print(json.dumps(audit(args.repo, args.catalog_ref), indent=2))


if __name__ == '__main__':
    main()
