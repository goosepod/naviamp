#!/usr/bin/env python3
"""Sign an existing Naviamp release bundle for Mac App Store / TestFlight.

The source application and release version stay unchanged. Signing inputs are
local arguments; no certificates, keys, or provisioning profiles belong in git.
"""
from __future__ import annotations

import argparse
import datetime
import plistlib
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

MACHO = {b'\xcf\xfa\xed\xfe', b'\xfe\xed\xfa\xcf', b'\xca\xfe\xba\xbe', b'\xbe\xba\xfe\xca'}


def run(*args: str) -> None:
    subprocess.run(list(args), check=True)


def plist(path: Path) -> dict:
    return plistlib.loads(path.read_bytes())


def write_plist(path: Path, value: dict) -> None:
    path.write_bytes(plistlib.dumps(value))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input-app', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True, help='JetBrains JBRSDK 21 home')
    parser.add_argument('--app-profile', type=Path, required=True)
    parser.add_argument('--runtime-profile', type=Path, required=True)
    parser.add_argument('--signing-identity', required=True)
    parser.add_argument('--installer-identity', required=True)
    parser.add_argument('--team-id', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--build', required=True)
    args = parser.parse_args()
    source = args.input_app.resolve()
    source_info = plist(source / 'Contents/Info.plist')
    if source_info.get('CFBundleShortVersionString') != args.version or str(source_info.get('CFBundleVersion')) != args.build:
        parser.error('Input app version/build does not match the requested release')
    bundle_id = source_info['CFBundleIdentifier']
    if bundle_id != 'app.naviamp.desktop':
        parser.error('Expected the Naviamp desktop bundle identifier')
    java_home = args.java_home.resolve()
    java_release = (java_home / 'release').read_text()
    if 'IMPLEMENTOR="JetBrains' not in java_release or 'JAVA_VERSION="21.' not in java_release:
        parser.error('Use JBRSDK 21: the macOS store sandbox input check requires the JetBrains runtime')
    if args.output.exists():
        parser.error('Output must be a new directory; existing packages are never overwritten')
    args.output.mkdir(parents=True)
    root = args.output.resolve()
    app = root / 'Naviamp.app'
    shutil.copytree(source, app, symlinks=True)
    # Validate Apple-issued profile identities before embedding them.
    profiles = {}
    for profile, expected, target in [
        (args.app_profile, bundle_id, app / 'Contents/embedded.provisionprofile'),
        (args.runtime_profile, 'com.oracle.java.' + bundle_id, app / 'Contents/runtime/Contents/embedded.provisionprofile'),
    ]:
        info = plistlib.loads(subprocess.check_output(['security', 'cms', '-D', '-i', str(profile.resolve())]))
        if info['Entitlements'].get('com.apple.application-identifier') != args.team_id + '.' + expected:
            raise ValueError(f'Provisioning profile does not match {expected}')
        if info['ExpirationDate'] <= datetime.datetime.now(datetime.UTC).replace(tzinfo=None):
            raise ValueError('Provisioning profile has expired')
        shutil.copyfile(profile, target)
        profiles[expected] = info
    # Preserve the release runtime's module set, replacing only its OS runtime.
    runtime_home = app / 'Contents/runtime/Contents/Home'
    modules = next(line.split('=', 1)[1].strip('"') for line in (runtime_home / 'release').read_text().splitlines() if line.startswith('MODULES='))
    linked = root / 'linked-java-runtime'
    run(str(java_home / 'bin/jlink'), '--add-modules', ','.join(modules.split()), '--strip-debug', '--no-header-files', '--no-man-pages', '--output', str(linked))
    shutil.rmtree(runtime_home)
    shutil.move(str(linked), runtime_home)
    shutil.copyfile(runtime_home / 'lib/libjli.dylib', app / 'Contents/runtime/Contents/MacOS/libjli.dylib')
    base = {
        'com.apple.security.app-sandbox': True,
        'com.apple.security.network.client': True,
        'com.apple.security.network.server': True,
        'com.apple.security.files.user-selected.read-write': True,
        'com.apple.security.cs.allow-jit': True,
        'com.apple.security.cs.allow-unsigned-executable-memory': True,
    }
    app_ent = dict(base, **profiles[bundle_id]['Entitlements'])
    runtime_ent = dict(base, **profiles['com.oracle.java.' + bundle_id]['Entitlements'])
    child_ent = {'com.apple.security.app-sandbox': True, 'com.apple.security.inherit': True}
    for name, value in [('app', app_ent), ('runtime', runtime_ent), ('child', child_ent)]:
        write_plist(root / (name + '-entitlements.plist'), value)

    def sign(path: Path, entitlements: str | None = None) -> None:
        command = ['codesign', '--force', '--sign', args.signing_identity, '--timestamp', '--options', 'runtime']
        if entitlements:
            command += ['--entitlements', str(root / (entitlements + '-entitlements.plist'))]
        run(*command, str(path))

    def thin_arm64(path: Path) -> None:
        architectures = subprocess.check_output(['lipo', '-archs', str(path)], text=True).split()
        if 'arm64' in architectures and len(architectures) > 1:
            temporary = path.with_name(path.name + '.arm64')
            run('lipo', str(path), '-thin', 'arm64', '-output', str(temporary))
            shutil.copymode(path, temporary)
            temporary.replace(path)

    libraries = app / 'Contents/app'
    # SQLite and JNA must load signed native code from the bundle, not extracted
    # temporary files. Signing the other jar members also preserves validation
    # when a library's supported loading path uses its packaged resources.
    direct = {
        'org/sqlite/native/Mac/aarch64/libsqlitejdbc.dylib': 'libsqlitejdbc.dylib',
        'com/sun/jna/darwin-aarch64/libjnidispatch.jnilib': 'libjnidispatch.jnilib',
    }
    found = set()
    for jar in libraries.glob('*.jar'):
        with zipfile.ZipFile(jar) as z:
            native = [name for name in z.namelist() if name.endswith(('.dylib', '.jnilib'))]
            if not native:
                continue
            with tempfile.TemporaryDirectory(dir=root) as temp:
                replacements = {}
                for index, name in enumerate(native):
                    target = Path(temp) / (str(index) + '-' + Path(name).name)
                    target.write_bytes(z.read(name))
                    thin_arm64(target)
                    sign(target)
                    replacements[name] = target.read_bytes()
                    if name in direct:
                        (libraries / direct[name]).write_bytes(replacements[name])
                        found.add(name)
                new_jar = jar.with_suffix('.signed-jar')
                with zipfile.ZipFile(new_jar, 'w') as output:
                    for entry in z.infolist():
                        output.writestr(entry, replacements.get(entry.filename, z.read(entry.filename)))
        new_jar.replace(jar)
    if found != set(direct):
        raise ValueError('Missing Apple Silicon SQLite/JNA native libraries')
    config = libraries / 'Naviamp.cfg'
    text = config.read_text()
    if 'org.sqlite.lib.path' in text or 'jna.boot.library.path' in text:
        raise ValueError('Input already has custom SQLite/JNA paths; inspect before packaging')
    config.write_text(text + '\njava-options=-Dorg.sqlite.lib.path=$APPDIR\njava-options=-Dorg.sqlite.lib.name=libsqlitejdbc.dylib\njava-options=-Djna.boot.library.path=$APPDIR\n')
    source_info['LSApplicationCategoryType'] = 'public.app-category.music'
    # App Store Connect requires macOS 12+ for Apple Silicon-only applications.
    minimum = source_info.get('LSMinimumSystemVersion', '0')
    if int(minimum.split('.')[0]) < 12:
        source_info['LSMinimumSystemVersion'] = '12.0'
    write_plist(app / 'Contents/Info.plist', source_info)
    for path in sorted(app.rglob('*'), key=lambda p: len(p.parts), reverse=True):
        if not path.is_file() or path.is_symlink():
            continue
        with path.open('rb') as file:
            magic = file.read(4)
        if magic in MACHO:
            thin_arm64(path)
            # The Metal visualizer is an executable despite its .dylib filename.
            file_kind = subprocess.check_output(['file', '-b', str(path)], text=True)
            executable = 'executable' in file_kind or path.name == 'jspawnhelper'
            sign(path, 'child' if executable else None)
    sign(app / 'Contents/runtime', 'runtime')
    sign(app, 'app')
    run('codesign', '--verify', '--deep', '--strict', '--verbose=2', str(app))
    package = root / f'Naviamp-{args.version}-{args.build}.pkg'
    run('productbuild', '--component', str(app), '/Applications', '--sign', args.installer_identity, str(package))
    run('pkgutil', '--check-signature', str(package))
    archive = root / 'Naviamp.xcarchive'
    (archive / 'Products/Applications').mkdir(parents=True)
    shutil.copytree(app, archive / 'Products/Applications/Naviamp.app', symlinks=True)
    write_plist(archive / 'Info.plist', {
        'ArchiveVersion': 2, 'Name': 'Naviamp', 'SchemeName': 'Naviamp',
        'CreationDate': datetime.datetime.now(datetime.UTC).replace(tzinfo=None),
        'ApplicationProperties': {
            'ApplicationPath': 'Applications/Naviamp.app', 'CFBundleIdentifier': bundle_id,
            'CFBundleShortVersionString': args.version, 'CFBundleVersion': args.build,
            'SigningIdentity': args.signing_identity, 'Team': args.team_id,
        },
    })
    write_plist(root / 'export-options.plist', {
        'method': 'app-store-connect', 'destination': 'export', 'teamID': args.team_id,
        'signingStyle': 'manual', 'signingCertificate': 'Apple Distribution',
        'installerSigningCertificate': '3rd Party Mac Developer Installer',
        # Xcode treats the runtime as a bundle rather than a profile-capable app;
        # its valid profile is already embedded and signed by this script.
        'provisioningProfiles': {bundle_id: profiles[bundle_id]['Name']},
        'manageAppVersionAndBuildNumber': False, 'uploadSymbols': False,
    })
    print(f'Prepared {package}; app version {args.version}, build {args.build}')


if __name__ == '__main__':
    main()
