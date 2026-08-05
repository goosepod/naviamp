package app.naviamp.presentation

import app.naviamp.domain.playback.EqualizerBandFrequencies
import app.naviamp.domain.playback.EqualizerPreset
import app.naviamp.domain.playback.EqualizerProfile
import app.naviamp.domain.playback.EqualizerSettings
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.radio.RadioArtistSpread
import app.naviamp.domain.radio.RadioDjPreset
import app.naviamp.domain.radio.RadioFamiliarity
import app.naviamp.domain.radio.RadioTuningSettings
import app.naviamp.domain.settings.AlbumCollectionLayout
import app.naviamp.domain.settings.AlbumSortOrder
import app.naviamp.domain.settings.AppBackgroundStyle
import app.naviamp.domain.settings.AuroraTone
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.DownloadedTrackPlayback
import app.naviamp.domain.settings.InterfaceLanguage
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.LyricsSourcePreference
import app.naviamp.domain.settings.NowPlayingDisplaySettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.PreviousButtonBehavior
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SampleRateConverter
import app.naviamp.domain.settings.SampleRateMatching
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import app.naviamp.domain.settings.StreamQualityMode
import app.naviamp.domain.settings.StreamQualityPreference
import app.naviamp.domain.settings.StreamingCodec
import app.naviamp.domain.settings.TrackSwipeAction
import app.naviamp.domain.settings.TrackSwipeSettings
import app.naviamp.domain.settings.UpNextSelectionBehavior
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.domain.settings.normalized
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Read-only view of a superseded keyed settings format. Hosts only expose raw values. */
interface NaviampCoreLegacySettingsValueStore {
    fun contains(key: String): Boolean
    fun read(key: String): String?
}

enum class NaviampCoreSettingsMigrationSection {
    Interface,
    Playback,
    Cache,
    Visualizer,
    RecentRadio,
    RecentInternetRadio,
    SyncRuntime,
    RecentPlaylists,
}

/**
 * Converts Naviamp's original per-field settings schema into Core-owned serialized settings.
 * Existing Core values always win, making this safe to run on every startup.
 */
