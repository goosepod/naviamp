package app.naviamp.ui

import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.voice_ambiguous
import app.naviamp.ui.generated.resources.voice_no_match
import app.naviamp.ui.generated.resources.voice_no_source
import app.naviamp.ui.generated.resources.voice_playback_failed
import app.naviamp.ui.generated.resources.voice_unsupported
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

enum class NaviampVoiceFailure { NoSource, NoMatch, Ambiguous, Unsupported, PlaybackFailed }

fun naviampVoiceMessageResource(failure: NaviampVoiceFailure): StringResource = when (failure) {
    NaviampVoiceFailure.NoSource -> Res.string.voice_no_source
    NaviampVoiceFailure.NoMatch -> Res.string.voice_no_match
    NaviampVoiceFailure.Ambiguous -> Res.string.voice_ambiguous
    NaviampVoiceFailure.Unsupported -> Res.string.voice_unsupported
    NaviampVoiceFailure.PlaybackFailed -> Res.string.voice_playback_failed
}

suspend fun naviampVoiceMessage(failure: NaviampVoiceFailure): String =
    getString(naviampVoiceMessageResource(failure))
