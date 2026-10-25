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
The original v2.9.0 bytes cannot remain unchanged and also contain this code fix. The owner explicitly
confirmed that the replacement must remain based on v2.9.0. A separate minimal backport branch starts
at `v2.9.0` (`826e14bf1c46dba8d604515f1bf77bf6afcd541d`) and changes only the native adapter and its
tests. Version metadata is unchanged; the private test rebuild is not the published original ZIP.

Backport source: `fix/337-store-credential-persistence-v2.9.0`, commit
`621fa44f11e6ecf79520008e8f62f5d719d0838d`. Its complete diff from v2.9.0 is the adapter and test
file listed above. All 5 native credential tests passed (zero failures/errors/skips), and
`packageReleaseDistributable` plus `verifyCoreFirstArchitecture` passed. The broader
`stageReleaseArtifacts` attempt produced the ZIP but failed on the unrelated EXE installer because
WiX was absent from PATH; no MSI/EXE success is claimed.

A private unsigned test MSIX was built using the #324 manifest/assets and the local rebuilt ZIP,
with the same `Goosepod.Naviamp` identity and `2.9.57.0` package version. MakeAppx schema/semantic
validation passed, and all 268 packaged application files match the private input ZIP byte-for-byte.
The local tooling metadata explicitly labels this a local test, not the published v2.9.0 asset.

- Private ZIP SHA-256: `c776276e22bbdeba4b20cc3b1c57c0f5933b6f6094daf138c5b12e88128b0db6`.
- Private MSIX SHA-256: `5b448229f8cdbf669833f42cf0fbda4e9e464b03265e76b90d3fa1014c779550`.
- Artifacts/logs/provenance: workspace `build/store-crash-investigation/private-v2.9.0-backport/`.

This candidate has not been signed, installed, or launched as an MSIX. No certificate trust or
Windows account changes have been made. Windows Sandbox is unavailable on this host. The install
helper in #324 requires explicit authorization for temporary machine certificate trust; a clean
Windows test account or another clean Windows environment is also needed for the acceptance below.

Before resubmission, test the exact replacement MSIX on a genuinely fresh Windows user profile
with no standalone Naviamp AppData, package state, or credential file. Launch through package
activation, connect using reviewer instructions, close/relaunch, confirm the same key persists
and login remains usable, then test existing-profile/standalone coexistence. Record package hash,
Windows build, package identity/version, logs and observations. Repeat certification checks and
update #323/#324 with this evidence. Do not treat an unpackaged test or an existing-profile install
as satisfying this gate.


## Packaged acceptance in progress (October 25)

The owner authorized temporary account/certificate setup and cleanup, and upload of the corrected
v2.9.0-based build to Microsoft Partner Center. Edge access works; Firefox automation is unsupported
by the Windows tool's URL policy checks. The dashboard confirms failed submission
`1152921505702215205`, original package version `2.9.57.0`.

A one-day signing certificate was used only for separate local test copies. The unsigned upload
candidate remains SHA-256 `5b448229f8cdbf669833f42cf0fbda4e9e464b03265e76b90d3fa1014c779550`.
A disposable normal-user account installed the test package successfully with no existing Naviamp
profile. Activation in that secondary account failed with Windows HRESULT `0x80070520` because it
had no interactive sign-in session; this is not an app launch result. Its test package was removed.

For normal activation in the active desktop session, the idle standalone Naviamp data directory was
preserved by a guarded same-directory rename, giving the package an absent real Naviamp directory.
The original rejected MSIX was downloaded from CI run `39975612140`, verified against SHA-256
`c71b45f8cd3fa8445bbcb38bd95ca27c287a1eed664bf53b7685c0216f5d4fd8`, signed as a test copy, and
activated with its real Store identity. It created no credential key and entered Compose's startup
error dialog (confirmed by JVM thread dump). The exact dialog message was not captured locally;
the certification screenshot supplies the DirectoryNotFoundException evidence.

The fixed package, with the same identity/version and fresh package data, visibly opens its
Connection Details screen. The owner also confirmed the app is open. A read-only probe inside its
package filesystem view finds the new key, and Windows CurrentUser DPAPI decrypts it to 32 bytes.
The key hash before relaunch is
`b35b8dc318d04358a0c9a4af0e70b5d09e0dc1ae354d92914a4683fc6de61faa` (no key bytes were recorded).
Do not infer absence from the ordinary outside-package physical path: the package-context probe is
the relevant filesystem view.

During an earlier desktop-permission wait the standalone profile was restored and all eight recorded
file hashes matched. It is preserved again for the final login/relaunch check. Authentication is a
manual handoff under the computer-use skill; the public Navidrome demo login is pending. The owner
approved desktop access to the Store app. The current test window is the fixed Store installation.

Remaining before completion: public-demo login/relaunch verification, upload/validation in the draft
submission, removal of the current-user test package, final restoration/hash verification of the
standalone profile, and elevated cleanup of the temporary account/profile/certificate/staging files.
No replacement has been uploaded, resubmitted, or published. The local checkpoint records every
cleanup target and the DPAPI-protected temporary account credential location; never upload those
private lifecycle files. No living-room TV control was performed.

The existing #324 wrapper remains pinned to the original public ZIP. That artifact still contains
the certification failure and must not be reused for resubmission; this replacement is a minimal
backport rebuild from the separately recorded v2.9.0 source commit.