fun migrateLegacyNaviampSettings(
    legacy: NaviampCoreLegacySettingsValueStore,
    destination: NaviampCoreSettingsValueStore,
    json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
): Set<NaviampCoreSettingsMigrationSection> {
    val migrated = mutableSetOf<NaviampCoreSettingsMigrationSection>()
    val catalog = naviampCoreSettingsValueCatalogWithoutMigration(destination, json)
    val settings = catalog.storedSettings

    migrateSerializedSection(
        legacy, destination, KeyInterface, "interfaceSettings", InterfaceSettings.serializer(), json,
    ) { settings.saveInterface(it.normalized()) }?.let { migrated += NaviampCoreSettingsMigrationSection.Interface }
    migrateSerializedSection(
        legacy, destination, KeyPlayback, "playback", PlaybackSettings.serializer(), json,
    ) { catalog.savePlayback(it.normalized()) }?.let { migrated += NaviampCoreSettingsMigrationSection.Playback }
    migrateSerializedSection(
        legacy, destination, KeyCache, "cache", CacheSettings.serializer(), json,
    ) { settings.saveCache(it.normalized()) }?.let { migrated += NaviampCoreSettingsMigrationSection.Cache }
    migrateSerializedSection(
        legacy, destination, KeyVisualizer, "visualizer", VisualizerSettings.serializer(), json,
    ) { settings.saveVisualizer(it) }?.let { migrated += NaviampCoreSettingsMigrationSection.Visualizer }
    migrateSerializedSection(
        legacy,
        destination,
        KeyRecentRadio,
        "recentRadioStreams",
        ListSerializer(RecentRadioStream.serializer()),
        json,
    ) { settings.saveRecentRadioStreams(it) }
        ?.let { migrated += NaviampCoreSettingsMigrationSection.RecentRadio }
    migrateSerializedSection(
        legacy,
        destination,
        KeyRecentInternetRadio,
        "recentInternetRadioStations",
        ListSerializer(SavedInternetRadioStation.serializer()),
        json,
    ) { settings.saveRecentInternetRadioStations(it) }
        ?.let { migrated += NaviampCoreSettingsMigrationSection.RecentInternetRadio }
    migrateSerializedSection(
        legacy, destination, KeySyncRuntime, "settingsSyncRuntime", SettingsSyncRuntimeState.serializer(), json,
    ) { settings.saveSyncRuntime(it.normalized()) }?.let { migrated += NaviampCoreSettingsMigrationSection.SyncRuntime }
    migrateSerializedSection(
        legacy,
        destination,
        KeyRecentPlaylists,
        "recentPlaylistIds",
        ListSerializer(String.serializer()),
        json,
    ) { settings.saveRecentPlaylistIds(it) }
        ?.let { migrated += NaviampCoreSettingsMigrationSection.RecentPlaylists }

    migrateSection(legacy, destination, KeyInterface, LegacyInterfaceKeys) {
        settings.saveInterface(legacyInterfaceSettings(legacy))
        migrated += NaviampCoreSettingsMigrationSection.Interface
    }
    migrateSection(legacy, destination, KeyPlayback, LegacyPlaybackKeys) {
        catalog.savePlayback(legacyPlaybackSettings(legacy, json))
        migrated += NaviampCoreSettingsMigrationSection.Playback
    }
    migrateSection(legacy, destination, KeyCache, LegacyCacheKeys) {
        settings.saveCache(legacyCacheSettings(legacy))
        migrated += NaviampCoreSettingsMigrationSection.Cache
    }
    migrateSection(legacy, destination, KeyVisualizer, listOf("selected_visualizer")) {
        settings.saveVisualizer(
            VisualizerSettings(
                selectedVisualizer = legacy.read("selected_visualizer")
                    ?: VisualizerSettings().selectedVisualizer,
            ),
        )
        migrated += NaviampCoreSettingsMigrationSection.Visualizer
    }
    migrateSection(legacy, destination, KeyRecentRadio, listOf("recent_radio_streams")) {
        settings.saveRecentRadioStreams(
            legacy.list("recent_radio_streams", RecentRadioStream.serializer(), json).take(12),
        )
        migrated += NaviampCoreSettingsMigrationSection.RecentRadio
    }
    migrateSection(
        legacy,
        destination,
        KeyRecentInternetRadio,
        listOf("recent_internet_radio_stations"),
    ) {
        settings.saveRecentInternetRadioStations(
            legacy.list(
                "recent_internet_radio_stations",
                SavedInternetRadioStation.serializer(),
                json,
            ).take(12),
        )
        migrated += NaviampCoreSettingsMigrationSection.RecentInternetRadio
    }
    migrateSection(legacy, destination, KeySyncRuntime, LegacySyncRuntimeKeys) {
        settings.saveSyncRuntime(
            SettingsSyncRuntimeState(
                autoExportEnabled = legacy.boolean("settings_sync_auto_export_enabled", false),
                lastLocalUpdateEpochMillis = legacy.long("settings_sync_last_local_update_epoch_millis", 0L),
                lastAppliedSyncUpdateEpochMillis = legacy.long(
                    "settings_sync_last_applied_update_epoch_millis",
                    0L,
                ),
            ),
        )
        migrated += NaviampCoreSettingsMigrationSection.SyncRuntime
    }
    return migrated
}

/** Moves the superseded Desktop JSON queue into the shared playback-session repository once. */
fun migrateLegacyNaviampPlaybackSession(
    values: NaviampCoreMutableSettingsValueStore,
    sourceId: String,
    loadCurrent: (String) -> PlaybackSessionSettings?,
    save: (PlaybackSessionSettings, String) -> Unit,
    json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
): Boolean {
    if (loadCurrent(sourceId) != null) return false
    val legacy = values.read(LegacyPlaybackSessionKey)
        ?.let { encoded ->
            runCatching { json.decodeFromString(PlaybackSessionSettings.serializer(), encoded) }.getOrNull()
        }
        ?: return false
    save(legacy, sourceId)
    values.remove(LegacyPlaybackSessionKey)
    return true
}

private inline fun <T> migrateSerializedSection(
    legacy: NaviampCoreLegacySettingsValueStore,
    destination: NaviampCoreSettingsValueStore,
    destinationKey: String,
    legacyKey: String,
    serializer: KSerializer<T>,
    json: Json,
    save: (T) -> Unit,
): Unit? {
    if (destination.read(destinationKey) != null) return null
    val value = legacy.read(legacyKey)
        ?.let { encoded -> runCatching { json.decodeFromString(serializer, encoded) }.getOrNull() }
        ?: return null
    save(value)
    return Unit
}

