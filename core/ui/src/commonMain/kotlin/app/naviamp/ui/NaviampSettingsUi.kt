package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.naviamp.domain.playback.EqualizerBandFrequencies
import app.naviamp.domain.playback.EqualizerPreset
import app.naviamp.domain.playback.MaxEqualizerGainDb
import app.naviamp.domain.playback.MinEqualizerGainDb
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.AudioOutputDevice
import app.naviamp.domain.radio.MaxArtistRunLength
import app.naviamp.domain.radio.MinArtistRunLength
import app.naviamp.domain.radio.RadioArtistSpread
import app.naviamp.domain.radio.RadioDjPreset
import app.naviamp.domain.radio.RadioFamiliarity
import app.naviamp.domain.radio.RadioArtistRunMode
import app.naviamp.domain.radio.RadioTuningSettings
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.DefaultWaveformBucketCount
import app.naviamp.domain.settings.InterfaceLanguage
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.LyricsSourcePreference
import app.naviamp.domain.settings.MaxReplayGainPreampDb
import app.naviamp.domain.settings.MaxWaveformBucketCount
import app.naviamp.domain.settings.MinReplayGainPreampDb
import app.naviamp.domain.settings.MinWaveformBucketCount
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.AudioOutputDeviceMode
import app.naviamp.domain.settings.AudioOutputDevicePreference
import app.naviamp.domain.settings.PreviousButtonBehavior
import app.naviamp.domain.settings.StreamBitrateKbpsOptions
import app.naviamp.domain.settings.StreamQualityMode
import app.naviamp.domain.settings.StreamQualityPreference
import app.naviamp.domain.settings.StreamingCodec
import app.naviamp.domain.settings.UpNextSelectionBehavior
import app.naviamp.domain.settings.normalizedLyricsSearchOrder
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.*
import app.naviamp.ui.generated.resources.naviamp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

data class NaviampDiagnosticsUi(
    val sections: List<NaviampDiagnosticsSectionUi> = emptyList(),
)

data class NaviampDiagnosticsSectionUi(
    val title: String,
    val rows: List<Pair<String, String>>,
)

data class NaviampAboutUi(
    val version: String = "Unknown",
    val buildNumber: String = "Unknown",
    val libraries: List<String> = DefaultNaviampLibraries,
    val changelog: List<String> = DefaultNaviampChangelog,
)

data class NaviampSavedConnectionUi(
    val id: String,
    val displayName: String,
    val serverUrl: String,
    val username: String,
    val selectedLibrarySummary: String = "",
    val current: Boolean = false,
)

enum class NaviampSettingsCategory(
    val defaultLabel: String,
    val defaultSubtitle: String,
    val icon: ImageVector,
) {
    Source("Source", "Servers and libraries", NaviampIcons.Library),
    Language("Language", "Interface language", NaviampIcons.Globe),
    Experience("Experience", "Appearance and behavior", NaviampIcons.Experience),
    Playback("Playback", "Make your ears happy", NaviampTransportIcons.Play),
    Downloads("Downloads", "Media on the go", NaviampIcons.Downloads),
    AudioCache("Audio Cache", "Prefetch and playback cache", NaviampIcons.Cache),
    Debugging("Debugging", "Diagnostics and local data", NaviampIcons.Bug),
    About("About", "Version, libraries, changelog", NaviampIcons.AppMark),
}

