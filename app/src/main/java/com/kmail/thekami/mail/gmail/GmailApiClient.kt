package com.kmail.thekami.mail.gmail

import com.kmail.thekami.domain.mail.MailError
import com.kmail.thekami.domain.mail.MailResult
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONException
import org.json.JSONObject

/**
 * Authorized access to the Gmail REST API: one valid token per call, one retry after a 401,
 * backoff on rate limits and server errors. No tokens, addresses or message content are logged.
 */
@Singleton
class GmailApiClient @Inject constructor(
    private val tokens: AccessTokenSource,
    private val http: GmailHttp,
) {
    /** [path] is relative to `users/me/`. */
    suspend fun json(
        email: String,
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: JSONObject? = null,
    ): MailResult<JSONObject> {
        val bytes = body?.toString()?.toByteArray(Charsets.UTF_8)
        val reply = call(email, method, buildUrl(path, query), bytes, if (bytes != null) JSON_TYPE else null)
        return when (reply) {
            is MailResult.Failure -> reply
            is MailResult.Success -> try {
                MailResult.Success(JSONObject(if (reply.value.isEmpty()) "{}" else String(reply.value, Charsets.UTF_8)))
            } catch (e: JSONException) {
                MailResult.Failure(MailError.Protocol("bad_json"))
            }
        }
    }

    /** For absolute URLs such as the media upload endpoint. */
    suspend fun rawCall(
        email: String,
        method: String,
        absoluteUrl: String,
        body: ByteArray?,
        contentType: String?,
    ): MailResult<ByteArray> = call(email, method, absoluteUrl, body, contentType)

    private suspend fun call(
        email: String,
        method: String,
        url: String,
        body: ByteArray?,
        contentType: String?,
    ): MailResult<ByteArray> {
        var attempt = 0
        var reauthorized = false
        while (true) {
            val token = when (val t = tokens.accessToken(email)) {
                is MailResult.Failure -> return t
                is MailResult.Success -> t.value
            }
            val reply = try {
                http.execute(method, url, token, body, contentType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                attempt++
                if (attempt >= MAX_ATTEMPTS) return MailResult.Failure(e.toGmailError())
                delay(backoffMillis(attempt, null))
                continue
            }
            when (val outcome = GmailErrors.classify(reply.code, reply.body, reply.retryAfterSeconds)) {
                Outcome.Ok -> return MailResult.Success(reply.body)
                Outcome.Reauthorize -> {
                    if (reauthorized) return MailResult.Failure(MailError.Authentication("unauthorized"))
                    reauthorized = true
                    tokens.drop(email)
                }
                is Outcome.Retry -> {
                    attempt++
                    if (attempt >= MAX_ATTEMPTS) return MailResult.Failure(MailError.Network("rate_limited_or_server_error"))
                    delay(backoffMillis(attempt, outcome.retryAfterSeconds))
                }
                is Outcome.Fail -> return MailResult.Failure(outcome.error)
            }
        }
    }

    private fun IOException.toGmailError(): MailError = when (this) {
        is SocketTimeoutException -> MailError.Timeout(javaClass.simpleName)
        is SSLException -> MailError.Tls(javaClass.simpleName)
        else -> MailError.Network(javaClass.simpleName)
    }

    companion object {
        const val BASE = "https://gmail.googleapis.com/gmail/v1/users/me/"
        const val SEND_UPLOAD_URL = "https://gmail.googleapis.com/upload/gmail/v1/users/me/messages/send?uploadType=media"
        private const val JSON_TYPE = "application/json; charset=UTF-8"
        private const val MAX_ATTEMPTS = 4

        internal fun backoffMillis(attempt: Int, retryAfterSeconds: Long?): Long =
            if (retryAfterSeconds != null) retryAfterSeconds.coerceIn(1, 30) * 1000 else 500L shl (attempt - 1)

        internal fun buildUrl(path: String, query: List<Pair<String, String>>): String {
            val sb = StringBuilder(BASE).append(path)
            query.forEachIndexed { i, (k, v) ->
                sb.append(if (i == 0) '?' else '&')
                    .append(URLEncoder.encode(k, "UTF-8")).append('=').append(URLEncoder.encode(v, "UTF-8"))
            }
            return sb.toString()
        }
    }
}
