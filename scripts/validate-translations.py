#!/usr/bin/env python3
"""Validate changed locale catalogs without starting Gradle or native tools.

Missing translated keys intentionally use source fallback. Existing keys must have
compatible types/format arguments; plural categories may differ by language.
"""
import argparse
from collections import Counter
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

RESOURCE_ROOT = Path('core/ui/src/commonMain/composeResources')
LOCALE = re.compile(r'^core/ui/src/commonMain/composeResources/values-[A-Za-z0-9-]+/strings\.xml$')
# Compose resource arguments are indexed string/integer conversions. Literal %
# (including the trailing percent in "%1$d%") and %% are not arguments.
ARGUMENT = re.compile(r'%(\d+)\$([A-Za-z])|(?<!%)%([sd])')
QUANTITIES = {'zero', 'one', 'two', 'few', 'many', 'other'}


def arguments(text):
    result = Counter()
    for match in ARGUMENT.finditer(text.replace('%%', '')):
        index, kind, unindexed = match.groups()
        if unindexed or kind not in {'s', 'd'} or int(index) < 1:
            raise ValueError(f'unsupported format argument: {match.group()}')
        result[(int(index), kind)] += 1
    return result


def catalog(path):
    data = path.read_bytes()
    if b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper():
        raise ValueError(f'{path}: XML declarations/entities are not permitted')
    root = ET.fromstring(data)
    if root.tag != 'resources' or root.attrib:
        raise ValueError(f'{path}: expected resources root without attributes')
    result = {}
    for node in root:
        name = node.get('name')
        if not name or name in result:
            raise ValueError(f'{path}: missing or duplicate resource name: {name}')
        if node.tag not in {'string', 'plurals', 'string-array'}:
            raise ValueError(f'{path}: unsupported resource type: {node.tag}')
        if set(node.attrib) != {'name'}:
            raise ValueError(f'{path}: unsupported attributes on {name}')
        if node.tag == 'string':
            if len(node):
                raise ValueError(f'{path}: nested markup in {name}')
        else:
            if not len(node) or any(item.tag != 'item' or len(item) for item in node):
                raise ValueError(f'{path}: invalid items in {name}')
            if node.tag == 'plurals':
                quantities = [item.get('quantity') for item in node]
                if ('other' not in quantities or len(set(quantities)) != len(quantities)
                        or any(q not in QUANTITIES for q in quantities)
                        or any(set(item.attrib) != {'quantity'} for item in node)):
                    raise ValueError(f'{path}: invalid plural quantities in {name}')
            elif any(item.attrib for item in node):
                raise ValueError(f'{path}: invalid array attributes in {name}')
        result[name] = node
    return result


def validate(source_path, translation_path):
    source = catalog(source_path)
    translated = catalog(translation_path)
    for name, node in translated.items():
        if name not in source:
            raise ValueError(f'{translation_path}: unknown resource {name}')
        original = source[name]
        if node.tag != original.tag:
            raise ValueError(f'{translation_path}: resource type mismatch: {name}')
        if node.tag == 'plurals':
            expected = arguments(''.join(original.find("item[@quantity='other']").itertext()))
            pairs = [(expected, ''.join(item.itertext())) for item in node]
        elif node.tag == 'string-array':
            if len(node) != len(original):
                raise ValueError(f'{translation_path}: array length mismatch: {name}')
            pairs = [(arguments(''.join(a.itertext())), ''.join(b.itertext()))
                     for a, b in zip(original, node)]
        else:
            pairs = [(arguments(''.join(original.itertext())), ''.join(node.itertext()))]
        for expected, text in pairs:
            if arguments(text) != expected:
                raise ValueError(f'{translation_path}: format arguments mismatch: {name}')


def changed_catalogs(base, head):
    result = subprocess.check_output(
        ['git', 'diff', '--no-renames', '--name-only', '-z', f'{base}...{head}'])
    return [Path(p) for p in result.decode().split('\0') if LOCALE.fullmatch(p)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base')
    parser.add_argument('--head')
    parser.add_argument('catalogs', nargs='*', type=Path)
    args = parser.parse_args()
    if bool(args.base) != bool(args.head):
        parser.error('--base and --head must be supplied together')
    paths = args.catalogs if not args.base else changed_catalogs(args.base, args.head)
    if not paths:
        parser.error('no translation catalogs selected')
    for path in paths:
        validate(RESOURCE_ROOT / 'values/strings.xml', path)
        print(f'Validated {path}')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, ET.ParseError, OSError, subprocess.CalledProcessError) as error:
        print(error, file=sys.stderr)
        sys.exit(1)
