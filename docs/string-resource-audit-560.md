# String resource audit (#560)

Baseline: `22a6feb6e` (main). Scope: all tracked application catalogs,
shared/host source sets, build scripts and packaging generators. No product behavior changes.

## Method

Run `python3 scripts/audit-string-resources.py > /tmp/string-audit.json`.
The read-only tool inventories XML string/plural/array catalogs and lists every
consumer file containing a resource identifier, including imports, comments,
conditional expressions, string literals in native/packaging scripts and XML
resource values. It deliberately over-retains mentions; its candidates require
manual review. It is not an automatic unused-resource detector or deletion tool.

To reproduce the original removal candidates after cleanup:
`python3 scripts/audit-string-resources.py --catalog-ref 22a6feb6e`.

Reviewed the 1,281 source keys and all 16 maintained translations (17 catalogs).
No separate tracked Android string catalog or Apple `.strings`, `.stringsdict`,
or `.xcstrings` catalog exists. Android's manifest `@string/app_name` is generated
by `resValue` in `apps/android/build.gradle.kts`; keep it, including test and
benchmark variants. iOS bundle display names are platform metadata in Info.plist.

Reviewed dynamic boundaries as well as name references:

- Shared `Res.string`, `Res.plurals` and resource-typed models select generated
  accessors explicitly. Conditional resources, enum mappings, language packs,
  Home section descriptors and Connect status descriptors remain roots even
  when resolved through another function. All imports alone were cross-checked:
  there are no keys whose only consumer is an import.
- No application use of name-based Android `getIdentifier`, Compose
  `allStringResources`/plural/array maps, `StringResource` constructors, or Apple
  localized-string key lookup was found. Reflection consumers are unrelated to
  strings (native window callbacks, JDBC and test probes).
- XML values were checked for resource-to-resource references separately from
  the `name` declarations. No removed key has an incoming value reference.
- Compose generates accessors and resource collectors from every catalog entry;
  those generated definitions are not independent product consumers. Generation
  must be rerun after cleanup. `prepareLanguageResources` also packages Indonesian
  as `values-in`; it copies `values-id` rather than maintaining another catalog.
- Keep `permission_local_network_usage`: `configure-macos-local-network.py`
  directly reads that key from each XML catalog to generate Info.plist and
  InfoPlist.strings. Native generated usage is a real consumer even without a
  composable reference. Build inputs in the desktop module preserve this path.
- Accessibility labels are ordinary typed resources. Keep navigation/back,
  transport, search, artwork and Home-scroll descriptions and the shared selected
  semantics. No removed key is referenced by a semantics/contentDescription path.

## Removed inventory

53 keys / 278 catalog entries (the full list below) have no consumers after reviewing the above
boundaries. Delete each from the source and every translation containing it.
Missing translations already use source fallback; no translation is synthesized.

- `common_clear`
- `common_export`
- `common_import`
- `connection_connect_or_enter_credentials_to_load_available_libraries`
- `connection_default_library`
- `discovery_chart_rank_history`
- `home_mixes_for_you`
- `home_navibeat_mixes`
- `home_recent_radio_empty`
- `home_recently_added_music`
- `library_clear_search`
- `mix_seed_tracks`
- `nav_album_mix`
- `nav_artist_mix`
- `nav_downloads`
- `nav_genre_mix`
- `nav_home`
- `nav_library`
- `nav_playlists`
- `nav_radio`
- `nav_search`
- `nav_sonic_path`
- `search_clear`
- `settings_about_license_title`
- `settings_autoplay_summary`
- `settings_experience_empty`
- `settings_lyrics_prefer_synced`
- `settings_lyrics_prefer_synced_subtitle`
- `settings_lyrics_prefer_word_synced`
- `settings_lyrics_prefer_word_synced_subtitle`
- `settings_lyrics_source_download`
- `settings_lyrics_source_download_subtitle`
- `settings_lyrics_summary_synced`
- `settings_lyrics_summary_synced_download`
- `settings_lyrics_summary_word_synced`
- `settings_lyrics_timing`
- `settings_lyrics_timing_subtitle`
- `settings_output_default_suffix`
- `settings_output_selected_suffix`
- `settings_related_autoplay_summary`
- `settings_related_lyrics_download`
- `settings_related_summary`
- `settings_sync_configured`
- `similarity_radio_failed`
- `smart_playlist_summary_ascending`
- `smart_playlist_summary_descending`
- `tv_adjust_how_strongly_the_album_artwork_is_softened`
- `tv_adjust_hue_saturation_and_brightness`
- `tv_lyrics_are_not_available`
- `tv_make_this_tv_visible_temporarily_to_a_phone_or_desktop_on_the_same_network`
- `tv_put_this_tv_in_pairing_mode_find_it_from_naviamp_on_the_same_network_and_enter`
- `tv_this_tv_is_visible_to_naviamp_controllers_on_your_local_network`
- `tv_tune_artwork_derived_colors_for_the_room`

