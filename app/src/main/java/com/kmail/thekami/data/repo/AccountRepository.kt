package com.kmail.thekami.data.repo

import com.kmail.thekami.data.db.AccountDao
import com.kmail.thekami.data.db.toDomain
import com.kmail.thekami.data.db.toEntity
import com.kmail.thekami.data.security.CredentialStore
import com.kmail.thekami.data.security.SecretUnavailableException
import com.kmail.thekami.domain.model.Account
import com.kmail.thekami.domain.model.AccountCreator
import com.kmail.thekami.domain.model.AccountProvider
import com.kmail.thekami.domain.model.AccountSettings
import com.kmail.thekami.domain.model.AddAccountResult
import com.kmail.thekami.domain.model.AuthMethod
import com.kmail.thekami.domain.model.ConnectionSecurity
import com.kmail.thekami.domain.model.CredentialPurpose
import com.kmail.thekami.domain.model.NewAccount
import com.kmail.thekami.domain.model.ServerEndpoint
import com.kmail.thekami.domain.model.normalizeEmail
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val credentialStore: CredentialStore,
) : AccountCreator {
    fun observeAccounts(): Flow<List<Account>> =
        accountDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getAccount(id: String): Account? = accountDao.getById(id)?.toDomain()

    /** Saves the account and its encrypted passwords. Leaves nothing behind if saving fails. */
    override suspend fun addAccount(draft: NewAccount): AddAccountResult {
        val email = normalizeEmail(draft.emailAddress) ?: return AddAccountResult.InvalidEmail
        if (accountDao.findByEmail(email) != null) return AddAccountResult.AlreadyExists

        val account = Account(
            id = UUID.randomUUID().toString(),
            emailAddress = email,
            displayName = draft.displayName.trim().ifEmpty { email },
            colorArgb = draft.colorArgb,
            settings = draft.settings,
            createdAtMillis = System.currentTimeMillis(),
        )
        accountDao.insert(account.toEntity())

        var saved = false
        try {
            credentialStore.save(account.id, CredentialPurpose.IMAP, draft.imapPassword)
            credentialStore.save(account.id, CredentialPurpose.SMTP, draft.smtpPassword)
            saved = true
        } catch (e: SecretUnavailableException) {
            // reported through the result below
        } finally {
            if (!saved) withContext(NonCancellable) { accountDao.deleteById(account.id) }
        }
        return if (saved) AddAccountResult.Added(account) else AddAccountResult.CredentialStorageFailed
    }

    /**
     * Saves a Google account after authorization and Gmail access were validated by the caller.
     * Nothing secret is stored: access tokens are never persisted. The IMAP/SMTP endpoints are
     * unused placeholders required by the table layout.
     */
    suspend fun addGoogleAccount(emailAddress: String, colorArgb: Int): AddAccountResult {
        val email = normalizeEmail(emailAddress) ?: return AddAccountResult.InvalidEmail
        if (accountDao.findByEmail(email) != null) return AddAccountResult.AlreadyExists
        val account = Account(
            id = UUID.randomUUID().toString(),
            emailAddress = email,
            displayName = email,
            colorArgb = colorArgb,
            settings = AccountSettings(
                imap = ServerEndpoint("imap.gmail.com", 993, ConnectionSecurity.SSL_TLS, AuthMethod.NORMAL_PASSWORD, email),
                smtp = ServerEndpoint("smtp.gmail.com", 465, ConnectionSecurity.SSL_TLS, AuthMethod.NORMAL_PASSWORD, email),
            ),
            createdAtMillis = System.currentTimeMillis(),
            provider = AccountProvider.GMAIL,
        )
        accountDao.insert(account.toEntity())
        return AddAccountResult.Added(account)
    }

    /** True when [emailAddress] is already connected through Google (used to allow re-authorization). */
    suspend fun isGoogleAccount(emailAddress: String): Boolean {
        val email = normalizeEmail(emailAddress) ?: return false
        return accountDao.findByEmail(email)?.providerType == AccountProvider.GMAIL
    }

    /** Removes the account, its secrets and (by cascade) its cached folders, messages and Gmail id map. */
    suspend fun removeAccount(id: String) {
        credentialStore.delete(id)
        accountDao.deleteById(id)
    }
}
