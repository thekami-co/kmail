package com.kmail.thekami.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmail.thekami.R
import com.kmail.thekami.data.prefs.LinkMode
import com.kmail.thekami.data.prefs.LinkPrefs

/** Choose where links open. */
@Composable
internal fun LinksPage() {
    val prefs = LinkPrefs.get(LocalContext.current)
    val mode by prefs.mode.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.links_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LinkChoice(R.string.links_in_app, R.string.links_in_app_desc, mode == LinkMode.IN_APP) {
            prefs.setMode(LinkMode.IN_APP)
        }
        LinkChoice(R.string.links_external, R.string.links_external_desc, mode == LinkMode.EXTERNAL) {
            prefs.setMode(LinkMode.EXTERNAL)
        }
    }
}

@Composable
private fun LinkChoice(@StringRes title: Int, @StringRes desc: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
