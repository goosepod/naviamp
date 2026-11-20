# Public BASS for tvOS

Exact upstream input: [bass-tvos.zip](https://www.un4seen.com/stuff/bass-tvos.zip), inspected
2026-11-14. SHA-256: `8f3bef0a67c20fdf1b65a7b6164b25bfb3a75e47e8bd5f1094eb0fad1ac3bbe8`.
`upstream-inventory.json` records all 18 original files and their hashes; no binaries were rebuilt.
The archive contains base BASS, FLAC and Opus, with separate tvOS device and simulator slices.
It has no headers, BASSmix, license/readme files or other add-ons. Base BASS reports 2.4.18.14.

Both base BASS framework bundles include a Naviamp-supplied `PrivacyInfo.xcprivacy` for the
file timestamp and elapsed-time APIs imported by the tvOS binary. It uses the same declared
reasons as the iOS BASS bundle. The original files retain their upstream hashes.

The shared Apple adapter uses Naviamp's existing public `bass.h`, now in
`platforms/apple/src/nativeInterop/cinterop/include`. The existing upstream BASS/FLAC/Opus
license documents remain at `platforms/ios/vendor/bass/licenses/{bass,bassflac,bassopus}.txt`.
Naviamp continues to use the upstream non-commercial terms; the tvOS archive does not supply a
separate licensing grant. Review upstream terms before a commercial distribution.

Keep the upstream signature files for provenance. Staging excludes those obsolete signatures;
the final app must sign its embedded frameworks itself. Do not link the iOS BASSmix binary into
tvOS or add an unresolved mixer symbol. A direct backend inherits Core's mixer-free capability
behavior; unsupported preferences remain preserved for other devices.

Native tvOS targets are opt-in with `-Pnaviamp.tvos.enabled=true`. The probe has run on tvOS
27.2 only; it does not establish the final app deployment minimum or physical-device audibility.
