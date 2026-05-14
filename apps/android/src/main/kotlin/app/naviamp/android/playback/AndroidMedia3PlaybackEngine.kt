package app.naviamp.android.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class AndroidMedia3PlaybackEngine(
    context: Context,
) : PlaybackEngine {
    private val appContext = context.applicationContext
    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Naviamp Android")
        .setAllowCrossProtocolRedirects(true)
    private val player = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
        .also { it.setSeekParameters(SeekParameters.EXACT) }
    private val mediaSession = MediaSession.Builder(appContext, player).build()
    private var progressJob: Job? = null
    private var onStateChanged: ((PlaybackState) -> Unit)? = null
    private var onProgressChanged: ((PlaybackProgress) -> Unit)? = null
    private var onMetadataChanged: ((PlaybackStreamMetadata) -> Unit)? = null
    private var tlsSettings: NavidromeTlsSettings = NavidromeTlsSettings()
    private var notificationMetadata = AndroidPlaybackNotificationMetadata()

    override val name: String = "Media3"
    override val supportsPause: Boolean = true
    override val supportsSeek: Boolean = true
    override val supportsGapless: Boolean = true
    override val supportsCrossfade: Boolean = false
    override val supportsReplayGain: Boolean = false
    override val supportsSoftwareVolume: Boolean = true
    override val prefersOriginalStream: Boolean = false

    fun applyTlsSettings(tlsSettings: NavidromeTlsSettings) {
        this.tlsSettings = tlsSettings
        AndroidPlaybackTls.applyDefaults(tlsSettings)
    }

    fun updateNotificationMetadata(
        title: String?,
        subtitle: String?,
        coverArtUrl: String?,
    ) {
        notificationMetadata = AndroidPlaybackNotificationMetadata(
            title = title,
            subtitle = subtitle,
            coverArtUrl = coverArtUrl,
        )
        AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
    }

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    publishPlayerState()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    publishPlayerState()
                }

                override fun onPlayerError(error: PlaybackException) {
                    val cause = error.cause?.message
                    onStateChanged?.invoke(PlaybackState.Error(cause ?: error.message ?: "Playback failed."))
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    onMetadataChanged?.invoke(
                        PlaybackStreamMetadata.fromProperties(
                            properties = mediaMetadata.toStreamProperties(),
                            fallbackTitle = mediaMetadata.title?.toString(),
                        ),
                    )
                }

                override fun onMetadata(metadata: Metadata) {
                    val properties = metadata.toStreamProperties()
                    if (properties.isNotEmpty()) {
                        onMetadataChanged?.invoke(PlaybackStreamMetadata.fromProperties(properties))
                    }
                }
            },
        )
    }

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        this.onStateChanged = onStateChanged
        this.onProgressChanged = onProgressChanged
        this.onMetadataChanged = onMetadataChanged
        onStateChanged(PlaybackState.Loading)
        onProgressChanged(PlaybackProgress.Unknown)

        AndroidPlaybackTls.applyDefaults(tlsSettings)
        AndroidPlaybackNotificationControls.isPlaying = true
        AndroidPlaybackForegroundService.start(appContext, notificationMetadata)
        player.setMediaItem(MediaItem.fromUri(request.url))
        player.prepare()
        request.startPositionSeconds?.let { player.seekTo((it * 1000).toLong().coerceAtLeast(0L)) }
        player.play()
        startProgressPolling(scope)
    }

    override fun pause() {
        player.pause()
        AndroidPlaybackNotificationControls.isPlaying = false
        AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
    }

    override fun resume() {
        player.play()
        AndroidPlaybackNotificationControls.isPlaying = true
        AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
    }

    override fun seek(positionSeconds: Double) {
        player.seekTo((positionSeconds * 1000).toLong().coerceAtLeast(0L))
    }

    override fun setVolume(percent: Int) {
        player.volume = percent.coerceIn(0, 100) / 100f
    }

    override fun stop() {
        progressJob?.cancel()
        progressJob = null
        AndroidPlaybackNotificationControls.isPlaying = false
        AndroidPlaybackForegroundService.stop(appContext)
        player.stop()
        player.clearMediaItems()
        onProgressChanged?.invoke(PlaybackProgress.Unknown)
        onStateChanged?.invoke(PlaybackState.Stopped)
    }

    fun release() {
        progressJob?.cancel()
        progressJob = null
        AndroidPlaybackNotificationControls.clear()
        AndroidPlaybackForegroundService.stop(appContext)
        mediaSession.release()
        player.release()
        onStateChanged = null
        onProgressChanged = null
        onMetadataChanged = null
    }

    private fun startProgressPolling(scope: CoroutineScope) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                onProgressChanged?.invoke(player.toPlaybackProgress())
                delay(500)
            }
        }
    }

    private fun publishPlayerState() {
        val state = when (player.playbackState) {
            Player.STATE_BUFFERING -> PlaybackState.Loading
            Player.STATE_READY -> if (player.isPlaying) PlaybackState.Playing else PlaybackState.Paused
            Player.STATE_ENDED -> PlaybackState.Finished
            Player.STATE_IDLE -> PlaybackState.Idle
            else -> PlaybackState.Idle
        }
        AndroidPlaybackNotificationControls.isPlaying = state == PlaybackState.Playing
        if (state == PlaybackState.Playing || state == PlaybackState.Paused || state == PlaybackState.Loading) {
            AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
        }
        onStateChanged?.invoke(state)
    }

    private fun ExoPlayer.toPlaybackProgress(): PlaybackProgress {
        val knownDuration = duration.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET }
        return PlaybackProgress(
            positionSeconds = currentPosition.coerceAtLeast(0L) / 1000.0,
            durationSeconds = knownDuration?.let { it / 1000.0 },
        )
    }
}

