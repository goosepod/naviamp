# Release Builds

Naviamp builds release artifacts through Forgejo Actions on every push to `main`, which covers normal merge-to-main release candidates. The workflow also supports manual runs through `workflow_dispatch`.

Workflow:

```text
.forgejo/workflows/release-builds.yml
```

## Artifacts

- Android: unsigned release APK and release AAB.
- Windows: standalone app-image zip plus jpackage MSI/EXE installers.
- macOS: standalone `.app` zip plus jpackage DMG installer.

The current Android release artifacts are unsigned because the Android module does not yet define a release keystore/signing config. The current desktop installers are unsigned and the macOS DMG is not notarized.

## Runner Requirements

- Android job: Linux runner with Java 17. The workflow installs Android platform 36, build tools 36.0.0, and CMake 3.22.1 through `sdkmanager`.
- Windows job: Windows runner with Java 17 and Chocolatey. The workflow installs WiX Toolset 3.14 when `candle.exe` is missing, because jpackage needs WiX for MSI/EXE output.
- macOS job: Apple Silicon macOS runner with Java 17. The packaged platform is `macos-arm64` because the repo currently vendors macOS ARM64 BASS libraries.

If Forgejo uses different runner labels than `ubuntu-latest`, `windows-latest`, or `macos-latest`, update `runs-on` in the workflow without changing the Gradle tasks.

## Local Equivalents

```shell
make android-release
make macos-standalone
make macos-installer
```

On Windows:

```powershell
make windows-standalone
make windows-installer
```

## Follow-Up Before Public Distribution

- Add Android release signing and upload signed APK/AAB artifacts.
- Add Windows code signing for MSI/EXE.
- Add macOS Developer ID signing and notarization for the DMG.
- Decide whether build artifacts should also be attached to Forgejo releases for tagged builds.
