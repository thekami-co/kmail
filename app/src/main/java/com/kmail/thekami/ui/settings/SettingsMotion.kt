package com.kmail.thekami.ui.settings

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

private val Smooth = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val DURATION = 280

/** Hub is the first level, a page the second, an account's detail the third. */
private fun level(key: String): Int = when (key) {
    "Hub" -> 0
    "detail" -> 2
    else -> 1
}

/**
 * Going deeper slides the new page in from the reading-direction side, going back slides it the
 * other way. Same time and curve both ways, and no blank moment in between.
 */
fun AnimatedContentTransitionScope<String>.settingsTransition(): ContentTransform {
    val direction = if (level(targetState) > level(initialState)) SlideDirection.Start else SlideDirection.End
    return (
        slideIntoContainer(direction, tween(DURATION, easing = Smooth)) { it / 10 } +
            fadeIn(tween(DURATION))
        ) togetherWith (
        slideOutOfContainer(direction, tween(DURATION, easing = Smooth)) { it / 10 } +
            fadeOut(tween(DURATION / 2))
        )
}
