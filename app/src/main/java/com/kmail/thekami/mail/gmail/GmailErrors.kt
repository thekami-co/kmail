package com.kmail.thekami.mail.gmail

import com.kmail.thekami.domain.mail.MailError
import org.json.JSONException
import org.json.JSONObject

internal sealed interface Outcome {
    data object Ok : Outcome
    data object Reauthorize : Outcome
    data class Retry(val retryAfterSeconds: Long?) : Outcome
    data class Fail(val error: MailError) : Outcome
}

/** Maps Gmail HTTP replies to outcomes. Error details only ever contain a short reason code. */
internal object GmailErrors {
    fun classify(code: Int, body: ByteArray, retryAfterSeconds: Long?): Outcome = when {
        code in 200..299 -> Outcome.Ok
        code == 401 -> Outcome.Reauthorize
        code == 429 || code in 500..599 -> Outcome.Retry(retryAfterSeconds)
        code == 403 -> {
            val reason = reason(body)
            if (reason == "rateLimitExceeded" || reason == "userRateLimitExceeded" || reason == "backendError") {
                Outcome.Retry(retryAfterSeconds)
            } else {
                Outcome.Fail(MailError.Authentication("forbidden_$reason"))
            }
        }
        code == 404 -> Outcome.Fail(MailError.Protocol("not_found"))
        code == 400 -> Outcome.Fail(MailError.Protocol("bad_request"))
        else -> Outcome.Fail(MailError.Protocol("http_$code"))
    }

    fun reason(body: ByteArray): String = try {
        val error = JSONObject(String(body, Charsets.UTF_8)).optJSONObject("error")
        val raw = error?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
        raw.filter { it.isLetter() }.take(40).ifEmpty { "unknown" }
    } catch (e: JSONException) {
        "unknown"
    }
}