@Composable
fun NaviampSharedSettingsContent(
    colors: NaviampColors,
    interfaceSettings: InterfaceSettings = InterfaceSettings(),
    playbackSettings: PlaybackSettings,
    cacheSettings: CacheSettings = CacheSettings(),
    diagnostics: NaviampDiagnosticsUi = NaviampDiagnosticsUi(),
    about: NaviampAboutUi = NaviampAboutUi(),
    savedConnections: List<NaviampSavedConnectionUi> = emptyList(),
    isConnectionFormOpen: Boolean = false,
    isConnecting: Boolean = false,
    connectionStatus: String? = null,
    settingsSyncStatus: String? = null,
    availableMusicFolders: List<ConnectionFormMusicFolder> = emptyList(),
    musicFoldersStatus: String? = null,
    connectionForm: ConnectionFormState = ConnectionFormState(),
    hasSavedConnection: Boolean = false,
    onEditConnection: () -> Unit,
    onNewConnection: () -> Unit = onEditConnection,
    onEditSavedConnection: (NaviampSavedConnectionUi) -> Unit = { onEditConnection() },
    onConnectSavedConnection: (NaviampSavedConnectionUi) -> Unit = {},
    onDeleteSavedConnection: (NaviampSavedConnectionUi) -> Unit = {},
    onImportSettingsSyncFile: (() -> Unit)? = null,
    onChooseSettingsSyncFolder: (() -> Unit)? = null,
    onImportSettingsSyncFolder: (() -> Unit)? = null,
    onExportSettingsSyncFolder: (() -> Unit)? = null,
    settingsSyncAutoExportEnabled: Boolean = false,
    onSettingsSyncAutoExportChanged: ((Boolean) -> Unit)? = null,
    onConnectionFormChanged: (ConnectionFormState) -> Unit = {},
    onConnect: () -> Unit = {},
    onCancelConnectionForm: () -> Unit = {},
    onInterfaceSettingsChanged: (InterfaceSettings) -> Unit = {},
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit = onPlaybackSettingsChanged,
    onCacheSettingsChanged: (CacheSettings) -> Unit = {},
    onClearCache: (() -> Unit)? = null,
    onClearLibrary: (() -> Unit)? = null,
    onResetDatabase: (() -> Unit)? = null,
    supportsReplayGain: Boolean = false,
    supportsGapless: Boolean = true,
    supportsCrossfade: Boolean = false,
    supportsEqualizer: Boolean = false,
    supportsAudioOutputDeviceSelection: Boolean = false,
    audioOutputDevices: List<AudioOutputDevice> = emptyList(),
    supportsSonicSimilarity: Boolean = false,
    downloadBytes: Long = 0L,
    showQueueBehavior: Boolean = true,
    showDebugLogging: Boolean = true,
    showMobileNetworkQuality: Boolean = false,
) {
    var selectedCategory by remember { mutableStateOf<NaviampSettingsCategory?>(null) }
    val languagePack = remember(interfaceSettings.language) {
        naviampLanguagePack(interfaceSettings.language)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        selectedCategory?.let { category ->
            SettingsDetailHeader(
                category = category,
                languagePack = languagePack,
                colors = colors,
                onBack = { selectedCategory = null },
            )
            when (category) {
                NaviampSettingsCategory.Source -> {
                    NaviampConnectionsSettingsSection(
                        colors = colors,
                        savedConnections = savedConnections,
                        isConnectionFormOpen = isConnectionFormOpen,
                        isConnecting = isConnecting,
                        connectionStatus = connectionStatus,
                        settingsSyncStatus = settingsSyncStatus,
                        availableMusicFolders = availableMusicFolders,
                        musicFoldersStatus = musicFoldersStatus,
                        connectionForm = connectionForm,
                        hasSavedConnection = hasSavedConnection,
                        onNewConnection = onNewConnection,
                        onEditConnection = onEditSavedConnection,
                        onConnectConnection = onConnectSavedConnection,
                        onDeleteConnection = onDeleteSavedConnection,
                        onImportSettingsSyncFile = onImportSettingsSyncFile,
                        onChooseSettingsSyncFolder = onChooseSettingsSyncFolder,
                        onImportSettingsSyncFolder = onImportSettingsSyncFolder,
                        onExportSettingsSyncFolder = onExportSettingsSyncFolder,
                        settingsSyncAutoExportEnabled = settingsSyncAutoExportEnabled,
                        onSettingsSyncAutoExportChanged = onSettingsSyncAutoExportChanged,
                        onConnectionFormChanged = onConnectionFormChanged,
                        onConnect = onConnect,
                        onCancelConnectionForm = onCancelConnectionForm,
                    )
                }
                NaviampSettingsCategory.Language -> NaviampLanguageSettingsSection(
                    colors = colors,
                    interfaceSettings = interfaceSettings,
                    languagePack = languagePack,
                    onInterfaceSettingsChanged = onInterfaceSettingsChanged,
                )
                NaviampSettingsCategory.Experience -> NaviampExperienceSettingsSection(
                    colors = colors,
                    playbackSettings = playbackSettings,
                    cacheSettings = cacheSettings,
                    showQueueBehavior = showQueueBehavior,
                    showLrclibLyrics = true,
                    supportsSonicSimilarity = supportsSonicSimilarity,
                    onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                    onCacheSettingsChanged = onCacheSettingsChanged,
                )
                NaviampSettingsCategory.Playback -> NaviampPlaybackSettingsSection(
                    colors = colors,
                    playbackSettings = playbackSettings,
                    supportsReplayGain = supportsReplayGain,
                    supportsGapless = supportsGapless,
                    supportsCrossfade = supportsCrossfade,
                    supportsEqualizer = supportsEqualizer,
                    supportsAudioOutputDeviceSelection = supportsAudioOutputDeviceSelection,
                    audioOutputDevices = audioOutputDevices,
                    supportsSonicSimilarity = supportsSonicSimilarity,
                    showReplayGain = true,
                    showCrossfade = true,
                    showMobileNetworkQuality = showMobileNetworkQuality,
                    showLrclibLyrics = true,
                    downloadBytes = downloadBytes,
                    onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                    onPlaybackSettingsChangedAndRedownload = onPlaybackSettingsChangedAndRedownload,
                )
                NaviampSettingsCategory.Downloads -> NaviampDownloadsSettingsSection(
                    colors = colors,
                    playbackSettings = playbackSettings,
                    cacheSettings = cacheSettings,
                    diagnostics = diagnostics,
                    showMobileNetworkQuality = showMobileNetworkQuality,
                    downloadBytes = downloadBytes,
                    onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                    onPlaybackSettingsChangedAndRedownload = onPlaybackSettingsChangedAndRedownload,
                    onCacheSettingsChanged = onCacheSettingsChanged,
                )
                NaviampSettingsCategory.AudioCache -> NaviampAudioCacheSettingsSection(
                    colors = colors,
                    cacheSettings = cacheSettings,
                    diagnostics = diagnostics,
                    onCacheSettingsChanged = onCacheSettingsChanged,
                )
                NaviampSettingsCategory.Debugging -> {
                    if (showDebugLogging) {
                        NaviampDebugPlaybackSettingsSection(
                            colors = colors,
                            playbackSettings = playbackSettings,
                            onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                        )
                    }
                    NaviampDiagnosticsSettingsSection(
                        colors = colors,
                        title = stringResource(Res.string.settings_debugging_title),
                        diagnostics = diagnostics,
                        emptyText = stringResource(Res.string.settings_debugging_empty),
                    )
                    SharedLocalDataActions(
                        colors = colors,
                        onClearCache = onClearCache,
                        onClearLibrary = onClearLibrary,
                        onResetDatabase = onResetDatabase,
                    )
                }
                NaviampSettingsCategory.About -> NaviampAboutSettingsSection(
                    colors = colors,
                    about = about,
                )
            }
        } ?: run {
            Text(languagePack.settingsTitle(), color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            val currentConnection = savedConnections.firstOrNull { it.current }
            NaviampSettingsCategory.entries.forEach { category ->
                SettingsCategoryRow(
                    category = category,
                    languagePack = languagePack,
                    colors = colors,
                    enabled = true,
                    subtitle = when (category) {
                        NaviampSettingsCategory.Source -> currentConnection?.displayName ?: connectionStatus ?: languagePack.categorySubtitle(category)
                        NaviampSettingsCategory.Language -> languagePack.languageTitle(interfaceSettings.language)
                        else -> languagePack.categorySubtitle(category)
                    },
                    onClick = { selectedCategory = category },
                )
            }
        }
    }
}

@Composable
private fun SettingsPlaceholderSection(
    colors: NaviampColors,
    title: String,
    body: String,
    value: String? = null,
) {
    SettingsSectionTitle(title, colors)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(body, color = colors.secondaryText, fontSize = 12.sp)
        value?.let {
            Text(it, color = colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun NaviampExperienceSettingsSection(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    cacheSettings: CacheSettings,
    showQueueBehavior: Boolean,
    showLrclibLyrics: Boolean,
    supportsSonicSimilarity: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onCacheSettingsChanged: (CacheSettings) -> Unit,
) {
    var selectedSection by remember { mutableStateOf<ExperienceSettingsPage?>(null) }

    selectedSection?.let { section ->
        SettingsSubsectionHeader(section.title(), section.subtitle(), colors) { selectedSection = null }
        when (section) {
            ExperienceSettingsPage.Player -> QueueRulesSettings(
                colors = colors,
                playbackSettings = playbackSettings,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
            ExperienceSettingsPage.RelatedTracks -> RelatedTracksSettings(
                colors = colors,
                playbackSettings = playbackSettings,
                supportsSonicSimilarity = supportsSonicSimilarity,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
            ExperienceSettingsPage.Lyrics -> LyricsSettings(
                colors = colors,
                playbackSettings = playbackSettings,
                showLrclibLyrics = showLrclibLyrics,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
            ExperienceSettingsPage.Waveforms -> WaveformSettings(
                colors = colors,
                cacheSettings = cacheSettings,
                onCacheSettingsChanged = onCacheSettingsChanged,
            )
        }
    } ?: run {
        if (showQueueBehavior) {
            SettingsRow(
                title = ExperienceSettingsPage.Player.title(),
                subtitle = ExperienceSettingsPage.Player.subtitle(),
                colors = colors,
            ) {
                selectedSection = ExperienceSettingsPage.Player
            }
        }
        if (showLrclibLyrics) {
            SettingsRow(
                colors = colors,
                title = stringResource(Res.string.settings_lyrics_title),
                subtitle = stringResource(Res.string.settings_lyrics_subtitle),
                value = playbackSettings.lyricsSummary(),
            ) {
                selectedSection = ExperienceSettingsPage.Lyrics
            }
        }
        if (supportsSonicSimilarity) {
            SettingsRow(
                title = ExperienceSettingsPage.RelatedTracks.title(),
                subtitle = ExperienceSettingsPage.RelatedTracks.subtitle(),
                colors = colors,
                value = playbackSettings.relatedTracksSummary(),
            ) {
                selectedSection = ExperienceSettingsPage.RelatedTracks
            }
        }
        SettingsRow(
            title = ExperienceSettingsPage.Waveforms.title(),
            subtitle = ExperienceSettingsPage.Waveforms.subtitle(),
            colors = colors,
            value = if (cacheSettings.normalized().waveformsEnabled) {
                stringResource(Res.string.common_enabled)
            } else {
                stringResource(Res.string.common_off)
            },
        ) {
            selectedSection = ExperienceSettingsPage.Waveforms
        }
        if (!showQueueBehavior && !showLrclibLyrics && !supportsSonicSimilarity) {
            Text(stringResource(Res.string.settings_experience_empty), color = colors.secondaryText, fontSize = 12.sp)
        }
    }
}

private enum class ExperienceSettingsPage(
    val title: String,
    val subtitle: String,
) {
    Player("Player", "Queue, Back To, and Up Next behavior"),
    RelatedTracks("Related Tracks", "Sonic similarity and autoplay"),
    Lyrics("Lyrics", "Download and source order"),
    Waveforms("Waveforms", "Track waveforms and detail"),
}

@Composable
private fun ExperienceSettingsPage.title(): String =
    when (this) {
        ExperienceSettingsPage.Player -> stringResource(Res.string.settings_experience_player_title)
        ExperienceSettingsPage.RelatedTracks -> stringResource(Res.string.settings_experience_related_tracks_title)
        ExperienceSettingsPage.Lyrics -> stringResource(Res.string.settings_lyrics_title)
        ExperienceSettingsPage.Waveforms -> stringResource(Res.string.settings_experience_waveforms_title)
    }

@Composable
private fun ExperienceSettingsPage.subtitle(): String =
    when (this) {
        ExperienceSettingsPage.Player -> stringResource(Res.string.settings_experience_player_subtitle)
        ExperienceSettingsPage.RelatedTracks -> stringResource(Res.string.settings_experience_related_tracks_subtitle)
        ExperienceSettingsPage.Lyrics -> stringResource(Res.string.settings_lyrics_subtitle)
        ExperienceSettingsPage.Waveforms -> stringResource(Res.string.settings_experience_waveforms_subtitle)
    }

@Composable
private fun PlaybackSettings.lyricsSummary(): String =
    when {
        preferSyncedLyrics && lrclibLyricsEnabled -> stringResource(Res.string.settings_lyrics_summary_synced_download)
        preferSyncedLyrics -> stringResource(Res.string.settings_lyrics_summary_synced)
        lrclibLyricsEnabled -> stringResource(Res.string.settings_lyrics_summary_download)
        else -> stringResource(Res.string.common_off)
    }

@Composable
private fun PlaybackSettings.relatedTracksSummary(): String? =
    when {
        sonicSimilarityEnabled && sonicAutoplayEnabled -> stringResource(Res.string.settings_related_autoplay_summary)
        sonicSimilarityEnabled -> stringResource(Res.string.settings_related_summary)
        sonicAutoplayEnabled -> stringResource(Res.string.settings_autoplay_summary)
        else -> null
    }

@Composable
private fun SettingsDetailHeader(
    category: NaviampSettingsCategory,
    languagePack: NaviampLanguagePack,
    colors: NaviampColors,
    onBack: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
            Icon(NaviampIcons.Back, contentDescription = stringResource(Res.string.common_back), tint = colors.primaryText)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(languagePack.categoryLabel(category), color = colors.primaryText, fontSize = SettingsCategoryTitleSize, fontWeight = FontWeight.Bold)
            Text(languagePack.categorySubtitle(category), color = colors.secondaryText, fontSize = SettingsDetailSubtitleSize)
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    category: NaviampSettingsCategory,
    languagePack: NaviampLanguagePack,
    colors: NaviampColors,
    enabled: Boolean,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = if (enabled) colors.secondaryText else colors.mutedText,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(languagePack.categoryLabel(category), color = if (enabled) colors.primaryText else colors.mutedText, fontSize = 15.sp)
            Text(subtitle, color = colors.mutedText, fontSize = 12.sp)
        }
        Icon(NaviampIcons.ChevronRight, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun NaviampLanguageSettingsSection(
    colors: NaviampColors,
    interfaceSettings: InterfaceSettings,
    languagePack: NaviampLanguagePack,
    onInterfaceSettingsChanged: (InterfaceSettings) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding),
    ) {
        InterfaceLanguage.entries.forEach { language ->
            SelectableTextOption(
                title = languagePack.languageTitle(language),
                subtitle = languagePack.languageSubtitle(language),
                selected = interfaceSettings.language == language,
                colors = colors,
                enabled = true,
                selectedLabel = languagePack.selectedLabel(),
            ) {
                onInterfaceSettingsChanged(interfaceSettings.copy(language = language).normalized())
            }
        }
    }
}

@Composable
fun NaviampAboutSettingsSection(
    colors: NaviampColors,
    about: NaviampAboutUi,
) {
    var page by remember { mutableStateOf<AboutSettingsPage?>(null) }

    page?.let { selected ->
        SettingsSubsectionHeader(selected.title(), selected.subtitle(), colors) { page = null }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding),
        ) {
            when (selected) {
                AboutSettingsPage.Thanks -> {
                    SettingsSectionTitle(stringResource(Res.string.settings_about_libraries_title), colors)
                    about.libraries.forEach { library ->
                        Text(library, color = colors.secondaryText, fontSize = SettingsDetailRowSubtitleSize)
                    }
                    SettingsSectionTitle(stringResource(Res.string.settings_about_fonts_title), colors)
                    Text("Nunito Sans", color = colors.secondaryText, fontSize = SettingsDetailRowSubtitleSize)
                    SettingsSectionTitle(stringResource(Res.string.settings_about_audio_title), colors)
                    Text("BASS audio library", color = colors.secondaryText, fontSize = SettingsDetailRowSubtitleSize)
                }
                AboutSettingsPage.Licenses -> {
                    SettingsSectionTitle("Naviamp", colors)
                    Text(stringResource(Res.string.settings_about_license_title), color = colors.primaryText, fontSize = SettingsDetailRowTitleSize, fontWeight = FontWeight.SemiBold)
                    Text(
                        ApacheLicenseText,
                        color = colors.secondaryText,
                        fontSize = SettingsDetailRowSubtitleSize,
                    )
                }
                AboutSettingsPage.Changelog -> {
                    SettingsSectionTitle(stringResource(Res.string.settings_about_latest_changes_title), colors)
                    if (about.changelog.isEmpty()) {
                        Text(stringResource(Res.string.settings_about_empty_changelog), color = colors.secondaryText, fontSize = SettingsDetailRowSubtitleSize)
                    } else {
                        about.changelog.forEach { entry ->
                            Text(entry, color = colors.secondaryText, fontSize = SettingsDetailRowSubtitleSize)
                        }
                    }
                }
            }
        }
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp),
    ) {
        Image(
            painter = painterResource(Res.drawable.naviamp),
            contentDescription = null,
            modifier = Modifier.size(78.dp),
        )
        Text("Naviamp", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.settings_about_production), color = colors.secondaryText, fontSize = 14.sp)
        Text(stringResource(Res.string.settings_about_version_build, about.version, about.buildNumber), color = colors.secondaryText, fontSize = 12.sp)
    }
    AboutSettingsPage.entries.forEach { aboutPage ->
        SettingsRow(
            title = aboutPage.title(),
            subtitle = aboutPage.description(),
            colors = colors,
        ) {
            page = aboutPage
        }
    }
}

private enum class AboutSettingsPage(
    val title: String,
    val subtitle: String,
    val rowSubtitle: String,
) {
    Thanks("Thanks", "Libraries, fonts, and components", "Some of the people and projects that made this app possible."),
    Licenses("Licenses", "App license", "Open source license information."),
    Changelog("Changelog", "Latest changes", "What changed in this version."),
}

@Composable
private fun AboutSettingsPage.title(): String =
    when (this) {
        AboutSettingsPage.Thanks -> stringResource(Res.string.settings_about_thanks_title)
        AboutSettingsPage.Licenses -> stringResource(Res.string.settings_about_licenses_title)
        AboutSettingsPage.Changelog -> stringResource(Res.string.settings_about_changelog_title)
    }

@Composable
private fun AboutSettingsPage.subtitle(): String =
    when (this) {
        AboutSettingsPage.Thanks -> stringResource(Res.string.settings_about_thanks_subtitle)
        AboutSettingsPage.Licenses -> stringResource(Res.string.settings_about_licenses_subtitle)
        AboutSettingsPage.Changelog -> stringResource(Res.string.settings_about_changelog_subtitle)
    }

@Composable
private fun AboutSettingsPage.description(): String =
    when (this) {
        AboutSettingsPage.Thanks -> stringResource(Res.string.settings_about_thanks_description)
        AboutSettingsPage.Licenses -> stringResource(Res.string.settings_about_licenses_description)
        AboutSettingsPage.Changelog -> stringResource(Res.string.settings_about_changelog_description)
    }

private val ApacheLicenseText = """
Apache License
Version 2.0, January 2004
http://www.apache.org/licenses/

TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

1. Definitions.

"License" shall mean the terms and conditions for use, reproduction, and
distribution as defined by Sections 1 through 9 of this document.

"Licensor" shall mean the copyright owner or entity authorized by the copyright
owner that is granting the License.

"Legal Entity" shall mean the union of the acting entity and all other entities
that control, are controlled by, or are under common control with that entity.
For the purposes of this definition, "control" means (i) the power, direct or
indirect, to cause the direction or management of such entity, whether by
contract or otherwise, or (ii) ownership of fifty percent (50%) or more of the
outstanding shares, or (iii) beneficial ownership of such entity.

"You" (or "Your") shall mean an individual or Legal Entity exercising
permissions granted by this License.

"Source" form shall mean the preferred form for making modifications, including
but not limited to software source code, documentation source, and configuration
files.

"Object" form shall mean any form resulting from mechanical transformation or
translation of a Source form, including but not limited to compiled object code,
generated documentation, and conversions to other media types.

"Work" shall mean the work of authorship, whether in Source or Object form,
made available under the License, as indicated by a copyright notice that is
included in or attached to the work.

"Derivative Works" shall mean any work, whether in Source or Object form, that
is based on (or derived from) the Work and for which the editorial revisions,
annotations, elaborations, or other modifications represent, as a whole, an
original work of authorship. For the purposes of this License, Derivative Works
shall not include works that remain separable from, or merely link (or bind by
name) to the interfaces of, the Work and Derivative Works thereof.

"Contribution" shall mean any work of authorship, including the original version
of the Work and any modifications or additions to that Work or Derivative Works
thereof, that is intentionally submitted to Licensor for inclusion in the Work
by the copyright owner or by an individual or Legal Entity authorized to submit
on behalf of the copyright owner.

"Contributor" shall mean Licensor and any individual or Legal Entity on behalf
of whom a Contribution has been received by Licensor and subsequently
incorporated within the Work.

2. Grant of Copyright License.

Subject to the terms and conditions of this License, each Contributor hereby
grants to You a perpetual, worldwide, non-exclusive, no-charge, royalty-free,
irrevocable copyright license to reproduce, prepare Derivative Works of,
publicly display, publicly perform, sublicense, and distribute the Work and such
Derivative Works in Source or Object form.

3. Grant of Patent License.

Subject to the terms and conditions of this License, each Contributor hereby
grants to You a perpetual, worldwide, non-exclusive, no-charge, royalty-free,
irrevocable patent license to make, have made, use, offer to sell, sell, import,
and otherwise transfer the Work, where such license applies only to those patent
claims licensable by such Contributor that are necessarily infringed by their
Contribution(s) alone or by combination of their Contribution(s) with the Work
to which such Contribution(s) was submitted.

If You institute patent litigation against any entity alleging that the Work or
a Contribution incorporated within the Work constitutes direct or contributory
patent infringement, then any patent licenses granted to You under this License
for that Work shall terminate as of the date such litigation is filed.

4. Redistribution.

You may reproduce and distribute copies of the Work or Derivative Works thereof
in any medium, with or without modifications, and in Source or Object form,
provided that You meet the following conditions:

(a) You must give any other recipients of the Work or Derivative Works a copy
of this License; and

(b) You must cause any modified files to carry prominent notices stating that
You changed the files; and

(c) You must retain, in the Source form of any Derivative Works that You
distribute, all copyright, patent, trademark, and attribution notices from the
Source form of the Work, excluding those notices that do not pertain to any part
of the Derivative Works; and

(d) If the Work includes a "NOTICE" text file as part of its distribution, then
any Derivative Works that You distribute must include a readable copy of the
attribution notices contained within such NOTICE file, excluding those notices
that do not pertain to any part of the Derivative Works, in at least one of the
following places: within a NOTICE text file distributed as part of the
Derivative Works; within the Source form or documentation, if provided along
with the Derivative Works; or, within a display generated by the Derivative
Works, if and wherever such third-party notices normally appear.

The contents of the NOTICE file are for informational purposes only and do not
modify the License. You may add Your own attribution notices within Derivative
Works that You distribute, alongside or as an addendum to the NOTICE text from
the Work, provided that such additional attribution notices cannot be construed
as modifying the License.

You may add Your own copyright statement to Your modifications and may provide
additional or different license terms and conditions for use, reproduction, or
distribution of Your modifications, or for any such Derivative Works as a whole,
provided Your use, reproduction, and distribution of the Work otherwise complies
with the conditions stated in this License.

5. Submission of Contributions.

Unless You explicitly state otherwise, any Contribution intentionally submitted
for inclusion in the Work by You to the Licensor shall be under the terms and
conditions of this License, without any additional terms or conditions.

6. Trademarks.

This License does not grant permission to use the trade names, trademarks,
service marks, or product names of the Licensor, except as required for
reasonable and customary use in describing the origin of the Work and
reproducing the content of the NOTICE file.

7. Disclaimer of Warranty.

Unless required by applicable law or agreed to in writing, Licensor provides the
Work on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
express or implied, including, without limitation, any warranties or conditions
of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR
PURPOSE.

8. Limitation of Liability.

In no event and under no legal theory, whether in tort, contract, or otherwise,
unless required by applicable law or agreed to in writing, shall any Contributor
be liable to You for damages, including any direct, indirect, special,
incidental, or consequential damages of any character arising as a result of
this License or out of the use or inability to use the Work, even if such
Contributor has been advised of the possibility of such damages.

9. Accepting Warranty or Additional Liability.

While redistributing the Work or Derivative Works thereof, You may choose to
offer, and charge a fee for, acceptance of support, warranty, indemnity, or
other liability obligations and/or rights consistent with this License. However,
in accepting such obligations, You may act only on Your own behalf and on Your
sole responsibility, not on behalf of any other Contributor, and only if You
agree to indemnify, defend, and hold each Contributor harmless for any liability
incurred by, or claims asserted against, such Contributor by reason of your
accepting any such warranty or additional liability.

END OF TERMS AND CONDITIONS
""".trimIndent()

@Composable
private fun NaviampConnectionsSettingsSection(
    colors: NaviampColors,
    savedConnections: List<NaviampSavedConnectionUi>,
    isConnectionFormOpen: Boolean,
    isConnecting: Boolean,
    connectionStatus: String?,
    settingsSyncStatus: String?,
    availableMusicFolders: List<ConnectionFormMusicFolder>,
    musicFoldersStatus: String?,
    connectionForm: ConnectionFormState,
    hasSavedConnection: Boolean,
    onNewConnection: () -> Unit,
    onEditConnection: (NaviampSavedConnectionUi) -> Unit,
    onConnectConnection: (NaviampSavedConnectionUi) -> Unit,
    onDeleteConnection: (NaviampSavedConnectionUi) -> Unit,
    onImportSettingsSyncFile: (() -> Unit)?,
    onChooseSettingsSyncFolder: (() -> Unit)?,
    onImportSettingsSyncFolder: (() -> Unit)?,
    onExportSettingsSyncFolder: (() -> Unit)?,
    settingsSyncAutoExportEnabled: Boolean,
    onSettingsSyncAutoExportChanged: ((Boolean) -> Unit)?,
    onConnectionFormChanged: (ConnectionFormState) -> Unit,
    onConnect: () -> Unit,
    onCancelConnectionForm: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<NaviampSavedConnectionUi?>(null) }
    var selectedPage by remember { mutableStateOf<SourceSettingsPage?>(null) }
    val hasSettingsSync =
        onImportSettingsSyncFile != null ||
            onChooseSettingsSyncFolder != null ||
            onImportSettingsSyncFolder != null ||
            onExportSettingsSyncFolder != null

    if (isConnectionFormOpen) {
        SettingsSubsectionHeader(
            title = if (hasSavedConnection) {
                stringResource(Res.string.settings_source_edit_connection_title)
            } else {
                stringResource(Res.string.settings_source_new_connection_title)
            },
            subtitle = if (hasSavedConnection) {
                stringResource(Res.string.settings_source_edit_connection_subtitle)
            } else {
                stringResource(Res.string.settings_source_new_connection_subtitle)
            },
            colors = colors,
            onBack = onCancelConnectionForm,
        )
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding)) {
            NaviampConnectionForm(
                form = connectionForm,
                colors = colors,
                isReconnect = hasSavedConnection,
                isConnecting = isConnecting,
                connectionStatus = connectionStatus,
                settingsSyncStatus = settingsSyncStatus,
                availableMusicFolders = availableMusicFolders,
                musicFoldersStatus = musicFoldersStatus,
                onFormChanged = onConnectionFormChanged,
                onConnect = onConnect,
                onImportSettingsSyncFile = onImportSettingsSyncFile,
                onCancel = onCancelConnectionForm,
            )
        }
    } else when (selectedPage) {
        SourceSettingsPage.Connections -> {
            SettingsSubsectionHeader(
                stringResource(Res.string.settings_source_connections_title),
                stringResource(Res.string.settings_source_connections_subtitle),
                colors,
            ) {
                selectedPage = null
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding),
            ) {
                if (savedConnections.isEmpty()) {
                    Text(stringResource(Res.string.settings_source_no_connections), color = colors.secondaryText, fontSize = SettingsDetailRowSubtitleSize)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        savedConnections.forEach { connection ->
                            NaviampSavedConnectionRow(
                                colors = colors,
                                connection = connection,
                                enabled = !isConnecting,
                                onEdit = { onEditConnection(connection) },
                                onDelete = { pendingDelete = connection },
                                onConnect = { onConnectConnection(connection) },
                            )
                        }
                    }
                }
                PrimaryButton(stringResource(Res.string.settings_source_new_connection_button), colors, enabled = !isConnecting, onClick = onNewConnection)
                connectionStatus?.let {
                    Text(it, color = colors.secondaryText, fontSize = SettingsDetailRowSubtitleSize)
                }
            }
        }
        SourceSettingsPage.SettingsSync -> {
            SettingsSubsectionHeader(
                stringResource(Res.string.settings_sync_title),
                stringResource(Res.string.settings_sync_subtitle),
                colors,
            ) {
                selectedPage = null
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding),
            ) {
                Text(
                    stringResource(Res.string.settings_sync_description),
                    color = colors.secondaryText,
                    fontSize = SettingsDetailRowSubtitleSize,
                )
                onSettingsSyncAutoExportChanged?.let { onAutoExportChanged ->
                    InlineSettingsToggleRow(
                        colors = colors,
                        checked = settingsSyncAutoExportEnabled,
                        enabled = !isConnecting,
                        label = stringResource(Res.string.settings_sync_auto_sync),
                        onCheckedChange = onAutoExportChanged,
                    )
                }
                onChooseSettingsSyncFolder?.let { chooseFolder ->
                    PrimarySettingsButton(stringResource(Res.string.settings_sync_choose_folder), colors, enabled = !isConnecting, onClick = chooseFolder)
                }
                onImportSettingsSyncFolder?.let { importFolder ->
                    PrimarySettingsButton(stringResource(Res.string.settings_sync_now), colors, enabled = !isConnecting, onClick = importFolder)
                }
                onExportSettingsSyncFolder?.let { exportFolder ->
                    PrimarySettingsButton(stringResource(Res.string.settings_sync_export_local), colors, enabled = !isConnecting, onClick = exportFolder)
                }
                onImportSettingsSyncFile?.let { importSettings ->
                    PrimarySettingsButton(stringResource(Res.string.settings_sync_import_provider), colors, enabled = !isConnecting, onClick = importSettings)
                }
                settingsSyncStatus?.let {
                    Text(it, color = colors.secondaryText, fontSize = SettingsDetailRowSubtitleSize)
                }
            }
        }
        null -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding),
            ) {
                SourceSubpageRow(
                    title = stringResource(Res.string.settings_source_connections_title),
                    subtitle = stringResource(Res.string.settings_source_connections_subtitle_period),
                    value = savedConnections.firstOrNull { it.current }?.displayName
                        ?: stringResource(Res.string.settings_source_saved_count, savedConnections.size),
                    colors = colors,
                    enabled = !isConnecting,
                ) {
                    selectedPage = SourceSettingsPage.Connections
                }
                if (hasSettingsSync) {
                    SourceSubpageRow(
                        title = stringResource(Res.string.settings_sync_title),
                        subtitle = stringResource(Res.string.settings_sync_subtitle_period),
                        value = settingsSyncStatus,
                        colors = colors,
                        enabled = !isConnecting,
                    ) {
                        selectedPage = SourceSettingsPage.SettingsSync
                    }
                }
                connectionStatus?.let {
                    Text(it, color = colors.secondaryText, fontSize = SettingsDetailRowSubtitleSize)
                }
            }
        }
    }
        pendingDelete?.let { connection ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(Res.string.settings_source_delete_title)) },
                text = { Text(stringResource(Res.string.settings_source_delete_message, connection.displayName)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDelete = null
                            onDeleteConnection(connection)
                        },
                    ) {
                        Text(stringResource(Res.string.common_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(stringResource(Res.string.common_cancel))
                    }
                },
            )
        }
}

