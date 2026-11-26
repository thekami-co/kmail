package com.kmail.thekami.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kmail.thekami.R
import com.kmail.thekami.ui.components.AppIcons
import com.kmail.thekami.ui.support.SupportSection

/** One tappable line in a settings section. */
data class SettingsEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    /** True for rows that leave the app (web page, store), so they get an "open" arrow. */
    val external: Boolean = false,
    /** Replaces the arrow on the right, for example a switch. */
    val trailing: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit,
)

/** The main settings page: flat Material 3 list items under small section labels. */
@Composable
fun SettingsHub(
    accountsSummary: String?,
    themeSummary: String,
    versionName: String,
    onAccounts: () -> Unit,
    onSignature: () -> Unit,
    onInbox: () -> Unit,
    onLinks: () -> Unit,
    onAppearance: () -> Unit,
    onLanguage: () -> Unit,
    onSync: () -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        SettingsGroup(
            title = null,
            entries = listOf(
                SettingsEntry(AppIcons.AccountCircle, stringResource(R.string.settings_accounts), accountsSummary, onClick = onAccounts),
            ),
        )
        SettingsGroup(
            title = stringResource(R.string.settings_group_mail),
            entries = listOf(
                SettingsEntry(AppIcons.Inbox, stringResource(R.string.settings_inbox), onClick = onInbox),
                SettingsEntry(AppIcons.NoteEdit, stringResource(R.string.settings_signature_title), onClick = onSignature),
                SettingsEntry(AppIcons.Sync, stringResource(R.string.settings_sync), onClick = onSync),
            ),
        )
        SettingsGroup(
            title = stringResource(R.string.settings_group_personalize),
            entries = buildList {
                add(SettingsEntry(AppIcons.Palette, stringResource(R.string.settings_appearance), themeSummary, onClick = onAppearance))
                add(SettingsEntry(AppIcons.OpenInNew, stringResource(R.string.settings_links), onClick = onLinks))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(SettingsEntry(AppIcons.Language, stringResource(R.string.settings_language), currentLanguageLabel(), onClick = onLanguage))
                }
            },
        )
        SettingsGroup(
            title = stringResource(R.string.settings_group_security),
            entries = listOf(
                SettingsEntry(AppIcons.Lock, stringResource(R.string.settings_privacy), onClick = onPrivacy),
            ),
        )
        SupportSection()
        SettingsGroup(
            title = stringResource(R.string.settings_group_about),
            entries = listOf(
                SettingsEntry(AppIcons.Info, stringResource(R.string.settings_about), onClick = onAbout),
            ),
        )
        HubFooter(versionName)
    }
}

/** A small colored section label with its rows below it. No boxes. */
@Composable
fun SettingsGroup(title: String?, entries: List<SettingsEntry>) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
        entries.forEach { entry -> SettingsRow(entry) }
    }
}

@Composable
private fun SettingsRow(entry: SettingsEntry) {
    val scheme = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(entry.title) },
        supportingContent = entry.subtitle?.let { subtitle ->
            {
                Text(
                    subtitle,
                    maxLines = if (entry.trailing != null) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = { Icon(entry.icon, contentDescription = null) },
        trailingContent = {
            if (entry.trailing != null) {
                entry.trailing.invoke()
            } else {
                Icon(
                    if (entry.external) AppIcons.OpenInNew else AppIcons.ChevronRight,
                    contentDescription = null,
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            leadingIconColor = scheme.onSurfaceVariant,
            trailingIconColor = scheme.onSurfaceVariant,
            supportingColor = scheme.onSurfaceVariant,
        ),
        modifier = Modifier.clickable(onClick = entry.onClick),
    )
}

@Composable
private fun HubFooter(versionName: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.settings_brand), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.settings_version, versionName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
