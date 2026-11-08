# Primary Home carousel defaults

Tracking: [#363](https://github.com/goosepod/naviamp/issues/363).

Favorite Artists, Mixes for You, Mix Builders, Recently Added Music and Recent Playlists now resolve
to Carousel when they have no saved presentation. The pre-existing Navibeat Carousel default remains;
other sections retain their defaults. Explicit saved List, Grid and Carousel choices are unchanged.
Visibility, page layout, item limits and section ordering are preserved.

This is a shared default policy, not a settings migration. New installations and older exports with
no presentation entry receive the fallback. Existing explicit presentations keep their choice after
normalization, export/import and sync. No schema, new setting, strings or platform source changes.

Verification: HomeCarouselDefaultsTest (two cases), SettingsSyncDocumentTest and SettingsSyncMappingTest
passed. JVM tests, Android and iOS simulator domain compilation and the architecture gate passed in a
clean worktree (17 tasks executed). Tests cover all five defaults, unchanged sections, all explicit
layout choices, normalization, older export defaults, visibility, page layout and order preservation.

```sh
./gradlew :core:domain:jvmTest --tests '*HomeCarouselDefaultsTest*' \
  --tests '*SettingsSyncDocumentTest*' --tests '*SettingsSyncMappingTest*' \
  :core:domain:compileDebugKotlinAndroid :core:domain:compileKotlinIosSimulatorArm64 \
  verifyCoreFirstArchitecture
```