private const val LegacyPlaybackSessionKey = "session"

private inline fun migrateSection(
    legacy: NaviampCoreLegacySettingsValueStore,
    destination: NaviampCoreSettingsValueStore,
    destinationKey: String,
    legacyKeys: List<String>,
    migrate: () -> Unit,
) {
    if (destination.read(destinationKey) == null && legacyKeys.any(legacy::contains)) migrate()
}

private fun legacyInterfaceSettings(values: NaviampCoreLegacySettingsValueStore): InterfaceSettings {
    val defaults = InterfaceSettings()
    val nowPlaying = defaults.nowPlaying
    val swipes = defaults.trackSwipes
    return InterfaceSettings(
        language = values.enum("interface_language", defaults.language),
        checkForUpdates = values.boolean("check_for_updates", defaults.checkForUpdates),
        startPlayingOnLaunch = values.boolean("start_playing_on_launch", defaults.startPlayingOnLaunch),
        showDesktopTooltips = values.boolean("show_desktop_tooltips", defaults.showDesktopTooltips),
        appBackgroundStyle = values.enum("app_background_style", defaults.appBackgroundStyle),
        auroraTone = values.enum("aurora_tone", defaults.auroraTone),
        albumBlurRadiusDp = values.int("album_blur_radius_dp", defaults.albumBlurRadiusDp),
        singleColorHex = values.read("single_color_hex") ?: defaults.singleColorHex,
        albumCollectionLayout = values.enum("album_collection_layout", defaults.albumCollectionLayout),
        albumSortOrder = values.enum("album_sort_order", defaults.albumSortOrder),
        groupAlbumsByReleaseType = values.boolean(
            "group_albums_by_release_type",
            defaults.groupAlbumsByReleaseType,
        ),
        nowPlaying = NowPlayingDisplaySettings(
            showAlbumYear = values.boolean("now_playing_show_album_year", nowPlaying.showAlbumYear),
            showAudioInfo = values.boolean("now_playing_show_audio_info", nowPlaying.showAudioInfo),
            showVolumeBar = values.boolean("now_playing_show_volume_bar", nowPlaying.showVolumeBar),
            scrollTrackTitle = values.boolean("now_playing_scroll_track_title", nowPlaying.scrollTrackTitle),
            scrollArtistName = values.boolean("now_playing_scroll_artist_name", nowPlaying.scrollArtistName),
            scrollAlbumName = values.boolean("now_playing_scroll_album_name", nowPlaying.scrollAlbumName),
        ),
        trackSwipes = TrackSwipeSettings(
            libraryRight = values.enum("swipe_library_right", swipes.libraryRight),
            libraryLeft = values.enum("swipe_library_left", swipes.libraryLeft),
            queueRight = values.enum("swipe_queue_right", swipes.queueRight),
            queueLeft = values.enum("swipe_queue_left", swipes.queueLeft),
            relatedRight = values.enum("swipe_related_right", swipes.relatedRight),
            relatedLeft = values.enum("swipe_related_left", swipes.relatedLeft),
            playlistEditRight = values.enum("swipe_playlist_edit_right", swipes.playlistEditRight),
            playlistEditLeft = values.enum("swipe_playlist_edit_left", swipes.playlistEditLeft),
            downloadsRight = values.enum("swipe_downloads_right", swipes.downloadsRight),
            downloadsLeft = values.enum("swipe_downloads_left", swipes.downloadsLeft),
        ),
    ).normalized()
}

