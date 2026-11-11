package app.naviamp.ui

import androidx.compose.runtime.Composable
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun List<NaviampStreamDetailSection>.localizedStreamDetailSections(): List<NaviampDetailSectionUi> = map { section ->
    NaviampDetailSectionUi(section.label.streamDetailLabel(), section.rows.map { row ->
        val value = when {
            row.embeddedPicture -> stringResource(Res.string.stream_details_embedded_artwork)
            row.label == NaviampStreamDetailLabel.Bitrate -> row.value.toIntOrNull()
                ?.let { stringResource(Res.string.settings_quality_kbps, it) } ?: row.value
            else -> row.value
        }
        val displayValue = value.replace("<redacted>", "<${stringResource(Res.string.stream_details_redacted)}>")
            .replace("<unparseable url>", stringResource(Res.string.stream_details_unavailable_url))
        (row.label?.streamDetailLabel() ?: row.metadataKey.orEmpty()) to displayValue
    })
}

@Composable
private fun NaviampStreamDetailLabel.streamDetailLabel(): String = stringResource(when (this) {
    NaviampStreamDetailLabel.Station -> Res.string.stream_details_station
    NaviampStreamDetailLabel.Broadcast -> Res.string.stream_details_broadcast
    NaviampStreamDetailLabel.Audio -> Res.string.settings_about_audio_title
    NaviampStreamDetailLabel.Metadata -> Res.string.stream_details_metadata
    NaviampStreamDetailLabel.Name -> Res.string.favorite_artists_sort_name
    NaviampStreamDetailLabel.StreamUrl -> Res.string.connection_stream_url
    NaviampStreamDetailLabel.HomePageUrl -> Res.string.connection_home_page_url
    NaviampStreamDetailLabel.Title -> Res.string.tv_album_sort_title
    NaviampStreamDetailLabel.Artist -> Res.string.tv_artist
    NaviampStreamDetailLabel.Album -> Res.string.tv_album
    NaviampStreamDetailLabel.Genre -> Res.string.stream_details_genre
    NaviampStreamDetailLabel.Description -> Res.string.stream_details_description
    NaviampStreamDetailLabel.ArtworkUrl -> Res.string.stream_details_artwork_url
    NaviampStreamDetailLabel.Codec -> Res.string.settings_quality_codec_title
    NaviampStreamDetailLabel.Bitrate -> Res.string.settings_quality_bitrate_title
    NaviampStreamDetailLabel.SampleRate -> Res.string.stream_details_sample_rate
    NaviampStreamDetailLabel.Channels -> Res.string.stream_details_channels
    NaviampStreamDetailLabel.ContentType -> Res.string.stream_details_content_type
})
