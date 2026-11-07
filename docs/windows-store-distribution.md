# Microsoft Store distribution

Tracking: [#323](https://github.com/goosepod/naviamp/issues/323).

## First submission: released v2.9.0

The first Store package wraps the **published v2.9.0 Windows x64 standalone ZIP**.
It does not rebuild the application from main, modify the launcher configuration, or
replace any JAR, JVM, BASS binary or native helper. All 268 application payload files
must match the public release byte-for-byte. The added files are the MSIX manifest
and three Windows brand assets; MakeAppx adds its block map and content types.

The input URL and published GitHub asset SHA-256 are pinned in
[`release.json`](../apps/desktop/packaging/windows-store/release.json). Changing that
lock is an explicit packaging review, never an automatic selection of "latest".
The source release tag remains immutable; this work does not create a new app release.

## Partner Center identity

| Property | Value |
| --- | --- |
| Display name | Naviamp |
| Publisher display name | Goosepod |
| Identity name | Goosepod.Naviamp |
| Identity publisher | CN=71F0ADF4-9FDC-421A-AD04-F920729B2A5A |
| Package family | Goosepod.Naviamp_fc7t29wmhhnwe |
| Store ID | 9PCFJ644LXLL |
| Initial MSIX version | 2.9.57.0 |
| Public application version | v2.9.0 (build 57) |

The MSIX version follows the existing Windows installer version (`2.9.57`) with a
zero fourth component reserved for Store use. Future submissions must retain the
identity and increase the package version. Do not use the fourth component for
packaging revisions. The initial package is x64 only, Windows Desktop 10.0.19041+
(Windows 10 2004 / Windows 11), using the full-trust desktop entry point. This is
an initial packaging target; real hardware acceptance is still required.

[Partner Center product](https://partner.microsoft.com/en-US/dashboard/products/9PCFJ644LXLL/overview)
has Submission 1 **In certification** as of 2026-10-23, with automatic publication
selected after certification passes. The owner approved submitting unchanged v2.9.0
with the observed popup/tooltip defects as a known issue, targeting the fix to
[v2.10.0 / #325](https://github.com/goosepod/naviamp/issues/325). See the
[submission evidence](verification/windows-store-msix-323.md) for the exact uploaded
CI checksum, acceptance limitations, listing, age ratings and cleanup record.
Submission is not certification approval or Store publication.

## Build and verify

Requirements: Windows x64, Python 3.11+, and the Windows 10/11 SDK x64 MakeAppx tool.
Neither Gradle nor a Java development kit is needed: the published ZIP already has
the application runtime. Run from a checkout containing the packaging scripts:

```powershell
python -m unittest discover -s scripts/tests -p test_windows_store_package.py -v
./scripts/build-windows-store.ps1
```

To use an already downloaded release or a portable SDK:

```powershell
./scripts/build-windows-store.ps1 `
  -ReleaseZip C:\Downloads\Naviamp-v2.9.0-windows-x64-release.zip `
  -MakeAppxPath 'C:\SDK\bin\10.0.26100.0\x64\makeappx.exe' `
  -OutputDirectory C:\Builds\naviamp-store-290
```

Use a fresh output directory for each build. The script verifies the release hash,
rejects unsafe/ambiguous ZIP paths, checks the native x64 payload, prepares a new
layout, invokes MakeAppx **without `/nv`**, and compares every packaged application
file with the pinned source ZIP. It also rejects missing/extra application files,
incorrect Store identity, changed assets and unexpected capabilities.

Outputs in `build/windows-store` (or the chosen directory):

- `Naviamp-v2.9.0-windows-x64-store.msix`: **unsigned Store submission package**.
- `provenance.json`: release identity, source hash, package hash and every payload hash.
- `SHA256SUMS`: checksum for this exact package.
- `makeappx.log`: Windows SDK validation/build output.
- `layout-<id>`: development deployment layout; not an installer.

The separate **Windows Store package** workflow runs on packaging pull requests and
manual dispatches. Its `windows-store-v2.9.0` artifact retains the package, hashes,
provenance and SDK log. It does not upload to Partner Center, publish to customers,
or add assets to the existing GitHub release. Application release CI stays unchanged.

The verifier's ZIP fixtures test content integrity; only the real MakeAppx build
tests the Windows package format. Neither replaces interactive acceptance or WACK.

## Signing and local deployment

Microsoft signs the package after Store certification. Do not buy a signing
certificate just to upload this MSIX, and do not upload a locally test-signed copy.
The unsigned upload artifact cannot be installed through the ordinary signed-package
path before Store signing.

For local acceptance, use an existing developer-enabled test machine to register
the loose layout, or create a separate test-signed copy and explicitly trust its
test certificate on a dedicated Windows test account/machine. Do not silently
enable Developer Mode, install root certificates, or alter production user data.
Keep test signing separate from the unsigned upload artifact. Remove temporary
test certificates and private keys after the local test lifecycle is complete.

After the operator authorizes temporary machine trust, the test lifecycle script
signs a separate copy using a one-day, non-exportable key. Run it in an elevated
**interactive Windows desktop session**, such as Sunshine's Desktop through Moonlight:

```powershell
./scripts/test-windows-store-install.ps1 -Action Install `
  -StateDirectory C:\Builds\naviamp-store-test `
  -UnsignedPackage C:\Builds\Naviamp-v2.9.0-windows-x64-store.msix `
  -ExpectedUnsignedSha256 '<exact candidate SHA-256 from SHA256SUMS>' `
  -SignToolPath 'C:\SDK\bin\10.0.26100.0\x64\signtool.exe' `
  -AllowTemporaryCertificateTrust

# After testing, removes the recorded test package, signed copy, trust and private key:
./scripts/test-windows-store-install.ps1 -Action Cleanup `
  -StateDirectory C:\Builds\naviamp-store-test
```

The script refuses to replace an existing Store installation and verifies that
the unsigned candidate is unchanged. On installation failure it automatically
cleans up. On success, run `Cleanup` after testing. It never imports a root CA or
exports the private key. Use a fresh state directory for each installation attempt.
An SSH logon can validate the signature but fail AppX deployment when Windows
cannot initialize its process-lifecycle manager; use the logged-in desktop for
the installation and app acceptance checks.

## Acceptance and submission gates

Record results against the exact package SHA-256, Windows build, hardware and test
account. Leave #323 open until these checks are satisfied:

- Install and launch from Start with the correct identity, app name and icon.
- Launch after a Windows restart, close/reopen, minimize/restore and resize.
- Authenticate to a reviewer-accessible server; verify credential persistence and
  DPAPI protection after relaunch. Test unavailable-server and offline behavior.
- Play, pause, seek, switch audio output, use media keys and continue background
  playback. Verify BASS helpers load from the installed package.
- Download, play offline, delete a download, import/export settings and confirm
  writable data/cache paths stay outside the immutable installed package.
- Check screen-reader access and keyboard navigation in the installed app.
- Test a package upgrade and confirm settings, credentials and downloads survive.
- Test coexistence with the existing MSI/EXE release. Windows data redirection can
  affect `%APPDATA%/Naviamp`; verify isolation and migration before promising either.
  Do not run two installs against the same writable database during testing.
- Uninstall the test package and inspect its data behavior without removing the
  user's separate installed Naviamp data.
- Run Windows App Certification Kit and retain its reports. Passing MakeAppx is
  not a WACK result or Microsoft certification approval.

Submission 1 additionally needs pricing/markets, Music category and properties,
privacy/support URLs, IARC age rating, English description and screenshots, and
reviewer credentials/instructions. Explain `runFullTrust`: the existing desktop
application bundles a JVM and native BASS playback/helpers and needs ordinary
desktop audio, filesystem, media controls and secure credential APIs. It does not
require administrator privileges.

The released application can show GitHub release notifications and open the release
page; it does not replace its own installed binaries. Review that experience during
Store acceptance so customers understand that package updates arrive from the Store.
Any required product behavior change belongs in shared Core and a separately reviewed
release; do not patch the released JARs in this wrapper.

Do not submit for certification until acceptance and listing preparation are complete.

## Microsoft references

- [Manual desktop MSIX packaging](https://learn.microsoft.com/en-us/windows/msix/desktop/desktop-to-uwp-manual-conversion)
- [MakeAppx validation and CLI](https://learn.microsoft.com/en-us/windows/msix/package/create-app-package-with-makeappx-tool)
- [Store package identity, signing and version requirements](https://learn.microsoft.com/en-us/windows/apps/publish/publish-your-app/msix/app-package-requirements)
