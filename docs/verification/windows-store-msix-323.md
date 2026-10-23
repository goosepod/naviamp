# Windows Store MSIX packaging acceptance — issue 323

Date: 2026-10-23. Tracking: [#323](https://github.com/goosepod/naviamp/issues/323).

## Input and package

- Published input: `Naviamp-v2.9.0-windows-x64-release.zip` from GitHub release `v2.9.0`.
- Release commit: `826e14bf1c46dba8d604515f1bf77bf6afcd541d`.
- Input SHA-256: `86907877c7bb6b34438bb43737aeea083dbc871dee7a6358c2b0dee13f4f4381`.
- Output: `Naviamp-v2.9.0-windows-x64-store.msix` (unsigned Store upload candidate).
- Output SHA-256: `ebb956cdeab37a63a662f510236383e5011db665e34b6dcdfc50088ab4874064`.
- Identity: `Goosepod.Naviamp`, version `2.9.57.0`, x64, publisher matching Partner Center.

The package contains all **268** released application files without changes, plus
the MSIX manifest, three existing-brand PNG derivatives, and MakeAppx's generated
block map/content-type metadata. No application rebuild or runtime patch occurred.
Repackaging can change the outer MSIX checksum; always record the exact tested output.

## Completed checks

| Check | Result |
| --- | --- |
| Published release ZIP checksum | Passed |
| Packaging unit tests on macOS / Python 3.15 | 8 passed |
| Packaging unit tests on Windows / Python 3.12.10 | 8 passed |
| Launcher, JVM and BASS x64 PE checks | Passed |
| MakeAppx schema and semantic validation, no `/nv` | Passed |
| Compare every MSIX application file with release ZIP | 268/268 matched |
| Re-run content verifier on retrieved package on macOS | 268/268 matched |
| Reject changed input, changed/extra app payload and wrong publisher | Passed |
| Reject unsafe paths and Windows filename normalization/collisions | Passed |
| PNG dimensions (50, 44 and 150 pixels square) | Passed |

Windows build environment: x64, OS build `10.0.26300.0`. Used Microsoft NuGet
`Microsoft.Windows.SDK.BuildTools` **10.0.26100.7463**, x64 MakeAppx from its
`bin/10.0.26100.0/x64` directory. Its Authenticode signature was verified valid
and issued to Microsoft Corporation before execution. Python was the official
portable Python 3.12.10 distribution. Tools lived in an isolated build directory;
no SDK or Python system installation was needed. PowerShell execution policy was
overridden only for the build process; machine policy was unchanged.

The candidate, `makeappx.log`, `provenance.json`, local verification report and
`SHA256SUMS` are retained under the issue worktree's `build/windows-store/final`.
The Windows build also retains them under `naviamp-msix-323/output-final` in the
build user's profile. CI retains corresponding artifacts on subsequent builds.

## Outstanding gates

**No installed-app, playback, WACK or Store certification result is claimed.**
Developer Mode was not enabled on the available Windows host during packaging.

### Authorized local signing and deployment attempt

After the project owner explicitly approved temporary test-certificate trust,
tested the final CI candidate from
[Windows Store package run 39975612140](https://github.com/goosepod/naviamp/actions/runs/39975612140).
Its unsigned SHA-256 is
`c71b45f8cd3fa8445bbcb38bd95ca27c287a1eed664bf53b7685c0216f5d4fd8`.
All 268 payload files also passed independent verification after download to macOS.

- Preflight: no existing `Goosepod.Naviamp` Store registration or Naviamp process.
- Created a one-day non-exportable code-signing key and public certificate.
- Signed a separate `LOCAL-TEST.msix`; the unsigned CI candidate remained unchanged.
- Trusted only that public certificate in machine `TrustedPeople`, with user approval.
- SignTool signature verification passed. Windows AppX also validated its signature.
- `Add-AppxPackage` through SSH failed with `0x80070005`; deployment log event 718
  reported "Failed to initialize PLM" and event 605 identified the
  `PackagesInUseClosed` state handler. Installation acceptance therefore did not pass.
- Failure cleanup completed: no Store registration remained; the signed test copy,
  machine trust entry, personal certificate and private signing key were removed.
  Independent queries confirmed both certificate-store entries were absent.

The owner requested retrying through Moonlight's Desktop connection to Sunshine.
The computer-control service failed before reaching any native app with
`Sky Computer Use native pipe startup failed`, including after resetting the tool
and directly selecting Moonlight. Interactive testing is blocked on restoration
of desktop-control access; no GUI, audio or animation result is inferred from the
successful package/signature checks. `test-windows-store-install.ps1` records the
explicit install/cleanup lifecycle for the interactive retry.

Follow the installed-app matrix in
[`windows-store-distribution.md`](../windows-store-distribution.md): native audio,
credentials/relaunch, server connections/offline behavior, downloads, media keys,
background playback, accessibility, update, coexistence/data paths and uninstall.
Temporary test signing/trust is already authorized for this test. Resume in the
interactive desktop without asking for that authorization again, and clean up
the test installation and certificate afterward.

Partner Center listing, screenshots, privacy/support links, age rating, reviewer
access and restricted-capability explanation remain part of submission preparation.
Nothing has been uploaded to Partner Center or submitted for certification by this
packaging work. Keep issue 323 and its PR open until acceptance is complete.

## Architecture review

No Android/Desktop/iOS production source file changed. The changes are build
scripts, a separate CI packaging workflow, Windows package metadata/assets,
packaging tests and documentation. Shared application behavior is unchanged.
