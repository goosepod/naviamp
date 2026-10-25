# Windows Store first-launch credential persistence (#337)

## Problem and native boundary

The Microsoft Partner Center report for `9PCFJ644LXLL`, completed October 23, 2026, rejected
submission `1152921505702215205` under 10.1.2.10 for a launch crash on Windows `26300.9711`
(Dell Latitude 5410). PowerShell `WriteAllBytes` raised `DirectoryNotFoundException` for
`AppData/Roaming/Naviamp/credential-key.dpapi`.

The shared owner is `StorageCredentialProtector`, consumed by `StorageMediaSourceStore` for
credential persistence and migration. Its policy and common behavior are unchanged. The concrete
native boundary in the only changed platform production file,
`platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/security/DesktopCredentialProtector.kt`,
is Windows CurrentUser DPAPI via PowerShell/.NET, JVM ProcessBuilder and JVM filesystem IO.
All credential adapters were inspected before editing.

The released adapter creates directories in the JVM but reads/writes files in a PowerShell child.
The fix keeps all key file IO in the application process and uses the helper only to transform
base64 bytes over stdin/stdout. Helpers stop on errors before any persisted file is touched.
Raw binary DPAPI format, CurrentUser scope, key location, and credential encryption format remain
unchanged. No migration or new setting is introduced.

Microsoft documents redirected AppData writes and package-specific filesystem views:
https://learn.microsoft.com/en-us/windows/msix/desktop/desktop-to-uwp-behind-the-scenes#appdata-operations-on-windows-10-version-1903-and-later

## Exact v2.9.0 source and artifacts

The owner explicitly requires the Store build to remain based on v2.9.0. The replacement is built
from `fix/337-store-credential-persistence-v2.9.0`, commit
`621fa44f11e6ecf79520008e8f62f5d719d0838d`, based on tag `v2.9.0`
(`826e14bf1c46dba8d604515f1bf77bf6afcd541d`). Its complete diff from that tag is the Windows
credential adapter and `DesktopCredentialProtectorTest.kt`. `VERSION` remains `v2.9.0` and
`VERSION_CODE` remains `57`. No main/v2.10.0 application code is substituted.

- Rebuilt input ZIP SHA-256: `c776276e22bbdeba4b20cc3b1c57c0f5933b6f6094daf138c5b12e88128b0db6`.
- Tested unsigned MSIX SHA-256: `5b448229f8cdbf669833f42cf0fbda4e9e464b03265e76b90d3fa1014c779550`.
- Final upload MSIX SHA-256: `0eae4fafefbadebcefbd22d9e15a4e99dec1a03a0c0d2bbef79167f8c0e824b4`.
- Upload filename: `Naviamp-v2.9.0-windows-x64-store-fix337-2.9.58.0.msix`.
- Identity: `Goosepod.Naviamp`, x64, wrapper version `2.9.58.0` (app remains v2.9.0/code 57).
- Publisher: `CN=71F0ADF4-9FDC-421A-AD04-F920729B2A5A`.

The #324 manifest/assets were used for the wrapper. MakeAppx schema and semantic validation passed.
Independent verification matched all 268 packaged application files to the rebuilt ZIP. These are
rebuilt backport bytes, not the unchanged original public ZIP. Local test metadata is explicitly
labeled as such; it must not be mistaken for published-release provenance. Separate signed local
copies were used for installation. Partner Center rejected replacement bytes with the original
`2.9.57.0` full package name, and would not save removal without a replacement. Only the wrapper
version was incremented to `2.9.58.0`. MakeAppx validation passed again and all 268 application files
were independently reverified against the same tested ZIP. No application code changed after testing.

The original rejected MSIX was downloaded from CI run `39975612140` and verified against SHA-256
`c71b45f8cd3fa8445bbcb38bd95ca27c287a1eed664bf53b7685c0216f5d4fd8` before testing.
The original public ZIP and release tag remain untouched. #324's existing workflow still pins that
original public ZIP and must not be reused for resubmission without incorporating this fix.

## Automated verification

On main: 5 native credential tests and 8 shared `StorageMediaSourceStoreTest` tests passed with zero
failures/errors/skips. `verifyCoreFirstArchitecture` passed. On the exact v2.9.0 backport: all 5
native tests passed with zero failures/errors/skips; `packageReleaseDistributable` and the
architecture guard passed.

