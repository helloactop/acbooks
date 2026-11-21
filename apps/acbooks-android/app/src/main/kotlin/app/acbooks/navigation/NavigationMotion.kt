package app.acbooks.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

private const val PageDurationMillis = 200
private val PageEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

internal fun pageEnter(): EnterTransition =
    slideInHorizontally(tween(PageDurationMillis, easing = PageEasing)) { fullWidth -> fullWidth }

internal fun pageReturnExit(): ExitTransition =
    slideOutHorizontally(tween(PageDurationMillis, easing = PageEasing)) { fullWidth -> fullWidth }
