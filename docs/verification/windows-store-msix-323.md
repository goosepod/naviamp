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
Developer Mode was not enabled on the available Windows host. No test certificate
was created/trusted and no package was installed during these packaging checks.

Follow the installed-app matrix in
[`windows-store-distribution.md`](../windows-store-distribution.md): native audio,
credentials/relaunch, server connections/offline behavior, downloads, media keys,
background playback, accessibility, update, coexistence/data paths and uninstall.
Local installation needs an explicit decision about a trusted test-signing
certificate on the Windows test machine, or a developer-enabled test environment.

Partner Center listing, screenshots, privacy/support links, age rating, reviewer
access and restricted-capability explanation remain part of submission preparation.
Nothing has been uploaded to Partner Center or submitted for certification by this
packaging work. Keep issue 323 and its PR open until acceptance is complete.

## Architecture review

No Android/Desktop/iOS production source file changed. The changes are build
scripts, a separate CI packaging workflow, Windows package metadata/assets,
packaging tests and documentation. Shared application behavior is unchanged.
