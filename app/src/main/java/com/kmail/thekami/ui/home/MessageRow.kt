package com.kmail.thekami.ui.home

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmail.thekami.R
import com.kmail.thekami.ui.components.AppIcons
import java.util.Date
import kotlinx.coroutines.delay

private val StarAmber = Color(0xFFF2A900)
private val RowShape = RoundedCornerShape(20.dp)

@Composable
private fun formatTime(millis: Long): String {
    if (millis <= 0) return ""
    val context = LocalContext.current
    return if (DateUtils.isToday(millis)) {
        DateFormat.getTimeFormat(context).format(Date(millis))
    } else {
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
}

/**
 * Swipe a message and the whole card slides away in that direction. When the slide ends the chosen
 * action runs (set in Settings > Inbox), and the rows below glide up to fill the gap. If the action
 * keeps the row in the list (star, or nothing changed) the card slides back.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun SwipeableMessageRow(
    item: MessageItem,
    selected: Boolean,
    selecting: Boolean,
    showAccountDot: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleStar: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { InboxPrefs.get(context) }
    val rightAction by prefs.swipeRight.collectAsStateWithLifecycle()
    val leftAction by prefs.swipeLeft.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { distance -> distance * 0.4f })

    LaunchedEffect(state.currentValue) {
        val value = state.currentValue
        if (value == SwipeToDismissBoxValue.Settled) return@LaunchedEffect
        val action = if (value == SwipeToDismissBoxValue.StartToEnd) rightAction else leftAction
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        when (action) {
            SwipeAction.ARCHIVE -> onArchive()
            SwipeAction.DELETE -> onDelete()
            SwipeAction.STAR -> onToggleStar()
            SwipeAction.NONE -> Unit
        }
        // Archive and delete remove this row, which ends this effect. Anything else brings the card back.
        delay(if (action == SwipeAction.STAR) 150 else 1200)
        state.reset()
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 1.dp).clip(RowShape),
        enableDismissFromStartToEnd = !selecting && rightAction != SwipeAction.NONE,
        enableDismissFromEndToStart = !selecting && leftAction != SwipeAction.NONE,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val toStart = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val action = when {
                toEnd -> rightAction
                toStart -> leftAction
                else -> SwipeAction.NONE
            }
            val armed = state.targetValue != SwipeToDismissBoxValue.Settled
            val scheme = MaterialTheme.colorScheme
            val container by animateColorAsState(
                targetValue = when {
                    !armed || action == SwipeAction.NONE -> scheme.surfaceContainerHigh
                    action == SwipeAction.DELETE -> scheme.errorContainer
                    action == SwipeAction.STAR -> scheme.secondaryContainer
                    else -> scheme.primaryContainer
                },
                label = "swipe-container",
            )
            val iconTint = when (action) {
                SwipeAction.DELETE -> scheme.onErrorContainer
                SwipeAction.STAR -> scheme.onSecondaryContainer
                else -> scheme.onPrimaryContainer
            }
            val iconScale by animateFloatAsState(
                targetValue = if (armed) 1.1f else 0.75f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                label = "swipe-icon",
            )
            Box(
                Modifier.fillMaxSize().background(container).padding(horizontal = 28.dp),
                contentAlignment = if (toStart) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                val icon = when (action) {
                    SwipeAction.ARCHIVE -> AppIcons.Archive
                    SwipeAction.DELETE -> AppIcons.Trash
                    SwipeAction.STAR -> if (item.isStarred) AppIcons.StarOutline else AppIcons.Star
                    SwipeAction.NONE -> null
                }
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.scale(iconScale),
                    )
                }
            }
        },
    ) {
        MessageRow(item, selected, onClick, onLongClick, onToggleStar)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    item: MessageItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleStar: () -> Unit,
) {
    val unread = !item.isRead
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else scheme.surface,
        animationSpec = tween(180),
        label = "row-background",
    )
    // The star changes the moment it is tapped. The list catches up when the update lands.
    var starred by remember(item.id, item.isStarred) { mutableStateOf(item.isStarred) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SenderAvatar(item, selected)
        Column(Modifier.weight(1f)) {
            // Who it is from, and when.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.sender.ifBlank { stringResource(R.string.home_unknown_sender) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unread) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (item.hasAttachments) {
                    Icon(
                        AppIcons.Attachment,
                        contentDescription = stringResource(R.string.home_attachment),
                        modifier = Modifier.size(14.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = formatTime(item.receivedAtMillis),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unread) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
            // The headline.
            Text(
                text = item.subject.ifBlank { stringResource(R.string.home_no_subject) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            // A short preview, and the star on the right of it so nothing ever overlaps.
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f).padding(top = 2.dp)) {
                    if (item.snippet.isNotBlank()) {
                        Text(
                            text = item.snippet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .offset(x = 8.dp)
                        .size(width = 32.dp, height = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) {
                            starred = !starred
                            onToggleStar()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = starred,
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center,
                        transitionSpec = {
                            (
                                scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), initialScale = 0.5f) +
                                    fadeIn(tween(90))
                                ) togetherWith fadeOut(tween(70))
                        },
                        label = "star",
                    ) { isStarred ->
                        Icon(
                            imageVector = if (isStarred) AppIcons.Star else AppIcons.StarOutline,
                            contentDescription = stringResource(R.string.home_toggle_star),
                            tint = if (isStarred) StarAmber else scheme.outline,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The sender's picture when they have one, otherwise their first letter on a soft tint.
 * Selecting turns it into a check mark.
 */
@Composable
private fun SenderAvatar(item: MessageItem, selected: Boolean) {
    val context = LocalContext.current
    val prefs = remember { InboxPrefs.get(context) }
    val showPhotos by prefs.showPhotos.collectAsStateWithLifecycle()
    val email = item.senderKey.takeIf { it.contains('@') }
    val photo by produceState<ImageBitmap?>(
        initialValue = if (showPhotos && email != null) SenderPhotos.cached(email) else null,
        email,
        showPhotos,
    ) {
        value = if (showPhotos && email != null) SenderPhotos.load(context, email) else null
    }

    val initial = item.sender.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    val tone = AvatarColors.colorFor(item.senderKey.ifBlank { item.sender })
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) scheme.primary else tone.copy(alpha = 0.16f),
        animationSpec = tween(180),
        label = "avatar-background",
    )
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                (fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.6f)) togetherWith
                    (fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.6f))
            },
            label = "avatar-content",
        ) { isSelected ->
            if (isSelected) {
                Icon(AppIcons.Check, contentDescription = null, tint = scheme.onPrimary)
            } else {
                Crossfade(targetState = photo, animationSpec = tween(220), label = "avatar-photo") { picture ->
                    if (picture != null) {
                        Image(
                            bitmap = picture,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(initial, style = MaterialTheme.typography.titleMedium, color = tone)
                        }
                    }
                }
            }
        }
    }
}

/** Placeholder rows with a soft moving highlight, shown while the first sync runs. */
@Composable
fun SkeletonList(count: Int = 9) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shift",
    )
    val ink = MaterialTheme.colorScheme.onSurface
    val base = ink.copy(alpha = 0.06f)
    val glow = ink.copy(alpha = 0.13f)
    val x = -500f + shift * 2400f
    val brush = Brush.linearGradient(
        colors = listOf(base, glow, base),
        start = Offset(x, 0f),
        end = Offset(x + 500f, 0f),
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        repeat(count) { index ->
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(brush))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        Modifier.height(12.dp).fillMaxWidth(if (index % 2 == 0) 0.35f else 0.45f)
                            .clip(RoundedCornerShape(6.dp)).background(brush),
                    )
                    Box(
                        Modifier.height(15.dp).fillMaxWidth(0.8f)
                            .clip(RoundedCornerShape(7.dp)).background(brush),
                    )
                    Box(
                        Modifier.height(12.dp).fillMaxWidth(if (index % 3 == 0) 0.6f else 0.72f)
                            .clip(RoundedCornerShape(6.dp)).background(brush),
                    )
                }
            }
        }
    }
}
