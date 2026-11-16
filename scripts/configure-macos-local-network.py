#!/usr/bin/env python3
"""Generate/check Apple local-network declarations from shared strings and service constants."""
import argparse
import json
from pathlib import Path
import plistlib
import re
import xml.etree.ElementTree as ET

RESOURCE_KEY = 'permission_local_network_usage'
PLIST_KEY = 'NSLocalNetworkUsageDescription'
ENTRY = re.compile(r'"?NSLocalNetworkUsageDescription"?\s*=\s*"((?:\\.|[^"\\])*)"\s*;')


def shared_metadata(repo):
    catalogs = repo / 'core/ui/src/commonMain/composeResources'
    translations = {}
    for path in sorted(catalogs.glob('values*/strings.xml')):
        element = ET.parse(path).getroot().find(f"string[@name='{RESOURCE_KEY}']")
        text = ''.join(element.itertext()).strip() if element is not None else ''
        if not text:
            raise ValueError(f'{path}: missing {RESOURCE_KEY}')
        folder = path.parent.name
        language = 'en' if folder == 'values' else folder.removeprefix('values-').replace('-r', '-')
        language = {'in': 'id', 'zh-CN': 'zh-Hans', 'zh-TW': 'zh-Hant'}.get(language, language)
        if language in translations:
            raise ValueError(f'Duplicate native language: {language}')
        translations[language] = text
    if 'en' not in translations:
        raise ValueError('Missing default shared strings')
    sources = [
        ('core/domain/src/commonMain/kotlin/app/naviamp/domain/connect/NaviampConnectProtocol.kt',
         r'const val NaviampConnectServiceType\s*:\s*String\s*=\s*"([^"]+)"'),
        ('core/app/src/commonMain/kotlin/app/naviamp/app/NaviampCastDiscoveryController.kt',
         r'const val ServiceType\s*=\s*"([^"]+)"'),
    ]
    services = []
    for relative, pattern in sources:
        match = re.search(pattern, (repo / relative).read_text(encoding='utf-8'))
        if match is None:
            raise ValueError(f'Cannot read shared Bonjour service in {relative}')
        service = match.group(1).removesuffix('.local.')
        if not re.fullmatch(r'_[a-zA-Z0-9-]+\._tcp', service):
            raise ValueError(f'Invalid Bonjour declaration: {service}')
        services.append(service)
    return translations, sorted(set(services))


def strings_entry(text):
    escaped = text.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('\r', '\\r').replace('\t', '\\t')
    return f'"{PLIST_KEY}" = "{escaped}";'


def configure(repo, app, check=False, platform='macos'):
    if platform not in ('macos', 'ios'):
        raise ValueError(f'Unknown Apple platform: {platform}')
    translations, services = shared_metadata(repo)
    plist_path = app / ('Contents/Info.plist' if platform == 'macos' else 'Info.plist')
    resource_root = app / 'Contents/Resources' if platform == 'macos' else app
    with plist_path.open('rb') as handle:
        plist = plistlib.load(handle)
    if check:
        if plist.get(PLIST_KEY) != translations['en']:
            raise ValueError('Missing or outdated local-network usage description')
        if platform == 'macos' and sorted(plist.get('NSBonjourServices', [])) != services:
            raise ValueError('Bonjour declarations do not match the shared discovery service types')
        if not set(translations).issubset(plist.get('CFBundleLocalizations', [])):
            raise ValueError('Missing declared Apple localizations')
    else:
        plist[PLIST_KEY] = translations['en']
        if platform == 'macos':
            plist['NSBonjourServices'] = services
        plist['CFBundleLocalizations'] = sorted(set(plist.get('CFBundleLocalizations', [])) | set(translations))
        with plist_path.open('wb') as handle:
            plistlib.dump(plist, handle, sort_keys=False)
    for language, text in translations.items():
        path = resource_root / f'{language}.lproj/InfoPlist.strings'
        existing = path.read_text(encoding='utf-8') if path.exists() else ''
        matches = list(ENTRY.finditer(existing))
        if len(matches) > 1:
            raise ValueError(f'Duplicate local-network usage entries in {path}')
        if check:
            if not matches or json.loads('"' + matches[0].group(1) + '"') != text:
                raise ValueError(f'Missing or outdated usage translation: {language}')
        else:
            entry = strings_entry(text)
            updated = ENTRY.sub(lambda _: entry, existing) if matches else existing.rstrip() + '\n' + entry + '\n'
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(updated, encoding='utf-8')
    return len(translations)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--app', type=Path, required=True)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--platform', choices=['macos', 'ios'], default='macos')
    args = parser.parse_args()
    count = configure(args.repo, args.app, check=args.check, platform=args.platform)
    print(f'{"Mac" if args.platform == "macos" else "iOS"} local-network declarations {"verified" if args.check else "configured"}: {count} languages')


if __name__ == '__main__':
    main()
