package com.kmail.thekami.ui.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/**
 * How far the top bar is pushed up. Scrolling down hides it, the first bit of scrolling up brings it back.
 * 0 is fully shown, -maxPx is fully hidden.
 */
@Stable
class CollapsingBarState(private val maxPx: Float) {
    var offset by mutableFloatStateOf(0f)
        private set

    /** Off while searching or selecting, so the bar never hides then. */
    var enabled: Boolean = true

    val hiddenFraction: Float get() = if (maxPx == 0f) 0f else -offset / maxPx

    fun show() {
        offset = 0f
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!enabled) return Offset.Zero
            val next = (offset + available.y).coerceIn(-maxPx, 0f)
            val used = next - offset
            offset = next
            return Offset(0f, used)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (offset != 0f && offset != -maxPx) {
                val target = if (offset < -maxPx / 2f) -maxPx else 0f
                animate(offset, target, animationSpec = tween(180)) { value, _ -> offset = value }
            }
            return Velocity.Zero
        }
    }
}

/** Slides its content up out of view as [state] collapses, and fades it. The space below follows. */
@Composable
fun CollapsingBar(state: CollapsingBarState, content: @Composable () -> Unit) {
    Box(
        Modifier
            .graphicsLayer { alpha = 1f - state.hiddenFraction }
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val shift = state.offset.roundToInt()
                layout(placeable.width, (placeable.height + shift).coerceAtLeast(0)) {
                    placeable.placeRelative(0, shift)
                }
            },
    ) { content() }
}
