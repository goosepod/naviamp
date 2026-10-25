# Windows Store first-launch credential persistence (#337)

## Certification evidence

The owner supplied the Microsoft Partner Center report for product `9PCFJ644LXLL`,
review completed October 23, 2026. Policy 10.1.2.10 rejected the submission for a launch crash on
Windows build 26300.9711 (Dell Latitude 5410). The error is `DirectoryNotFoundException` from
PowerShell `WriteAllBytes` targeting `AppData/Roaming/Naviamp/credential-key.dpapi`.
This supersedes the earlier in-certification status recorded for #323 / #324.

The submitted package contains unchanged v2.9.0 application bytes. Its Windows credential adapter
creates directories in the JVM but reads/writes their files in a PowerShell child process. A
packaged filesystem-view mismatch is the working diagnosis; it has not yet been independently
reproduced under the certification account. Microsoft documents redirected AppData writes for
virtualized packaged apps:
https://learn.microsoft.com/en-us/windows/msix/desktop/desktop-to-uwp-behind-the-scenes#appdata-operations-on-windows-10-version-1903-and-later

## Change and ownership

The shared owner is `StorageCredentialProtector`, consumed by `StorageMediaSourceStore` for
credential persistence/migration. Its behavior and common tests need no change for this native IO
failure. All platform credential adapters were inspected before editing.

Only `platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/security/DesktopCredentialProtector.kt`
is changed in platform production code. Its concrete native boundary is Windows CurrentUser DPAPI
via PowerShell/.NET, JVM ProcessBuilder, and JVM filesystem operations within the app's MSIX view.
PowerShell now receives/returns only base64 bytes over pipes. Directory creation and both file
reads/writes stay in the JVM process. No credential path is sent to the helper. Helpers stop on
errors before any persisted file is touched. The raw binary DPAPI file format, CurrentUser scope,
key location, and credential encryption format remain unchanged; no migration is needed.

## Verification

Native adapter tests exercise real Windows DPAPI, not a fake encryption implementation:

- First write into two absent nested directories, including spaces; missing file returns null.
- Read back and reopen the same persisted key in a new store instance.
- Confirm persisted bytes differ from the unprotected key.
- Invalid encryption input fails without changing a previously persisted file.
- Read a binary key produced independently by the released file-based helper without rewriting it.
- Existing protect/reveal, stability, and wrong-key rejection tests.

Command (Windows, installed CMake configured through CMAKE_EXE):

```powershell
./gradlew.bat :platforms:desktop:desktopTest --tests '*DesktopCredentialProtectorTest' `
  '-Pkotlin.native.enableKlibsCrossCompilation=false' '-Pnaviamp.bass.platform=windows-x64' `
  '-Pnaviamp.bass.test.outputDevice=0' --console=plain
```

Main branch results: 5 native credential tests and 8 shared StorageMediaSourceStore tests passed, zero failures/errors/skips. `verifyCoreFirstArchitecture` passed. Local logs are ignored under `build/verification-337`.
These adapter tests do not reproduce MSIX filesystem virtualization and are not a packaged launch pass.

## Store resubmission gate

No Store package, published release, submission, or existing user profile was changed by this fix.
The original v2.9.0 bytes cannot remain unchanged and also contain this code fix. A replacement
release/build must be selected and produced through the normal review/release process.

Before resubmission, test the exact replacement MSIX on a genuinely fresh Windows user profile
with no standalone Naviamp AppData, package state, or credential file. Launch through package
activation, connect using reviewer instructions, close/relaunch, confirm the same key persists
and login remains usable, then test existing-profile/standalone coexistence. Record package hash,
Windows build, package identity/version, logs and observations. Repeat certification checks and
update #323/#324 with this evidence. Do not treat an unpackaged test or an existing-profile install
as satisfying this gate.

