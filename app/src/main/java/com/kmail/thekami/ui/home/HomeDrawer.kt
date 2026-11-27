package com.kmail.thekami.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmail.thekami.R
import com.kmail.thekami.data.db.FolderEntity
import com.kmail.thekami.domain.model.FolderRole
import com.kmail.thekami.ui.components.AppIcons

fun folderIcon(role: FolderRole): ImageVector = when (role) {
    FolderRole.INBOX -> AppIcons.Inbox
    FolderRole.SENT -> AppIcons.Send
    FolderRole.DRAFTS -> AppIcons.MailOpen
    FolderRole.ARCHIVE -> AppIcons.Archive
    FolderRole.SPAM -> AppIcons.Spam
    FolderRole.TRASH -> AppIcons.Trash
    FolderRole.CUSTOM -> AppIcons.Folder
}

@Composable
fun folderLabel(folder: FolderEntity): String = when (folder.role) {
    FolderRole.INBOX -> stringResource(R.string.role_inbox)
    FolderRole.SENT -> stringResource(R.string.role_sent)
    FolderRole.DRAFTS -> stringResource(R.string.role_drafts)
    FolderRole.ARCHIVE -> stringResource(R.string.role_archive)
    FolderRole.SPAM -> stringResource(R.string.role_spam)
    FolderRole.TRASH -> stringResource(R.string.role_trash)
    FolderRole.CUSTOM -> folder.displayName
}

private fun rolePriority(role: FolderRole): Int = when (role) {
    FolderRole.INBOX -> 0
    FolderRole.DRAFTS -> 1
    FolderRole.SENT -> 2
    FolderRole.ARCHIVE -> 3
    FolderRole.SPAM -> 4
    FolderRole.TRASH -> 5
    FolderRole.CUSTOM -> 6
}

private fun countText(count: Int): String = if (count > 999) "999+" else count.toString()

/** The app name in a flowing hand, with the first letter in the brand teal. No logo. */
@Composable
private fun DrawerBrand() {
    val name = stringResource(R.string.app_name)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val teal = if (dark) Color(0xFF4DB6AC) else Color(0xFF00897B)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = teal)) { append(name.take(1)) }
            append(name.drop(1))
        },
        fontFamily = FontFamily.Cursive,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 28.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
    )
}

@Composable
private fun AccountAvatar(email: String, color: Color, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            email.take(1).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}

/** One line in the drawer: icon, name, and a plain count on the right. */
@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
    emphasizeCount: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else Color.Transparent,
        animationSpec = tween(150),
        label = "drawer-item",
    )
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null && count > 0) {
            Text(
                text = countText(count),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (emphasizeCount) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) scheme.onSecondaryContainer else if (emphasizeCount) scheme.onSurface else scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DrawerDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

/**
 * One flat list, narrow and light.
 * Top: the account switcher, then the combined views (all inboxes, starred, unread).
 * Middle: the folders of the active account.
 * Bottom: drafts and settings.
 */
@Composable
fun HomeDrawer(
    state: HomeUiState,
    onSelect: (MailboxSelection) -> Unit,
    onDrafts: () -> Unit,
    onSettings: () -> Unit,
    onAddAccount: () -> Unit,
) {
    var chosenId by rememberSaveable { mutableStateOf<String?>(null) }
    var switching by remember { mutableStateOf(false) }

    fun inboxUnread(folders: List<FolderEntity>): Int =
        folders.filter { it.role == FolderRole.INBOX }.sumOf { state.unread[it.id] ?: 0 }

    val allUnread = state.sidebar.sumOf { inboxUnread(it.folders) }
    val selectedAccountId = (state.selection as? MailboxSelection.Folder)?.let { sel ->
        state.sidebar.firstOrNull { e -> e.folders.any { it.id == sel.id } }?.account?.id
    }
    val activeId = selectedAccountId ?: chosenId
    val active = state.sidebar.firstOrNull { it.account.id == activeId } ?: state.sidebar.firstOrNull()
    val scheme = MaterialTheme.colorScheme

    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Column(
            Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        ) {
            DrawerBrand()

            if (active != null) {
                // The account in use. Tap to switch. The list below includes this account too.
                Row(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.surfaceContainerHigh)
                        .clickable { switching = !switching }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccountAvatar(active.account.emailAddress, Color(active.account.colorArgb), 30.dp)
                    Text(
                        text = active.account.emailAddress,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (switching) AppIcons.ChevronUp else AppIcons.ChevronDown,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                    )
                }

                AnimatedVisibility(visible = switching) {
                    Column(Modifier.padding(top = 4.dp)) {
                        state.sidebar.forEach { entry ->
                            val isActive = entry.account.id == active.account.id
                            Row(
                                Modifier
                                    .padding(horizontal = 12.dp)
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable {
                                        chosenId = entry.account.id
                                        switching = false
                                        if (!isActive) {
                                            entry.folders.firstOrNull { it.role == FolderRole.INBOX }
                                                ?.let { onSelect(MailboxSelection.Folder(it.id)) }
                                        }
                                    }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                AccountAvatar(entry.account.emailAddress, Color(entry.account.colorArgb), 26.dp)
                                Text(
                                    text = entry.account.emailAddress,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isActive) {
                                    Icon(
                                        AppIcons.Check,
                                        contentDescription = null,
                                        tint = scheme.primary,
                                    )
                                } else {
                                    val unread = inboxUnread(entry.folders)
                                    if (unread > 0) {
                                        Text(
                                            countText(unread),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        DrawerItem(
                            icon = AppIcons.AddAccount,
                            label = stringResource(R.string.home_add_account),
                            selected = false,
                            onClick = onAddAccount,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Combined views.
            DrawerItem(
                icon = AppIcons.InboxAll,
                label = stringResource(R.string.home_all_inboxes),
                selected = state.selection is MailboxSelection.Unified,
                onClick = { onSelect(MailboxSelection.Unified) },
                count = allUnread,
                emphasizeCount = true,
            )
            DrawerItem(
                icon = AppIcons.StarOutline,
                label = stringResource(R.string.sidebar_starred),
                selected = state.selection is MailboxSelection.Starred,
                onClick = { onSelect(MailboxSelection.Starred) },
            )
            DrawerItem(
                icon = AppIcons.MarkUnread,
                label = stringResource(R.string.sidebar_unread),
                selected = state.selection is MailboxSelection.Unread,
                onClick = { onSelect(MailboxSelection.Unread) },
            )

            // Folders of the active account.
            if (active != null) {
                AnimatedVisibility(visible = !switching) {
                    Column {
                        DrawerDivider()
                        active.folders.sortedBy { rolePriority(it.role) }.forEach { folder ->
                            DrawerItem(
                                icon = folderIcon(folder.role),
                                label = folderLabel(folder),
                                selected = state.selection == MailboxSelection.Folder(folder.id),
                                onClick = { onSelect(MailboxSelection.Folder(folder.id)) },
                                count = state.unread[folder.id],
                                emphasizeCount = folder.role == FolderRole.INBOX,
                            )
                        }
                    }
                }
            }

            DrawerDivider()
            DrawerItem(
                icon = AppIcons.NoteEdit,
                label = stringResource(R.string.home_drafts),
                selected = false,
                onClick = onDrafts,
            )
            DrawerItem(
                icon = AppIcons.Settings,
                label = stringResource(R.string.home_settings),
                selected = false,
                onClick = onSettings,
            )
            DrawerFooter()
        }
    }
}
