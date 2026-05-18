#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$APP_DIR"

RUST_HOST="$(rustc -vV | awk '/^host:/ { print $2 }')"
case "${NAVIAMP_PLATFORM_SLUG:-$RUST_HOST}" in
  aarch64-apple-darwin|macos-arm64) PLATFORM_SLUG="macos-arm64" ;;
  x86_64-apple-darwin|macos-x64) PLATFORM_SLUG="macos-x64" ;;
  *) echo "Unsupported macOS Rust host: $RUST_HOST" >&2; exit 1 ;;
esac

BASS_SOURCE_DIR="${NAVIAMP_BASS_DIR:-$APP_DIR/vendor/bass/$PLATFORM_SLUG}"
if [[ ! -f "$BASS_SOURCE_DIR/libbass.dylib" ]]; then
  echo "BASS runtime not found in $BASS_SOURCE_DIR" >&2
  echo "Set NAVIAMP_BASS_DIR or copy BASS dylibs into vendor/bass/$PLATFORM_SLUG." >&2
  exit 1
fi

cargo build --release

BUNDLE_DIR="$APP_DIR/target/release/Naviamp.app"
MACOS_DIR="$BUNDLE_DIR/Contents/MacOS"
RESOURCES_DIR="$BUNDLE_DIR/Contents/Resources"
BASS_BUNDLE_DIR="$RESOURCES_DIR/playback/bass/$PLATFORM_SLUG"

rm -rf "$BUNDLE_DIR"
mkdir -p "$MACOS_DIR" "$BASS_BUNDLE_DIR"

cp "$APP_DIR/target/release/naviamp" "$MACOS_DIR/naviamp"
cp "$BASS_SOURCE_DIR"/*.dylib "$BASS_BUNDLE_DIR/"

cat > "$BUNDLE_DIR/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDevelopmentRegion</key>
  <string>en</string>
  <key>CFBundleExecutable</key>
  <string>naviamp</string>
  <key>CFBundleIdentifier</key>
  <string>app.naviamp.desktop</string>
  <key>CFBundleInfoDictionaryVersion</key>
  <string>6.0</string>
  <key>CFBundleName</key>
  <string>Naviamp</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
  <key>CFBundleShortVersionString</key>
  <string>0.1.0</string>
  <key>CFBundleVersion</key>
  <string>0.1.0</string>
  <key>LSMinimumSystemVersion</key>
  <string>11.0</string>
  <key>NSHighResolutionCapable</key>
  <true/>
</dict>
</plist>
PLIST

plutil -lint "$BUNDLE_DIR/Contents/Info.plist" >/dev/null
xattr -dr com.apple.quarantine "$BUNDLE_DIR" 2>/dev/null || true

echo "Packaged $BUNDLE_DIR"
du -sh "$BUNDLE_DIR"
