package app.acbooks.core.designsystem.icons

import androidx.compose.ui.graphics.vector.ImageVector
import fluent.ui.system.icons.FluentIcons
import fluent.ui.system.icons.filled.Bookmark
import fluent.ui.system.icons.filled.DataBarVertical
import fluent.ui.system.icons.filled.Heart
import fluent.ui.system.icons.filled.Home
import fluent.ui.system.icons.filled.Library
import fluent.ui.system.icons.regular.Add
import fluent.ui.system.icons.regular.Bookmark
import fluent.ui.system.icons.regular.Dismiss
import fluent.ui.system.icons.regular.Heart
import fluent.ui.system.icons.regular.MoreHorizontal

class AppIcon internal constructor(
    internal val vector: ImageVector,
    internal val filled: Boolean,
) {
    internal val controlVector by lazy { if (filled) vector else controlWeight(vector) }
}

class IconPair internal constructor(val regular: AppIcon, val filled: AppIcon) {
    fun forState(active: Boolean): AppIcon = if (active) filled else regular
}

object AppIcons {
    val Home by lazy { AppIcon(FluentIcons.Filled.Home, filled = true) }
    val Library by lazy { AppIcon(FluentIcons.Filled.Library, filled = true) }
    val Statistics by lazy { AppIcon(FluentIcons.Filled.DataBarVertical, filled = true) }

    val Add by lazy { AppIcon(FluentIcons.Regular.Add, filled = false) }
    val Clear by lazy { AppIcon(FluentIcons.Regular.Dismiss, filled = false) }
    val More by lazy { AppIcon(FluentIcons.Regular.MoreHorizontal, filled = false) }

    val Favorite by lazy { pair(FluentIcons.Regular.Heart, FluentIcons.Filled.Heart) }
    val Bookmark by lazy { pair(FluentIcons.Regular.Bookmark, FluentIcons.Filled.Bookmark) }

    private fun pair(regular: ImageVector, filled: ImageVector) =
        IconPair(AppIcon(regular, filled = false), AppIcon(filled, filled = true))
}
