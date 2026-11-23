package app.naviamp.domain.network

/**
 * The operating system's billing classification of the active network. Hosts report only this
 * fact; Core decides what each state means. It is a device-local observation and is never synced.
 */
enum class NetworkMetering {
    Unmetered,
    Metered,
    Unknown,
}

/** Host effect for the OS metering state of the active network; absent when a host cannot observe it. */
fun interface NetworkMeteringSource {
    fun current(): NetworkMetering
}