private enum class SourceSettingsPage {
    Connections,
    SettingsSync,
}

@Composable
private fun SourceSubpageRow(
    title: String,
    subtitle: String,
    value: String?,
    colors: NaviampColors,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = SettingsDetailRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) colors.primaryText else colors.mutedText, fontSize = SettingsDetailRowTitleSize)
            Text(subtitle, color = colors.mutedText, fontSize = SettingsDetailRowSubtitleSize)
        }
        value?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = if (enabled) colors.secondaryText else colors.mutedText,
                fontSize = SettingsDetailRowSubtitleSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(0.45f, fill = false)
                    .padding(start = 10.dp),
            )
        }
        Icon(
            NaviampIcons.ChevronRight,
            contentDescription = null,
            tint = if (enabled) colors.secondaryText else colors.mutedText,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun NaviampSavedConnectionRow(
    colors: NaviampColors,
    connection: NaviampSavedConnectionUi,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (connection.current) colors.accent else colors.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    connection.displayName,
                    color = colors.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (connection.current) {
                Text(stringResource(Res.string.common_current), color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            IconButton(enabled = enabled, onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(NaviampIcons.Edit, contentDescription = stringResource(Res.string.settings_source_edit_content_description), tint = if (enabled) colors.primaryText else colors.mutedText)
            }
            IconButton(enabled = enabled, onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(NaviampIcons.Trash, contentDescription = stringResource(Res.string.settings_source_delete_content_description), tint = if (enabled) colors.primaryText else colors.mutedText)
            }
        }
        Text(
            connection.username,
            color = colors.secondaryText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            connection.serverUrl,
            color = colors.secondaryText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (connection.selectedLibrarySummary.isNotBlank()) {
            Text(
                stringResource(Res.string.settings_source_libraries_prefix, connection.selectedLibrarySummary),
                color = colors.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(
            enabled = enabled,
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (connection.current) stringResource(Res.string.common_reconnect) else stringResource(Res.string.common_connect),
                color = if (enabled) colors.accent else colors.mutedText,
            )
        }
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
    colors: NaviampColors,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, color = colors.mutedText, fontSize = 12.sp, modifier = Modifier.weight(0.38f))
        Text(value, color = colors.primaryText, fontSize = 12.sp, modifier = Modifier.weight(0.62f))
    }
}

@Composable
fun NaviampDiagnosticsSettingsSection(
    colors: NaviampColors,
    title: String = "Diagnostics",
    diagnostics: NaviampDiagnosticsUi,
    emptyText: String = "No diagnostics yet.",
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding),
    ) {
        SettingsSectionTitle(title, colors)
        if (diagnostics.sections.isEmpty()) {
            Text(emptyText, color = colors.secondaryText, fontSize = SettingsDetailRowSubtitleSize)
        } else {
            diagnostics.sections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(section.title, color = colors.secondaryText, fontSize = SettingsDetailRowSubtitleSize, fontWeight = FontWeight.SemiBold)
                    section.rows.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                label,
                                color = colors.mutedText,
                                fontSize = SettingsDetailTinyTextSize,
                                modifier = Modifier.weight(0.42f),
                            )
                            Text(
                                value,
                                color = colors.primaryText,
                                fontSize = SettingsDetailTinyTextSize,
                                modifier = Modifier.weight(0.58f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val DefaultNaviampLibraries = listOf(
    "Kotlin Multiplatform",
    "Compose Multiplatform and Material 3",
    "kotlinx.coroutines",
    "kotlinx.serialization",
    "Ktor",
    "SQLDelight and SQLite",
    "BASS audio engine and add-ons",
    "Skiko and Skia",
    "Navidrome and OpenSubsonic APIs",
)

private val DefaultNaviampChangelog = listOf(
    "Added English and Spanish UI language resources that follow the system language.",
    "Improved smart playlist editing with searchable fields, fresher playback, newer Navidrome options, and token refresh when saving.",
    "Added Lyrics settings for Download Lyrics, Prefer Synced, and draggable Search Order.",
    "Fixed Android and desktop artist detail parity, including expanded bios and Android popular tracks.",
    "Refined Now Playing visuals with brighter album-art gradients and a vertical dots menu action.",
)

@Composable
private fun SharedLocalDataActions(
    colors: NaviampColors,
    onClearCache: (() -> Unit)?,
    onClearLibrary: (() -> Unit)?,
    onResetDatabase: (() -> Unit)?,
) {
    var confirmAction by remember { mutableStateOf<SharedLocalDataAction?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding),
    ) {
        SettingsSectionTitle(stringResource(Res.string.settings_local_data_title), colors)
        Text(
            stringResource(Res.string.settings_local_data_description),
            color = colors.secondaryText,
            fontSize = SettingsDetailRowSubtitleSize,
        )
        PrimarySettingsButton(stringResource(Res.string.settings_local_clear_cache), colors, enabled = onClearCache != null) {
            confirmAction = SharedLocalDataAction.ClearCache
        }
        PrimarySettingsButton(stringResource(Res.string.settings_local_clear_library), colors, enabled = onClearLibrary != null) {
            confirmAction = SharedLocalDataAction.ClearLibrary
        }
        PrimarySettingsButton(stringResource(Res.string.settings_local_reset_database), colors, enabled = onResetDatabase != null) {
            confirmAction = SharedLocalDataAction.ResetDatabase
        }
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(action.title()) },
            text = { Text(action.message()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAction = null
                        when (action) {
                            SharedLocalDataAction.ClearCache -> onClearCache?.invoke()
                            SharedLocalDataAction.ClearLibrary -> onClearLibrary?.invoke()
                            SharedLocalDataAction.ResetDatabase -> onResetDatabase?.invoke()
                        }
                    },
                ) {
                    Text(action.confirmLabel())
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SharedCacheSettingsSection(
    colors: NaviampColors,
    cacheSettings: CacheSettings,
    diagnostics: NaviampDiagnosticsUi,
    onCacheSettingsChanged: (CacheSettings) -> Unit,
) {
    val normalized = cacheSettings.normalized()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle(stringResource(Res.string.settings_audio_cache_title), colors)
        diagnosticRowValue(diagnostics, "Storage", "Audio cache")?.let { value ->
            Text(value, color = colors.secondaryText, fontSize = 12.sp)
        }
        SettingsCheckboxRow(
            colors = colors,
            checked = normalized.audioCachingEnabled,
            label = stringResource(Res.string.settings_audio_cache_enable),
            onCheckedChange = { enabled ->
                onCacheSettingsChanged(normalized.copy(audioCachingEnabled = enabled).normalized())
            },
        )
        DetentIntSettingsSlider(
            colors = colors,
            title = stringResource(Res.string.settings_audio_cache_prefetch_depth_title),
            value = normalized.audioPrefetchDepth,
            detents = PrefetchDepthOptions,
            enabled = normalized.audioCachingEnabled,
            valueLabel = { depth -> if (depth == 0) stringResource(Res.string.common_off) else stringResource(Res.string.settings_tracks_count, depth) },
            onValueChanged = { depth ->
                onCacheSettingsChanged(normalized.copy(audioPrefetchDepth = depth).normalized())
            },
        )
        DetentByteSettingsSlider(
            colors = colors,
            title = stringResource(Res.string.settings_audio_cache_budget_title),
            valueBytes = normalized.maxAudioCacheBytes,
            detents = AudioCacheBudgetOptions,
            onValueChanged = { bytes ->
                onCacheSettingsChanged(normalized.copy(maxAudioCacheBytes = bytes).normalized())
            },
        )

        SettingsSectionTitle(stringResource(Res.string.settings_waveforms_title), colors)
        diagnosticRowValue(diagnostics, "Storage", "Waveforms")?.let { value ->
            Text(value, color = colors.secondaryText, fontSize = 12.sp)
        }
        SettingsCheckboxRow(
            colors = colors,
            checked = normalized.waveformsEnabled,
            label = stringResource(Res.string.settings_waveforms_generate),
            onCheckedChange = { enabled ->
                onCacheSettingsChanged(normalized.copy(waveformsEnabled = enabled).normalized())
            },
        )
        DetentIntSettingsSlider(
            colors = colors,
            title = stringResource(Res.string.settings_waveforms_detail_title),
            value = normalized.waveformBucketCount,
            detents = WaveformBucketCountOptions,
            enabled = normalized.waveformsEnabled,
            valueLabel = { count -> stringResource(Res.string.settings_steps_count, count) },
            onValueChanged = { count ->
                onCacheSettingsChanged(normalized.copy(waveformBucketCount = count).normalized())
            },
        )

        SettingsSectionTitle(stringResource(Res.string.settings_downloads_title), colors)
        diagnosticRowValue(diagnostics, "Storage", "Downloads")?.let { value ->
            Text(value, color = colors.secondaryText, fontSize = 12.sp)
        }
        SettingsCheckboxRow(
            colors = colors,
            checked = normalized.offlineModeEnabled,
            label = stringResource(Res.string.settings_downloads_offline_mode),
            onCheckedChange = { enabled ->
                onCacheSettingsChanged(normalized.copy(offlineModeEnabled = enabled).normalized())
            },
        )
        Text(
            stringResource(Res.string.settings_downloads_offline_mode_subtitle),
            color = colors.secondaryText,
            fontSize = 12.sp,
        )
        DetentByteSettingsSlider(
            colors = colors,
            title = stringResource(Res.string.settings_downloads_storage_budget_title),
            valueBytes = normalized.maxDownloadBytes,
            detents = DownloadBudgetOptions,
            onValueChanged = { bytes ->
                onCacheSettingsChanged(normalized.copy(maxDownloadBytes = bytes).normalized())
            },
        )

        NaviampDiagnosticsSettingsSection(
            colors = colors,
            title = stringResource(Res.string.settings_storage_title),
            diagnostics = diagnostics,
            emptyText = stringResource(Res.string.settings_storage_empty),
        )
    }
}

@Composable
private fun DetentIntSettingsSlider(
    colors: NaviampColors,
    title: String,
    value: Int,
    detents: List<Int>,
    enabled: Boolean = true,
    valueLabel: @Composable (Int) -> String,
    onValueChanged: (Int) -> Unit,
) {
    val normalizedDetents = detents.distinct().sorted()
    val selectedIndex = normalizedDetents.indexOf(value).takeIf { it >= 0 } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = colors.secondaryText, fontSize = 12.sp)
            Text(valueLabel(normalizedDetents.getOrElse(selectedIndex) { value }), color = colors.primaryText, fontSize = 12.sp)
        }
        Slider(
            enabled = enabled && normalizedDetents.size > 1,
            value = selectedIndex.toFloat(),
            onValueChange = { raw ->
                val index = raw.toInt().coerceIn(normalizedDetents.indices)
                onValueChanged(normalizedDetents[index])
            },
            valueRange = 0f..(normalizedDetents.lastIndex.coerceAtLeast(0)).toFloat(),
            steps = (normalizedDetents.size - 2).coerceAtLeast(0),
        )
    }
}

@Composable
private fun DetentByteSettingsSlider(
    colors: NaviampColors,
    title: String,
    valueBytes: Long,
    detents: List<Long>,
    onValueChanged: (Long) -> Unit,
) {
    val normalizedDetents = detents.distinct().sorted()
    val selectedIndex = normalizedDetents.indexOf(valueBytes).takeIf { it >= 0 }
        ?: normalizedDetents.indices.minByOrNull { index ->
            kotlin.math.abs(normalizedDetents[index] - valueBytes)
        }
        ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = colors.secondaryText, fontSize = 12.sp)
            Text(normalizedDetents.getOrElse(selectedIndex) { valueBytes }.storageBytesLabel(), color = colors.primaryText, fontSize = 12.sp)
        }
        Slider(
            enabled = normalizedDetents.size > 1,
            value = selectedIndex.toFloat(),
            onValueChange = { raw ->
                val index = raw.toInt().coerceIn(normalizedDetents.indices)
                onValueChanged(normalizedDetents[index])
            },
            valueRange = 0f..(normalizedDetents.lastIndex.coerceAtLeast(0)).toFloat(),
            steps = (normalizedDetents.size - 2).coerceAtLeast(0),
        )
    }
}

