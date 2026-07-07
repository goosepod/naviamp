# Linux Desktop Support

This checklist tracks the work to make Naviamp's Compose Desktop app usable and releasable on Linux.

## Distribution Direction

Naviamp should target Linux desktop conventions rather than individual desktop environments. The app should run under GNOME, KDE Plasma, Xfce, and other freedesktop.org-compatible sessions from the same build, with testing spread across representative Wayland and X11 environments.

Initial Linux artifacts should follow the existing desktop packaging model:

- Standalone app-image zip with the app, launcher, private runtime, and native libraries together.
- Native `.deb` and `.rpm` packages produced by Compose Desktop and `jpackage` on a Linux runner.
- Bundled trimmed Java runtime through `jlink`, matching macOS and Windows behavior.

A thin package that depends on system Java can be considered later, but it needs a separate launcher/package-maintainer path and broader distro compatibility testing. It should not block the first Linux app.

Flatpak is the preferred universal package to investigate after the app-image and native packages work. Snap can follow if Ubuntu Store distribution becomes useful.

## Current Status

- [x] Create a Linux desktop support tracker.
- [x] Add Compose Desktop Linux package formats (`.deb`, `.rpm`) to the desktop Gradle configuration.
- [x] Add Linux package metadata and PNG icon configuration.
- [x] Add Makefile entry points for Linux local app image, release zip, and native packages.
- [x] Vendor Linux x64 BASS libraries under `apps/desktop/vendor/bass/linux-x64`.
- [x] Add Linux JNI RPATH configuration so `libnaviamp_bass.so` can resolve adjacent BASS libraries.
- [x] Require `libnaviamp_bass.so` in Linux desktop package verification.
- [x] Verify Gradle copies Linux x64 BASS vendor files into desktop resources.
- [x] Verify the BASS JNI CMake target builds `libnaviamp_bass.so` on Linux.
- [x] Verify `make linux-test` stages and launches `build/local-test/Naviamp`.
- [x] Verify basic playback on Xubuntu over XRDP with PulseAudio passthrough.
- [x] Verify crossfade playback behavior on Linux.
- [x] Verify gapless playback behavior on Linux with Pink Floyd's `The Dark Side of the Moon`.
- [x] Verify internet radio playback on Linux.
- [x] Verify lyrics display on Linux.
- [x] Verify downloads on Linux.
- [x] Verify playlist creation on Linux.
- [x] Verify volume controls on Linux.
- [x] Verify library search on Linux.
- [x] Complete a general Xubuntu XRDP smoke test with no remaining user-visible blockers found.
- [x] Verify basic visualizer behavior on Linux.
- [x] Verify `make linux-standalone` produces `Naviamp-linux-x64-release.zip` and the standalone app runs.
- [x] Verify `make linux-installer` produces `.deb` and `.rpm` packages.
- [x] Verify the `.deb` installs and launches on Xubuntu.
- [ ] Verify seek behavior on Linux.
- [ ] Verify waveform generation on Linux.
- [ ] Verify GLSL/shader visualizer behavior on non-VM Linux graphics.
- [ ] Verify installed Linux app launcher icon and metadata after package metadata updates.
- [ ] Add AppStream metadata if Xubuntu App Center still needs richer package information from the raw `.deb`.
- [ ] Add Linux release artifacts to `.forgejo/workflows/release-builds.yml` after the native vendor set is present.
- [ ] Package a Flatpak bundle or repo manifest.
- [ ] Decide whether a Snap package is worth maintaining.

## Native Dependencies

Linux desktop playback depends on the same JNI-backed BASS path as macOS and Windows. Before Linux packages can be considered functional, the repository needs a Linux vendor directory:

```text
apps/desktop/vendor/bass/linux-x64
```

At minimum, the app-image verifier expects:

```text
libbass.so
libbassmix.so
libbassflac.so
libbassopus.so
```

The desktop JNI build should then produce:

```text
libnaviamp_bass.so
```

Additional BASS add-ons should mirror the intended format coverage from `docs/desktop-playback.md` where Linux redistributables are available.

The current Linux x64 vendor set was sourced from local Un4seen Linux archives under `/Users/jbmcmichael/Downloads/bass_linux` and includes:

```text
libbass.so
libbass_aac.so
libbass_ac3.so
libbass_fx.so
libbass_mpc.so
libbass_spx.so
libbass_tta.so
libbassalac.so
libbassape.so
libbassdsd.so
libbassflac.so
libbasshls.so
libbassloud.so
libbassmidi.so
libbassmix.so
libbassopus.so
libbasswebm.so
libbasswv.so
```

## Local Linux Commands

Run these on a Linux machine or Linux CI runner with JDK 17 and CMake available:

```shell
make linux-test
make linux-standalone
make linux-installer
```

Expected outputs once native resources are present:

```text
build/local-test/Naviamp
apps/desktop/build/compose/distributions/Naviamp-linux-x64-release.zip
apps/desktop/build/compose/binaries/main/deb/*.deb
apps/desktop/build/compose/binaries/main/rpm/*.rpm
```

Linux `.deb` and `.rpm` package versions should use the project `VERSION` directly, for example `0.14.0-1` for a first Linux package release. The positive-major native package version workaround is retained for package formats that require it, but Linux package managers accept pre-1.0 versions.

The standalone app-image `bin/Naviamp` executable may still show a generic executable icon in file managers because Linux icons normally belong to `.desktop` launchers and installed icon-theme resources, not to ELF launcher files. The installed `.deb`/`.rpm` launcher is the authoritative Linux icon integration point.

Xubuntu App Center can show limited information for third-party raw `.deb` files unless the package includes AppStream metadata. The Compose Desktop/jpackage metadata now includes a description, vendor, license file, Linux shortcut, package category, and icon, but a richer App Center listing may still require a dedicated AppStream metainfo file or Flatpak packaging.

## Xubuntu XRDP Audio Notes

The first Linux VM validation was done on Xubuntu over XRDP with PulseAudio passthrough. The app built, launched, connected to the server, loaded the library, and selected tracks correctly, but playback did not start until ALSA's Pulse bridge was installed and the default ALSA device routed to PulseAudio.

Useful packages for this setup:

```shell
sudo apt install -y libasound2-plugins alsa-utils pulseaudio-utils
```

Validation commands:

```shell
pactl info
aplay -L | grep -i pulse
speaker-test -D pulse -c 2 -t sine -l 1
speaker-test -D default -c 2 -t sine -l 1
```

If `speaker-test -D pulse` works but `speaker-test -D default` does not, create `~/.asoundrc`:

```text
pcm.!default {
    type pulse
}

ctl.!default {
    type pulse
}
```

After restarting Naviamp, BASS playback works through the XRDP PulseAudio sink.

## Desktop Environment Test Matrix

Minimum validation before calling Linux support ready:

- GNOME on Wayland.
- KDE Plasma on Wayland or X11.
- One lighter X11 environment such as Xfce.

Validation should cover login, library browsing, playback from original streams, transcoded fallback, seek, queue transitions, radio streams, cover art, visualizers, window persistence, and app menu integration.
