package app.naviamp.app

import app.naviamp.domain.provider.ProviderMediaByteResponse

/** Shared provider byte source; the host never receives a provider URL or chooses media identity. */
interface NaviampCastMediaByteSource {
    suspend fun stream(
        resource: NaviampCastMediaResource,
        range: NaviampCastRequestedRange?,
        headOnly: Boolean,
        onResponse: suspend (ProviderMediaByteResponse) -> Unit,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean
}
