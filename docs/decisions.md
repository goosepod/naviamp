# Decisions

## Accepted

- The project is named Naviamp.
- The app should be private initially but ready to open source later.
- Desktop is the first implementation target.
- Navidrome is the first and only required provider for initial releases.
- The architecture should support additional media providers later.
- Direct streaming is the initial playback mode.
- Gapless playback and configurable crossfade matter.
- Offline downloads are planned after streaming playback is working.
- Apache-2.0 is the current license choice.
- The Forgejo repository is `https://forgejo.goosepod.lan/ursasmar/naviamp`.
- Kotlin/Gradle is accepted as the main toolchain.
- The project should be written as a teaching project for learning Kotlin.
- The first implementation goal is the quickest useful MVP.
- JDK 21 is the local development baseline.
- Kotlin `2.2.21`, Compose Multiplatform `1.10.3`, and Gradle `8.14` are the initial version targets.

## Preferred

- Kotlin Multiplatform with Compose Multiplatform is the current preferred stack.

## Open

- Desktop playback engine choice.
- Credential storage approach per platform.
- Exact first public release scope.
