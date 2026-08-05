# BASS Native Dependency Inventory

**Last checked:** August 5, 2026  
**Authoritative source:** [Un4seen BASS downloads](https://www.un4seen.com/bass.html)

This inventory records the upstream archives used for BASS components that were refreshed after
the first Naviamp 2.0 alpha. Archives are downloaded directly from Un4seen, are not retained in the
repository, and are identified here by SHA-256.

| Component | Platform | Version/build | Upstream archive | SHA-256 |
| --- | --- | --- | --- | --- |
| BASSWEBM | Windows | 2.4.2 (2026-08-04) | `basswebm24.zip` | `32e4867cf259704245de14404ce11d5f3ba787a2629ef3e0679b77657576b971` |
| BASSWEBM | macOS | 2.4.2 (2026-08-04) | `basswebm24-osx.zip` | `a847e458b9a7ea1a0940671547887c6ba4ea68b7842f83b39d34ac86a2f4ccd7` |
| BASSWEBM | Linux | 2.4.2 (2026-08-04) | `basswebm24-linux.zip` | `87ed54c946fc352958b6964d6b02b2f5e1aed451c6a39b9290f3c419171d3731` |
| BASSWEBM | Android | 2.4.2 (2026-08-04) | `basswebm24-android.zip` | `4f00c1417b84033e1c8e8a621abfe4b980fe72c93e2ee8de25ed8e095f126efe` |
| BASSWEBM | iOS | 2.4.2 (2026-08-04) | `basswebm24-ios.zip` | `55a4246fe706fa4848ca4be115bc4bd88452423387d2053e791f9b13115a1088` |
| BASS_SSL | Windows | OpenSSL 1.1.1w (2026-07-31 package) | `bass_ssl.zip` | `32e5f55d93e892973f5d87580e89d6d9e2460729fd68d72f73b351d9a2718eab` |
| BASS_SSL | Android | OpenSSL 1.1.1w (2026-07-31 package) | `bass_ssl-android.zip` | `991f9dca218288c8966a3df43a5e659a5f70de7d6482c483aca17afbcc0b5c55` |
| BASSOPUS | Windows | 2.4.3.3 (2026-06-04) | `bassopus24.zip` | `1fb6e033289ea968ca1fd02dea154a2e5d06bb9c2e33cdeda277e63084d9ad20` |
| BASSOPUS | macOS | 2.4.3.3 (2026-06-04) | `bassopus24-osx.zip` | `1246333946bb0e969b5fead4dec1dd8f9d50158d67bf26a480506b220ff3184a` |
| BASSOPUS | Linux | 2.4.3.3 (2026-06-04) | `bassopus24-linux.zip` | `484399ad96a71450561a28be540dcf8ff8f5c68f9421244250d03665036f0537` |
| BASSOPUS | Android | 2.4.3.3 (2026-06-04) | `bassopus24-android.zip` | `76d3cb4ae8799d12d5c8f0138453b8834df7173a66d88e56b47c75a5e4518945` |
| BASSOPUS | iOS | 2.4.3.3 (2026-06-04) | `bassopus24-ios.zip` | `33b44809e9e8aa7a949386213336d0b8fa73652ef058caa63aa7cea5d72c2a96` |

## August 5 findings

- BASSWEBM 2.4.1 was outdated on every target and was replaced with 2.4.2. The update adds WebM
  attachment access (including embedded artwork) and seeking beyond the downloaded portion of a
  buffered internet file.
- Android and Windows BASS_SSL binaries differed from the July 31 packages. In particular, the
  previous Android libraries identified themselves as OpenSSL 1.1.0l from 2019; the replacements
  identify themselves as OpenSSL 1.1.1w.
- BASSOPUS was already versioned as 2.4.3.3 in the iOS inventory. Binary comparison showed Linux and
  iOS already matched the current archives, while Android, macOS, and Windows contained the earlier
  January build and were refreshed.
- Un4seen does not publish BASS_SSL for macOS, Linux, or iOS. Naviamp currently retains the Windows
  package while the add-on audit decides whether to remove it; BASS documents its Windows purpose as
  HTTPS support for BASSenc, which Naviamp does not use.

The complete iOS dependency inventory remains in
[`platforms/ios/vendor/bass/README.md`](../platforms/ios/vendor/bass/README.md).
