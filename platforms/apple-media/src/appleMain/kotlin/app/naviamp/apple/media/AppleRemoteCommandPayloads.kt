@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.apple.media

import platform.MediaPlayer.*

/** SDK property reads only. MediaPlayer creates event instances; tests supply explicit facts separately. */
interface AppleRemoteCommandPayloads {
    fun positionSeconds(event: MPRemoteCommandEvent?): Double?
    fun shuffleType(event: MPRemoteCommandEvent?): MPShuffleType?
    fun repeatType(event: MPRemoteCommandEvent?): MPRepeatType?
}

object AppleNativeRemoteCommandPayloads : AppleRemoteCommandPayloads {
    override fun positionSeconds(event: MPRemoteCommandEvent?) = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime
    override fun shuffleType(event: MPRemoteCommandEvent?) = (event as? MPChangeShuffleModeCommandEvent)?.shuffleType
    override fun repeatType(event: MPRemoteCommandEvent?) = (event as? MPChangeRepeatModeCommandEvent)?.repeatType
}