private fun legacyPlaybackSettings(
    values: NaviampCoreLegacySettingsValueStore,
    json: Json,
): PlaybackSettings {
    val defaults = PlaybackSettings()
    return PlaybackSettings(
        replayGainMode = values.enum("replay_gain_mode", defaults.replayGainMode),
        replayGainInspectorEnabled = values.boolean(
            "replay_gain_inspector_enabled",
            defaults.replayGainInspectorEnabled,
        ),
        sampleRateConverter = values.enum("sample_rate_converter", defaults.sampleRateConverter),
        sampleRateMatching = values.enum("sample_rate_matching", defaults.sampleRateMatching),
        gaplessEnabled = values.boolean("gapless_enabled", defaults.gaplessEnabled),
        crossfadeDurationSeconds = values.int("crossfade_duration_seconds", defaults.crossfadeDurationSeconds),
        equalizer = legacyEqualizerSettings(values, json),
        debugLoggingEnabled = values.boolean("debug_logging_enabled", defaults.debugLoggingEnabled),
        lrclibLyricsEnabled = values.boolean("lrclib_lyrics_enabled", defaults.lrclibLyricsEnabled),
        preferSyncedLyrics = values.boolean("prefer_synced_lyrics", defaults.preferSyncedLyrics),
        preferWordSyncedLyrics = values.boolean(
            "prefer_word_synced_lyrics",
            defaults.preferWordSyncedLyrics,
        ),
        lyricsSearchOrder = values.list(
            "lyrics_search_order",
            LyricsSourcePreference.serializer(),
            json,
            defaults.lyricsSearchOrder,
        ),
        sonicSimilarityEnabled = values.boolean("sonic_similarity_enabled", defaults.sonicSimilarityEnabled),
        sonicAutoplayEnabled = values.boolean("sonic_autoplay_enabled", defaults.sonicAutoplayEnabled),
        previousButtonBehavior = values.enum("previous_button_behavior", defaults.previousButtonBehavior),
        upNextSelectionBehavior = values.enum("up_next_selection_behavior", defaults.upNextSelectionBehavior),
        removePlayedTracksFromQueue = values.boolean(
            "remove_played_tracks_from_queue",
            defaults.removePlayedTracksFromQueue,
        ),
        radioTuning = RadioTuningSettings(
            familiarity = values.enum("radio_familiarity", defaults.radioTuning.familiarity),
            artistSpread = values.enum("radio_artist_spread", defaults.radioTuning.artistSpread),
            sameDecadeOnly = values.boolean("radio_same_decade_only", defaults.radioTuning.sameDecadeOnly),
        ),
        radioDjs = values.list("radio_djs", RadioDjPreset.serializer(), json),
        activeRadioDjId = values.read("active_radio_dj_id"),
        wifiStreamingQuality = legacyStreamQuality(
            values,
            "wifi_stream_quality_mode",
            "wifi_stream_codec",
            "wifi_stream_bitrate",
            defaults.wifiStreamingQuality,
        ),
        mobileStreamingQuality = legacyStreamQuality(
            values,
            "mobile_stream_quality_mode",
            "mobile_stream_codec",
            "mobile_stream_bitrate",
            defaults.mobileStreamingQuality,
        ),
        downloadQuality = legacyStreamQuality(
            values,
            "download_quality_mode",
            "download_codec",
            "download_bitrate",
            defaults.downloadQuality,
        ),
        downloadedTrackPlayback = values.enum("downloaded_track_playback", defaults.downloadedTrackPlayback),
        allowMobileDownloads = values.boolean("allow_mobile_downloads", defaults.allowMobileDownloads),
    ).normalized()
}

private fun legacyEqualizerSettings(
    values: NaviampCoreLegacySettingsValueStore,
    json: Json,
): EqualizerSettings {
    val defaults = EqualizerSettings()
    return EqualizerSettings(
        enabled = values.boolean("equalizer_enabled", defaults.enabled),
        preset = values.enum("equalizer_preset", EqualizerPreset.Flat),
        profileId = values.read("equalizer_profile_id"),
        savedProfiles = values.list("equalizer_profiles", EqualizerProfile.serializer(), json),
        bandsDb = EqualizerBandFrequencies.indices.map { index ->
            values.float("equalizer_band_$index", defaults.bandsDb.getOrNull(index) ?: 0f)
        },
    ).normalized()
}

private fun legacyStreamQuality(
    values: NaviampCoreLegacySettingsValueStore,
    modeKey: String,
    codecKey: String,
    bitrateKey: String,
    defaults: StreamQualityPreference,
): StreamQualityPreference = StreamQualityPreference(
    mode = values.enum(modeKey, defaults.mode),
    codec = values.enum(codecKey, defaults.codec),
    bitrateKbps = values.int(bitrateKey, defaults.bitrateKbps),
).normalized()