The native tests use real Windows DPAPI and cover missing nested directories and spaces, missing
key reads, write/read/reopen, protected bytes differing from plaintext, encryption failure preserving
an existing file, and reading a file produced independently by the released file-based helper.
Existing credential encrypt/decrypt, stability, and wrong-key rejection tests also pass.

```powershell
./gradlew.bat :platforms:desktop:desktopTest --tests '*DesktopCredentialProtectorTest' `
  '-Pkotlin.native.enableKlibsCrossCompilation=false' '-Pnaviamp.bass.platform=windows-x64' `
  '-Pnaviamp.bass.test.outputDevice=0' --console=plain
./gradlew.bat :apps:desktop:packageReleaseDistributable verifyCoreFirstArchitecture `
  '-Pkotlin.native.enableKlibsCrossCompilation=false' '-Pnaviamp.bass.platform=windows-x64'
```

The broader `stageReleaseArtifacts` attempt produced the ZIP but failed on the unrelated EXE
installer because WiX was absent from PATH. MSI/EXE success is not claimed; neither is needed for
this MSIX wrapper. Full hosted CI, WACK, Windows restart, and renewed Store certification are not
claimed by these focused checks.

## Packaged Windows acceptance (October 25)

The test host is Windows `26300.9711`, the same OS build reported by certification.

A disposable normal-user account installed the signed test copy successfully with no existing
Naviamp data. Windows activation in that account was blocked by `0x80070520` because it lacked an
interactive sign-in session. This is a test setup limitation, not an application launch result.
Its package was removed. Windows Sandbox is not installed on this host.

Normal activation was then tested in the active desktop session with the idle standalone Naviamp
data directory preserved by a guarded same-directory rename. The real Naviamp data directory was
absent before package activation. Both JVM processes were verified to hold the expected Store
package identity. The original rejected package created no key and entered Compose's startup error
dialog, confirmed by its JVM thread dump. Its exact dialog message was not captured locally; the
certification screenshot supplies the DirectoryNotFoundException evidence.

The fixed package passed:

- Visible first launch to the fresh Connection Details screen; owner also confirmed it opened.
- Package-context probe found the new key and CurrentUser DPAPI decrypted it to exactly 32 bytes.
- Owner entered the public Navidrome demo login directly, following the authentication handoff rule.
- After closing and relaunching the app, Home reopened with saved login and no authentication prompt.
- A fresh artist page loaded after relaunch, demonstrating use of the retained connection.
- Key hash remained `b35b8dc318d04358a0c9a4af0e70b5d09e0dc1ae354d92914a4683fc6de61faa` and still
  decrypted to 32 bytes. No raw key bytes were recorded.
- Binaerpilot / Tokyomatrix 3000 streamed as MP3. Position visibly advanced from 0:09 to 0:17 and
  playback was paused at 0:27 before cleanup.
- Test installation was removed; standalone data directory restored; all eight recorded file hashes
  matched (settings, DPAPI key, databases/WAL/SHM and window geometry).

The key probe runs inside the package's filesystem view; ordinary outside-package physical paths
do not reliably expose the same virtualized key. The accepted first-launch test used fresh Naviamp
application data in an existing signed-in Windows account, not a newly signed-in Windows account.
No living-room TV control was performed.

## Upload and cleanup status

The owner authorized upload to the existing Microsoft Store draft in Edge. The `2.9.58.0`
replacement passed Partner Center package validation and was saved. Reopening Packages confirmed
only the replacement remains, and the overview reports Packages Complete. The existing runFullTrust
approval warning remains; there are no blocking package errors. Reviewer testing instructions were
updated with the launch fix, exact wrapper version and fresh-data/relaunch acceptance results,
preserving the public-demo instructions and known v2.9.0 rendering issue. Nothing has been
resubmitted or published. The existing submission setting publishes automatically after approval;
the owner must authorize resubmission with that consequence before the final action.

The current-user test package is removed and the standalone profile is restored and hash-verified.
Elevated cleanup completed with no errors: the disposable account and profile, TrustedPeople
certificate, private signing key, public staging directory and disposable encrypted credential were
removed. The private local checkpoint records the exact cleanup targets and successful result.
Never upload/commit temporary account credentials, private lifecycle files or personal profiles.

Artifacts and local evidence are under workspace `build/store-crash-investigation/`; build logs
are under each worktree's `build/verification-337/`.
