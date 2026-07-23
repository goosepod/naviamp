package app.naviamp.app

import app.naviamp.domain.radio.recentRadioStreamsWith
import app.naviamp.domain.settings.RecentRadioStream

/** Shared application owner for durable recent-radio updates across UI and service lifetimes. */
class NaviampRecentRadioStreamController(
    private val load: () -> List<RecentRadioStream>,
    private val save: (List<RecentRadioStream>) -> Unit,
    private val onChanged: () -> Unit = {},
) {
    fun current(): List<RecentRadioStream> = load()

    fun remember(stream: RecentRadioStream): List<RecentRadioStream> =
        recentRadioStreamsWith(load(), stream).also { updated ->
            save(updated)
            onChanged()
        }
}
