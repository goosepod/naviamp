package app.naviamp.app

/** Shared receiver identity for native-protocol senders and Cast SDK adapters. */
object NaviampCastReceiver {
    // Google's public Default Media Receiver works without developer device registration.
    // Naviamp branding (C0A3069A) is deferred until its receiver is published and verified (#217).
    const val ApplicationId = "CC1AD845"
}
