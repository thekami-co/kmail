package com.kmail.thekami.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmail.thekami.R
import com.kmail.thekami.data.prefs.ImageMode
import com.kmail.thekami.data.prefs.ImagePrefs
import com.kmail.thekami.ui.components.AppIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class ImageSettingsViewModel @Inject constructor(
    private val prefs: ImagePrefs,
) : ViewModel() {
    val mode: StateFlow<ImageMode> = prefs.mode
    val senders: StateFlow<List<String>> = prefs.trustedSenders

    fun setMode(mode: ImageMode) = prefs.setMode(mode)
    fun remove(entry: String) = prefs.untrust(entry)
}

/** How pictures from the internet are handled, and the senders that were approved. */
@Composable
fun ImageSettingsPage(vm: ImageSettingsViewModel = hiltViewModel()) {
    val mode by vm.mode.collectAsStateWithLifecycle()
    val senders by vm.senders.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        SettingsGroup(
            title = stringResource(R.string.senders_mode_title),
            accent = AccentTeal,
            entries = listOf(
                Triple(ImageMode.ASK, R.string.senders_mode_ask, R.string.senders_mode_ask_desc),
                Triple(ImageMode.ALWAYS, R.string.senders_mode_always, R.string.senders_mode_always_desc),
                Triple(ImageMode.NEVER, R.string.senders_mode_never, R.string.senders_mode_never_desc),
            ).map { (value, title, desc) ->
                SettingsEntry(
                    icon = AppIcons.Image,
                    title = stringResource(title),
                    subtitle = stringResource(desc),
                    trailing = {
                        if (mode == value) {
                            Icon(AppIcons.Check, contentDescription = null, tint = scheme.primary)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = { vm.setMode(value) },
                )
            },
        )

        if (senders.isEmpty()) {
            Text(
                stringResource(R.string.senders_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 28.dp),
            )
        } else {
            SettingsGroup(
                title = stringResource(R.string.senders_approved),
                accent = AccentBlue,
                entries = senders.map { entry ->
                    val isCompany = entry.startsWith("@")
                    SettingsEntry(
                        icon = AppIcons.At,
                        title = if (isCompany) entry.drop(1) else entry,
                        subtitle = if (isCompany) stringResource(R.string.senders_all_addresses) else null,
                        trailing = {
                            IconButton(onClick = { vm.remove(entry) }) {
                                Icon(
                                    AppIcons.Close,
                                    contentDescription = stringResource(R.string.senders_remove),
                                    tint = scheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {},
                    )
                },
            )
        }
    }
}
