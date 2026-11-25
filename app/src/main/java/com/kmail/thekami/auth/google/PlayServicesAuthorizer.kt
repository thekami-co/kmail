package com.kmail.thekami.auth.google

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.kmail.thekami.domain.mail.MailError
import com.kmail.thekami.domain.mail.MailResult
import com.kmail.thekami.mail.gmail.AccessTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Uses the official AuthorizationClient. The Android OAuth client is identified by package name and
 * signing certificate, so no client id or secret appears in code. Access tokens are short-lived and
 * there is no refresh token: every call asks Google again (silently once consent was given). Tokens are
 * kept in memory for a few minutes only and are never written to disk.
 */
@Singleton
class PlayServicesAuthorizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : GoogleAuthorizer, AccessTokenSource {

    private class Cached(val token: String, val atMillis: Long)

    private class TaskCanceledException : Exception()

    private val cache = ConcurrentHashMap<String, Cached>()

    private fun client() = Identity.getAuthorizationClient(context)

    private fun account(email: String) = Account(email, GOOGLE_ACCOUNT_TYPE)

    private fun request(email: String) = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(GMAIL_MODIFY)))
        .setAccount(account(email))
        .build()

    override suspend fun authorize(email: String): GoogleAuthResult = try {
        toResult(email, client().authorize(request(email)).await())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        GoogleAuthResult.Failed(classify(e.statusCode))
    } catch (e: Exception) {
        GoogleAuthResult.Failed(GoogleAuthFailure.UNKNOWN)
    }

    override fun resultFromConsent(email: String, data: Intent?): GoogleAuthResult = try {
        toResult(email, client().getAuthorizationResultFromIntent(data))
    } catch (e: ApiException) {
        GoogleAuthResult.Failed(classify(e.statusCode))
    } catch (e: Exception) {
        GoogleAuthResult.Failed(GoogleAuthFailure.UNKNOWN)
    }

    override suspend fun revoke(email: String): Boolean {
        cache.remove(email.lowercase())
        return try {
            val request = RevokeAccessRequest.builder()
                .setAccount(account(email))
                .setScopes(listOf(Scope(GMAIL_MODIFY)))
                .build()
            client().revokeAccess(request).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun accessToken(email: String): MailResult<String> {
        cache[email.lowercase()]?.let {
            if (System.currentTimeMillis() - it.atMillis < CACHE_MILLIS) return MailResult.Success(it.token)
        }
        return when (val r = authorize(email)) {
            is GoogleAuthResult.Granted -> MailResult.Success(r.accessToken)
            is GoogleAuthResult.NeedsConsent -> MailResult.Failure(MailError.Authentication("reauthorization_required"))
            is GoogleAuthResult.Failed -> MailResult.Failure(
                when (r.failure) {
                    GoogleAuthFailure.NETWORK -> MailError.Network("google_auth")
                    GoogleAuthFailure.PLAY_SERVICES_UNAVAILABLE -> MailError.Unsupported("play_services")
                    else -> MailError.Authentication("google_" + r.failure.name.lowercase())
                },
            )
        }
    }

    override fun drop(email: String) {
        cache.remove(email.lowercase())
    }

    private fun toResult(email: String, result: AuthorizationResult): GoogleAuthResult {
        if (result.hasResolution()) {
            val pending = result.pendingIntent ?: return GoogleAuthResult.Failed(GoogleAuthFailure.UNKNOWN)
            return GoogleAuthResult.NeedsConsent(pending)
        }
        val token = result.accessToken ?: return GoogleAuthResult.Failed(GoogleAuthFailure.DENIED)
        // The user may untick the Gmail permission on the consent screen.
        if (GMAIL_MODIFY !in result.grantedScopes) return GoogleAuthResult.Failed(GoogleAuthFailure.DENIED)
        cache[email.lowercase()] = Cached(token, System.currentTimeMillis())
        return GoogleAuthResult.Granted(token)
    }

    private fun classify(status: Int): GoogleAuthFailure = when (status) {
        CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> GoogleAuthFailure.NETWORK
        CommonStatusCodes.CANCELED, SIGN_IN_CANCELLED -> GoogleAuthFailure.CANCELLED
        CommonStatusCodes.DEVELOPER_ERROR -> GoogleAuthFailure.MISCONFIGURED
        CommonStatusCodes.API_NOT_CONNECTED,
        ConnectionResult.SERVICE_MISSING,
        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
        ConnectionResult.SERVICE_DISABLED,
        -> GoogleAuthFailure.PLAY_SERVICES_UNAVAILABLE
        else -> GoogleAuthFailure.UNKNOWN
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
        addOnCanceledListener { c.resumeWithException(TaskCanceledException()) }
    }

    private companion object {
        const val GMAIL_MODIFY = "https://www.googleapis.com/auth/gmail.modify"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        const val SIGN_IN_CANCELLED = 12501
        const val CACHE_MILLIS = 5 * 60 * 1000L
    }
}
