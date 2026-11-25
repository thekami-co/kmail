package com.kmail.thekami.ui.setup

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmail.thekami.auth.google.GoogleAuthFailure
import com.kmail.thekami.auth.google.GoogleAuthResult
import com.kmail.thekami.auth.google.GoogleAuthorizer
import com.kmail.thekami.data.repo.AccountRepository
import com.kmail.thekami.domain.mail.MailError
import com.kmail.thekami.domain.mail.MailResult
import com.kmail.thekami.domain.model.AddAccountResult
import com.kmail.thekami.domain.model.normalizeEmail
import com.kmail.thekami.mail.gmail.GmailApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GoogleFailure {
    CANCELLED, DENIED, NETWORK, PLAY_SERVICES, MISCONFIGURED, API_DISABLED, ALREADY_EXISTS, MISMATCH, UNKNOWN,
}

sealed interface GoogleConnectState {
    data object Idle : GoogleConnectState
    data class PickingAccount(val launched: Boolean) : GoogleConnectState
    data class Authorizing(val email: String) : GoogleConnectState
    data class AwaitingConsent(val email: String, val pendingIntent: PendingIntent, val launched: Boolean) : GoogleConnectState
    data class Validating(val email: String) : GoogleConnectState
    data class Connected(val email: String) : GoogleConnectState
    data class Failed(val failure: GoogleFailure) : GoogleConnectState
}

private val ACCOUNT_COLORS = intArrayOf(
    0xFF6750A4.toInt(), 0xFF00796B.toInt(), 0xFFC2185B.toInt(),
    0xFFEF6C00.toInt(), 0xFF1565C0.toInt(), 0xFF558B2F.toInt(),
)

/**
 * Account chooser, then Google authorization, then a Gmail profile check, then saving.
 * An account is shown as connected only after all of that succeeded. Only one flow runs at a time:
 * every step checks the current state first.
 */
@HiltViewModel
class GoogleConnectViewModel @Inject constructor(
    private val authorizer: GoogleAuthorizer,
    private val api: GmailApiClient,
    private val accounts: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<GoogleConnectState>(GoogleConnectState.Idle)
    val state: StateFlow<GoogleConnectState> = _state.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (_state.value is GoogleConnectState.Idle) _state.value = GoogleConnectState.PickingAccount(false)
    }

    fun retry() {
        if (_state.value is GoogleConnectState.Failed) _state.value = GoogleConnectState.PickingAccount(false)
    }

    fun chooserLaunched() = _state.update { if (it is GoogleConnectState.PickingAccount) it.copy(launched = true) else it }

    fun consentLaunched() = _state.update { if (it is GoogleConnectState.AwaitingConsent) it.copy(launched = true) else it }

    fun onLaunchFailed() {
        if (_state.value is GoogleConnectState.PickingAccount || _state.value is GoogleConnectState.AwaitingConsent) {
            _state.value = GoogleConnectState.Failed(GoogleFailure.UNKNOWN)
        }
    }

    fun onAccountChosen(name: String?) {
        if (_state.value !is GoogleConnectState.PickingAccount) return
        val email = name?.let(::normalizeEmail)
        if (email == null) {
            _state.value = GoogleConnectState.Failed(GoogleFailure.CANCELLED)
            return
        }
        _state.value = GoogleConnectState.Authorizing(email)
        job = viewModelScope.launch { handle(email, authorizer.authorize(email)) }
    }

    fun onConsentResult(ok: Boolean, data: Intent?) {
        val current = _state.value as? GoogleConnectState.AwaitingConsent ?: return
        if (!ok) {
            _state.value = GoogleConnectState.Failed(GoogleFailure.CANCELLED)
            return
        }
        _state.value = GoogleConnectState.Authorizing(current.email)
        job = viewModelScope.launch { handle(current.email, authorizer.resultFromConsent(current.email, data)) }
    }

    /** Back pressed while the flow runs. */
    fun cancel() {
        job?.cancel()
        job = null
        if (_state.value !is GoogleConnectState.Connected) _state.value = GoogleConnectState.Idle
    }

    private suspend fun handle(email: String, result: GoogleAuthResult) {
        when (result) {
            is GoogleAuthResult.Granted -> validateAndSave(email)
            is GoogleAuthResult.NeedsConsent -> _state.value = GoogleConnectState.AwaitingConsent(email, result.pendingIntent, false)
            is GoogleAuthResult.Failed -> _state.value = GoogleConnectState.Failed(result.failure.toUi())
        }
    }

    private suspend fun validateAndSave(email: String) {
        _state.value = GoogleConnectState.Validating(email)
        when (val profile = api.json(email, "GET", "profile", listOf("fields" to "emailAddress"))) {
            is MailResult.Failure -> {
                _state.value = GoogleConnectState.Failed(profile.error.toUi())
                return
            }
            is MailResult.Success -> {
                if (!profile.value.optString("emailAddress").equals(email, ignoreCase = true)) {
                    _state.value = GoogleConnectState.Failed(GoogleFailure.MISMATCH)
                    return
                }
            }
        }
        val color = ACCOUNT_COLORS[(email.hashCode() and Int.MAX_VALUE) % ACCOUNT_COLORS.size]
        _state.value = when (accounts.addGoogleAccount(email, color)) {
            is AddAccountResult.Added -> GoogleConnectState.Connected(email)
            // Connecting an existing Google account again is how access is re-authorized.
            AddAccountResult.AlreadyExists ->
                if (accounts.isGoogleAccount(email)) GoogleConnectState.Connected(email)
                else GoogleConnectState.Failed(GoogleFailure.ALREADY_EXISTS)
            AddAccountResult.InvalidEmail, AddAccountResult.CredentialStorageFailed ->
                GoogleConnectState.Failed(GoogleFailure.UNKNOWN)
        }
    }

    private fun GoogleAuthFailure.toUi() = when (this) {
        GoogleAuthFailure.CANCELLED -> GoogleFailure.CANCELLED
        GoogleAuthFailure.DENIED -> GoogleFailure.DENIED
        GoogleAuthFailure.NETWORK -> GoogleFailure.NETWORK
        GoogleAuthFailure.PLAY_SERVICES_UNAVAILABLE -> GoogleFailure.PLAY_SERVICES
        GoogleAuthFailure.MISCONFIGURED -> GoogleFailure.MISCONFIGURED
        GoogleAuthFailure.UNKNOWN -> GoogleFailure.UNKNOWN
    }

    private fun MailError.toUi() = when (this) {
        is MailError.Authentication ->
            if (detail?.contains("accessNotConfigured") == true) GoogleFailure.API_DISABLED else GoogleFailure.DENIED
        is MailError.Network, is MailError.Timeout, is MailError.Tls -> GoogleFailure.NETWORK
        else -> GoogleFailure.UNKNOWN
    }

    override fun onCleared() {
        job?.cancel()
    }
}