@Composable
private fun DetentIntSettingsPageRow(
    colors: NaviampColors,
    title: String,
    subtitle: String,
    value: Int,
    detents: List<Int>,
    enabled: Boolean = true,
    valueLabel: @Composable (Int) -> String,
    onValueChanged: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val normalizedDetents = detents.distinct().sorted()
    val selectedValue = normalizedDetents.firstOrNull { it == value }
        ?: normalizedDetents.minByOrNull { kotlin.math.abs(it - value) }
        ?: value

    if (open) {
        SettingsSubsectionHeader(title, subtitle, colors) { open = false }
        normalizedDetents.forEach { option ->
            SelectableSettingsRow(
                colors = colors,
                title = valueLabel(option),
                selected = option == selectedValue,
                enabled = enabled,
            ) {
                onValueChanged(option)
            }
        }
        return
    }

    SettingsRow(
        title = title,
        subtitle = subtitle,
        colors = colors,
        value = valueLabel(selectedValue),
        enabled = enabled,
    ) {
        open = true
    }
}

@Composable
private fun DetentByteSettingsPageRow(
    colors: NaviampColors,
    title: String,
    subtitle: String,
    valueBytes: Long,
    detents: List<Long>,
    onValueChanged: (Long) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val normalizedDetents = detents.distinct().sorted()
    val selectedValue = normalizedDetents.firstOrNull { it == valueBytes }
        ?: normalizedDetents.minByOrNull { kotlin.math.abs(it - valueBytes) }
        ?: valueBytes

    if (open) {
        SettingsSubsectionHeader(title, subtitle, colors) { open = false }
        normalizedDetents.forEach { option ->
            SelectableSettingsRow(
                colors = colors,
                title = option.storageBytesLabel(),
                selected = option == selectedValue,
            ) {
                onValueChanged(option)
            }
        }
        return
    }

    SettingsRow(
        title = title,
        subtitle = subtitle,
        colors = colors,
        value = selectedValue.storageBytesLabel(),
    ) {
        open = true
    }
}

private fun diagnosticRowValue(
    diagnostics: NaviampDiagnosticsUi,
    sectionTitle: String,
    label: String,
): String? =
    diagnostics.sections
        .firstOrNull { section -> section.title == sectionTitle }
        ?.rows
        ?.firstOrNull { row -> row.first == label }
        ?.second

@Composable
fun NaviampDownloadsSettingsSection(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    cacheSettings: CacheSettings,
    diagnostics: NaviampDiagnosticsUi,
    showMobileNetworkQuality: Boolean,
    downloadBytes: Long,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit = onPlaybackSettingsChanged,
    onCacheSettingsChanged: (CacheSettings) -> Unit,
) {
    val normalized = cacheSettings.normalized()
    var selectedPage by remember { mutableStateOf<DownloadsSettingsPage?>(null) }
    var pendingDownloadQualitySettings by remember { mutableStateOf<PlaybackSettings?>(null) }

    fun applyDownloadQuality(preference: StreamQualityPreference) {
        val updated = playbackSettings.copy(downloadQuality = preference.normalized())
        if (downloadBytes > 0L && preference.normalized() != playbackSettings.downloadQuality.normalized()) {
            pendingDownloadQualitySettings = updated
        } else {
            onPlaybackSettingsChanged(updated)
        }
    }

    selectedPage?.let { page ->
        SettingsSubsectionHeader(page.title(), page.subtitle(), colors) { selectedPage = null }
        when (page) {
            DownloadsSettingsPage.SavedFiles -> QualityBitrateOptions(
                colors = colors,
                preference = playbackSettings.downloadQuality,
                onPreferenceChanged = { preference -> applyDownloadQuality(preference) },
            )
            DownloadsSettingsPage.StorageBudget -> DownloadBudgetOptions.forEach { bytes ->
                SelectableSettingsRow(
                    colors = colors,
                    title = bytes.storageBytesLabel(),
                    selected = bytes == normalized.maxDownloadBytes,
                ) {
                    onCacheSettingsChanged(normalized.copy(maxDownloadBytes = bytes).normalized())
                }
            }
        }
        pendingDownloadQualitySettings?.let { pendingSettings ->
            DownloadQualityChangeDialog(
                pendingSettings = pendingSettings,
                onDismiss = { pendingDownloadQualitySettings = null },
                onKeepExisting = {
                    pendingDownloadQualitySettings = null
                    onPlaybackSettingsChanged(pendingSettings)
                },
                onRedownload = {
                    pendingDownloadQualitySettings = null
                    onPlaybackSettingsChangedAndRedownload(pendingSettings)
                },
            )
        }
        return
    }

    SettingsRow(
        title = stringResource(Res.string.settings_downloads_saved_files_title),
        subtitle = stringResource(Res.string.settings_downloads_saved_files_subtitle),
        colors = colors,
        value = playbackSettings.downloadQuality.summaryLabel(),
    ) {
        selectedPage = DownloadsSettingsPage.SavedFiles
    }
    if (showMobileNetworkQuality) {
        SettingsCheckboxRow(
            colors = colors,
            checked = playbackSettings.allowMobileDownloads,
            label = stringResource(Res.string.settings_downloads_mobile_data),
            onCheckedChange = { enabled ->
                onPlaybackSettingsChanged(playbackSettings.copy(allowMobileDownloads = enabled))
            },
        )
    }
    SettingsCheckboxRow(
        colors = colors,
        checked = normalized.offlineModeEnabled,
        label = stringResource(Res.string.settings_downloads_offline_mode),
        subtitle = stringResource(Res.string.settings_downloads_offline_mode_subtitle),
        onCheckedChange = { enabled ->
            onCacheSettingsChanged(normalized.copy(offlineModeEnabled = enabled).normalized())
        },
    )
    SettingsRow(
        title = stringResource(Res.string.settings_downloads_storage_budget_title),
        subtitle = stringResource(Res.string.settings_downloads_storage_budget_subtitle),
        colors = colors,
        value = normalized.maxDownloadBytes.storageBytesLabel(),
    ) {
        selectedPage = DownloadsSettingsPage.StorageBudget
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding),
    ) {
        SettingsSectionTitle(stringResource(Res.string.settings_storage_title), colors)
        diagnosticRowValue(diagnostics, "Storage", "Downloads")?.let { value ->
            Text(value, color = colors.secondaryText, fontSize = SettingsDetailRowSubtitleSize)
        }
    }
}

private enum class DownloadsSettingsPage(
    val title: String,
    val subtitle: String,
) {
    SavedFiles("Saved Files", "Quality for newly saved files"),
    StorageBudget("Download Storage Budget", "Maximum space for saved files"),
}

@Composable
private fun DownloadsSettingsPage.title(): String =
    when (this) {
        DownloadsSettingsPage.SavedFiles -> stringResource(Res.string.settings_downloads_saved_files_title)
        DownloadsSettingsPage.StorageBudget -> stringResource(Res.string.settings_downloads_storage_budget_title)
    }

@Composable
private fun DownloadsSettingsPage.subtitle(): String =
    when (this) {
        DownloadsSettingsPage.SavedFiles -> stringResource(Res.string.settings_downloads_saved_files_subtitle)
        DownloadsSettingsPage.StorageBudget -> stringResource(Res.string.settings_downloads_storage_budget_subtitle)
    }

@Composable
fun NaviampAudioCacheSettingsSection(
    colors: NaviampColors,
    cacheSettings: CacheSettings,
    diagnostics: NaviampDiagnosticsUi,
    onCacheSettingsChanged: (CacheSettings) -> Unit,
) {
    val normalized = cacheSettings.normalized()
    var selectedPage by remember { mutableStateOf<AudioCacheSettingsPage?>(null) }

    selectedPage?.let { page ->
        SettingsSubsectionHeader(page.title(), page.subtitle(), colors) { selectedPage = null }
        when (page) {
            AudioCacheSettingsPage.PrefetchDepth -> PrefetchDepthOptions.forEach { depth ->
                SelectableSettingsRow(
                    colors = colors,
                    title = if (depth == 0) stringResource(Res.string.common_off) else stringResource(Res.string.settings_tracks_count, depth),
                    selected = depth == normalized.audioPrefetchDepth,
                    enabled = normalized.audioCachingEnabled,
                ) {
                    onCacheSettingsChanged(normalized.copy(audioPrefetchDepth = depth).normalized())
                }
            }
            AudioCacheSettingsPage.AudioCacheBudget -> AudioCacheBudgetOptions.forEach { bytes ->
                SelectableSettingsRow(
                    colors = colors,
                    title = bytes.storageBytesLabel(),
                    selected = bytes == normalized.maxAudioCacheBytes,
                ) {
                    onCacheSettingsChanged(normalized.copy(maxAudioCacheBytes = bytes).normalized())
                }
            }
        }
        return
    }

    SettingsCheckboxRow(
        colors = colors,
        checked = normalized.audioCachingEnabled,
        label = stringResource(Res.string.settings_audio_cache_enable),
        subtitle = stringResource(Res.string.settings_audio_cache_enable_subtitle),
        onCheckedChange = { enabled ->
            onCacheSettingsChanged(normalized.copy(audioCachingEnabled = enabled).normalized())
        },
    )
    SettingsRow(
        title = stringResource(Res.string.settings_audio_cache_prefetch_depth_title),
        subtitle = stringResource(Res.string.settings_audio_cache_prefetch_depth_subtitle),
        colors = colors,
        value = if (normalized.audioPrefetchDepth == 0) {
            stringResource(Res.string.common_off)
        } else {
            stringResource(Res.string.settings_tracks_count, normalized.audioPrefetchDepth)
        },
        enabled = normalized.audioCachingEnabled,
    ) {
        selectedPage = AudioCacheSettingsPage.PrefetchDepth
    }
    SettingsRow(
        title = stringResource(Res.string.settings_audio_cache_budget_title),
        subtitle = stringResource(Res.string.settings_audio_cache_budget_subtitle),
        colors = colors,
        value = normalized.maxAudioCacheBytes.storageBytesLabel(),
    ) {
        selectedPage = AudioCacheSettingsPage.AudioCacheBudget
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsRowHorizontalPadding),
    ) {
        SettingsSectionTitle(stringResource(Res.string.settings_storage_title), colors)
        diagnosticRowValue(diagnostics, "Storage", "Audio cache")?.let { value ->
            Text(value, color = colors.secondaryText, fontSize = SettingsDetailRowSubtitleSize)
        }
    }
}

private enum class AudioCacheSettingsPage(
    val title: String,
    val subtitle: String,
) {
    PrefetchDepth("Prefetch Depth", "Tracks to cache ahead in the play queue"),
    AudioCacheBudget("Audio Cache Budget", "Maximum space for prefetched audio"),
}

@Composable
private fun AudioCacheSettingsPage.title(): String =
    when (this) {
        AudioCacheSettingsPage.PrefetchDepth -> stringResource(Res.string.settings_audio_cache_prefetch_depth_title)
        AudioCacheSettingsPage.AudioCacheBudget -> stringResource(Res.string.settings_audio_cache_budget_title)
    }

@Composable
private fun AudioCacheSettingsPage.subtitle(): String =
    when (this) {
        AudioCacheSettingsPage.PrefetchDepth -> stringResource(Res.string.settings_audio_cache_prefetch_depth_subtitle)
        AudioCacheSettingsPage.AudioCacheBudget -> stringResource(Res.string.settings_audio_cache_budget_subtitle)
    }

@Composable
private fun WaveformSettings(
    colors: NaviampColors,
    cacheSettings: CacheSettings,
    onCacheSettingsChanged: (CacheSettings) -> Unit,
) {
    val normalized = cacheSettings.normalized()
    var detailOpen by remember { mutableStateOf(false) }

    if (detailOpen) {
        SettingsSubsectionHeader(
            stringResource(Res.string.settings_waveforms_detail_title),
            stringResource(Res.string.settings_waveforms_detail_header_subtitle),
            colors,
        ) {
            detailOpen = false
        }
        WaveformBucketCountOptions.forEach { count ->
            SelectableSettingsRow(
                colors = colors,
                title = stringResource(Res.string.settings_steps_count, count),
                selected = count == normalized.waveformBucketCount,
                enabled = normalized.waveformsEnabled,
            ) {
                onCacheSettingsChanged(normalized.copy(waveformBucketCount = count).normalized())
            }
        }
        return
    }

    SettingsCheckboxRow(
        colors = colors,
        checked = normalized.waveformsEnabled,
        label = stringResource(Res.string.settings_waveforms_generate),
        subtitle = stringResource(Res.string.settings_waveforms_generate_subtitle),
        onCheckedChange = { enabled ->
            onCacheSettingsChanged(normalized.copy(waveformsEnabled = enabled).normalized())
        },
    )
    SettingsRow(
        title = stringResource(Res.string.settings_waveforms_detail_title),
        subtitle = stringResource(Res.string.settings_waveforms_detail_subtitle),
        colors = colors,
        value = stringResource(Res.string.settings_steps_count, normalized.waveformBucketCount),
        enabled = normalized.waveformsEnabled,
    ) {
        detailOpen = true
    }
}

