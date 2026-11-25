package com.kmail.thekami.domain.model

data class AccountSettings(
    val imap: ServerEndpoint,
    val smtp: ServerEndpoint,
)

/** How Kmail talks to a mailbox. [GMAIL] uses the Gmail REST API; its server settings are unused placeholders. */
enum class AccountProvider { IMAP, GMAIL }

data class Account(
    val id: String,
    val emailAddress: String,
    val displayName: String,
    val colorArgb: Int,
    val settings: AccountSettings,
    val createdAtMillis: Long,
    val provider: AccountProvider = AccountProvider.IMAP,
)
