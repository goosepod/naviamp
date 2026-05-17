# Naviamp Rust/Slint Desktop

Small Rust/Slint desktop app for Naviamp. The current focus is Windows and macOS, while keeping the core code friendly to Linux and future Android experiments.

Current capabilities:

- Fast native desktop window.
- Small portable release build.
- Navidrome track search.
- Native BASS playback.

## Prerequisites

Windows:

- Rust stable MSVC toolchain.
- Visual Studio C++ Build Tools.
- BASS runtime DLLs in `vendor\bass\windows-x64`.

macOS:

- Rust stable toolchain.
- Xcode command line tools.
- BASS runtime libraries. Packaging layout is still being finalized.

## Build

```powershell
cargo build --release
```

## Prepare BASS Runtime On Windows

```powershell
.\scripts\prepare-bass.ps1 -BassDownloadsDir C:\Users\ursasmar\Downloads\BASS
```

This copies the Windows x64 runtime DLLs from downloaded BASS zip packages into `vendor\bass\windows-x64`.

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
Copy-Item -Force vendor\bass\windows-x64\*.dll dist\
```
