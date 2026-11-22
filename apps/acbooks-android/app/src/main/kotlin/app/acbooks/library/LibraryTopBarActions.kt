package app.acbooks.library

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.acbooks.R
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.GlassPopup
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.icon.glass.Tune

internal enum class LibraryMenu { Display, More, Book }

@Composable
internal fun LibraryTopBarActions(
    displayAnchor: GlassPopupAnchor,
    moreAnchor: GlassPopupAnchor,
    onOpenMenu: (LibraryMenu) -> Unit,
) {
    GlassIconButton(
        onClick = { onOpenMenu(LibraryMenu.Display) },
        modifier = Modifier.glassPopupAnchor(displayAnchor, GlassTopAppBarDefaults.ButtonSize / 2),
    ) {
        LibraryMenuIcon(LibraryMenu.Display, Modifier.glassPopupAnchorContent(displayAnchor))
    }
    GlassIconButton(
        onClick = { onOpenMenu(LibraryMenu.More) },
        modifier = Modifier.glassPopupAnchor(moreAnchor, GlassTopAppBarDefaults.ButtonSize / 2),
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
    onDismiss: (LibraryMenu) -> Unit,
    onDisplayMode: (LibraryDisplayMode) -> Unit,
    onAddBooks: () -> Unit,
    onRead: (String) -> Unit,
    onDetails: (String) -> Unit,
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
                items = listOf(
                    DropdownItem(
                        text = stringResource(R.string.library_add_books),
                        enabled = !importing,
                        onClick = onAddBooks,
                    ),
                ),
            ),
        ),
        backdrop = backdrop,
    )
    if (bookTarget != null) {
        GlassPopup(
            show = activeMenu == LibraryMenu.Book,
            onDismissRequest = { onDismiss(LibraryMenu.Book) },
            anchorBounds = bookTarget.anchorBounds,
            backdrop = backdrop,
        ) {
            GlassPopupItem(
                text = stringResource(R.string.library_read_book),
                onClick = { onRead(bookTarget.book.id) },
            )
            GlassPopupItem(
                text = stringResource(R.string.library_book_details),
                onClick = { onDetails(bookTarget.book.id) },
            )
        }
    }
}
