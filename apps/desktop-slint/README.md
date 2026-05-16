# Naviamp Rust/Slint Desktop

Small Rust/Slint desktop app for Naviamp. The current focus is Windows and macOS, while keeping the core code friendly to Linux and future Android experiments.

Current capabilities:

- Fast native desktop window.
- Small portable release build.
- Navidrome track search.
- External `mpv` playback.

## Prerequisites

Windows:

- Rust stable MSVC toolchain.
- Visual Studio C++ Build Tools.
- `mpv.exe` on `PATH`.

macOS:

- Rust stable toolchain.
- Xcode command line tools.
- `mpv` on `PATH`.

## Build

```powershell
cargo build --release
```

## Run

```powershell
.\target\release\naviamp.exe
```

## Verify

```powershell
cargo fmt
cargo test
cargo clippy --all-targets -- -D warnings
```

## Portable Windows Copy

```powershell
New-Item -ItemType Directory -Force dist
Copy-Item -Force target\release\naviamp.exe dist\Naviamp.exe
```
