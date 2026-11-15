package app.acbooks.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acbooks.R
import app.acbooks.core.database.LocalBookCard
import app.acbooks.core.designsystem.components.LiquidGlassButton
import app.acbooks.core.designsystem.components.LiquidGlassIconButton
import app.acbooks.core.designsystem.components.AppLibraryHeader
import app.acbooks.core.designsystem.components.AppLibraryTools
import app.acbooks.core.designsystem.components.AppListItem
import app.acbooks.core.designsystem.components.AppMenuItem
import app.acbooks.core.designsystem.components.AppProgress
import app.acbooks.core.designsystem.components.AppSecondaryPage
import app.acbooks.core.designsystem.components.AppSheet
import app.acbooks.core.designsystem.components.AppState
import app.acbooks.core.designsystem.components.AppSwitch
import app.acbooks.core.designsystem.components.AppTextField
import app.acbooks.core.designsystem.components.ButtonKind
import app.acbooks.core.designsystem.glass.AppGlassScene
import app.acbooks.core.designsystem.glass.AppSurface
import app.acbooks.core.designsystem.icons.AppIcons
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.core.designsystem.tokens.AppTypography
import app.acbooks.core.designsystem.tokens.IconButtonSize
import app.acbooks.core.designsystem.tokens.TextButtonSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class LibraryPanel { Search, Shelves, Create, Delete }

