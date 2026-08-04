#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_svg="${1:-$repo_root/design/naviamp-icon.svg}"
website_root="${NAVIAMP_WEBSITE_ROOT:-}"

for command_name in rsvg-convert magick python3; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "Missing required icon tool: $command_name" >&2
        exit 1
    fi
done

if [[ ! -f "$source_svg" ]]; then
    echo "Icon source does not exist: $source_svg" >&2
    exit 1
fi

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/naviamp-icons.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT

transparent_master="$work_dir/naviamp-transparent-1024.png"
opaque_master="$work_dir/naviamp-opaque-1024.png"
monochrome_master="$work_dir/naviamp-monochrome-1024.png"

rsvg-convert -w 1024 -h 1024 "$source_svg" -o "$transparent_master"
magick -size 1024x1024 xc:black "$transparent_master" -compose over -composite "$opaque_master"
magick "$transparent_master" -fuzz 2% -transparent black "$work_dir/naviamp-mark.png"
magick "$work_dir/naviamp-mark.png" -alpha extract "$work_dir/naviamp-mark-alpha.png"
magick -size 1024x1024 xc:white "$work_dir/naviamp-mark-alpha.png" \
    -alpha off -compose CopyOpacity -composite "$monochrome_master"

resize_icon() {
    local source_file="$1"
    local size="$2"
    local destination="$3"
    mkdir -p "$(dirname "$destination")"
    magick "$source_file" -filter Lanczos -resize "${size}x${size}" "$destination"
}

resize_adaptive_icon_layer() {
    local source_file="$1"
    local destination="$2"
    local canvas_size=432
    local safe_zone_size=264
    mkdir -p "$(dirname "$destination")"
    magick -size "${canvas_size}x${canvas_size}" xc:none \
        \( "$source_file" -filter Lanczos -resize "${safe_zone_size}x${safe_zone_size}" \) \
        -gravity center -compose over -composite "$destination"
}

# Shared in-app identity and repository artwork.
resize_icon "$transparent_master" 512 "$repo_root/core/ui/src/commonMain/composeResources/drawable/naviamp.png"
resize_icon "$transparent_master" 512 "$repo_root/readme-assets/naviamp-icon.png"

# Android legacy, adaptive, themed, and notification identity assets.
declare -A android_legacy_sizes=(
    [mdpi]=48
    [hdpi]=72
    [xhdpi]=96
    [xxhdpi]=144
    [xxxhdpi]=192
)
for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
    resize_icon "$transparent_master" "${android_legacy_sizes[$density]}" \
        "$repo_root/apps/android/src/main/res/mipmap-$density/ic_launcher.png"
done
resize_adaptive_icon_layer "$transparent_master" \
    "$repo_root/apps/android/src/main/res/drawable-nodpi/ic_launcher_foreground.png"
resize_adaptive_icon_layer "$monochrome_master" \
    "$repo_root/apps/android/src/main/res/drawable-nodpi/ic_launcher_monochrome.png"
resize_icon "$monochrome_master" 96 \
    "$repo_root/apps/android/src/main/res/drawable-nodpi/ic_notification.png"

# iOS applies its own platform mask, so the App Store source remains square and fully opaque.
resize_icon "$opaque_master" 1024 \
    "$repo_root/apps/ios/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"

# Desktop runtime and installer formats retain the circular transparency from the master.
desktop_icon_dir="$repo_root/platforms/desktop/src/desktopMain/resources/icons"
resize_icon "$transparent_master" 512 "$desktop_icon_dir/naviamp.png"
magick "$transparent_master" -define icon:auto-resize=256,128,64,48,32,16 \
    "$desktop_icon_dir/naviamp.ico"

iconset_dir="$work_dir/naviamp.iconset"
mkdir -p "$iconset_dir"
resize_icon "$transparent_master" 16 "$iconset_dir/icon_16x16.png"
resize_icon "$transparent_master" 32 "$iconset_dir/icon_32x32.png"
resize_icon "$transparent_master" 64 "$iconset_dir/icon_64x64.png"
resize_icon "$transparent_master" 128 "$iconset_dir/icon_128x128.png"
resize_icon "$transparent_master" 256 "$iconset_dir/icon_256x256.png"
resize_icon "$transparent_master" 512 "$iconset_dir/icon_512x512.png"
resize_icon "$transparent_master" 1024 "$iconset_dir/icon_512x512@2x.png"
python3 - "$desktop_icon_dir/naviamp.icns" \
    "icp4=$iconset_dir/icon_16x16.png" \
    "icp5=$iconset_dir/icon_32x32.png" \
    "icp6=$iconset_dir/icon_64x64.png" \
    "ic07=$iconset_dir/icon_128x128.png" \
    "ic08=$iconset_dir/icon_256x256.png" \
    "ic09=$iconset_dir/icon_512x512.png" \
    "ic10=$iconset_dir/icon_512x512@2x.png" \
    "ic11=$iconset_dir/icon_32x32.png" \
    "ic12=$iconset_dir/icon_64x64.png" \
    "ic13=$iconset_dir/icon_256x256.png" \
    "ic14=$iconset_dir/icon_512x512.png" <<'PYTHON'
import struct
import sys
from pathlib import Path

destination = Path(sys.argv[1])
entries = bytearray()
for entry in sys.argv[2:]:
    icon_type, source_path = entry.split("=", 1)
    payload = Path(source_path).read_bytes()
    entries.extend(icon_type.encode("ascii"))
    entries.extend(struct.pack(">I", len(payload) + 8))
    entries.extend(payload)

destination.write_bytes(b"icns" + struct.pack(">I", len(entries) + 8) + entries)
PYTHON

# Optionally refresh the separate Goosepod Pages checkout from the same source.
if [[ -n "$website_root" ]]; then
    if [[ ! -f "$website_root/naviamp/index.html" ]]; then
        echo "NAVIAMP_WEBSITE_ROOT is not a Goosepod website checkout: $website_root" >&2
        exit 1
    fi
    website_asset_dir="$website_root/assets/naviamp"
    mkdir -p "$website_asset_dir"
    cp "$source_svg" "$website_asset_dir/icon.svg"
    resize_icon "$transparent_master" 512 "$website_asset_dir/icon.png"
    resize_icon "$transparent_master" 64 "$website_asset_dir/favicon.png"
    resize_icon "$transparent_master" 180 "$work_dir/apple-touch-transparent.png"
    magick -size 180x180 xc:black "$work_dir/apple-touch-transparent.png" \
        -compose over -composite "$website_asset_dir/apple-touch-icon.png"
    magick -size 1200x630 xc:'#fafaf7' \
        \( "$transparent_master" -resize 360x360 \) \
        -gravity center -compose over -composite -depth 8 "$website_asset_dir/social-preview.png"
fi

echo "Generated Naviamp icons from $source_svg"
