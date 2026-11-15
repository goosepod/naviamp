package app.naviamp.ios.playback

import app.naviamp.domain.audio.AudioTagReader
import app.naviamp.storage.AppleAudioTagReader

/** Original host API delegates to the shared POSIX file-read effect. */
class IosAudioTagReader : AudioTagReader by AppleAudioTagReader()