private fun legacyCacheSettings(values: NaviampCoreLegacySettingsValueStore): CacheSettings {
    val defaults = CacheSettings()
    return CacheSettings(
        audioCachingEnabled = values.boolean("audio_caching_enabled", defaults.audioCachingEnabled),
        offlineModeEnabled = values.boolean("offline_mode_enabled", defaults.offlineModeEnabled),
        audioPrefetchDepth = values.int("audio_prefetch_depth", defaults.audioPrefetchDepth),
        waveformsEnabled = values.boolean("waveforms_enabled", defaults.waveformsEnabled),
        waveformBucketCount = values.int("waveform_bucket_count", defaults.waveformBucketCount),
        maxAudioCacheBytes = values.long("max_audio_cache_bytes", defaults.maxAudioCacheBytes),
        maxDownloadBytes = values.long("max_download_bytes", defaults.maxDownloadBytes),
        customAudioCacheDirectory = values.read("custom_audio_cache_directory"),
        customDownloadDirectory = values.read("custom_download_directory"),
    ).normalized()
}

private inline fun <reified T : Enum<T>> NaviampCoreLegacySettingsValueStore.enum(
    key: String,
    default: T,
): T = read(key)?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

private fun NaviampCoreLegacySettingsValueStore.boolean(key: String, default: Boolean): Boolean =
    read(key)?.toBooleanStrictOrNull() ?: default

private fun NaviampCoreLegacySettingsValueStore.int(key: String, default: Int): Int =
    read(key)?.toIntOrNull() ?: default

private fun NaviampCoreLegacySettingsValueStore.long(key: String, default: Long): Long =
    read(key)?.toLongOrNull() ?: default

private fun NaviampCoreLegacySettingsValueStore.float(key: String, default: Float): Float =
    read(key)?.toFloatOrNull() ?: default

private fun <T> NaviampCoreLegacySettingsValueStore.list(
    key: String,
    serializer: KSerializer<T>,
    json: Json,
    default: List<T> = emptyList(),
): List<T> = read(key)
    ?.let { encoded ->
        runCatching { json.decodeFromString(ListSerializer(serializer), encoded) }.getOrNull()
    }
    ?: default

private val LegacyInterfaceKeys = listOf(
    "interface_language", "check_for_updates", "start_playing_on_launch", "show_desktop_tooltips",
    "app_background_style", "aurora_tone", "album_blur_radius_dp", "single_color_hex",
    "album_collection_layout", "album_sort_order", "group_albums_by_release_type",
    "now_playing_show_album_year", "now_playing_show_audio_info", "now_playing_show_volume_bar",
    "now_playing_scroll_track_title", "now_playing_scroll_artist_name", "now_playing_scroll_album_name",
    "swipe_library_right", "swipe_library_left", "swipe_queue_right", "swipe_queue_left",
    "swipe_related_right", "swipe_related_left", "swipe_playlist_edit_right", "swipe_playlist_edit_left",
    "swipe_downloads_right", "swipe_downloads_left",
)

private val LegacyPlaybackKeys = listOf(
    "replay_gain_mode", "sample_rate_converter", "sample_rate_matching", "replay_gain_inspector_enabled",
    "gapless_enabled", "crossfade_duration_seconds", "equalizer_enabled", "equalizer_preset",
    "equalizer_profile_id", "equalizer_profiles", "debug_logging_enabled", "lrclib_lyrics_enabled",
    "prefer_synced_lyrics", "lyrics_search_order", "sonic_similarity_enabled", "sonic_autoplay_enabled",
    "previous_button_behavior", "up_next_selection_behavior", "remove_played_tracks_from_queue",
    "radio_familiarity", "radio_artist_spread", "radio_same_decade_only", "radio_djs",
    "active_radio_dj_id", "wifi_stream_quality_mode", "wifi_stream_codec", "wifi_stream_bitrate",
    "mobile_stream_quality_mode", "mobile_stream_codec", "mobile_stream_bitrate",
    "download_quality_mode", "download_codec", "download_bitrate", "downloaded_track_playback",
    "allow_mobile_downloads",
) + EqualizerBandFrequencies.indices.map { "equalizer_band_$it" }

private val LegacyCacheKeys = listOf(
    "audio_caching_enabled", "offline_mode_enabled", "audio_prefetch_depth", "waveforms_enabled",
    "waveform_bucket_count", "max_audio_cache_bytes", "max_download_bytes", "custom_audio_cache_directory",
    "custom_download_directory",
)

private val LegacySyncRuntimeKeys = listOf(
    "settings_sync_auto_export_enabled",
    "settings_sync_last_local_update_epoch_millis",
    "settings_sync_last_applied_update_epoch_millis",
)
