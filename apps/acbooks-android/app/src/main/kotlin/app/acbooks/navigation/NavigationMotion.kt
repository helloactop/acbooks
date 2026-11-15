package app.acbooks.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import app.acbooks.core.designsystem.tokens.AppMotion

internal fun pageEnter(): EnterTransition =
    slideInHorizontally(tween(AppMotion.pageDurationMillis, easing = AppMotion.symmetricEasing)) { fullWidth -> fullWidth }

internal fun pageReturnExit(): ExitTransition =
    slideOutHorizontally(tween(AppMotion.pageDurationMillis, easing = AppMotion.symmetricEasing)) { fullWidth -> fullWidth }