private fun MediaMetadata.toStreamProperties(): Map<String, String> =
    buildMap<String, String> {
        title?.toString()?.takeIf { it.isNotBlank() }?.let { put("title", it) }
        artist?.toString()?.takeIf { it.isNotBlank() }?.let { put("artist", it) }
        albumTitle?.toString()?.takeIf { it.isNotBlank() }?.let { put("album", it) }
        station?.toString()?.takeIf { it.isNotBlank() }?.let { put("station", it) }
    }

private fun Metadata.toStreamProperties(): Map<String, String> =
    buildMap<String, String> {
        for (index in 0 until length()) {
            when (val entry = this@toStreamProperties[index]) {
                is IcyInfo -> {
                    entry.title?.takeIf { it.isNotBlank() }?.let { put("icy-title", it) }
                    entry.url?.takeIf { it.isNotBlank() }?.let { put("icy-url", it) }
                }
            }
        }
    }

private object AndroidPlaybackTls {
    private val platformSslContext: SSLContext = SSLContext.getDefault()
    private val platformHostnameVerifier: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

    fun applyDefaults(tlsSettings: NavidromeTlsSettings) {
        if (tlsSettings == NavidromeTlsSettings()) {
            SSLContext.setDefault(platformSslContext)
            HttpsURLConnection.setDefaultSSLSocketFactory(platformSslContext.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier(platformHostnameVerifier)
            return
        }

        val sslContext = createSslContext(tlsSettings)
        SSLContext.setDefault(sslContext)
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier(
            if (tlsSettings.insecureSkipTlsVerification) {
                HostnameVerifier { _, _ -> true }
            } else {
                platformHostnameVerifier
            },
        )
    }

    private fun createSslContext(tlsSettings: NavidromeTlsSettings): SSLContext {
        val context = SSLContext.getInstance("TLS")
        context.init(
            tlsSettings.keyManagers(),
            tlsSettings.trustManagers(),
            SecureRandom(),
        )
        return context
    }

    private fun NavidromeTlsSettings.trustManagers(): Array<TrustManager>? =
        when {
            insecureSkipTlsVerification -> arrayOf(TrustAllCertificates)
            hasCustomCertificate -> {
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                    FileInputStream(customCertificatePath!!).use { input ->
                        CertificateFactory.getInstance("X.509").generateCertificates(input)
                            .forEachIndexed { index, certificate ->
                                setCertificateEntry("naviamp-playback-$index", certificate)
                            }
                    }
                }
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
                    init(keyStore)
                    trustManagers
                }
            }
            else -> null
        }

    private fun NavidromeTlsSettings.keyManagers(): Array<KeyManager>? {
        if (!hasClientCertificate) return null
        val password = clientCertificateKeyStorePassword.orEmpty().toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            FileInputStream(clientCertificateKeyStorePath!!).use { input ->
                load(input, password)
            }
        }
        return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).run {
            init(keyStore, password)
            keyManagers
        }
    }

    private object TrustAllCertificates : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
    }
}