@Composable
internal fun LibraryScreen(
    model: LibraryViewModel,
    onImport: () -> Unit,
    onReadBook: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val error by model.shelfError.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    var measuredHeader by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf("") }
    var shelfId by rememberSaveable { mutableStateOf<String?>(null) }
    var sortByTitle by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf<LibraryPanel?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val shelf = catalog.shelves.firstOrNull { it.id == shelfId }
    val deleting = catalog.shelves.firstOrNull { it.id == deleteId }
    val shelfBooks = remember(catalog.memberships, shelf) {
        catalog.memberships.filter { it.shelfId == shelf?.id }.mapTo(mutableSetOf()) { it.bookId }
    }
    val books = remember(catalog.books, shelf, shelfBooks, format, query, sortByTitle) {
        val term = query.trim()
        val matching = catalog.books.filter { book ->
            (shelf == null || book.id in shelfBooks) && (format.isEmpty() || book.format == format) &&
                (book.title.contains(term, ignoreCase = true) || book.author?.contains(term, ignoreCase = true) == true)
        }
        if (sortByTitle) matching.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }) else matching
    }
    val headerHeight = if (measuredHeader == 0) AppSpacing.tabHeaderTop + AppSizes.touch
        else with(LocalDensity.current) { measuredHeader.toDp() }
    val scrollOffset = grid.firstVisibleItemScrollOffset
    val pastFirstItem = grid.firstVisibleItemIndex > 0
    val headerOffset = if (pastFirstItem) Int.MAX_VALUE else scrollOffset
    val all = stringResource(R.string.library_all)
    val chooseFiles = stringResource(R.string.library_import_files)
    val shelfLabel = stringResource(R.string.library_shelves)
    val filterLabel = if (format.isEmpty()) all else format.uppercase()
    val openShelves = {
        model.clearShelfError()
        panel = LibraryPanel.Shelves
    }

    AppGlassScene(
        Modifier.fillMaxSize(),
        sampleButtonBackground = true,
        background = {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val cells = when {
                    maxWidth >= 600.dp -> GridCells.Adaptive(140.dp)
                    maxWidth < 340.dp -> GridCells.Fixed(2)
                    else -> GridCells.Fixed(3)
                }
                LazyVerticalGrid(
                    columns = cells,
                    state = grid,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppSpacing.page,
                        end = AppSpacing.page,
                        top = headerHeight + AppSpacing.md,
                        bottom = AppSizes.navigation + AppSpacing.page,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.libraryToolsGap),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            AppLibraryTools(
                                filterLabel = filterLabel,
                                filterItems = listOf(
                                    AppMenuItem(all, { format = "" }),
                                    AppMenuItem(stringResource(R.string.library_format_epub), { format = "epub" }),
                                    AppMenuItem(stringResource(R.string.library_format_pdf), { format = "pdf" }),
                                ),
                                onSearch = { panel = LibraryPanel.Search },
                                onCategories = openShelves,
                            )
                            shelf?.let { Text(it.name, style = AppTypography.headline) }
                            if (query.isNotBlank()) {
                                LiquidGlassButton(query, { query = "" }, size = TextButtonSize.Compact)
                            }
                        }
                    }
                    if (!catalog.loaded) {
                        item(span = { GridItemSpan(maxLineSpan) }) { AppProgress() }
                    } else if (books.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AppState(
                                title = stringResource(
                                    if (catalog.books.isEmpty()) R.string.library_empty else R.string.library_no_results,
                                ),
                            ) {
                                if (catalog.books.isEmpty()) LiquidGlassButton(chooseFiles, onImport, kind = ButtonKind.Primary)
                            }
                        }
                    }
                    items(books, key = { it.id }) { book ->
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                        ) {
                            val readLabel = stringResource(R.string.reader_open_book, book.title)
                            BookCover(
                                book, model,
                                Modifier
                                    .semantics { contentDescription = readLabel }
                                    .clickable(role = Role.Button) { onReadBook(book.id) },
                            )
                            Box(
                                Modifier.fillMaxWidth().heightIn(min = AppSizes.touch)
                                    .clickable(role = Role.Button) { onOpenDetails(book.id) },
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(book.title, style = AppTypography.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                book.author ?: book.format.uppercase(),
                                style = AppTypography.caption,
                                color = LocalAppColors.current.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
    ) {
        AppLibraryHeader(
            title = stringResource(R.string.library_title),
            scrollOffset = headerOffset,
            modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding()
                .onSizeChanged { measuredHeader = it.height },
            moreItems = listOf(
                AppMenuItem(chooseFiles, onImport),
                AppMenuItem(stringResource(R.string.library_shelf_management), openShelves),
                AppMenuItem(
                    stringResource(if (sortByTitle) R.string.library_sort_recent else R.string.library_sort_title),
                    { sortByTitle = !sortByTitle },
                ),
            ),
        )
    }

    val panelTitle = when (panel) {
        LibraryPanel.Search -> stringResource(R.string.library_search)
        LibraryPanel.Shelves -> shelfLabel
        LibraryPanel.Create -> stringResource(R.string.library_create_shelf)
        LibraryPanel.Delete -> stringResource(R.string.library_shelf_delete)
        null -> ""
    }
    AppSheet(panel != null, panelTitle, { panel = null }) {
        when (panel) {
            LibraryPanel.Search -> {
                AppTextField(query, { query = it }, stringResource(R.string.library_search_hint), clearable = true)
                LiquidGlassButton(
                    stringResource(R.string.library_done), { panel = null }, Modifier.fillMaxWidth(),
                    kind = ButtonKind.Primary, size = TextButtonSize.Dialog,
                )
            }
            LibraryPanel.Shelves -> {
                LiquidGlassButton(
                    all, { shelfId = null; panel = null }, Modifier.fillMaxWidth(),
                    kind = if (shelf == null) ButtonKind.Primary else ButtonKind.Secondary,
                )
                catalog.shelves.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiquidGlassButton(
                            option.name, { shelfId = option.id; panel = null }, Modifier.weight(1f),
                            kind = if (shelf == option) ButtonKind.Primary else ButtonKind.Secondary,
                        )
                        LiquidGlassIconButton(
                            AppIcons.Clear, stringResource(R.string.library_shelf_delete),
                            { deleteId = option.id; panel = LibraryPanel.Delete },
                            kind = ButtonKind.Destructive,
                            size = IconButtonSize.XS,
                        )
                    }
                }
                LiquidGlassButton(
                    stringResource(R.string.library_create_shelf),
                    { draft = ""; model.clearShelfError(); panel = LibraryPanel.Create },
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.Primary,
                )
            }
            LibraryPanel.Create -> {
                AppTextField(
                    draft, { draft = it.take(80); model.clearShelfError() },
                    stringResource(R.string.library_shelf_name),
                    errorMessage = error?.let { stringResource(it) },
                )
                LiquidGlassButton(
                    stringResource(R.string.library_create_shelf),
                    { model.createShelf(draft) { panel = LibraryPanel.Shelves } },
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.Primary, size = TextButtonSize.Dialog,
                    enabled = draft.isNotBlank(),
                )
            }
            LibraryPanel.Delete -> if (deleting != null) {
                Text(stringResource(R.string.library_shelf_delete_message, deleting.name))
                LiquidGlassButton(
                    stringResource(R.string.library_shelf_delete),
                    {
                        model.deleteShelf(deleting.id)
                        panel = LibraryPanel.Shelves
                    },
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.Destructive,
                    size = TextButtonSize.Dialog,
                )
                LiquidGlassButton(stringResource(R.string.library_done), { panel = LibraryPanel.Shelves }, Modifier.fillMaxWidth(),
                    size = TextButtonSize.Dialog)
            }
            null -> Unit
        }
        if (panel != LibraryPanel.Create) {
            error?.let { Text(stringResource(it), color = LocalAppColors.current.destructiveText) }
        }
    }
}

