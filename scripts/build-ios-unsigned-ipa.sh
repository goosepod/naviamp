#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
build_root="$repo_root/build/ios-unsigned"
archive_path="$build_root/Naviamp.xcarchive"
package_root="$build_root/package"
distribution_dir="$build_root/distributions"
app_path="$archive_path/Products/Applications/Naviamp.app"

version="$(tr -d '[:space:]' < "$repo_root/VERSION")"
build_number="$(tr -d '[:space:]' < "$repo_root/VERSION_CODE")"
version_without_prefix="${version#v}"
marketing_version="${version_without_prefix%%-*}"
ipa_name="Naviamp-${version}-ios-unsigned.ipa"
ipa_path="$distribution_dir/$ipa_name"

case "$build_root" in
    "$repo_root"/build/ios-unsigned) ;;
    *)
        echo "Refusing to clean unexpected iOS build directory: $build_root" >&2
        exit 1
        ;;
esac

rm -rf "$build_root"
mkdir -p "$distribution_dir"

# Release-mode Kotlin/Native linking exceeds the project's ordinary 2 GiB development heap. Keep
# this override local to the archive process so device builds are reliable on clean CI workers.
export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.jvmargs=-Xmx6g"

xcodebuild \
    -project "$repo_root/apps/ios/Naviamp.xcodeproj" \
    -scheme Naviamp \
    -configuration Release \
    -sdk iphoneos \
    -destination "generic/platform=iOS" \
    -archivePath "$archive_path" \
    CODE_SIGNING_ALLOWED=NO \
    CODE_SIGNING_REQUIRED=NO \
    CODE_SIGN_IDENTITY= \
    DEVELOPMENT_TEAM= \
    MARKETING_VERSION="$marketing_version" \
    CURRENT_PROJECT_VERSION="$build_number" \
    archive

if [[ ! -d "$app_path" ]]; then
    echo "Expected archived app was not produced: $app_path" >&2
    exit 1
fi

if codesign --verify "$app_path" >/dev/null 2>&1; then
    echo "Expected an unsigned app, but the archive has a valid code signature." >&2
    exit 1
fi

archs="$(lipo -archs "$app_path/Naviamp")"
if [[ " $archs " != *" arm64 "* ]]; then
    echo "Archived app does not contain the required arm64 device architecture: $archs" >&2
    exit 1
fi

archived_version="$(plutil -extract CFBundleShortVersionString raw "$app_path/Info.plist")"
archived_build="$(plutil -extract CFBundleVersion raw "$app_path/Info.plist")"
if [[ "$archived_version" != "$marketing_version" || "$archived_build" != "$build_number" ]]; then
    echo "Archived version mismatch: expected $marketing_version ($build_number), got $archived_version ($archived_build)" >&2
    exit 1
fi

mkdir -p "$package_root/Payload"
ditto "$app_path" "$package_root/Payload/Naviamp.app"
(
    cd "$package_root"
    /usr/bin/zip -qry "$ipa_path" Payload
)

/usr/bin/unzip -tq "$ipa_path"
cp "$repo_root/docs/ios-sideloading.md" "$distribution_dir/Naviamp-iOS-Sideloading.md"

echo "Created unsigned iOS device package: $ipa_path"
echo "This IPA must be signed by the person installing it; it is not directly installable as downloaded."