@Composable
private fun PrimarySettingsButton(
    label: String,
    colors: NaviampColors,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(label, color = if (enabled) colors.primaryText else colors.mutedText, fontSize = SettingsDetailRowTitleSize)
    }
}

@Composable
private fun InlineSettingsToggleRow(
    colors: NaviampColors,
    checked: Boolean,
    enabled: Boolean = true,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = SettingsDetailRowVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (enabled) colors.primaryText else colors.mutedText,
            fontSize = SettingsDetailRowTitleSize,
            modifier = Modifier.weight(1f),
        )
        CompactSettingsSwitch(
            colors = colors,
            checked = checked,
            enabled = enabled,
            onClick = { onCheckedChange(!checked) },
        )
    }
}

private enum class SharedLocalDataAction(
    val title: String,
    val message: String,
    val confirmLabel: String,
) {
    ClearCache(
        title = "Clear Cache",
        message = "This removes cached images, provider responses, prefetched audio, waveforms, and lyrics. Saved servers and the library index stay intact.",
        confirmLabel = "Clear cache",
    ),
    ClearLibrary(
        title = "Clear Library Index",
        message = "This removes the local library index for the active server. You can sync the library again after this finishes.",
        confirmLabel = "Clear library",
    ),
    ResetDatabase(
        title = "Reset Database",
        message = "This removes saved servers, local cache, downloads, library data, playback history, and local settings stored in the app database.",
        confirmLabel = "Reset database",
    ),
}

@Composable
private fun SharedLocalDataAction.title(): String =
    when (this) {
        SharedLocalDataAction.ClearCache -> stringResource(Res.string.settings_clear_cache_title)
        SharedLocalDataAction.ClearLibrary -> stringResource(Res.string.settings_clear_library_title)
        SharedLocalDataAction.ResetDatabase -> stringResource(Res.string.settings_reset_database_title)
    }

@Composable
private fun SharedLocalDataAction.message(): String =
    when (this) {
        SharedLocalDataAction.ClearCache -> stringResource(Res.string.settings_clear_cache_message)
        SharedLocalDataAction.ClearLibrary -> stringResource(Res.string.settings_clear_library_message)
        SharedLocalDataAction.ResetDatabase -> stringResource(Res.string.settings_reset_database_message)
    }

@Composable
private fun SharedLocalDataAction.confirmLabel(): String =
    when (this) {
        SharedLocalDataAction.ClearCache -> stringResource(Res.string.settings_clear_cache_confirm)
        SharedLocalDataAction.ClearLibrary -> stringResource(Res.string.settings_clear_library_confirm)
        SharedLocalDataAction.ResetDatabase -> stringResource(Res.string.settings_reset_database_confirm)
    }

@Composable
fun NaviampPlaybackSettingsSection(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    supportsReplayGain: Boolean,
    supportsGapless: Boolean,
    supportsCrossfade: Boolean,
    supportsEqualizer: Boolean,
    supportsAudioOutputDeviceSelection: Boolean = false,
    audioOutputDevices: List<AudioOutputDevice> = emptyList(),
    supportsSonicSimilarity: Boolean = false,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit = onPlaybackSettingsChanged,
    showReplayGain: Boolean = true,
    showCrossfade: Boolean = true,
    showQueueBehavior: Boolean = true,
    showDebugLogging: Boolean = true,
    showLrclibLyrics: Boolean = true,
    showMobileNetworkQuality: Boolean = false,
    downloadBytes: Long = 0L,
) {
    var selectedSection by remember { mutableStateOf<NaviampPlaybackSettingsSection?>(null) }

    selectedSection?.let { section ->
        SettingsSubsectionHeader(section.title(), section.subtitle(), colors) { selectedSection = null }
        when (section) {
            NaviampPlaybackSettingsSection.Output -> AudioOutputSettings(
                colors = colors,
                playbackSettings = playbackSettings,
                devices = audioOutputDevices,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
            NaviampPlaybackSettingsSection.AudioQuality -> StreamingQualitySettings(
                colors = colors,
                playbackSettings = playbackSettings,
                showMobileNetworkQuality = showMobileNetworkQuality,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
            NaviampPlaybackSettingsSection.ReplayGain -> ReplayGainSettings(
                colors = colors,
                playbackSettings = playbackSettings,
                supportsReplayGain = supportsReplayGain,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
            NaviampPlaybackSettingsSection.GaplessCrossfade -> GaplessCrossfadeSettings(
                colors = colors,
                playbackSettings = playbackSettings,
                supportsGapless = supportsGapless,
                supportsCrossfade = supportsCrossfade,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
            NaviampPlaybackSettingsSection.Equalizer -> EqualizerSettings(
                colors = colors,
                playbackSettings = playbackSettings,
                supportsEqualizer = supportsEqualizer,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
            NaviampPlaybackSettingsSection.DjBuilder -> RadioDjSettingsSection(
                colors = colors,
                playbackSettings = playbackSettings,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
        }
    } ?: run {
        playbackSettingsSections(
            showOutput = supportsAudioOutputDeviceSelection,
            showReplayGain = showReplayGain,
            showCrossfade = showCrossfade,
        ).forEach { section ->
            SettingsRow(
                title = section.title(),
                subtitle = section.subtitle(),
                colors = colors,
                value = playbackSettingsSectionValue(section, playbackSettings),
            ) {
                selectedSection = section
            }
        }
    }
}

private enum class NaviampPlaybackSettingsSection(
    val title: String,
    val subtitle: String,
) {
    Output("Audio Output", "Where the music plays"),
    AudioQuality("Quality", "Streaming, downloads, and network quality"),
    ReplayGain("Loudness Leveling", "Track and album volume matching"),
    GaplessCrossfade("Fades", "Album flow and transition timing"),
    Equalizer("Equalizer", "10-band EQ and saved profiles"),
    DjBuilder("Radio DJs", "Saved DJs and radio tuning"),
}

@Composable
private fun NaviampPlaybackSettingsSection.title(): String =
    when (this) {
        NaviampPlaybackSettingsSection.Output -> stringResource(Res.string.settings_playback_output_title)
        NaviampPlaybackSettingsSection.AudioQuality -> stringResource(Res.string.settings_playback_quality_title)
        NaviampPlaybackSettingsSection.ReplayGain -> stringResource(Res.string.settings_playback_loudness_title)
        NaviampPlaybackSettingsSection.GaplessCrossfade -> stringResource(Res.string.settings_playback_fades_title)
        NaviampPlaybackSettingsSection.Equalizer -> stringResource(Res.string.settings_playback_equalizer_title)
        NaviampPlaybackSettingsSection.DjBuilder -> stringResource(Res.string.settings_playback_radio_djs_title)
    }

@Composable
private fun NaviampPlaybackSettingsSection.subtitle(): String =
    when (this) {
        NaviampPlaybackSettingsSection.Output -> stringResource(Res.string.settings_playback_output_subtitle)
        NaviampPlaybackSettingsSection.AudioQuality -> stringResource(Res.string.settings_playback_quality_subtitle)
        NaviampPlaybackSettingsSection.ReplayGain -> stringResource(Res.string.settings_playback_loudness_subtitle)
        NaviampPlaybackSettingsSection.GaplessCrossfade -> stringResource(Res.string.settings_playback_fades_subtitle)
        NaviampPlaybackSettingsSection.Equalizer -> stringResource(Res.string.settings_playback_equalizer_subtitle)
        NaviampPlaybackSettingsSection.DjBuilder -> stringResource(Res.string.settings_playback_radio_djs_subtitle)
    }

@Composable
private fun playbackSettingsSectionValue(
    section: NaviampPlaybackSettingsSection,
    playbackSettings: PlaybackSettings,
): String? =
    when (section) {
        NaviampPlaybackSettingsSection.Output -> playbackSettings.outputDevice.outputSummary()
        NaviampPlaybackSettingsSection.AudioQuality -> playbackSettings.wifiStreamingQuality.summaryLabel()
        NaviampPlaybackSettingsSection.ReplayGain -> playbackSettings.replayGainMode.displayName
        NaviampPlaybackSettingsSection.GaplessCrossfade -> playbackSettings.fadeSummary()
        NaviampPlaybackSettingsSection.Equalizer -> if (playbackSettings.equalizer.normalized().enabled) {
            stringResource(Res.string.common_enabled)
        } else {
            stringResource(Res.string.common_off)
        }
        NaviampPlaybackSettingsSection.DjBuilder -> playbackSettings.radioDjs.size.takeIf { it > 0 }?.let {
            stringResource(Res.string.settings_source_saved_count, it)
        }
    }

private fun playbackSettingsSections(
    showOutput: Boolean,
    showReplayGain: Boolean,
    showCrossfade: Boolean,
): List<NaviampPlaybackSettingsSection> =
    buildList {
        if (showOutput) add(NaviampPlaybackSettingsSection.Output)
        add(NaviampPlaybackSettingsSection.AudioQuality)
        if (showReplayGain) add(NaviampPlaybackSettingsSection.ReplayGain)
        if (showCrossfade) add(NaviampPlaybackSettingsSection.GaplessCrossfade)
        add(NaviampPlaybackSettingsSection.Equalizer)
        add(NaviampPlaybackSettingsSection.DjBuilder)
    }

@Composable
private fun AudioOutputSettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    devices: List<AudioOutputDevice>,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    val normalizedDevices = devices.mapNotNull { it.normalized() }
    val selected = playbackSettings.outputDevice.normalized()
    val selectedDevice = normalizedDevices.firstOrNull { it.id == selected.deviceId }
    val pinnedUnavailable = selected.mode == AudioOutputDeviceMode.Pinned &&
        selectedDevice?.isEnabled != true

    SelectableSettingsRow(
        title = stringResource(Res.string.common_default),
        subtitle = stringResource(Res.string.settings_output_default_subtitle),
        colors = colors,
        selected = selected.mode == AudioOutputDeviceMode.FollowSystem,
    ) {
        onPlaybackSettingsChanged(
            playbackSettings.copy(outputDevice = AudioOutputDevicePreference()),
        )
    }
    normalizedDevices
        .filter { it.isEnabled && !it.isSyntheticDefaultOutputDevice() }
        .forEach { device ->
            val deviceSelected = selected.mode == AudioOutputDeviceMode.Pinned && selected.deviceId == device.id
            SelectableSettingsRow(
                title = device.outputDeviceDisplayName(),
                subtitle = device.outputDeviceSubtitle(),
                colors = colors,
                selected = deviceSelected,
            ) {
                onPlaybackSettingsChanged(
                    playbackSettings.copy(
                        outputDevice = AudioOutputDevicePreference(
                            mode = AudioOutputDeviceMode.Pinned,
                            deviceId = device.id,
                            deviceName = device.name,
                        ).normalized(),
                    ),
                )
            }
        }
    if (pinnedUnavailable) {
        Text(
            stringResource(
                Res.string.settings_output_pinned_unavailable,
                selected.deviceName ?: selected.deviceId.orEmpty(),
            ),
            color = colors.secondaryText,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun AudioOutputDevicePreference.outputSummary(): String =
    normalized().let { preference ->
        when (preference.mode) {
            AudioOutputDeviceMode.FollowSystem -> stringResource(Res.string.common_default)
            AudioOutputDeviceMode.Pinned -> preference.deviceName ?: stringResource(Res.string.settings_output_pinned)
        }
    }

@Composable
private fun AudioOutputDevice.outputDeviceSubtitle(): String =
    buildList {
        if (isInitialized) add(stringResource(Res.string.common_active))
        add(if (isEnabled) stringResource(Res.string.common_available) else stringResource(Res.string.common_unavailable))
    }.joinToString(" / ")

@Composable
private fun AudioOutputDevice.outputDeviceDisplayName(): String =
    if (isDefault && name.equals("Default", ignoreCase = true)) {
        stringResource(Res.string.settings_output_system_default_device)
    } else {
        name
    }

private fun AudioOutputDevice.isSyntheticDefaultOutputDevice(): Boolean =
    isDefault && name.equals("Default", ignoreCase = true)

@Composable
private fun SelectableSettingsRow(
    colors: NaviampColors,
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = SettingsRowHorizontalPadding)
            .fillMaxWidth()
            .background(
                color = if (selected && enabled) colors.accent.copy(alpha = 0.16f) else colors.background.copy(alpha = 0f),
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = SettingsDetailRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) colors.primaryText else colors.mutedText, fontSize = SettingsDetailRowTitleSize)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = colors.mutedText, fontSize = SettingsDetailRowSubtitleSize)
            }
        }
        if (selected) {
            Text(
                stringResource(Res.string.settings_selected_label),
                color = if (enabled) colors.primaryText else colors.mutedText,
                fontSize = SettingsDetailRowSubtitleSize,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun SelectableTextOption(
    colors: NaviampColors,
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    selectedLabel: String = "Selected",
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = SettingsRowHorizontalPadding)
            .fillMaxWidth()
            .background(
                color = if (selected && enabled) colors.accent.copy(alpha = 0.16f) else colors.background.copy(alpha = 0f),
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = SettingsDetailRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) colors.primaryText else colors.mutedText, fontSize = SettingsDetailRowTitleSize)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = colors.mutedText, fontSize = SettingsDetailRowSubtitleSize)
            }
        }
        if (selected) {
            Text(
                selectedLabel,
                color = if (enabled) colors.primaryText else colors.mutedText,
                fontSize = SettingsDetailRowSubtitleSize,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

private fun AudioOutputDevice.outputDeviceTitle(selected: Boolean): String =
    buildString {
        append(name)
        if (isDefault) append(" (Default)")
        if (selected) append(" (Selected)")
    }

@Composable
private fun StreamQualityPreference.summaryLabel(): String =
    normalized().let { quality ->
        when (quality.mode) {
            StreamQualityMode.Original -> stringResource(Res.string.settings_quality_maximum)
            StreamQualityMode.Transcode -> "${quality.codec.label} ${stringResource(Res.string.settings_quality_kbps, quality.bitrateKbps)}"
        }
    }

@Composable
private fun PlaybackSettings.fadeSummary(): String =
    when {
        crossfadeDurationSeconds > 0 -> stringResource(Res.string.settings_seconds_short, crossfadeDurationSeconds)
        gaplessEnabled -> stringResource(Res.string.settings_gapless_title)
        else -> stringResource(Res.string.common_off)
    }

@Composable
private fun PlaybackOptionRow(
    colors: NaviampColors,
    title: String,
    subtitle: String,
    value: String? = null,
    onClick: () -> Unit,
) {
    SettingsRow(title = title, subtitle = subtitle, colors = colors, value = value, onClick = onClick)
}

@Composable
private fun PlaybackToggleRow(
    colors: NaviampColors,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsCheckboxRow(
        colors = colors,
        checked = checked,
        enabled = enabled,
        label = title,
        subtitle = subtitle,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun QualityValueRow(
    colors: NaviampColors,
    title: String,
    subtitle: String,
    preference: StreamQualityPreference,
    onPreferenceChanged: (StreamQualityPreference) -> Unit,
) {
    val normalized = preference.normalized()
    PlaybackOptionRow(
        colors = colors,
        title = title,
        subtitle = subtitle,
        value = normalized.summaryLabel(),
    ) {
        val updated = when (normalized.mode) {
            StreamQualityMode.Original -> normalized.copy(mode = StreamQualityMode.Transcode)
            StreamQualityMode.Transcode -> normalized.copy(mode = StreamQualityMode.Original)
        }
        onPreferenceChanged(updated.normalized())
    }
}

@Composable
private fun QualityBitrateOptions(
    colors: NaviampColors,
    preference: StreamQualityPreference,
    onPreferenceChanged: (StreamQualityPreference) -> Unit,
) {
    val normalized = preference.normalized()
    SettingsSectionTitle(stringResource(Res.string.settings_quality_codec_title), colors)
    SelectableTextOption(
        colors = colors,
        title = stringResource(Res.string.settings_quality_maximum),
        subtitle = stringResource(Res.string.settings_quality_maximum_subtitle),
        selected = normalized.mode == StreamQualityMode.Original,
    ) {
        onPreferenceChanged(normalized.copy(mode = StreamQualityMode.Original))
    }
    StreamingCodec.entries.forEach { codec ->
        SelectableTextOption(
            colors = colors,
            title = codec.label,
            subtitle = stringResource(Res.string.settings_quality_convert_to, codec.label),
            selected = normalized.mode == StreamQualityMode.Transcode && normalized.codec == codec,
        ) {
            onPreferenceChanged(normalized.copy(mode = StreamQualityMode.Transcode, codec = codec))
        }
    }
    SettingsSectionTitle(stringResource(Res.string.settings_quality_bitrate_title), colors)
    StreamBitrateKbpsOptions.sortedDescending().forEach { bitrate ->
        SelectableTextOption(
            colors = colors,
            title = stringResource(Res.string.settings_quality_kbps, bitrate),
            selected = normalized.mode == StreamQualityMode.Transcode && normalized.bitrateKbps == bitrate,
        ) {
            onPreferenceChanged(normalized.copy(mode = StreamQualityMode.Transcode, bitrateKbps = bitrate))
        }
    }
}

@Composable
private fun SettingsSubsectionHeader(
    title: String,
    subtitle: String,
    colors: NaviampColors,
    onBack: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
            Icon(NaviampIcons.Back, contentDescription = stringResource(Res.string.common_back), tint = colors.primaryText)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = colors.primaryText, fontSize = SettingsDetailTitleSize, fontWeight = FontWeight.Bold)
            Text(subtitle, color = colors.secondaryText, fontSize = SettingsDetailSubtitleSize)
        }
    }
}

@Composable
private fun ReplayGainSettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    supportsReplayGain: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    var preampOpen by remember { mutableStateOf(false) }
    var replayGainModeOpen by remember { mutableStateOf(false) }
    if (replayGainModeOpen) {
        SettingsSubsectionHeader(
            stringResource(Res.string.settings_replay_gain_title),
            stringResource(Res.string.settings_playback_loudness_subtitle),
            colors,
        ) {
            replayGainModeOpen = false
        }
        ReplayGainMode.entries.forEach { mode ->
            SelectableSettingsRow(
                colors = colors,
                title = mode.displayName,
                subtitle = mode.replayGainSubtitle(),
                selected = playbackSettings.replayGainMode == mode,
                enabled = supportsReplayGain,
            ) {
                onPlaybackSettingsChanged(playbackSettings.copy(replayGainMode = mode))
            }
        }
        return
    }
    if (preampOpen) {
        SettingsSubsectionHeader(
            stringResource(Res.string.settings_replay_gain_preamp_title),
            stringResource(Res.string.settings_replay_gain_preamp_subtitle),
            colors,
        ) { preampOpen = false }
        PreampDbOptions.forEach { gain ->
            SelectableSettingsRow(
                colors = colors,
                title = gain.preampLabel(),
                subtitle = if (gain == 0f) stringResource(Res.string.common_default) else null,
                selected = kotlin.math.abs(playbackSettings.replayGainPreampDb - gain) < 0.05f,
                enabled = supportsReplayGain,
            ) {
                onPlaybackSettingsChanged(playbackSettings.copy(replayGainPreampDb = gain))
            }
        }
        return
    }

    SettingsRow(
        title = stringResource(Res.string.settings_replay_gain_title),
        subtitle = playbackSettings.replayGainMode.replayGainSubtitle(),
        colors = colors,
        value = playbackSettings.replayGainMode.displayName,
        enabled = supportsReplayGain,
    ) {
        replayGainModeOpen = true
    }
    SettingsRow(
        title = stringResource(Res.string.settings_replay_gain_preamp_title),
        subtitle = stringResource(Res.string.settings_replay_gain_preamp_row_subtitle),
        colors = colors,
        value = playbackSettings.replayGainPreampDb.preampLabel(),
        enabled = supportsReplayGain,
    ) {
        preampOpen = true
    }
    SettingsCheckboxRow(
        colors = colors,
        checked = playbackSettings.replayGainInspectorEnabled,
        enabled = supportsReplayGain,
        label = stringResource(Res.string.settings_replay_gain_inspector),
        subtitle = stringResource(Res.string.settings_replay_gain_inspector_subtitle),
        onCheckedChange = { enabled ->
            onPlaybackSettingsChanged(playbackSettings.copy(replayGainInspectorEnabled = enabled))
        },
    )
}

@Composable
private fun GaplessCrossfadeSettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    supportsGapless: Boolean,
    supportsCrossfade: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    var crossfadeOpen by remember { mutableStateOf(false) }
    if (crossfadeOpen) {
        SettingsSubsectionHeader(
            stringResource(Res.string.settings_crossfade_title),
            stringResource(Res.string.settings_crossfade_subtitle),
            colors,
        ) { crossfadeOpen = false }
        CrossfadeDurationOptions.forEach { seconds ->
            SelectableSettingsRow(
                colors = colors,
                title = if (seconds == 0) stringResource(Res.string.common_off) else stringResource(Res.string.settings_seconds_count, seconds),
                subtitle = if (seconds == 0) stringResource(Res.string.settings_crossfade_off_subtitle) else null,
                selected = playbackSettings.crossfadeDurationSeconds == seconds,
                enabled = supportsCrossfade || seconds == 0,
            ) {
                onPlaybackSettingsChanged(
                    playbackSettings.copy(
                        crossfadeDurationSeconds = seconds,
                        gaplessEnabled = if (seconds > 0) false else playbackSettings.gaplessEnabled,
                    ),
                )
            }
        }
        return
    }
    SettingsCheckboxRow(
        colors = colors,
        checked = playbackSettings.gaplessEnabled && playbackSettings.crossfadeDurationSeconds == 0,
        enabled = supportsGapless,
        label = stringResource(Res.string.settings_gapless_title),
        subtitle = stringResource(Res.string.settings_gapless_subtitle),
        onCheckedChange = { enabled ->
            onPlaybackSettingsChanged(
                playbackSettings.copy(
                    gaplessEnabled = enabled,
                    crossfadeDurationSeconds = if (enabled) 0 else playbackSettings.crossfadeDurationSeconds,
                ),
            )
        },
    )
    SettingsRow(
        title = stringResource(Res.string.settings_crossfade_title),
        subtitle = stringResource(Res.string.settings_crossfade_row_subtitle),
        colors = colors,
        value = if (playbackSettings.crossfadeDurationSeconds == 0) {
            stringResource(Res.string.common_off)
        } else {
            stringResource(Res.string.settings_seconds_short, playbackSettings.crossfadeDurationSeconds)
        },
        enabled = supportsCrossfade,
    ) {
        crossfadeOpen = true
    }
}

@Composable
private fun QueueRulesSettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    var selectedPage by remember { mutableStateOf<PlayerBehaviorPage?>(null) }

    selectedPage?.let { page ->
        SettingsSubsectionHeader(page.title(), page.subtitle(), colors) { selectedPage = null }
        when (page) {
            PlayerBehaviorPage.PreviousClick -> PreviousButtonBehavior.entries.forEach { behavior ->
                SelectableSettingsRow(
                    colors = colors,
                    title = behavior.label(),
                    subtitle = behavior.previousButtonSubtitle(),
                    selected = playbackSettings.previousButtonBehavior == behavior,
                ) {
                    onPlaybackSettingsChanged(playbackSettings.copy(previousButtonBehavior = behavior))
                }
            }
            PlayerBehaviorPage.UpNextSelection -> UpNextSelectionBehavior.entries.forEach { behavior ->
                SelectableSettingsRow(
                    colors = colors,
                    title = behavior.label(),
                    subtitle = behavior.upNextSelectionSubtitle(),
                    selected = playbackSettings.upNextSelectionBehavior == behavior,
                ) {
                    onPlaybackSettingsChanged(playbackSettings.copy(upNextSelectionBehavior = behavior))
                }
            }
        }
        return
    }

    SettingsCheckboxRow(
        colors = colors,
        checked = playbackSettings.removePlayedTracksFromQueue,
        label = stringResource(Res.string.settings_player_remove_played),
        subtitle = stringResource(Res.string.settings_player_remove_played_subtitle),
        onCheckedChange = { enabled ->
            onPlaybackSettingsChanged(playbackSettings.copy(removePlayedTracksFromQueue = enabled))
        },
    )
    SettingsRow(
        title = PlayerBehaviorPage.PreviousClick.title(),
        subtitle = PlayerBehaviorPage.PreviousClick.subtitle(),
        colors = colors,
        value = playbackSettings.previousButtonBehavior.label(),
    ) {
        selectedPage = PlayerBehaviorPage.PreviousClick
    }
    SettingsRow(
        title = PlayerBehaviorPage.UpNextSelection.title(),
        subtitle = PlayerBehaviorPage.UpNextSelection.subtitle(),
        colors = colors,
        value = playbackSettings.upNextSelectionBehavior.label(),
    ) {
        selectedPage = PlayerBehaviorPage.UpNextSelection
    }
}