## Dispositions by current owner

- Radio summary: `PlaybackSettings.radioSummary()` in `NaviampSettingsUi.kt`
  uses `settings_related_sonic_similarity` or `radio_instant_mix`, then
  `settings_related_sonic_autoplay`. The three issue examples
  (`settings_related_autoplay_summary`, `settings_related_summary`,
  `settings_autoplay_summary`) are all obsolete.
- Lyrics: `lyricsSummary()`, `LyricsTimingPreference.label()` and
  `LyricsDisplayPreference.label()` use current timing/display resources and
  `settings_lyrics_summary_download`. The old prefer-synced switches, legacy
  summary combinations, old timing heading and old download-source labels are
  unused. TV uses the same current timing choices in `NaviampTelevisionSettings.kt`;
  `NaviampLyricsUi.kt` owns the current lyrics display/empty paths.
- Navigation/Home/mixes: `NaviampWindowUi.kt`, `NaviampRouteContent.kt`,
  `NaviampTelevisionStrings.kt` and `NaviampMediaComponents.kt` use current page
  titles, Home collection descriptors and `mix_selected_seed_tracks` /
  `mix_find_seed_tracks`. No name-derived `nav_*` or Home lookup exists.
- Search/clear and generic import/export: current shared controls and settings
  actions do not consume these legacy keys. Settings sync uses explicit
  `settings_sync_export_local` and `settings_sync_import_provider` action labels.
- Settings experience/license/sync/output: current settings categories are
  populated; About uses `NaviampLegalNotices.kt`; sync displays its current
  state/actions; output rows use current default/availability text and shared
  selected semantics. The suffix resources are not read even by the legacy
  `outputDeviceTitle` helper, which itself has no callers.
- TV pairing/background/lyrics: `NaviampTelevisionSettings.kt` uses current
  shared Connect status and pairing descriptions, current background controls
  and shared lyrics paths. The seven obsolete TV prose keys are not selected by
  `NaviampTelevisionStrings.kt` or any platform host.
- Connection: `NaviampConnectionUi.kt` renders the current connection and library
  picker model; the two old library placeholder labels have no consumer.
- Radio failures: `NaviampSimilarityTestContent.kt` selects current Radio outcome
  resources; the old generic failure resource is unused.
- Smart-playlist summary: `NaviampSmartPlaylistSummary.kt` chooses A–Z/Z–A,
  newest/oldest and highest/lowest by field kind; generic ascending/descending
  labels are unused.
- Discovery charts: `NaviampDiscoveryChartsUi.kt` displays position, new-entry
  and movement resources; it does not render a recent-rank history string.

## Verification

Passed:

- Three audit-tool tests (conditional selection, native/packaging literals, XML
  value references and string/plural/array inventory).
- All 16 maintained translations validated with `validate-translations.py`.
- All 84 existing `test_verification_*.py` tests (outside the sandbox because the
  fixture suite needs process inspection and loopback HTTP servers).
- Exact XML comparison against the baseline: only the 278 intended entries
  removed; every retained resource's attributes, children and value unchanged.
- Baseline-mode audit reproduces the 53 candidates after deletion; current-mode
  audit reports no further unreferenced candidates.
- Compose resource generation, `:core:ui:compileDebugKotlinAndroid`,
  `:core:ui:compileKotlinJvm`, `:core:ui:compileKotlinIosArm64`,
  `:core:ui:compileKotlinIosSimulatorArm64` and `verifyCoreFirstArchitecture`
  passed together (2m 57s). Used installed JDK 21 with `--no-daemon` to avoid
  a stale Java path retained by the local daemon registry.

- Five existing JVM locale regressions passed: `NaviampTranslationResourcesTest`,
  `NaviampLanguageResourcesUiTest` and `NaviampLanguageCatalogTest` (47s). These
  exercise all bundled languages, actual rendered resource resolution, preserved
  focus/composition and RTL direction, and full-catalog key/format parity.

No platform production source files changed. No new UI copy or settings were
introduced. Local affected checks pass; the complete protected-branch CI matrix
and review remain the merge gate. Keep #560 open until merge.
