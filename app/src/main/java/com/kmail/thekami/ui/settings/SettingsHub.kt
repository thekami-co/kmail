package com.kmail.thekami.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kmail.thekami.R
import com.kmail.thekami.ui.components.AppIcons
import com.kmail.thekami.ui.support.SupportSection

internal val AccentBlue = Color(0xFF4C8DF6)
internal val AccentTeal = Color(0xFF26A69A)
internal val AccentPurple = Color(0xFFAB7DF0)
internal val AccentOrange = Color(0xFFF2994A)
internal val AccentGray = Color(0xFF8E99A4)

/** One tappable line in a settings card. */
data class SettingsEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    /** True for rows that leave the app (web page, store), so they get an "open" arrow. */
    val external: Boolean = false,
    /** Replaces the arrow on the right, for example a switch. */
    val trailing: (@Composable () -> Unit)? = null,
    /** Color of the icon and its soft background. Falls back to the group's color. */
    val tint: Color? = null,
    val onClick: () -> Unit,
)

/** The main settings page: a profile header, then grouped rounded cards. */
@Composable
fun SettingsHub(
    accountsSummary: String?,
    themeSummary: String,
    versionName: String,
    onAccounts: () -> Unit,
    onSignature: () -> Unit,
    onInbox: () -> Unit,
    onLinks: () -> Unit,
    onSenders: () -> Unit,
    onAppearance: () -> Unit,
    onLanguage: () -> Unit,
    onSync: () -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
    profileEmail: String? = null,
    profileColor: Color? = null,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (profileEmail != null) ProfileHeader(profileEmail, profileColor, onAccounts)
        SettingsGroup(
            title = null,
            accent = AccentBlue,
            entries = listOf(
                SettingsEntry(AppIcons.AccountCircle, stringResource(R.string.settings_accounts), accountsSummary, onClick = onAccounts),
            ),
        )
        SettingsGroup(
            title = stringResource(R.string.settings_group_mail),
            accent = AccentTeal,
            entries = listOf(
                SettingsEntry(AppIcons.Inbox, stringResource(R.string.settings_inbox), onClick = onInbox),
                SettingsEntry(AppIcons.Image, stringResource(R.string.settings_images), onClick = onSenders),
                SettingsEntry(AppIcons.NoteEdit, stringResource(R.string.settings_signature_title), onClick = onSignature),
                SettingsEntry(AppIcons.Sync, stringResource(R.string.settings_sync), onClick = onSync),
            ),
        )
        SettingsGroup(
            title = stringResource(R.string.settings_group_personalize),
            accent = AccentPurple,
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
            accent = AccentOrange,
            entries = listOf(
                SettingsEntry(AppIcons.Lock, stringResource(R.string.settings_privacy), onClick = onPrivacy),
            ),
        )
        SupportSection()
        SettingsGroup(
            title = stringResource(R.string.settings_group_about),
            accent = AccentGray,
            entries = listOf(
                SettingsEntry(AppIcons.Info, stringResource(R.string.settings_about), onClick = onAbout),
            ),
        )
        HubFooter(versionName)
    }
}

@Composable
private fun ProfileHeader(email: String, color: Color?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(color ?: MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                email.take(1).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            email,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A small label with its rows inside one rounded card. */
@Composable
fun SettingsGroup(
    title: String?,
    entries: List<SettingsEntry>,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp),
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
        Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainer) {
            Column {
                entries.forEachIndexed { index, entry ->
                    SettingsRow(entry, accent)
                    if (index < entries.lastIndex) {
                        HorizontalDivider(
                            Modifier.padding(start = 72.dp),
                            color = scheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(entry: SettingsEntry, accent: Color) {
    val scheme = MaterialTheme.colorScheme
    val tint = entry.tint ?: accent
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = entry.onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(entry.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            if (entry.subtitle != null) {
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = if (entry.trailing != null) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.trailing != null) {
            entry.trailing.invoke()
        } else {
            Icon(
                if (entry.external) AppIcons.OpenInNew else AppIcons.ChevronRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
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
