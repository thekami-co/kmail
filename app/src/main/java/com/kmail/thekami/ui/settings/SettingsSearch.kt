package com.kmail.thekami.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kmail.thekami.R
import com.kmail.thekami.ui.components.AppIcons
import kotlinx.coroutines.delay

private val Smooth = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** One thing you can find. [page] is the name of the settings page it lives on. */
private class SettingsHit(
    val title: String,
    val parent: String?,
    val icon: ImageVector,
    val page: String,
    val isPage: Boolean = false,
)

private fun tintFor(page: String): Color = when (page) {
    "Accounts" -> AccentBlue
    "Signature", "Inbox", "Sync", "Senders" -> AccentTeal
    "Appearance", "Links", "Language" -> AccentPurple
    "Privacy" -> AccentOrange
    else -> AccentGray
}

@Composable
private fun settingsIndex(): List<SettingsHit> {
    val accounts = stringResource(R.string.settings_accounts)
    val signature = stringResource(R.string.settings_signature_title)
    val appearance = stringResource(R.string.settings_appearance)
    val language = stringResource(R.string.settings_language)
    val sync = stringResource(R.string.settings_sync)
    val privacy = stringResource(R.string.settings_privacy)
    val inbox = stringResource(R.string.settings_inbox)
    val links = stringResource(R.string.settings_links)
    val about = stringResource(R.string.settings_about)
    val images = stringResource(R.string.settings_images)
    return buildList {
        fun page(title: String, icon: ImageVector, key: String) = add(SettingsHit(title, null, icon, key, true))
        fun item(title: String, parent: String, icon: ImageVector, key: String) =
            add(SettingsHit(title, parent, icon, key))

        page(accounts, AppIcons.AccountCircle, "Accounts")
        item(stringResource(R.string.home_add_account), accounts, AppIcons.AddAccount, "Accounts")
        item(stringResource(R.string.transfer_title), accounts, AppIcons.QrCode, "Accounts")

        page(inbox, AppIcons.Inbox, "Inbox")
        item(stringResource(R.string.inbox_photos), inbox, AppIcons.Image, "Inbox")
        item(stringResource(R.string.inbox_swipe_right), inbox, AppIcons.Archive, "Inbox")
        item(stringResource(R.string.inbox_swipe_left), inbox, AppIcons.Trash, "Inbox")

        page(images, AppIcons.Image, "Senders")
        item(stringResource(R.string.senders_mode_title), images, AppIcons.Image, "Senders")
        item(stringResource(R.string.senders_approved), images, AppIcons.At, "Senders")

        page(signature, AppIcons.NoteEdit, "Signature")
        item(stringResource(R.string.settings_signature_enabled), signature, AppIcons.NoteEdit, "Signature")
        item(stringResource(R.string.settings_signature_default), signature, AppIcons.NoteEdit, "Signature")

        page(sync, AppIcons.Sync, "Sync")
        item(stringResource(R.string.settings_bg_sync), sync, AppIcons.Sync, "Sync")
        item(stringResource(R.string.settings_interval), sync, AppIcons.Sync, "Sync")
        item(stringResource(R.string.settings_notifications), sync, AppIcons.Mail, "Sync")
        item(stringResource(R.string.settings_hide_content), sync, AppIcons.EyeOff, "Sync")

        page(appearance, AppIcons.Palette, "Appearance")
        item(stringResource(R.string.appearance_theme), appearance, AppIcons.Palette, "Appearance")
        item(stringResource(R.string.appearance_amoled), appearance, AppIcons.Palette, "Appearance")
        item(stringResource(R.string.appearance_dynamic), appearance, AppIcons.Palette, "Appearance")

        page(links, AppIcons.OpenInNew, "Links")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            page(language, AppIcons.Language, "Language")
        }

        page(privacy, AppIcons.Lock, "Privacy")
        item(stringResource(R.string.settings_app_lock), privacy, AppIcons.Lock, "Privacy")
        item(stringResource(R.string.settings_biometric), privacy, AppIcons.Fingerprint, "Privacy")
        item(stringResource(R.string.settings_recovery), privacy, AppIcons.Lock, "Privacy")
        item(stringResource(R.string.settings_auto_lock), privacy, AppIcons.Lock, "Privacy")

        page(about, AppIcons.Info, "About")
        item(stringResource(R.string.settings_privacy_policy), about, AppIcons.Info, "About")
        item(stringResource(R.string.legal_how_to_use), about, AppIcons.Info, "About")
    }
}

/** The search pill. It grows out from the right, where the search icon was. */
@Composable
internal fun SettingsSearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(Unit) {
        delay(180)
        runCatching { focus.requestFocus() }
    }
    AnimatedVisibility(
        visibleState = appear,
        enter = expandHorizontally(animationSpec = tween(300, easing = Smooth), expandFrom = Alignment.End) +
            fadeIn(tween(220)),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = scheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { focusManager.clearFocus(); onClose() }) {
                    Icon(AppIcons.Back, contentDescription = stringResource(R.string.reader_back))
                }
                TextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    placeholder = { Text(stringResource(R.string.settings_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                AnimatedVisibility(visible = query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.settings_search_clear))
                    }
                }
            }
        }
    }
}

/** The results. They fall down from just under the pill. */
@Composable
internal fun SettingsSearchResults(query: String, padding: PaddingValues, onOpen: (String) -> Unit) {
    val all = settingsIndex()
    val q = query.trim()
    val hits = if (q.isEmpty()) {
        all.filter { it.isPage }
    } else {
        all.filter { it.title.contains(q, ignoreCase = true) || it.parent?.contains(q, ignoreCase = true) == true }
            .sortedBy { if (it.title.startsWith(q, ignoreCase = true)) 0 else 1 }
    }
    val focusManager = LocalFocusManager.current
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = appear,
        enter = slideInVertically(tween(340, easing = Smooth)) { -it / 6 } + fadeIn(tween(260)),
    ) {
        if (hits.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.settings_search_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = 24.dp,
            )) {
                item {
                    SettingsGroup(
                        title = null,
                        entries = hits.map { hit ->
                            SettingsEntry(
                                icon = hit.icon,
                                title = hit.title,
                                subtitle = hit.parent,
                                tint = tintFor(hit.page),
                                onClick = { focusManager.clearFocus(); onOpen(hit.page) },
                            )
                        },
                    )
                }
            }
        }
    }
}
