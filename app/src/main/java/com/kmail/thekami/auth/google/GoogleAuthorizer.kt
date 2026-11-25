package com.kmail.thekami.auth.google

import android.app.PendingIntent
import android.content.Intent

enum class GoogleAuthFailure { CANCELLED, DENIED, NETWORK, PLAY_SERVICES_UNAVAILABLE, MISCONFIGURED, UNKNOWN }

sealed interface GoogleAuthResult {
    /** The token is short-lived. It is deliberately hidden from [toString]. */
    class Granted(val accessToken: String) : GoogleAuthResult {
        override fun toString() = "Granted(token=***)"
    }

    /** The user must approve access in a Google window: launch [pendingIntent] and pass the result back. */
    class NeedsConsent(val pendingIntent: PendingIntent) : GoogleAuthResult

    class Failed(val failure: GoogleAuthFailure) : GoogleAuthResult
}

/** Google Identity Services authorization for the `gmail.modify` scope. No WebView, no client secret. */
interface GoogleAuthorizer {
    suspend fun authorize(email: String): GoogleAuthResult

    /** Reads the result of the consent window launched from [GoogleAuthResult.NeedsConsent]. */
    fun resultFromConsent(email: String, data: Intent?): GoogleAuthResult

    /** Revokes Kmail's Google access for [email]. Returns false if Google could not be reached. */
    suspend fun revoke(email: String): Boolean
}
