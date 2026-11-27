package com.kmail.thekami.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kmail.thekami.ui.theme.rememberReducedMotion

/**
 * A round badge that drops down from the top as you pull, draws its ring while you pull,
 * and spins while the refresh runs. Use it as the `indicator` of PullToRefreshBox.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.KmailPullIndicator(state: PullToRefreshState, refreshing: Boolean) {
    if (refreshing || state.distanceFraction > 0f) PullBadge(state, refreshing)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.PullBadge(state: PullToRefreshState, refreshing: Boolean) {
    val reduced = rememberReducedMotion()
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val badge = 44.dp
    val badgePx = with(density) { badge.toPx() }
    val dropPx = with(density) { 64.dp.toPx() }
    val ringPx = with(density) { 3.dp.toPx() }
    val spin by rememberInfiniteTransition(label = "pull-spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "pull-angle",
    )
    val ring = scheme.primary

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer {
                val f = state.distanceFraction
                translationY = f.coerceAtMost(1.3f) * dropPx - badgePx
                val s = 0.6f + 0.4f * f.coerceIn(0f, 1f)
                scaleX = s
                scaleY = s
                alpha = f.coerceIn(0f, 1f)
            }
            .size(badge)
            .shadow(4.dp, CircleShape)
            .background(scheme.surfaceContainerHighest, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val pull = state.distanceFraction.coerceIn(0f, 1f)
            val sweep = if (refreshing) 270f else 270f * pull
            val start = when {
                refreshing && !reduced -> spin - 90f
                refreshing -> -90f
                else -> -90f + 120f * pull
            }
            drawArc(
                color = ring,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(ringPx / 2, ringPx / 2),
                size = Size(size.width - ringPx, size.height - ringPx),
                style = Stroke(width = ringPx, cap = StrokeCap.Round),
            )
        }
    }
}
