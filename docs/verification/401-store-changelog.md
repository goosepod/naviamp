# Three-store release changelog correction — October 29, 2026

Tracking: #401, requested by the owner during phone acceptance fixes #398/#399.

**Superseded by the owner on October 29:** Store launch entries are not required in the
changelog. The current combined candidate removes them from the shared About changelog,
`CHANGELOG.md` and draft v2.10.0 release body, restoring Aurral as the leading feature.
The observations below describe the earlier Store-first candidate, not the current content.

The shared English About changelog now names Microsoft Store distribution on Windows, Mac App Store distribution on macOS, and App Store distribution on iOS in one leading store-release entry. The matching `CHANGELOG.md` and draft v2.10.0 GitHub release body carry the same platform mapping and place store distribution first. Existing direct downloads remain mentioned. The existing Mac listing is reused; no unverified iOS listing URL is invented.

The signed validation candidate retains its existing Aurral notes and orders them after the store releases. The focused branch against main changes only the omitted store wording and matching regression; it does not drop other accepted release changes when integrated with the newer candidate.

The changelog regression explicitly checks all three store/platform names in the first feature. Both rendered About/Changelog cases pass, including scrolling all current entries and TV keyboard navigation. The start capture visibly shows the complete three-store entry followed by Aurral. All five related connection cases also pass in the combined candidate. Changelog content stays in shared Kotlin and English release Markdown, not in translated resources; no navigation/control label is changed.

The final common implementation compiles for JVM/Android and both iOS ARM64 targets. `:apps:android:stageReleaseArtifacts` succeeds. The final signed build-60 APK SHA-256 is `b1a4883d3496986d1495fc0ef94135187667e44d77aefda9e7df6a789a153cca`, with the existing production signing certificate. It is installed in place, and the actual phone About → Changelog UI displays all three store/platform names together in its first feature entry. The native probe passes in 4.181 seconds.

The subsequent combined cleanup probe activates the fixed TLS label on and off, then loses the device's USB/ADB transport. That interruption is not counted as a completed final playback/cleanup run. Reconnection and removal of the two test harness packages remain to be recorded with the acceptance work.

No tag, GitHub release, store submission or publication is performed. This correction changes release content, not store approval or availability status. No Android/Desktop/iOS production source is modified.
