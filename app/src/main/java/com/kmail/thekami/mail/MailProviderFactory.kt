package com.kmail.thekami.mail

import com.kmail.thekami.data.repo.AccountRepository
import com.kmail.thekami.data.security.CredentialStore
import com.kmail.thekami.domain.mail.ConnectionTester
import com.kmail.thekami.domain.mail.MailProvider
import com.kmail.thekami.domain.mail.ProviderSource
import com.kmail.thekami.domain.model.AccountProvider
import com.kmail.thekami.mail.gmail.GmailApiClient
import com.kmail.thekami.mail.gmail.GmailIdMap
import com.kmail.thekami.mail.gmail.GmailMailProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the provider for an account: IMAP/SMTP (Angus) or the Gmail REST API. */
@Singleton
class MailProviderFactory @Inject constructor(
    private val accounts: AccountRepository,
    private val credentials: CredentialStore,
    private val tester: ConnectionTester,
    private val gmailApi: GmailApiClient,
    private val gmailIds: GmailIdMap,
) : ProviderSource {
    override suspend fun forAccount(accountId: String): MailProvider? =
        accounts.getAccount(accountId)?.let {
            when (it.provider) {
                AccountProvider.IMAP -> AngusMailProvider(it.id, it.settings, credentials, tester)
                AccountProvider.GMAIL -> GmailMailProvider(it.id, it.emailAddress, gmailApi, gmailIds)
            }
        }
}
