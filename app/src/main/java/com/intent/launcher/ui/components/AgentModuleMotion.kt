package com.intent.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.intent.launcher.engine.ReducedMotion
import com.intent.launcher.ui.theme.LocalReducedMotion

/**
 * Conversation Agent-module card enter/exit — soft fade+slide when motion is
 * allowed; instant show/hide when [LocalReducedMotion] is true (system Remove
 * animations or Settings Prefer reduced motion). Content is always reachable;
 * motion is never the sole cue.
 */
@Composable
fun AgentModuleMotion(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reduced = LocalReducedMotion.current
    val enterMs = ReducedMotion.cardEnterMs(reduced)
    val exitMs = ReducedMotion.cardExitMs(reduced)
    if (reduced) {
        if (visible) {
            content()
        }
    } else {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = fadeIn(animationSpec = tween(enterMs)) +
                slideInVertically(
                    animationSpec = tween(enterMs),
                    initialOffsetY = { it / 8 }
                ),
            exit = fadeOut(animationSpec = tween(exitMs)) +
                slideOutVertically(
                    animationSpec = tween(exitMs),
                    targetOffsetY = { it / 12 }
                )
        ) {
            content()
        }
    }
}