@Composable
internal fun LibraryBookDetails(
    model: LibraryViewModel,
    bookId: String,
    onBack: () -> Unit,
) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val error by model.shelfError.collectAsStateWithLifecycle()
    val book = catalog.books.firstOrNull { it.id == bookId }
    AppSecondaryPage(stringResource(R.string.library_book_details), onBack) {
        if (!catalog.loaded) {
            AppProgress()
        } else if (book == null) {
            Text(stringResource(R.string.library_book_missing))
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = AppSizes.navigation * 2).fillMaxWidth()) {
                    BookCover(book, model, Modifier)
                }
            }
            Text(book.title, style = AppTypography.title)
            book.author?.let { Text(it, color = LocalAppColors.current.textSecondary) }
            Text(book.format.uppercase(), style = AppTypography.caption)
            Text(stringResource(R.string.library_shelves), style = AppTypography.headline)
            if (catalog.shelves.isEmpty()) {
                Text(stringResource(R.string.library_shelf_empty), style = AppTypography.small)
            }
            catalog.shelves.forEach { shelf ->
                AppListItem(title = shelf.name, trailing = {
                    AppSwitch(
                        checked = catalog.memberships.any { it.bookId == bookId && it.shelfId == shelf.id },
                        onCheckedChange = { model.setMembership(bookId, shelf.id, it) },
                        label = shelf.name,
                    )
                })
            }
            error?.let { Text(stringResource(it), color = LocalAppColors.current.destructiveText) }
        }
    }
}

@Composable
private fun BookCover(book: LocalBookCard, model: LibraryViewModel, modifier: Modifier) {
    val colors = LocalAppColors.current
    val image by produceState<ImageBitmap?>(null, book.sha256) {
        value = withContext(Dispatchers.IO) {
            model.coverFile(book.sha256)?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
        }
    }
    AppSurface(modifier.fillMaxWidth().aspectRatio(3f / 4f), tint = colors.surfaceSecondary) {
        val cover = image
        if (cover != null) {
            Image(cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                book.title,
                Modifier.align(Alignment.Center).padding(AppSpacing.sm).clearAndSetSemantics {},
                style = AppTypography.small,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun LibraryImportPanel(
    visible: Boolean,
    model: LibraryViewModel,
    onChooseFiles: () -> Unit,
    onOpenLibrary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val status by model.progress.collectAsStateWithLifecycle()
    AppSheet(visible, stringResource(R.string.add_books_title), onDismiss) {
        val progress = status
        if (progress?.running == true) {
            Text(stringResource(R.string.library_import_progress, progress.processed, progress.total))
            AppProgress(Modifier.fillMaxWidth(), progress.processed.toFloat() / progress.total)
            LiquidGlassButton(stringResource(R.string.library_import_cancel), model::cancelImport, Modifier.fillMaxWidth(),
                kind = ButtonKind.Secondary)
        } else {
            if (progress != null) {
                if (progress.processed < progress.total) {
                    Text(stringResource(R.string.library_import_cancelled, progress.processed, progress.total))
                }
                Text(
                    stringResource(
                        R.string.library_import_result,
                        progress.added, progress.duplicate, progress.unsupported, progress.failed,
                    ),
                )
                if (progress.matching > 0) {
                    Text(stringResource(R.string.library_import_matches, progress.matching))
                    LiquidGlassButton(
                        stringResource(R.string.library_import_matches_action),
                        model::importMatches, Modifier.fillMaxWidth(),
                        kind = ButtonKind.Secondary,
                    )
                }
            }
            LiquidGlassButton(
                stringResource(R.string.library_import_files), onChooseFiles, Modifier.fillMaxWidth(),
                kind = ButtonKind.Primary,
            )
            if ((progress?.added ?: 0) > 0) {
                LiquidGlassButton(stringResource(R.string.library_open), onOpenLibrary, Modifier.fillMaxWidth(),
                    kind = ButtonKind.Secondary)
            }
        }
    }
}
