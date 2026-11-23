package app.acbooks.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.acbooks.R
import app.acbooks.core.database.LocalBookCard
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.GlassTransformPopup
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.BookOutline
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Info
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.icon.glass.Tune

internal enum class LibraryMenu { Display, More, Book }

@Composable
internal fun LibraryTopBarActions(
    displayAnchor: GlassPopupAnchor,
    moreAnchor: GlassPopupAnchor,
    onOpenMenu: (LibraryMenu) -> Unit,
) {
    val buttonSize = GlassTopAppBarDefaults.ButtonSize
    GlassIconButton(
        onClick = { onOpenMenu(LibraryMenu.Display) },
        modifier = Modifier.glassPopupAnchor(displayAnchor, buttonSize / 2),
        size = buttonSize,
    ) {
        LibraryMenuIcon(LibraryMenu.Display, Modifier.glassPopupAnchorContent(displayAnchor))
    }
    Spacer(Modifier.width(8.dp))
    GlassIconButton(
        onClick = { onOpenMenu(LibraryMenu.More) },
        modifier = Modifier.glassPopupAnchor(moreAnchor, buttonSize / 2),
        size = buttonSize,
    ) {
        LibraryMenuIcon(LibraryMenu.More, Modifier.glassPopupAnchorContent(moreAnchor))
    }
}

@Composable
private fun LibraryMenuIcon(menu: LibraryMenu, modifier: Modifier = Modifier) {
    val display = menu == LibraryMenu.Display
    Icon(
        imageVector = if (display) MiuixIcons.Glass.Tune else MiuixIcons.Glass.More,
        contentDescription = stringResource(if (display) R.string.library_display_sort else R.string.library_more),
        modifier = modifier,
    )
}

@Composable
internal fun BoxScope.LibraryMenus(
    activeMenu: LibraryMenu?,
    displayAnchor: GlassPopupAnchor,
    moreAnchor: GlassPopupAnchor,
    bookTarget: BookMenuTarget?,
    backdrop: Backdrop,
    mode: LibraryDisplayMode,
    importing: Boolean,
    cloudConnected: Boolean,
    cloudBusy: Boolean,
    onDismiss: (LibraryMenu) -> Unit,
    onDisplayMode: (LibraryDisplayMode) -> Unit,
    onAddBooks: () -> Unit,
    onSync: () -> Unit,
    onRead: (String) -> Unit,
    onDetails: (String) -> Unit,
    onDelete: (LocalBookCard) -> Unit,
) {
    GlassIconDropdownMenu(
        show = activeMenu == LibraryMenu.Display,
        onDismissRequest = { onDismiss(LibraryMenu.Display) },
        anchor = displayAnchor,
        anchorContent = { LibraryMenuIcon(LibraryMenu.Display) },
        entries = listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = stringResource(R.string.library_display_mode),
                        children = listOf(
                            DropdownItem(
                                text = stringResource(R.string.library_compact),
                                selected = mode == LibraryDisplayMode.Compact,
                                onClick = { onDisplayMode(LibraryDisplayMode.Compact) },
                            ),
                            DropdownItem(
                                text = stringResource(R.string.library_comfortable),
                                selected = mode == LibraryDisplayMode.Comfortable,
                                onClick = { onDisplayMode(LibraryDisplayMode.Comfortable) },
                            ),
                        ),
                    ),
                ),
            ),
        ),
        backdrop = backdrop,
    )
    GlassIconDropdownMenu(
        show = activeMenu == LibraryMenu.More,
        onDismissRequest = { onDismiss(LibraryMenu.More) },
        anchor = moreAnchor,
        anchorContent = { LibraryMenuIcon(LibraryMenu.More) },
        entries = listOf(
            DropdownEntry(
                items = buildList {
                    add(DropdownItem(
                        text = stringResource(R.string.library_add_books),
                        enabled = !importing,
                        onClick = onAddBooks,
                    ))
                    if (cloudConnected) add(DropdownItem(
                        text = stringResource(R.string.cloud_sync_now),
                        enabled = !cloudBusy,
                        onClick = onSync,
                    ))
                },
            ),
        ),
        backdrop = backdrop,
    )
    BookContextMenu(
        target = bookTarget,
        show = activeMenu == LibraryMenu.Book,
        backdrop = backdrop,
        onDismiss = { onDismiss(LibraryMenu.Book) },
        onRead = onRead,
        onDetails = onDetails,
        onDelete = onDelete,
    )
}

@Composable
private fun BoxScope.BookContextMenu(
    target: BookMenuTarget?,
    show: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onRead: (String) -> Unit,
    onDetails: (String) -> Unit,
    onDelete: (LocalBookCard) -> Unit,
) {
    val anchor = rememberGlassPopupAnchor()
    var hostCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var placedTarget by remember { mutableStateOf<BookMenuTarget?>(null) }
    // Leave room for the closing spring to overshoot without producing negative geometry.
    val anchorSize = 12.dp

    Box(Modifier.matchParentSize().onGloballyPositioned { hostCoordinates = it }) {
        val host = hostCoordinates
        if (target != null && host?.isAttached == true) {
            Spacer(
                Modifier.align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset {
                        val point = host.windowToLocal(target.anchorPoint)
                        val halfSize = anchorSize.roundToPx() / 2f
                        IntOffset((point.x - halfSize).roundToInt(), (point.y - halfSize).roundToInt())
                    }
                    .size(anchorSize)
                    .glassPopupAnchor(anchor, cornerRadius = anchorSize / 2)
                    .onGloballyPositioned { placedTarget = target },
            )
        }
        // Compose the closed popup before the anchor is placed so the first open animates too.
        GlassTransformPopup(
            show = show && target != null && placedTarget == target,
            onDismissRequest = onDismiss,
            anchor = anchor,
            backdrop = backdrop,
            anchorContent = {},
            simplified = true,
        ) {
            val book = target?.book ?: return@GlassTransformPopup
            GlassPopupItem(
                text = stringResource(
                    if (book.canDownload && !book.hasLocalCopy) R.string.cloud_download_and_read else R.string.library_read_book,
                ),
                icon = MiuixIcons.Glass.BookOutline,
                onClick = { onRead(book.id) },
            )
            GlassPopupItem(
                text = stringResource(R.string.library_book_details),
                icon = MiuixIcons.Glass.Info,
                onClick = { onDetails(book.id) },
            )
            GlassPopupItem(
                text = stringResource(R.string.library_delete_book),
                icon = MiuixIcons.Glass.Delete,
                onClick = { onDelete(book) },
            )
        }
    }
}
