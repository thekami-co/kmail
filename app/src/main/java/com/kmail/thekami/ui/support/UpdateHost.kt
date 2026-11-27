package com.kmail.thekami.ui.support

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.kmail.thekami.R

private const val ASK_AGAIN_MS = 24L * 60 * 60 * 1000

/** Put once in the main content. Play installs get an update dialog, GitHub installs get the red dot. */
@Composable
fun UpdateHost() {
    val context = LocalContext.current
    if (remember { isInstalledFromPlayStore(context) }) PlayUpdateHost() else GithubUpdateHost()
}

@Composable
private fun GithubUpdateHost() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { GithubUpdates.checkIfDue(context) }
    }
}

private class PlayAskStore(context: Context) {
    private val prefs = context.getSharedPreferences("play_update", Context.MODE_PRIVATE)

    fun shouldAsk(code: Int): Boolean =
        prefs.getInt("code", 0) != code ||
            System.currentTimeMillis() - prefs.getLong("at", 0L) > ASK_AGAIN_MS

    fun markAsked(code: Int) {
        prefs.edit().putInt("code", code).putLong("at", System.currentTimeMillis()).apply()
    }
}

@Composable
private fun PlayUpdateHost() {
    val context = LocalContext.current
    val manager = remember { AppUpdateManagerFactory.create(context.applicationContext) }
    val store = remember { PlayAskStore(context.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var pending by remember { mutableStateOf<AppUpdateInfo?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    val options = remember { AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build() }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            manager.appUpdateInfo.addOnSuccessListener { info ->
                val availability = info.updateAvailability()
                if (availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    // The person already agreed. Carry on where it stopped.
                    manager.startUpdateFlowForResult(info, launcher, options)
                } else if (
                    availability == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) &&
                    store.shouldAsk(info.availableVersionCode())
                ) {
                    store.markAsked(info.availableVersionCode())
                    pending = info
                }
            }
        }
    }

    pending?.let { info ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.update_title)) },
            text = { Text(stringResource(R.string.update_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    manager.startUpdateFlowForResult(info, launcher, options)
                }) { Text(stringResource(R.string.update_now)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.update_later)) }
            },
        )
    }
}