private enum class PlayerBehaviorPage(
    val title: String,
    val subtitle: String,
) {
    PreviousClick("Previous Click", "Back button behavior"),
    UpNextSelection("Up Next Selection", "What happens when choosing a queued track"),
}

@Composable
private fun PlayerBehaviorPage.title(): String =
    when (this) {
        PlayerBehaviorPage.PreviousClick -> stringResource(Res.string.settings_player_previous_click_title)
        PlayerBehaviorPage.UpNextSelection -> stringResource(Res.string.settings_player_up_next_title)
    }

@Composable
private fun PlayerBehaviorPage.subtitle(): String =
    when (this) {
        PlayerBehaviorPage.PreviousClick -> stringResource(Res.string.settings_player_previous_click_subtitle)
        PlayerBehaviorPage.UpNextSelection -> stringResource(Res.string.settings_player_up_next_subtitle)
    }

@Composable
private fun RadioTuningControls(
    colors: NaviampColors,
    tuning: RadioTuningSettings,
    onTuningChanged: (RadioTuningSettings) -> Unit,
) {
    Text(stringResource(Res.string.settings_radio_familiarity), color = colors.secondaryText, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RadioFamiliarity.entries.forEach { familiarity ->
            FilterChip(
                selected = tuning.familiarity == familiarity,
                onClick = { onTuningChanged(tuning.copy(familiarity = familiarity)) },
                label = { Text(familiarity.label, fontSize = 12.sp) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
    Text(stringResource(Res.string.settings_radio_artist_spread), color = colors.secondaryText, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RadioArtistSpread.entries.forEach { spread ->
            FilterChip(
                selected = tuning.artistSpread == spread,
                onClick = { onTuningChanged(tuning.copy(artistSpread = spread)) },
                label = { Text(spread.label, fontSize = 12.sp) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
    SettingsCheckboxRow(
        colors = colors,
        checked = tuning.sameDecadeOnly,
        label = stringResource(Res.string.settings_radio_stay_in_decade),
        onCheckedChange = { enabled ->
            onTuningChanged(tuning.copy(sameDecadeOnly = enabled))
        },
    )
    Text(stringResource(Res.string.settings_radio_artist_runs), color = colors.secondaryText, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RadioArtistRunMode.entries.forEach { mode ->
            FilterChip(
                selected = tuning.artistRunMode == mode,
                onClick = { onTuningChanged(tuning.copy(artistRunMode = mode)) },
                label = { Text(mode.label, fontSize = 12.sp) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
    if (tuning.artistRunMode == RadioArtistRunMode.ArtistBlocks) {
        SettingsNumberSlider(
            colors = colors,
            label = stringResource(Res.string.settings_radio_same_artist_run),
            value = tuning.sameArtistRunLength,
            onValueChanged = { value -> onTuningChanged(tuning.copy(sameArtistRunLength = value)) },
        )
        SettingsNumberSlider(
            colors = colors,
            label = stringResource(Res.string.settings_radio_other_artists_run),
            value = tuning.otherArtistRunLength,
            onValueChanged = { value -> onTuningChanged(tuning.copy(otherArtistRunLength = value)) },
        )
    }
}

@Composable
private fun SettingsNumberSlider(
    colors: NaviampColors,
    label: String,
    value: Int,
    onValueChanged: (Int) -> Unit,
) {
    val normalized = value.coerceIn(MinArtistRunLength, MaxArtistRunLength)
    Text("$label: $normalized", color = colors.secondaryText, fontSize = 12.sp)
    Slider(
        value = normalized.toFloat(),
        onValueChange = { onValueChanged(it.toInt().coerceIn(MinArtistRunLength, MaxArtistRunLength)) },
        valueRange = MinArtistRunLength.toFloat()..MaxArtistRunLength.toFloat(),
        steps = MaxArtistRunLength - MinArtistRunLength - 1,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RadioDjSettingsSection(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var draftName by remember { mutableStateOf("") }
    var draftTuning by remember { mutableStateOf(playbackSettings.radioTuning) }
    val editingPreset = editingId?.let { id -> playbackSettings.radioDjs.firstOrNull { it.id == id } }

    if (editingId != null) {
        SettingsSubsectionHeader(
            title = if (editingPreset == null) stringResource(Res.string.settings_radio_new_dj) else editingPreset.name,
            subtitle = stringResource(Res.string.settings_radio_edit_dj_subtitle),
            colors = colors,
        ) {
            editingId = null
        }
        OutlinedTextField(
            value = draftName,
            onValueChange = { draftName = it },
            singleLine = true,
            label = { Text(stringResource(Res.string.settings_radio_dj_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        RadioTuningControls(
            colors = colors,
            tuning = draftTuning,
            onTuningChanged = { draftTuning = it },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = draftName.isNotBlank(),
                onClick = {
                    val preset = RadioDjPreset(
                        id = editingPreset?.id ?: radioDjIdFor(draftName, playbackSettings.radioDjs),
                        name = draftName,
                        tuning = draftTuning,
                    ).normalized()
                    val updated = if (editingPreset == null) {
                        playbackSettings.radioDjs + preset
                    } else {
                        playbackSettings.radioDjs.map { if (it.id == preset.id) preset else it }
                    }
                    onPlaybackSettingsChanged(
                        playbackSettings.copy(
                            radioDjs = updated,
                            activeRadioDjId = playbackSettings.activeRadioDjId?.takeIf { id ->
                                updated.any { it.id == id }
                            },
                        ),
                    )
                    editingId = null
                },
            ) {
                Text(stringResource(Res.string.common_save), color = if (draftName.isNotBlank()) colors.primaryText else colors.mutedText)
            }
            if (editingPreset != null) {
                TextButton(
                    onClick = {
                        val updated = playbackSettings.radioDjs.filterNot { it.id == editingPreset.id }
                        onPlaybackSettingsChanged(
                            playbackSettings.copy(
                                radioDjs = updated,
                                activeRadioDjId = playbackSettings.activeRadioDjId?.takeIf { it != editingPreset.id },
                            ),
                        )
                        editingId = null
                    },
                ) {
                    Text(stringResource(Res.string.common_delete), color = colors.primaryText)
                }
            }
            TextButton(onClick = { editingId = null }) {
                Text(stringResource(Res.string.common_cancel), color = colors.secondaryText)
            }
        }
        return
    }

    if (playbackSettings.radioDjs.isEmpty()) {
        Text(stringResource(Res.string.settings_radio_no_djs), color = colors.secondaryText, fontSize = 12.sp)
    } else {
        playbackSettings.radioDjs.forEach { preset ->
            SettingsRow(preset.name, preset.tuning.summaryLabel(), colors) {
                editingId = preset.id
                draftName = preset.name
                draftTuning = preset.tuning
            }
        }
    }
    PrimarySettingsButton(stringResource(Res.string.settings_radio_new_dj), colors, enabled = true) {
        editingId = NewRadioDjId
        draftName = ""
        draftTuning = playbackSettings.radioTuning
    }
}

@Composable
private fun LyricsSettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    showLrclibLyrics: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    var showSearchOrder by remember { mutableStateOf(false) }
    if (showSearchOrder) {
        SettingsSubsectionHeader(
            title = stringResource(Res.string.settings_lyrics_search_order),
            subtitle = stringResource(Res.string.settings_lyrics_search_order_subtitle),
            colors = colors,
        ) {
            showSearchOrder = false
        }
        LyricsSearchOrderSettings(
            colors = colors,
            order = playbackSettings.lyricsSearchOrder.normalizedLyricsSearchOrder(),
            onOrderChanged = { order ->
                onPlaybackSettingsChanged(playbackSettings.copy(lyricsSearchOrder = order))
            },
        )
        return
    }

    if (showLrclibLyrics) {
        SettingsCheckboxRow(
            colors = colors,
            checked = playbackSettings.lrclibLyricsEnabled,
            label = stringResource(Res.string.settings_lyrics_download),
            subtitle = stringResource(Res.string.settings_lyrics_download_subtitle),
            onCheckedChange = { enabled ->
                onPlaybackSettingsChanged(playbackSettings.copy(lrclibLyricsEnabled = enabled))
            },
        )
    }
    SettingsCheckboxRow(
        colors = colors,
        checked = playbackSettings.preferSyncedLyrics,
        label = stringResource(Res.string.settings_lyrics_prefer_synced),
        subtitle = stringResource(Res.string.settings_lyrics_prefer_synced_subtitle),
        onCheckedChange = { enabled ->
            onPlaybackSettingsChanged(playbackSettings.copy(preferSyncedLyrics = enabled))
        },
    )
    SettingsRow(
        title = stringResource(Res.string.settings_lyrics_search_order),
        subtitle = stringResource(Res.string.settings_lyrics_search_order_subtitle),
        colors = colors,
        value = playbackSettings.lyricsSearchOrder.normalizedLyricsSearchOrder()
            .map { source -> source.label() }
            .joinToString(", "),
    ) {
        showSearchOrder = true
    }
}

@Composable
private fun LyricsSearchOrderSettings(
    colors: NaviampColors,
    order: List<LyricsSourcePreference>,
    onOrderChanged: (List<LyricsSourcePreference>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        order.forEachIndexed { index, source ->
            var dragY by remember(order, source) { mutableStateOf(0f) }
            val isDragging = dragY != 0f
            val rowShape = RoundedCornerShape(6.dp)
            Row(
                modifier = Modifier
                    .padding(horizontal = SettingsRowHorizontalPadding)
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = dragY.coerceIn(-132f, 132f)
                        shadowElevation = if (isDragging) 10f else 0f
                        shape = rowShape
                    }
                    .background(
                        color = if (isDragging) {
                            colors.controlSurface.copy(alpha = 0.72f)
                        } else {
                            colors.background.copy(alpha = 0.12f)
                        },
                        shape = rowShape,
                    )
                    .border(
                        width = 1.dp,
                        color = colors.border.copy(alpha = if (isDragging) 0.78f else 0.18f),
                        shape = rowShape,
                    )
                    .pointerInput(order, source) {
                        detectDragGestures(
                            onDragStart = { dragY = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragY += dragAmount.y
                            },
                            onDragEnd = {
                                val rowStep = 56.dp.toPx()
                                val targetIndex = (index + (dragY / rowStep).roundToInt()).coerceIn(order.indices)
                                if (targetIndex != index) {
                                    onOrderChanged(order.moveItem(index, targetIndex))
                                }
                                dragY = 0f
                            },
                            onDragCancel = { dragY = 0f },
                        )
                    }
                    .padding(vertical = SettingsDetailRowVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    NaviampTransportIcons.Menu,
                    contentDescription = stringResource(Res.string.settings_lyrics_drag_source),
                    tint = colors.secondaryText,
                    modifier = Modifier.size(18.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(source.label(), color = colors.primaryText, fontSize = SettingsDetailRowTitleSize)
                    Text(source.subtitle(), color = colors.mutedText, fontSize = SettingsDetailRowSubtitleSize)
                }
            }
        }
    }
}

@Composable
private fun LyricsSourcePreference.label(): String =
    when (this) {
        LyricsSourcePreference.Provider -> stringResource(Res.string.settings_lyrics_source_provider)
        LyricsSourcePreference.Embedded -> stringResource(Res.string.settings_lyrics_source_embedded)
        LyricsSourcePreference.Download -> stringResource(Res.string.settings_lyrics_source_download)
    }

@Composable
private fun LyricsSourcePreference.subtitle(): String =
    when (this) {
        LyricsSourcePreference.Provider -> stringResource(Res.string.settings_lyrics_source_provider_subtitle)
        LyricsSourcePreference.Embedded -> stringResource(Res.string.settings_lyrics_source_embedded_subtitle)
        LyricsSourcePreference.Download -> stringResource(Res.string.settings_lyrics_source_download_subtitle)
    }

private fun <T> List<T>.moveItem(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return mutable
}

@Composable
private fun RelatedTracksSettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    supportsSonicSimilarity: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    if (supportsSonicSimilarity) {
        SettingsCheckboxRow(
            colors = colors,
            checked = playbackSettings.sonicSimilarityEnabled,
            label = stringResource(Res.string.settings_related_sonic_similarity),
            subtitle = stringResource(Res.string.settings_related_sonic_similarity_subtitle),
            onCheckedChange = { enabled ->
                onPlaybackSettingsChanged(playbackSettings.copy(sonicSimilarityEnabled = enabled))
            },
        )
        SettingsCheckboxRow(
            colors = colors,
            checked = playbackSettings.sonicAutoplayEnabled,
            label = stringResource(Res.string.settings_related_sonic_autoplay),
            subtitle = stringResource(Res.string.settings_related_sonic_autoplay_subtitle),
            onCheckedChange = { enabled ->
                onPlaybackSettingsChanged(playbackSettings.copy(sonicAutoplayEnabled = enabled))
            },
        )
    } else {
        Text(stringResource(Res.string.settings_related_requires_support), color = colors.secondaryText, fontSize = 12.sp)
    }
}

@Composable
fun NaviampDebugPlaybackSettingsSection(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    SettingsCheckboxRow(
        colors = colors,
        checked = playbackSettings.debugLoggingEnabled,
        label = stringResource(Res.string.settings_debug_logging),
        onCheckedChange = { enabled ->
            onPlaybackSettingsChanged(playbackSettings.copy(debugLoggingEnabled = enabled))
        },
    )
}

@Composable
private fun EqualizerCurvePanel(
    colors: NaviampColors,
    equalizer: app.naviamp.domain.playback.EqualizerSettings,
    enabled: Boolean,
    onBandGainChanged: (Int, Float) -> Unit,
) {
    val normalized = equalizer.normalized()
    val currentOnBandGainChanged by rememberUpdatedState(onBandGainChanged)
    fun gainForOffset(y: Float, height: Float): Float {
        val inset = 10f
        val usableHeight = (height - inset * 2f).coerceAtLeast(1f)
        val fraction = ((y - inset) / usableHeight).coerceIn(0f, 1f)
        return (MaxEqualizerGainDb - (fraction * (MaxEqualizerGainDb - MinEqualizerGainDb)))
            .coerceIn(MinEqualizerGainDb, MaxEqualizerGainDb)
    }
    fun bandForOffset(x: Float, width: Float): Int {
        val lastIndex = EqualizerBandFrequencies.lastIndex.coerceAtLeast(1)
        return ((x.coerceIn(0f, width.coerceAtLeast(1f)) / width.coerceAtLeast(1f)) * lastIndex)
            .roundToInt()
            .coerceIn(EqualizerBandFrequencies.indices)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.controlSurface.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            normalized.bandsDb.forEach { gain ->
                Text(
                    gain.equalizerGainLabel(),
                    color = colors.primaryText,
                    fontSize = 9.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentOnBandGainChanged(
                                bandForOffset(offset.x, size.width.toFloat()),
                                gainForOffset(offset.y, size.height.toFloat()),
                            )
                        },
                    ) { change, _ ->
                        currentOnBandGainChanged(
                            bandForOffset(change.position.x, size.width.toFloat()),
                            gainForOffset(change.position.y, size.height.toFloat()),
                        )
                    }
                },
        ) {
            val usableWidth = size.width.coerceAtLeast(1f)
            val verticalInset = 10f
            val usableHeight = (size.height - verticalInset * 2f).coerceAtLeast(1f)
            val zeroY = (
                verticalInset +
                    (MaxEqualizerGainDb / (MaxEqualizerGainDb - MinEqualizerGainDb)) * usableHeight
                ).coerceIn(verticalInset, verticalInset + usableHeight)
            drawLine(
                color = colors.secondaryText.copy(alpha = 0.28f),
                start = Offset(0f, zeroY),
                end = Offset(usableWidth, zeroY),
                strokeWidth = 1.5f,
            )
            val points = normalized.bandsDb.mapIndexed { index, gain ->
                val x = if (EqualizerBandFrequencies.lastIndex == 0) {
                    usableWidth / 2f
                } else {
                    usableWidth * (index.toFloat() / EqualizerBandFrequencies.lastIndex.toFloat())
                }
                val y = (
                    verticalInset +
                        ((MaxEqualizerGainDb - gain) / (MaxEqualizerGainDb - MinEqualizerGainDb)) * usableHeight
                    ).coerceIn(verticalInset, verticalInset + usableHeight)
                Offset(x, y)
            }
            points.zipWithNext().forEach { (start, end) ->
                drawLine(
                    color = colors.primaryText,
                    start = start,
                    end = end,
                    strokeWidth = 3.2f,
                    cap = StrokeCap.Round,
                )
            }
            points.forEach { point ->
                drawCircle(
                    color = colors.primaryText,
                    radius = 5.5f,
                    center = point,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            EqualizerBandFrequencies.forEach { frequency ->
                Text(
                    frequency.equalizerFrequencyLabel(),
                    color = colors.secondaryText,
                    fontSize = 9.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EqualizerSettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    supportsEqualizer: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    val equalizer = playbackSettings.equalizer.normalized()
    val activeProfile = equalizer.savedProfiles.firstOrNull { it.id == equalizer.profileId }
    var selectedPage by remember { mutableStateOf<EqualizerSettingsPage?>(null) }
    var profileDialogOpen by remember { mutableStateOf(false) }
    var profileName by remember(equalizer.profileId, activeProfile?.name) {
        mutableStateOf(activeProfile?.name.orEmpty())
    }
    selectedPage?.let { page ->
        SettingsSubsectionHeader(page.title(), page.subtitle(), colors) { selectedPage = null }
        when (page) {
            EqualizerSettingsPage.Preset -> EqualizerPreset.entries.forEach { preset ->
                SelectableSettingsRow(
                    colors = colors,
                    title = preset.displayName,
                    selected = equalizer.preset == preset,
                    enabled = supportsEqualizer,
                ) {
                    onPlaybackSettingsChanged(playbackSettings.copy(equalizer = equalizer.withPreset(preset)))
                }
            }
            EqualizerSettingsPage.Profile -> {
                if (equalizer.savedProfiles.isEmpty()) {
                    Text(stringResource(Res.string.settings_equalizer_no_profiles), color = colors.secondaryText, fontSize = 12.sp)
                } else {
                    equalizer.savedProfiles.forEach { profile ->
                        SelectableSettingsRow(
                            colors = colors,
                            title = profile.name,
                            selected = activeProfile?.id == profile.id,
                            enabled = supportsEqualizer,
                        ) {
                            onPlaybackSettingsChanged(playbackSettings.copy(equalizer = equalizer.withProfile(profile)))
                        }
                    }
                }
            }
        }
        return
    }

    EqualizerCurvePanel(
        colors = colors,
        equalizer = equalizer,
        enabled = supportsEqualizer,
    ) { index, gain ->
        onPlaybackSettingsChanged(
            playbackSettings.copy(equalizer = equalizer.withBandGain(index, gain)),
        )
    }
    SettingsCheckboxRow(
        colors = colors,
        checked = equalizer.enabled,
        enabled = supportsEqualizer,
        label = stringResource(Res.string.settings_equalizer_enable),
        subtitle = if (supportsEqualizer) {
            stringResource(Res.string.settings_equalizer_enable_subtitle)
        } else {
            stringResource(Res.string.settings_equalizer_unavailable_subtitle)
        },
        onCheckedChange = { enabled ->
            onPlaybackSettingsChanged(
                playbackSettings.copy(equalizer = equalizer.copy(enabled = enabled).normalized()),
            )
        },
    )
    if (equalizer.savedProfiles.isNotEmpty()) {
        SettingsRow(
            title = stringResource(Res.string.settings_equalizer_saved_profile_title),
            subtitle = stringResource(Res.string.settings_equalizer_saved_profile_subtitle),
            colors = colors,
            value = activeProfile?.name ?: stringResource(Res.string.common_none),
            enabled = supportsEqualizer,
        ) {
            selectedPage = EqualizerSettingsPage.Profile
        }
    }
    SettingsRow(
        title = stringResource(Res.string.settings_equalizer_preset_title),
        subtitle = stringResource(Res.string.settings_equalizer_preset_subtitle),
        colors = colors,
        value = equalizer.preset.displayName,
        enabled = supportsEqualizer,
    ) {
        selectedPage = EqualizerSettingsPage.Preset
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            enabled = supportsEqualizer,
            onClick = {
                onPlaybackSettingsChanged(playbackSettings.copy(equalizer = equalizer.withPreset(EqualizerPreset.Flat)))
            },
        ) {
            Text(stringResource(Res.string.common_reset), color = if (supportsEqualizer) colors.primaryText else colors.mutedText, fontSize = 12.sp)
        }
        TextButton(
            enabled = supportsEqualizer,
            onClick = {
                profileName = activeProfile?.name.orEmpty()
                profileDialogOpen = true
            },
        ) {
            Text(
                if (activeProfile == null) {
                    stringResource(Res.string.settings_equalizer_save_profile)
                } else {
                    stringResource(Res.string.settings_equalizer_rename_profile)
                },
                color = if (supportsEqualizer) colors.primaryText else colors.mutedText,
                fontSize = 12.sp,
            )
        }
    }
    if (profileDialogOpen) {
        AlertDialog(
            onDismissRequest = { profileDialogOpen = false },
            title = {
                Text(
                    if (activeProfile == null) {
                        stringResource(Res.string.settings_equalizer_save_profile_title)
                    } else {
                        stringResource(Res.string.settings_equalizer_rename_profile_title)
                    },
                )
            },
            text = {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.settings_equalizer_profile_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = profileName.isNotBlank(),
                    onClick = {
                        profileDialogOpen = false
                        onPlaybackSettingsChanged(
                            playbackSettings.copy(equalizer = equalizer.savedAsProfile(profileName)),
                        )
                    },
                ) {
                    Text(stringResource(Res.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { profileDialogOpen = false }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }
}

private enum class EqualizerSettingsPage(
    val title: String,
    val subtitle: String,
) {
    Preset("Preset", "Start from a built-in EQ curve"),
    Profile("Saved Profile", "Apply a saved EQ curve"),
}

@Composable
private fun EqualizerSettingsPage.title(): String =
    when (this) {
        EqualizerSettingsPage.Preset -> stringResource(Res.string.settings_equalizer_preset_title)
        EqualizerSettingsPage.Profile -> stringResource(Res.string.settings_equalizer_saved_profile_title)
    }

@Composable
private fun EqualizerSettingsPage.subtitle(): String =
    when (this) {
        EqualizerSettingsPage.Preset -> stringResource(Res.string.settings_equalizer_preset_subtitle)
        EqualizerSettingsPage.Profile -> stringResource(Res.string.settings_equalizer_saved_profile_subtitle)
    }

@Composable
private fun StreamingQualitySettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    showMobileNetworkQuality: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    var selectedPage by remember { mutableStateOf<StreamingQualitySettingsPage?>(null) }
    selectedPage?.let { page ->
        SettingsSubsectionHeader(page.title(), page.subtitle(), colors) { selectedPage = null }
        when (page) {
            StreamingQualitySettingsPage.Wifi -> QualityBitrateOptions(
                colors = colors,
                preference = playbackSettings.wifiStreamingQuality,
                onPreferenceChanged = { preference ->
                    onPlaybackSettingsChanged(playbackSettings.copy(wifiStreamingQuality = preference.normalized()))
                },
            )
            StreamingQualitySettingsPage.Mobile -> QualityBitrateOptions(
                colors = colors,
                preference = playbackSettings.mobileStreamingQuality,
                onPreferenceChanged = { preference ->
                    onPlaybackSettingsChanged(playbackSettings.copy(mobileStreamingQuality = preference.normalized()))
                },
            )
        }
        return
    }

    SettingsSectionTitle(stringResource(Res.string.settings_quality_streaming_title), colors)
    SettingsRow(
        title = stringResource(Res.string.settings_quality_wifi_title),
        subtitle = stringResource(Res.string.settings_quality_wifi_subtitle),
        colors = colors,
        value = playbackSettings.wifiStreamingQuality.summaryLabel(),
    ) {
        selectedPage = StreamingQualitySettingsPage.Wifi
    }
    if (showMobileNetworkQuality) {
        SettingsRow(
            title = stringResource(Res.string.settings_quality_mobile_title),
            subtitle = stringResource(Res.string.settings_quality_mobile_subtitle),
            colors = colors,
            value = playbackSettings.mobileStreamingQuality.summaryLabel(),
        ) {
            selectedPage = StreamingQualitySettingsPage.Mobile
        }
    }
}

private enum class StreamingQualitySettingsPage(
    val title: String,
    val subtitle: String,
) {
    Wifi("Wi-Fi / wired", "Quality when on trusted networks"),
    Mobile("Mobile data", "Quality when using cellular data"),
}

@Composable
private fun StreamingQualitySettingsPage.title(): String =
    when (this) {
        StreamingQualitySettingsPage.Wifi -> stringResource(Res.string.settings_quality_wifi_title)
        StreamingQualitySettingsPage.Mobile -> stringResource(Res.string.settings_quality_mobile_title)
    }

@Composable
private fun StreamingQualitySettingsPage.subtitle(): String =
    when (this) {
        StreamingQualitySettingsPage.Wifi -> stringResource(Res.string.settings_quality_wifi_subtitle)
        StreamingQualitySettingsPage.Mobile -> stringResource(Res.string.settings_quality_mobile_subtitle)
    }

@Composable
private fun DownloadQualitySettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    showMobileNetworkQuality: Boolean,
    downloadBytes: Long,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
) {
    var pendingDownloadQualitySettings by remember { mutableStateOf<PlaybackSettings?>(null) }
    var savedFilesOpen by remember { mutableStateOf(false) }

    fun applyDownloadQuality(preference: StreamQualityPreference) {
        val updated = playbackSettings.copy(downloadQuality = preference.normalized())
        if (downloadBytes > 0L && preference.normalized() != playbackSettings.downloadQuality.normalized()) {
            pendingDownloadQualitySettings = updated
        } else {
            onPlaybackSettingsChanged(updated)
        }
    }

    if (savedFilesOpen) {
        SettingsSubsectionHeader(
            stringResource(Res.string.settings_downloads_saved_files_title),
            stringResource(Res.string.settings_downloads_saved_files_subtitle),
            colors,
        ) { savedFilesOpen = false }
        QualityBitrateOptions(
            colors = colors,
            preference = playbackSettings.downloadQuality,
            onPreferenceChanged = { preference -> applyDownloadQuality(preference) },
        )
    } else {
        SettingsSectionTitle(stringResource(Res.string.settings_downloads_title), colors)
        SettingsRow(
            title = stringResource(Res.string.settings_downloads_saved_files_title),
            subtitle = stringResource(Res.string.settings_downloads_saved_files_subtitle),
            colors = colors,
            value = playbackSettings.downloadQuality.summaryLabel(),
        ) {
            savedFilesOpen = true
        }
        if (showMobileNetworkQuality) {
            SettingsCheckboxRow(
                colors = colors,
                checked = playbackSettings.allowMobileDownloads,
                label = stringResource(Res.string.settings_downloads_mobile_data),
                onCheckedChange = { enabled ->
                    onPlaybackSettingsChanged(playbackSettings.copy(allowMobileDownloads = enabled))
                },
            )
        }
    }
    pendingDownloadQualitySettings?.let { pendingSettings ->
        AlertDialog(
            onDismissRequest = { pendingDownloadQualitySettings = null },
            title = { Text(stringResource(Res.string.settings_downloads_change_quality_title)) },
            text = {
                Text(stringResource(Res.string.settings_downloads_change_quality_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDownloadQualitySettings = null
                        onPlaybackSettingsChanged(pendingSettings)
                    },
                ) {
                    Text(stringResource(Res.string.settings_downloads_keep_existing))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            pendingDownloadQualitySettings = null
                            onPlaybackSettingsChangedAndRedownload(pendingSettings)
                        },
                    ) {
                        Text(stringResource(Res.string.settings_downloads_redownload))
                    }
                    TextButton(onClick = { pendingDownloadQualitySettings = null }) {
                        Text(stringResource(Res.string.common_cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun DownloadQualityChangeDialog(
    pendingSettings: PlaybackSettings,
    onDismiss: () -> Unit,
    onKeepExisting: () -> Unit,
    onRedownload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_downloads_change_quality_title)) },
        text = {
            Text(stringResource(Res.string.settings_downloads_change_quality_message))
        },
        confirmButton = {
            TextButton(onClick = onKeepExisting) {
                Text(stringResource(Res.string.settings_downloads_keep_existing))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRedownload) {
                    Text(stringResource(Res.string.settings_downloads_redownload))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.common_cancel))
                }
            }
        },
    )
}

@Composable
private fun SettingsCheckboxRow(
    colors: NaviampColors,
    checked: Boolean,
    enabled: Boolean = true,
    label: String,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = SettingsRowHorizontalPadding)
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = SettingsDetailRowVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) colors.primaryText else colors.mutedText, fontSize = SettingsDetailRowTitleSize)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = colors.mutedText, fontSize = SettingsDetailRowSubtitleSize)
            }
        }
        CompactSettingsSwitch(
            colors = colors,
            checked = checked,
            enabled = enabled,
            onClick = { onCheckedChange(!checked) },
        )
    }
}

@Composable
private fun CompactSettingsSwitch(
    colors: NaviampColors,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val trackColor = when {
        !enabled -> colors.border.copy(alpha = 0.28f)
        checked -> colors.accent
        else -> colors.background.copy(alpha = 0.92f)
    }
    val thumbColor = when {
        !enabled -> colors.mutedText.copy(alpha = 0.62f)
        checked -> colors.primaryText
        else -> colors.secondaryText
    }
    Box(
        modifier = Modifier
            .width(34.dp)
            .height(18.dp)
            .background(trackColor, RoundedCornerShape(999.dp))
            .border(1.dp, colors.border.copy(alpha = if (checked) 0.22f else 0.72f), RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .background(thumbColor, RoundedCornerShape(999.dp)),
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String, colors: NaviampColors) {
    Text(title, color = colors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = SettingsSectionTitleSize)
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    colors: NaviampColors,
    value: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = SettingsRowHorizontalPadding)
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = SettingsDetailRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) colors.primaryText else colors.mutedText, fontSize = SettingsDetailRowTitleSize)
            Text(subtitle, color = colors.mutedText, fontSize = SettingsDetailRowSubtitleSize)
        }
        value?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = if (enabled) colors.secondaryText else colors.mutedText,
                fontSize = SettingsDetailRowSubtitleSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Icon(
            NaviampIcons.ChevronRight,
            contentDescription = null,
            tint = if (enabled) colors.secondaryText else colors.mutedText,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PreviousButtonBehavior.label(): String =
    when (this) {
        PreviousButtonBehavior.RestartThenPrevious -> stringResource(Res.string.settings_previous_restart_first)
        PreviousButtonBehavior.AlwaysPrevious -> stringResource(Res.string.settings_previous_always_previous)
    }

@Composable
private fun PreviousButtonBehavior.previousButtonSubtitle(): String =
    when (this) {
        PreviousButtonBehavior.RestartThenPrevious -> stringResource(Res.string.settings_previous_restart_first_subtitle)
        PreviousButtonBehavior.AlwaysPrevious -> stringResource(Res.string.settings_previous_always_previous_subtitle)
    }

@Composable
private fun UpNextSelectionBehavior.label(): String =
    when (this) {
        UpNextSelectionBehavior.MoveSelectedToCurrent -> stringResource(Res.string.settings_up_next_move_selected)
        UpNextSelectionBehavior.SkipToSelected -> stringResource(Res.string.settings_up_next_skip_selected)
    }

@Composable
private fun UpNextSelectionBehavior.upNextSelectionSubtitle(): String =
    when (this) {
        UpNextSelectionBehavior.MoveSelectedToCurrent -> stringResource(Res.string.settings_up_next_move_selected_subtitle)
        UpNextSelectionBehavior.SkipToSelected -> stringResource(Res.string.settings_up_next_skip_selected_subtitle)
    }

@Composable
private fun ReplayGainMode.replayGainSubtitle(): String =
    when (this) {
        ReplayGainMode.Off -> stringResource(Res.string.settings_replay_gain_off_description)
        ReplayGainMode.Track -> stringResource(Res.string.settings_replay_gain_track_description)
        ReplayGainMode.Album -> stringResource(Res.string.settings_replay_gain_album_description)
    }

private val StreamingCodec.label: String
    get() = when (this) {
        StreamingCodec.Mp3 -> "MP3"
        StreamingCodec.Aac -> "AAC"
        StreamingCodec.Opus -> "Opus"
    }

@Composable
private fun RadioTuningSettings.summaryLabel(): String =
    listOf(
        familiarity.label,
        artistSpread.label,
        if (sameDecadeOnly) stringResource(Res.string.settings_radio_same_decade) else stringResource(Res.string.settings_radio_any_decade),
        when (artistRunMode) {
            RadioArtistRunMode.Mixed -> stringResource(Res.string.settings_radio_mixed_artists)
            RadioArtistRunMode.SingleArtist -> stringResource(Res.string.settings_radio_single_artist)
            RadioArtistRunMode.ArtistBlocks -> stringResource(
                Res.string.settings_radio_artist_blocks,
                sameArtistRunLength,
                otherArtistRunLength,
            )
        },
    ).joinToString(" / ")

private fun radioDjIdFor(name: String, existing: List<RadioDjPreset>): String {
    val base = name.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "dj" }
    if (existing.none { it.id == base }) return base
    var index = 2
    while (existing.any { it.id == "$base-$index" }) {
        index += 1
    }
    return "$base-$index"
}

private fun Int.equalizerFrequencyLabel(): String =
    if (this >= 1_000) "${this / 1_000} kHz" else "$this Hz"

private fun Float.equalizerGainLabel(): String =
    if (this == 0f) "0 dB" else "%+.1f dB".format(this)

private fun Float.preampLabel(): String =
    when {
        this == 0f -> "0 dB"
        this % 1f == 0f -> "%+d dB".format(this.toInt())
        else -> "%+.1f dB".format(this)
    }

private val CrossfadeDurationOptions = listOf(0, 3, 5, 8, 12)
private val PreampDbOptions = listOf(6f, 5f, 4f, 3f, 2f, 1f, 0f, -1f, -2f, -3f, -4f, -5f, -6f, -9f, -12f)
private val SettingsRowHorizontalPadding = 14.dp
private val SettingsDetailRowVerticalPadding = 6.dp
private val SettingsCategoryTitleSize = 17.sp
private val SettingsDetailTitleSize = 16.sp
private val SettingsDetailSubtitleSize = 11.sp
private val SettingsDetailRowTitleSize = 14.sp
private val SettingsDetailRowSubtitleSize = 11.sp
private val SettingsDetailTinyTextSize = 10.sp
private val SettingsSectionTitleSize = 13.sp
private const val NewRadioDjId = "__new_radio_dj__"
private val PrefetchDepthOptions = listOf(0, 3, 5, 10, 15, 25)
private val WaveformBucketCountOptions = listOf(
    MinWaveformBucketCount,
    DefaultWaveformBucketCount,
    250,
    320,
    400,
    MaxWaveformBucketCount,
)
private val AudioCacheBudgetOptions = listOf(
    256L * 1024L * 1024L,
    512L * 1024L * 1024L,
    1L * 1024L * 1024L * 1024L,
    2L * 1024L * 1024L * 1024L,
    5L * 1024L * 1024L * 1024L,
    10L * 1024L * 1024L * 1024L,
    20L * 1024L * 1024L * 1024L,
)
private val DownloadBudgetOptions = listOf(
    512L * 1024L * 1024L,
    1L * 1024L * 1024L * 1024L,
    2L * 1024L * 1024L * 1024L,
    5L * 1024L * 1024L * 1024L,
    10L * 1024L * 1024L * 1024L,
    25L * 1024L * 1024L * 1024L,
    50L * 1024L * 1024L * 1024L,
    100L * 1024L * 1024L * 1024L,
)
