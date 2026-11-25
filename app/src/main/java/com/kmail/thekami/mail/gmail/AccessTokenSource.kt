package com.kmail.thekami.mail.gmail

import com.kmail.thekami.domain.mail.MailResult

/** Supplies a currently valid Gmail access token. Tokens are short-lived and never persisted. */
interface AccessTokenSource {
    suspend fun accessToken(email: String): MailResult<String>

    /** Forgets a token the server rejected, so the next call asks Google again. */
    fun drop(email: String)
}
