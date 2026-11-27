package com.kmail.thekami.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry

private typealias Scope = AnimatedContentTransitionScope<NavBackStackEntry>

private val Smooth = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val DURATION = 280

private enum class Kind { PAGE, DETAIL, COMPOSE }

private fun NavBackStackEntry.kind(): Kind = when (destination.route) {
    Routes.READER -> Kind.DETAIL
    Routes.COMPOSE -> Kind.COMPOSE
    else -> Kind.PAGE
}

/** Slide distance as a share of the screen width. Bigger for a message so it feels like it opens on top. */
private fun Kind.shift(): Int = if (this == Kind.DETAIL) 4 else 12

/**
 * Same duration and curve in both directions, and no delays, so a screen is never blank halfway.
 * That also keeps it smooth when the back gesture drives the animation. Sideways motion follows the
 * reading direction, so it is mirrored for right-to-left languages. When the system animation scale
 * is off, every screen change is instant.
 */
object KmailTransitions {
    fun enter(reduce: Boolean): Scope.() -> EnterTransition = {
        if (reduce) {
            EnterTransition.None
        } else {
            val kind = targetState.kind()
            if (kind == Kind.COMPOSE) {
                slideInVertically(tween(DURATION, easing = Smooth)) { it / 6 } + fadeIn(tween(DURATION))
            } else {
                slideIntoContainer(SlideDirection.Start, tween(DURATION, easing = Smooth)) { it / kind.shift() } +
                    fadeIn(tween(DURATION))
            }
        }
    }

    fun exit(reduce: Boolean): Scope.() -> ExitTransition = {
        if (reduce) {
            ExitTransition.None
        } else {
            val kind = targetState.kind()
            if (kind == Kind.COMPOSE) {
                fadeOut(tween(DURATION))
            } else {
                slideOutOfContainer(SlideDirection.Start, tween(DURATION, easing = Smooth)) { it / kind.shift() } +
                    fadeOut(tween(DURATION))
            }
        }
    }

    fun popEnter(reduce: Boolean): Scope.() -> EnterTransition = {
        if (reduce) {
            EnterTransition.None
        } else {
            val kind = initialState.kind()
            if (kind == Kind.COMPOSE) {
                fadeIn(tween(DURATION))
            } else {
                slideIntoContainer(SlideDirection.End, tween(DURATION, easing = Smooth)) { it / kind.shift() } +
                    fadeIn(tween(DURATION))
            }
        }
    }

    fun popExit(reduce: Boolean): Scope.() -> ExitTransition = {
        if (reduce) {
            ExitTransition.None
        } else {
            val kind = initialState.kind()
            if (kind == Kind.COMPOSE) {
                slideOutVertically(tween(DURATION, easing = Smooth)) { it / 6 } + fadeOut(tween(DURATION))
            } else {
                slideOutOfContainer(SlideDirection.End, tween(DURATION, easing = Smooth)) { it / kind.shift() } +
                    fadeOut(tween(DURATION))
            }
        }
    }
}
