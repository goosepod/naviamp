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
ICON_SOURCE="$APP_DIR/assets/naviamp.png"
ICONSET_DIR="$APP_DIR/target/release/Naviamp.iconset"
ICON_TIFF="$APP_DIR/target/release/Naviamp-icon.tiff"

rm -rf "$BUNDLE_DIR"
mkdir -p "$MACOS_DIR" "$BASS_BUNDLE_DIR"

cp "$APP_DIR/target/release/naviamp" "$MACOS_DIR/naviamp"
cp "$BASS_SOURCE_DIR"/*.dylib "$BASS_BUNDLE_DIR/"

if [[ -f "$ICON_SOURCE" ]]; then
  rm -rf "$ICONSET_DIR"
  mkdir -p "$ICONSET_DIR"
  sips -z 16 16 "$ICON_SOURCE" --out "$ICONSET_DIR/icon_16x16.png" >/dev/null
  sips -z 32 32 "$ICON_SOURCE" --out "$ICONSET_DIR/icon_16x16@2x.png" >/dev/null
  sips -z 32 32 "$ICON_SOURCE" --out "$ICONSET_DIR/icon_32x32.png" >/dev/null
  sips -z 64 64 "$ICON_SOURCE" --out "$ICONSET_DIR/icon_32x32@2x.png" >/dev/null
  sips -z 128 128 "$ICON_SOURCE" --out "$ICONSET_DIR/icon_128x128.png" >/dev/null
  sips -z 256 256 "$ICON_SOURCE" --out "$ICONSET_DIR/icon_128x128@2x.png" >/dev/null
  sips -z 256 256 "$ICON_SOURCE" --out "$ICONSET_DIR/icon_256x256.png" >/dev/null
  sips -z 512 512 "$ICON_SOURCE" --out "$ICONSET_DIR/icon_256x256@2x.png" >/dev/null
  sips -z 512 512 "$ICON_SOURCE" --out "$ICONSET_DIR/icon_512x512.png" >/dev/null
  sips -z 1024 1024 "$ICON_SOURCE" --out "$ICONSET_DIR/icon_512x512@2x.png" >/dev/null
  if ! iconutil -c icns "$ICONSET_DIR" -o "$RESOURCES_DIR/Naviamp.icns" 2>/dev/null; then
    sips -z 1024 1024 -s format tiff "$ICON_SOURCE" --out "$ICON_TIFF" >/dev/null
    tiff2icns "$ICON_TIFF" "$RESOURCES_DIR/Naviamp.icns"
    rm -f "$ICON_TIFF"
  fi
  rm -rf "$ICONSET_DIR"
else
  echo "App icon source not found: $ICON_SOURCE" >&2
  exit 1
fi

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
  <key>CFBundleIconFile</key>
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
