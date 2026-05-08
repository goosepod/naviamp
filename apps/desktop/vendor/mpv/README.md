# Bundled mpv Vendor Files

Place local mpv executables here when building desktop packages that should not depend on a system mpv install.

Expected layout:

```text
apps/desktop/vendor/mpv/macos-arm64/mpv
apps/desktop/vendor/mpv/macos-x64/mpv
apps/desktop/vendor/mpv/linux-x64/mpv
apps/desktop/vendor/mpv/windows-x64/mpv.exe
```

The Gradle build copies only the executable for the current build platform into generated desktop resources:

```text
playback/mpv/<platform>/mpv
```

The binaries themselves are ignored by git. Before distributing a build, verify the bundled mpv binary and any required native libraries are licensed and packaged appropriately for the target platform.
