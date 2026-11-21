package com.kmail.thekami.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmail.thekami.R
import com.kmail.thekami.ui.components.AppIcons
import com.kmail.thekami.ui.home.InboxPrefs
import com.kmail.thekami.ui.home.SwipeAction

private enum class Side { Right, Left }

@Composable
private fun SwipeAction.label(): String = stringResource(
    when (this) {
        SwipeAction.ARCHIVE -> R.string.swipe_archive
        SwipeAction.DELETE -> R.string.swipe_delete
        SwipeAction.STAR -> R.string.swipe_star
        SwipeAction.NONE -> R.string.swipe_none
    },
)

private fun SwipeAction.icon(): ImageVector = when (this) {
    SwipeAction.ARCHIVE -> AppIcons.Archive
    SwipeAction.DELETE -> AppIcons.Trash
    SwipeAction.STAR -> AppIcons.Star
    SwipeAction.NONE -> AppIcons.Close
}

/** Settings > Inbox: sender pictures and what each swipe does. */
@Composable
fun InboxSettingsPage() {
    val context = LocalContext.current
    val prefs = remember { InboxPrefs.get(context) }
    val photos by prefs.showPhotos.collectAsStateWithLifecycle()
    val right by prefs.swipeRight.collectAsStateWithLifecycle()
    val left by prefs.swipeLeft.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsGroup(
        title = stringResource(R.string.inbox_group_list),
        entries = listOf(
            SettingsEntry(
                icon = AppIcons.Account,
                title = stringResource(R.string.inbox_photos),
                subtitle = stringResource(R.string.inbox_photos_desc),
                trailing = { Switch(checked = photos, onCheckedChange = null) },
                onClick = { prefs.setShowPhotos(!photos) },
            ),
        ),
    )
    SettingsGroup(
        title = stringResource(R.string.inbox_group_swipe),
        entries = listOf(
            SettingsEntry(
                icon = right.icon(),
                title = stringResource(R.string.inbox_swipe_right),
                subtitle = right.label(),
                onClick = { picking = Side.Right.name },
            ),
            SettingsEntry(
                icon = left.icon(),
                title = stringResource(R.string.inbox_swipe_left),
                subtitle = left.label(),
                onClick = { picking = Side.Left.name },
            ),
        ),
    )

    val side = picking?.let { Side.valueOf(it) }
    if (side != null) {
        val current = if (side == Side.Right) right else left
        AlertDialog(
            onDismissRequest = { picking = null },
            title = {
                Text(stringResource(if (side == Side.Right) R.string.inbox_swipe_right else R.string.inbox_swipe_left))
            },
            text = {
                androidx.compose.foundation.layout.Column {
                    SwipeAction.entries.forEach { action ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = action == current,
                                    role = Role.RadioButton,
                                    onClick = {
                                        if (side == Side.Right) prefs.setSwipeRight(action) else prefs.setSwipeLeft(action)
                                        picking = null
                                    },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(selected = action == current, onClick = null)
                            Text(action.label(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = null }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}
