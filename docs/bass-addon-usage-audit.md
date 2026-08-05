# Cross-Platform BASS Add-On Usage Audit

**Status:** Implementing and verifying  
**Started:** August 5, 2026  
**Scope:** Android, Desktop, and iOS playback, streaming, offline playback, analysis, effects, and packaging

## Goal

Define one intentional BASS capability inventory for Naviamp. Each bundled binary must have a
documented product purpose, a working load path, representative media coverage, and a justified
platform substitution. The final inventory should be enforced by packaging tests so it cannot drift
silently.

No codec binary should be removed until the supported-format contract and representative playback
tests are in place. A library being present, or successfully loaded by the operating system, does not
by itself prove that BASS can use it.

## Terms Used by This Audit

- **Bundled:** The binary is shipped in the platform package.
- **Process-loaded:** The operating-system loader has loaded the binary.
- **Plugin-registered:** `BASS_PluginLoad` returned a non-zero handle, extending the standard BASS
  stream creation functions.
- **Directly used:** Naviamp calls the add-on's own API rather than using it as a stream plugin.
- **Substituted:** The supported operating system supplies the codec through the system codec path
  that BASS uses after its built-in and plugin decoders.

These states are not interchangeable. BASS documents additional stream formats as becoming
available through [`BASS_PluginLoad`](https://www.un4seen.com/doc/bass/BASS_PluginLoad.html).

## Confirmed Product Behavior

| Capability | Implementation | Audit result |
| --- | --- | --- |
| Playback, URL streaming, file decoding | BASS core stream APIs | Required |
| Gapless playback, crossfade, prepared-next playback, resampling | Direct BASSmix API calls | Required on every platform |
| Equalizer | BASS core `BASS_ChannelSetFX` with `BASS_FX_DX8_PARAMEQ` | Does **not** use the BASS FX add-on |
| FFT and visualizers | BASS core `BASS_ChannelGetData` | Does not use an add-on |
| Waveform analysis | BASS core data and level APIs | Does not use an add-on |
| ReplayGain | Provider/file metadata applied by Core | Does not use BASSloud |
| HTTPS on Android | BASS_SSL supplies the TLS implementation used by BASS | Required while Android streams URLs directly through BASS |

The direct BASSmix calls are present in the shared backend contract's native adapters and include
stream creation, channel insertion/removal, seeking, and version checks. No Naviamp production call
site was found for BASS FX, BASSloud, BASSMIDI, BASSWMA, or BASS_SPX APIs.

## Current Bundled Inventory

`Yes` means at least one binary for that component is currently vendored for the target. It does not
mean the component is usable.

| Component | Purpose | Android | macOS | Windows | Linux | iOS |
| --- | --- | :---: | :---: | :---: | :---: | :---: |
| BASS core | Core playback and built-in formats | Yes | Yes | Yes | Yes | Yes |
| BASSmix | Mixing and resampling | Yes | Yes | Yes | Yes | Yes |
| BASS_FLAC | FLAC and Ogg FLAC | Yes | Yes | Yes | Yes | Yes |
| BASSOPUS | Opus | Yes | Yes | Yes | Yes | Yes |
| BASS_AAC | AAC/MP4 | Yes | No | Yes | Yes | No |
| BASSALAC | ALAC | Yes | No | Yes | Yes | No |
| BASS_AC3 | AC-3 | Yes | No | No | Yes | No |
| BASS_APE | Monkey's Audio | Yes | Yes | Yes | Yes | Yes |
| BASSDSD | DSD | Yes | Yes | Yes | Yes | Yes |
| BASSHLS | HLS streams and playlists | Yes | Yes | Yes | Yes | Yes |
| BASSMIDI | MIDI using SF2/SFZ soundfonts | Yes | Yes | Yes | Yes | Yes |
| BASS_MPC | Musepack | Yes | Yes | Yes | Yes | Yes |
| BASSWEBM | WebM/Matroska | Yes | Yes | Yes | Yes | Yes |
| BASSWV | WavPack | Yes | Yes | No | Yes | Yes |
| BASS_TTA | True Audio | Yes | No | No | Yes | Yes |
| BASS_SPX | Speex | No | No | No | Yes | No |
| BASSWMA | Windows Media Audio | No | No | Yes | No | No |
| BASS FX | Reverse, tempo/pitch, and additional effects | Removed | Removed | Removed | Removed | Removed |
| BASSloud | Loudness measurement | Removed | Removed | Removed | Removed | Removed |
| BASS_SSL | TLS support | Yes | No | Yes | No | No |

Before cleanup, the raw vendor trees occupied approximately 12,516 KiB for Android, 7,056 KiB for
Desktop, and 9,936 KiB for iOS. Removing BASS FX and BASSloud reduced those trees to 11,988 KiB,
6,564 KiB, and 8,784 KiB respectively: a combined source-tree reduction of 2,172 KiB. These are
costs across architectures, not expected one-for-one release-size savings.

Approximate per-component source-tree costs are below. Android combines four ABI binaries, Desktop
combines every target on which the component exists, and iOS includes both XCFramework slices and
their metadata. A dash means the component is not currently vendored for that host family.

| Component | Android KiB | Desktop KiB | iOS KiB |
| --- | ---: | ---: | ---: |
| BASS core | 1,232 | 1,264 | 1,720 |
| BASSmix | 248 | 280 | 532 |
| BASS_SSL | 6,252 | 956 | — |
| FLAC | 264 | 352 | 620 |
| Opus | 452 | 628 | 924 |
| AAC | 880 | 464 | — |
| ALAC | 88 | 44 | — |
| AC-3 | 152 | 40 | — |
| APE | 932 | 500 | 764 |
| DSD | 72 | 136 | 364 |
| HLS | 116 | 208 | 432 |
| MIDI | 596 | 732 | 1,052 |
| Musepack | 204 | 296 | 508 |
| WebM | 172 | 244 | 476 |
| WavPack | 260 | 328 | 684 |
| True Audio | 68 | 12 | 360 |
| Speex | — | 48 | — |
| WMA | — | 32 | — |
| BASS FX | 440 removed | 468 removed | 692 removed |
| BASSloud | 88 removed | 24 removed | 380 removed |

## Platform Load Findings

### Android

`AndroidBassNativeLoader` process-loads BASS core, BASS_SSL, BASSmix, and the 13 bundled codec
libraries. The native bridge directly links BASS core and BASSmix, registers the codecs through its
JNI surface, and publishes the results through the shared plugin diagnostic model.

**Finding A1 — corrected:** Android previously proved only that codec libraries could be
process-loaded. The shared codec inventory is now translated to Android filenames and registered
through `BASS_PluginLoad` at the narrow JNI boundary. On August 5, 2026, a clean emulator launch
reported all 16 packaged native prerequisites loaded and all 13 codec plugins registered, with zero
failures. Representative media playback remains part of the format acceptance matrix.

BASSmix remains functional because Naviamp calls its API directly. BASS_SSL is a special Android
dependency used by BASS for HTTPS and is not a codec plugin.

### Desktop

Desktop discovers present binaries from the Core-owned decoder inventory and calls
`BASS_PluginLoad` only for those codec plugins.

**Finding D1 — corrected:** BASSmix is a directly linked feature library, while BASS FX and BASSloud
were feature libraries with no Naviamp call sites. Desktop now consumes the Core-owned decoder
inventory, does not attempt to register feature libraries as codecs, and no longer packages BASS FX
or BASSloud. The native Desktop integration test now requires every present codec plugin to register
successfully.

Desktop packaging still copies the full target vendor directory, but verification now declares the
exact intentional inventory for macOS, Windows, and Linux and rejects missing or unexpected entries.

### iOS

iOS now links and embeds 12 XCFrameworks. It calls `BASS_PluginLoad` only for its codec set and
ensures registration occurs before every file or URL stream creation path. A simulator integration
test confirms all 12 retained components load, including all 10 codec plugins.

**Finding I1 — partially corrected:** BASS FX and BASSloud have been removed from linking, embedding,
headers, licenses, and runtime diagnostics. BASSMIDI remains registered as a codec, but no soundfont
or BASSMIDI API use was found; MIDI playback is not a viable advertised capability without a
deliberate soundfont product decision.

## Component Decisions

| Component | Current classification | Evidence or next decision |
| --- | --- | --- |
| BASS core | Keep | Required by every playback and analysis path |
| BASSmix | Keep | Directly required for queue mixing, gapless, crossfade, and resampling |
| BASS_SSL on Android | Keep | Required for direct HTTPS URL streams |
| FLAC and Opus | Keep and verify | Explicit Naviamp stream/download formats; test local and remote playback |
| AAC and ALAC | Keep substitution, verify | CoreAudio supplies them on Apple; modern Android and Windows have system codec paths, but fallbacks and minimum OS behavior need tests |
| APE, DSD, HLS, MPC, WebM, WavPack, TTA, AC-3 | Decision pending | Potentially useful for original server streams; retain until a supported-format contract and fixtures prove the intended cross-platform set |
| BASS FX | Removed | No add-on API calls; Naviamp EQ uses the similarly named effect API built into BASS core |
| BASSloud | Removed | No API calls; ReplayGain is not loudness measurement |
| BASSMIDI | Removal candidate unless MIDI is planned | No bundled soundfont and no BASSMIDI calls |
| BASS_SPX on Linux | Removal candidate | No API calls, no parity, and no documented product requirement |
| BASSWMA on Windows | Removal candidate | No API calls; supported Windows versions provide WMA through Media Foundation |
| BASS_SSL on Windows | Removal candidate | BASS documents its Windows role for BASSenc; Naviamp does not use BASSenc |

Removal candidates are not yet approved deletions. They become approved only after the format and
feature acceptance suite passes on every affected platform.

## Proposed Supported-Format Contract

The contract should describe user-visible formats, not library filenames:

1. **Required Naviamp formats:** MP3, MP2/MP1, Ogg Vorbis, WAV, AIFF, FLAC/Ogg FLAC, Opus, AAC/M4A,
   and ALAC. These cover BASS built-ins plus the formats explicitly represented by Naviamp's
   streaming and offline-file code.
2. **Original-library compatibility candidates:** APE, WavPack, DSD, Musepack, True Audio, AC-3,
   WebM/Matroska audio, and HLS. Confirm what Navidrome can return unchanged and decide which of these
   Naviamp promises on all release platforms.
3. **Not currently promised:** MIDI, Speex, and legacy WMA add-on behavior. Promote one only with a
   concrete use case, consistent platform support, and representative tests.

The BASS core documentation lists MP3/MP2/MP1, Ogg Vorbis, WAV, and AIFF as built-in stream formats,
with additional codecs supplied by plugins or operating-system codecs:
[`BASS_StreamCreateFile`](https://www.un4seen.com/doc/bass/BASS_StreamCreateFile.html). The add-on
purposes and published platform packages are listed on the
[`BASS audio library`](https://www.un4seen.com/bass.html) page.

## Work Checklist

### Inventory and evidence

- [x] Inventory Android, macOS, Windows, Linux, and iOS vendor trees.
- [x] Locate all direct add-on API call sites.
- [x] Separate process loading, BASS plugin registration, and direct API use.
- [x] Record raw vendor-tree size by platform and component.
- [x] Identify operating-system codec substitutions documented by BASS.
- [ ] Confirm the supported minimum-OS codec behavior for AAC, ALAC, AC-3, and WMA.
- [ ] Confirm which original formats Navidrome can return to Naviamp without transcoding.
- [ ] Approve the user-visible supported-format contract.

### Test media and automated acceptance

- [ ] Add small, redistributable fixtures for every promised format.
- [ ] Test file playback and URL playback through the same Core engine contract.
- [ ] Test offline/downloaded playback for every required cached-file extension.
- [ ] Test waveform generation, FFT/visualizer data, seek, duration, and end-of-stream callbacks.
- [ ] Test gapless and crossfade transitions between unlike formats and sample rates.
- [x] Test ReplayGain volume application and BASS core equalizer behavior without BASS FX or BASSloud present on Desktop; retain cross-platform acceptance coverage below.
- [x] Record actual plugin registration success/errors in Android, Desktop, and iOS diagnostics and integration tests.
- [ ] Run the matrix on Android ARM64, macOS ARM64, Windows x64, Linux x64, and iOS device/simulator.

### Corrections and cleanup

- [x] Add Android codec registration and plugin diagnostics at the JNI/native-loading boundary.
- [x] Split Desktop codec plugins from directly linked feature libraries.
- [x] Remove BASS FX and BASSloud load attempts, binaries, headers/licenses, build links, and package metadata.
- [x] Make decoder classification derive from one reviewed Core inventory.
- [x] Add package checks that reject missing required components and unexpected bundled components.
- [ ] Compare release artifact sizes before and after cleanup.
- [ ] Re-run the full playback, offline, waveform, crossfade, EQ, visualizer, and packaging acceptance suite.

## First Implementation Slice

The first code change corrected registration and diagnostics before broader codec decisions:

1. [x] Define the intended decoder-plugin stems once in shared Core metadata.
2. [x] Expose Android's narrow `BASS_PluginLoad` operation through JNI and publish the same diagnostic
   model used by the other BASS backends.
3. [x] Stop Desktop from attempting to register feature-only libraries as decoders.
4. [ ] Add representative FLAC and Opus integration coverage on all three host families.

After that slice proves the audit can distinguish usable decoders from packaged files, the safe
removal candidates can be handled as a separate, measurable cleanup change.
