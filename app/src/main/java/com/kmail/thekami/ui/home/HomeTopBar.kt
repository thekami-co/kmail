package com.kmail.thekami.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kmail.thekami.R
import com.kmail.thekami.data.db.FolderEntity
import com.kmail.thekami.domain.model.FolderRole
import com.kmail.thekami.ui.components.AppIcons
import com.kmail.thekami.ui.components.KmailWordmark
import kotlinx.coroutines.delay

private val Smooth = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The top of the inbox. Normally: menu, the Kmail name in the middle, search, and the account picture.
 * While searching, the whole row turns into a search pill that grows out from the right.
 */
@Composable
fun HomeTopBar(
    state: HomeUiState,
    searching: Boolean,
    placeholder: String,
    onQuery: (String) -> Unit,
    onMenu: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onSelect: (MailboxSelection) -> Unit,
    onAddAccount: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AnimatedContent(
        targetState = searching,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "home-top-bar",
    ) { isSearching ->
        if (isSearching) {
            SearchRow(state.query, placeholder, onQuery, onSearchClose)
        } else {
            TitleRow(state, onMenu, onSearchOpen, onSelect, onAddAccount, onOpenSettings)
        }
    }
}

@Composable
private fun TitleRow(
    state: HomeUiState,
    onMenu: () -> Unit,
    onSearchOpen: () -> Unit,
    onSelect: (MailboxSelection) -> Unit,
    onAddAccount: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    fun inboxUnread(folders: List<FolderEntity>): Int =
        folders.filter { it.role == FolderRole.INBOX }.sumOf { state.unread[it.id] ?: 0 }

    val selectedAccountId = (state.selection as? MailboxSelection.Folder)?.let { sel ->
        state.sidebar.firstOrNull { e -> e.folders.any { it.id == sel.id } }?.account?.id
    }
    val active = state.sidebar.firstOrNull { it.account.id == selectedAccountId } ?: state.sidebar.firstOrNull()
    val accountsLabel = stringResource(R.string.home_top_accounts)

    Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp)) {
        IconButton(onClick = onMenu, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(AppIcons.Menu, contentDescription = stringResource(R.string.home_open_menu))
        }
        KmailWordmark(modifier = Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSearchOpen) {
                Icon(AppIcons.Search, contentDescription = stringResource(R.string.home_top_search))
            }
            Box(Modifier.padding(end = 10.dp)) {
                if (active != null) {
                    TopBarAvatar(
                        email = active.account.emailAddress,
                        color = Color(active.account.colorArgb),
                        size = 34.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { menuOpen = true }
                            .semantics { contentDescription = accountsLabel },
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(24.dp),
                    containerColor = scheme.surfaceContainerHigh,
                    modifier = Modifier.widthIn(min = 280.dp),
                ) {
                    state.sidebar.forEach { entry ->
                        val isActive = state.selection is MailboxSelection.Folder &&
                            entry.account.id == active?.account?.id
                        val unread = inboxUnread(entry.folders)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    entry.account.emailAddress,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            leadingIcon = {
                                TopBarAvatar(entry.account.emailAddress, Color(entry.account.colorArgb), 28.dp)
                            },
                            trailingIcon = {
                                if (isActive) {
                                    Icon(AppIcons.Check, contentDescription = null, tint = scheme.primary)
                                } else if (unread > 0) {
                                    Text(
                                        if (unread > 99) "99+" else unread.toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                menuOpen = false
                                if (!isActive) {
                                    entry.folders.firstOrNull { it.role == FolderRole.INBOX }
                                        ?.let { onSelect(MailboxSelection.Folder(it.id)) }
                                }
                            },
                        )
                    }
                    if (state.sidebar.size > 1) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_all_inboxes)) },
                            leadingIcon = { Icon(AppIcons.InboxAll, contentDescription = null) },
                            trailingIcon = {
                                if (state.selection == MailboxSelection.Unified) {
                                    Icon(AppIcons.Check, contentDescription = null, tint = scheme.primary)
                                }
                            },
                            onClick = { menuOpen = false; onSelect(MailboxSelection.Unified) },
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_add_account)) },
                        leadingIcon = { Icon(AppIcons.AddAccount, contentDescription = null) },
                        onClick = { menuOpen = false; onAddAccount() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_title)) },
                        leadingIcon = { Icon(AppIcons.Settings, contentDescription = null) },
                        onClick = { menuOpen = false; onOpenSettings() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchRow(query: String, placeholder: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(Unit) {
        delay(180)
        runCatching { focus.requestFocus() }
    }
    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visibleState = appear,
            enter = expandHorizontally(animationSpec = tween(320, easing = Smooth), expandFrom = Alignment.End) +
                fadeIn(tween(220)),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = scheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { focusManager.clearFocus(); onClose() }) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.reader_back))
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        placeholder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.home_clear_search))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBarAvatar(email: String, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            email.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** The sort order, as a chip at the end of the filter row. */
@Composable
internal fun SortChip(options: ListOptions, onSort: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    fun label(sort: SortOrder) = when (sort) {
        SortOrder.NEWEST -> R.string.sort_newest
        SortOrder.OLDEST -> R.string.sort_oldest
        SortOrder.UNREAD_FIRST -> R.string.sort_unread_first
        SortOrder.READ_FIRST -> R.string.sort_read_first
    }

    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(stringResource(label(options.sort)), style = MaterialTheme.typography.labelLarge) },
            trailingIcon = { Icon(AppIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            shape = RoundedCornerShape(50),
            border = null,
            colors = AssistChipDefaults.assistChipColors(containerColor = scheme.surfaceContainerHigh),
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = scheme.surfaceContainerHigh,
        ) {
            SortOrder.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(label(sort))) },
                    trailingIcon = {
                        if (options.sort == sort) {
                            Icon(AppIcons.Check, contentDescription = null, tint = scheme.primary)
                        }
                    },
                    onClick = { onSort(sort); open = false },
                )
            }
        }
    }
}
