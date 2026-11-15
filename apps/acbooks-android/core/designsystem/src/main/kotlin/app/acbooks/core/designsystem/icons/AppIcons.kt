package app.acbooks.core.designsystem.icons

import androidx.compose.ui.graphics.vector.ImageVector
import fluent.ui.system.icons.FluentIcons
import fluent.ui.system.icons.filled.Bookmark
import fluent.ui.system.icons.filled.DataBarVertical
import fluent.ui.system.icons.filled.Heart
import fluent.ui.system.icons.filled.Home
import fluent.ui.system.icons.filled.Library
import fluent.ui.system.icons.regular.Add
import fluent.ui.system.icons.regular.ArrowLeft
import fluent.ui.system.icons.regular.Bookmark
import fluent.ui.system.icons.regular.ChevronRight
import fluent.ui.system.icons.regular.ChevronDown
import fluent.ui.system.icons.regular.Checkmark
import fluent.ui.system.icons.regular.Cloud
import fluent.ui.system.icons.regular.Dismiss
import fluent.ui.system.icons.regular.Heart
import fluent.ui.system.icons.regular.MoreHorizontal
import fluent.ui.system.icons.regular.Edit
import fluent.ui.system.icons.regular.Search
import fluent.ui.system.icons.regular.TextBulletListLtr
import fluent.ui.system.icons.regular.MoreVertical
import fluent.ui.system.icons.regular.PaintBrush
import fluent.ui.system.icons.regular.Person

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
    val Back by lazy { AppIcon(FluentIcons.Regular.ArrowLeft, filled = false) }
    val ChevronRight by lazy { AppIcon(FluentIcons.Regular.ChevronRight, filled = false) }
    val ChevronDown by lazy { AppIcon(FluentIcons.Regular.ChevronDown, filled = false) }
    val Checkmark by lazy { AppIcon(FluentIcons.Regular.Checkmark, filled = false) }
    val Cloud by lazy { AppIcon(FluentIcons.Regular.Cloud, filled = false) }
    val Appearance by lazy { AppIcon(FluentIcons.Regular.PaintBrush, filled = false) }
    val Profile by lazy { AppIcon(FluentIcons.Regular.Person, filled = false) }
    val Clear by lazy { AppIcon(FluentIcons.Regular.Dismiss, filled = false) }
    val More by lazy { AppIcon(FluentIcons.Regular.MoreHorizontal, filled = false) }
    val MoreVertical by lazy { AppIcon(FluentIcons.Regular.MoreVertical, filled = false) }
    val Edit by lazy { AppIcon(FluentIcons.Regular.Edit, filled = false) }
    val Search by lazy { AppIcon(FluentIcons.Regular.Search, filled = false) }
    val Categories by lazy { AppIcon(FluentIcons.Regular.TextBulletListLtr, filled = false) }

    val Favorite by lazy { pair(FluentIcons.Regular.Heart, FluentIcons.Filled.Heart) }
    val Bookmark by lazy { pair(FluentIcons.Regular.Bookmark, FluentIcons.Filled.Bookmark) }

    private fun pair(regular: ImageVector, filled: ImageVector) =
        IconPair(AppIcon(regular, filled = false), AppIcon(filled, filled = true))
}
