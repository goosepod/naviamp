# BASS for iOS

These are the official Un4seen iOS XCFramework distributions used by Naviamp. They were downloaded
from [un4seen.com](https://www.un4seen.com/bass.html) on 2026-07-24. The checked-in archives are not
retained; the SHA-256 values below identify the exact upstream inputs.

Naviamp is a non-commercial product and uses BASS under Un4seen's free non-commercial terms. The
upstream license/readme supplied with each distribution is retained in `licenses/`.

## Platform-equivalent inventory

| Component | Version | Upstream archive | SHA-256 |
| --- | --- | --- | --- |
| BASS | 2.4.18.3 | `bass24-ios.zip` | `087bdb8aec6735a8b8de21253e07270f647cfb7bb3e1f276efa7ba979d46836e` |
| BASSmix | 2.4.12 | `bassmix24-ios.zip` | `20c1157ebda75ad9ee84aa94d1821fd6eb3f715b4b254f1ec0be73bfadc1560b` |
| BASSFLAC | 2.4.6.1 | `bassflac24-ios.zip` | `57dd543278b63e75e6ecd236ee0b9afaf5d79e8ad0880a81d565d5d2ed4aed50` |
| BASSOPUS | 2.4.3.3 | `bassopus24-ios.zip` | `33b44809e9e8aa7a949386213336d0b8fa73652ef058caa63aa7cea5d72c2a96` |
| BASSMIDI | 2.4.16 | `bassmidi24-ios.zip` | `de3ef15b0f3416803c600f7b1063a8e84492523c9110d8f0a36675cb1c43f7bd` |
| BASSWV | 2.4.7.4 | `basswv24-ios.zip` | `ecaa61286d57f030e8c111f7831f0e3bcee46649b35291ab89407ac08fe06c1e` |
| BASSDSD | 2.4.2 | `bassdsd24-ios.zip` | `7625d5ce417ecfaf1252084eb70472352f2ae5ac69d52c6b875deec843d36f04` |
| BASSWEBM | 2.4.1 | `basswebm24-ios.zip` | `61f7fa21725ce0392c93a39cb10d7600a7759be72f0ab6652483f766f8d233dc` |
| BASSHLS | 2.4.5 | `basshls24-ios.zip` | `0cf476d50c8269f94be20597c3984f4848710748ce11f725e3a1b033d3eb30de` |
| BASSAPE | 2.4.1 | `bassape24-ios.zip` | `21bedf9e4b70bf3a2ac25f1ce93d46ef1a5a4691cc262742dc9dbcaa55527cc7` |
| BASS_MPC | 2.4.1.2 | `bass_mpc24-ios.zip` | `153961beaa506850b527e774c95c187a9194a25bc1764b8a98abaf4d81536172` |
| BASS_TTA | 2.4.0.2 | `bass_tta24-ios.zip` | `5174195fbe3e14735db521744bd85e153b8e723c297c6a6b5f41eddf96bfc6ae` |

This is the iOS equivalent of the Android/Desktop playback inventory. Un4seen does not publish iOS
BASS_AAC, BASS_AC3, or BASSALAC packages because BASS uses Apple's codecs for those formats.
BASS_SSL is only published for Android in this product's target set. BASS_SPX and BASSWMA have no
iOS distribution.

Every vendored XCFramework contains an `ios-arm64_i386_x86_64-simulator` slice with arm64 support
and an `ios-arm64` device slice. The Xcode host links and embeds all of them; BASS initialization is
kept behind the narrow Apple/native playback adapter while playback policy remains in Core.
