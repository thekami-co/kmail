package com.kmail.thekami.ui.setup

import android.accounts.AccountManager
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.IntentSender
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmail.thekami.R
import com.kmail.thekami.ui.components.ErrorState
import com.kmail.thekami.ui.components.LoadingState

@Composable
fun GoogleConnectScreen(
    onOpenInbox: () -> Unit,
    onAddAnother: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: GoogleConnectViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.onAccountChosen(
            if (result.resultCode == Activity.RESULT_OK) result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) else null,
        )
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.onConsentResult(result.resultCode == Activity.RESULT_OK, result.data)
    }

    LaunchedEffect(Unit) { vm.start() }

    // The launched flag stops a rotation from opening the same window twice.
    LaunchedEffect(state) {
        when (val s = state) {
            is GoogleConnectState.PickingAccount -> if (!s.launched) {
                vm.chooserLaunched()
                try {
                    chooser.launch(
                        AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null),
                    )
                } catch (e: ActivityNotFoundException) {
                    vm.onLaunchFailed()
                }
            }
            is GoogleConnectState.AwaitingConsent -> if (!s.launched) {
                vm.consentLaunched()
                try {
                    consent.launch(IntentSenderRequest.Builder(s.pendingIntent.intentSender).build())
                } catch (e: IntentSender.SendIntentException) {
                    vm.onLaunchFailed()
                }
            }
            else -> Unit
        }
    }

    when (val s = state) {
        is GoogleConnectState.Connected -> {
            BackHandler { onOpenInbox() }
            ConnectedScreen(
                emailAddress = s.email,
                onOpenInbox = onOpenInbox,
                onAddAnother = onAddAnother,
                modifier = modifier,
            )
        }
        is GoogleConnectState.Failed -> ErrorState(
            title = stringResource(R.string.google_error_title),
            message = stringResource(s.failure.messageRes()),
            retryLabel = stringResource(R.string.google_retry),
            onRetry = vm::retry,
            modifier = modifier,
        )
        else -> {
            BackHandler {
                vm.cancel()
                onBack()
            }
            LoadingState(message = stringResource(s.progressRes()), modifier = modifier)
        }
    }
}

@StringRes
private fun GoogleConnectState.progressRes(): Int = when (this) {
    is GoogleConnectState.AwaitingConsent -> R.string.google_connect_consent
    is GoogleConnectState.Validating -> R.string.google_connect_validating
    is GoogleConnectState.Authorizing -> R.string.google_connect_authorizing
    else -> R.string.google_connect_picking
}

@StringRes
private fun GoogleFailure.messageRes(): Int = when (this) {
    GoogleFailure.CANCELLED -> R.string.google_error_cancelled
    GoogleFailure.DENIED -> R.string.google_error_denied
    GoogleFailure.NETWORK -> R.string.google_error_network
    GoogleFailure.PLAY_SERVICES -> R.string.google_error_play
    GoogleFailure.MISCONFIGURED -> R.string.google_error_misconfigured
    GoogleFailure.API_DISABLED -> R.string.google_error_api_disabled
    GoogleFailure.ALREADY_EXISTS -> R.string.google_error_exists
    GoogleFailure.MISMATCH -> R.string.google_error_mismatch
    GoogleFailure.UNKNOWN -> R.string.google_error_unknown
}
