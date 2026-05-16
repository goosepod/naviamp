# Naviamp Slint Prototype

Small Rust/Slint desktop spike for Naviamp.

Goals:

- Fast native desktop window.
- Small portable release build.
- Minimal Navidrome search.
- External `mpv` playback.

Build:

```powershell
cargo build --release
```

Run:

```powershell
.\target\release\naviamp-slint.exe
```

This prototype expects `mpv.exe` to be installed on `PATH`.
