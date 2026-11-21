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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acbooks.R
import app.acbooks.core.database.LocalBookCard
import app.acbooks.core.designsystem.R as UiR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Back
import top.yukonga.miuix.kmp.icon.glass.Clear
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.FolderOutline
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.icon.glass.Ok
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

private enum class LibraryPanel { Search, Shelves, Create, Delete }

@Composable
internal fun LibraryScreen(
    model: LibraryViewModel,
    onImport: () -> Unit,
    onReadBook: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    backdrop: Backdrop,
) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val error by model.shelfError.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    var showMore by rememberSaveable { mutableStateOf(false) }
    var showFormats by rememberSaveable { mutableStateOf(false) }
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
    val all = stringResource(R.string.library_all)
    val chooseFiles = stringResource(R.string.library_import_files)
    val shelfLabel = stringResource(R.string.library_shelves)
    val filterLabel = if (format.isEmpty()) all else format.uppercase()
    val openShelves = {
        model.clearShelfError()
        panel = LibraryPanel.Shelves
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.library_title),
                modifier = Modifier.semantics(mergeDescendants = true) { heading() },
                defaultWindowInsetsPadding = false,
                actions = {
                    Box {
                        IconButton(onClick = { showMore = true }, minWidth = 48.dp, minHeight = 48.dp) {
                            Icon(MiuixIcons.Glass.More, stringResource(UiR.string.app_library_more))
                        }
                        WindowListPopup(show = showMore, onDismissRequest = { showMore = false }) {
                            ListPopupColumn {
                                BasicComponent(title = chooseFiles, onClick = { showMore = false; onImport() })
                                BasicComponent(
                                    title = stringResource(R.string.library_shelf_management),
                                    onClick = { showMore = false; openShelves() },
                                )
                                BasicComponent(
                                    title = stringResource(if (sortByTitle) R.string.library_sort_recent else R.string.library_sort_title),
                                    onClick = { showMore = false; sortByTitle = !sortByTitle },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val cells = when {
                maxWidth >= 600.dp -> GridCells.Adaptive(140.dp)
                maxWidth < 340.dp -> GridCells.Fixed(2)
                else -> GridCells.Fixed(3)
            }
            LazyVerticalGrid(
                columns = cells,
                state = grid,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(stringResource(UiR.string.app_library_search), { panel = LibraryPanel.Search }, Modifier.weight(1f))
                            Box {
                                TextButton(filterLabel, { showFormats = true })
                                WindowListPopup(show = showFormats, onDismissRequest = { showFormats = false }) {
                                    ListPopupColumn {
                                        listOf(
                                            "" to all,
                                            "epub" to stringResource(R.string.library_format_epub),
                                            "pdf" to stringResource(R.string.library_format_pdf),
                                        ).forEach { (value, label) ->
                                            val checked = format == value
                                            BasicComponent(
                                                title = label,
                                                modifier = Modifier.semantics { selected = checked },
                                                titleColor = BasicComponentDefaults.titleColor(
                                                    color = if (checked) {
                                                        MiuixTheme.colorScheme.primary
                                                    } else {
                                                        MiuixTheme.colorScheme.onBackground
                                                    },
                                                ),
                                                endActions = { if (checked) Icon(MiuixIcons.Glass.Ok, null) },
                                                onClick = {
                                                    format = value
                                                    showFormats = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            IconButton(onClick = openShelves, minWidth = 48.dp, minHeight = 48.dp) {
                                Icon(MiuixIcons.Glass.FolderOutline, stringResource(UiR.string.app_library_categories))
                            }
                        }
                        shelf?.let { Text(it.name, style = MiuixTheme.textStyles.title4) }
                        if (query.isNotBlank()) TextButton(query, { query = "" })
                    }
                }
                if (!catalog.loaded) {
                    item(span = { GridItemSpan(maxLineSpan) }) { CircularProgressIndicator() }
                } else if (books.isEmpty() && catalog.books.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.library_no_results))
                    }
                }
                items(books, key = { it.id }) { book ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val readLabel = stringResource(R.string.reader_open_book, book.title)
                        BookCover(
                            book, model,
                            Modifier.semantics { contentDescription = readLabel }
                                .clickable(role = Role.Button) { onReadBook(book.id) },
                        )
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { onOpenDetails(book.id) },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            book.author ?: book.format.uppercase(),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        val panelTitle = when (panel) {
            LibraryPanel.Search -> stringResource(R.string.library_search)
            LibraryPanel.Shelves -> shelfLabel
            LibraryPanel.Create -> stringResource(R.string.library_create_shelf)
            LibraryPanel.Delete -> stringResource(R.string.library_shelf_delete)
            null -> ""
        }
        GlassBottomSheet(
            show = panel != null,
            title = panelTitle,
            onDismissRequest = { panel = null },
            backdrop = backdrop,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (panel) {
                    LibraryPanel.Search -> {
                        TextField(
                            query, { query = it }, Modifier.fillMaxWidth(),
                            label = stringResource(R.string.library_search_hint), singleLine = true,
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) { Icon(MiuixIcons.Glass.Clear, stringResource(UiR.string.app_clear)) }
                                }
                            },
                        )
                        TextButton(
                            stringResource(R.string.library_done), { panel = null }, Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                    LibraryPanel.Shelves -> {
                        TextButton(
                            all, { shelfId = null; panel = null }, Modifier.fillMaxWidth(),
                            colors = if (shelf == null) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                        )
                        catalog.shelves.forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    option.name, { shelfId = option.id; panel = null }, Modifier.weight(1f),
                                    colors = if (shelf == option) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                                )
                                IconButton(onClick = { deleteId = option.id; panel = LibraryPanel.Delete }, minWidth = 48.dp, minHeight = 48.dp) {
                                    Icon(MiuixIcons.Glass.Delete, stringResource(R.string.library_shelf_delete), tint = MiuixTheme.colorScheme.error)
                                }
                            }
                        }
                        TextButton(
                            stringResource(R.string.library_create_shelf),
                            { draft = ""; model.clearShelfError(); panel = LibraryPanel.Create },
                            Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                    LibraryPanel.Create -> {
                        TextField(
                            draft, { draft = it.take(80); model.clearShelfError() }, Modifier.fillMaxWidth(),
                            label = stringResource(R.string.library_shelf_name), singleLine = true,
                        )
                        error?.let { Text(stringResource(it), color = MiuixTheme.colorScheme.error) }
                        TextButton(
                            stringResource(R.string.library_create_shelf),
                            { model.createShelf(draft) { panel = LibraryPanel.Shelves } },
                            Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            enabled = draft.isNotBlank(),
                        )
                    }
                    LibraryPanel.Delete -> if (deleting != null) {
                        Text(stringResource(R.string.library_shelf_delete_message, deleting.name))
                        TextButton(
                            stringResource(R.string.library_shelf_delete),
                            {
                                model.deleteShelf(deleting.id)
                                panel = LibraryPanel.Shelves
                            },
                            Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                color = MiuixTheme.colorScheme.error, textColor = MiuixTheme.colorScheme.onError,
                            ),
                        )
                        TextButton(stringResource(R.string.library_done), { panel = LibraryPanel.Shelves }, Modifier.fillMaxWidth())
                    }
                    null -> Unit
                }
                if (panel != LibraryPanel.Create) {
                    error?.let { Text(stringResource(it), color = MiuixTheme.colorScheme.error) }
                }
            }
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
    Scaffold(topBar = {
        SmallTopAppBar(
            title = stringResource(R.string.library_book_details),
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            navigationIcon = {
                IconButton(onBack, minWidth = 48.dp, minHeight = 48.dp) {
                    Icon(MiuixIcons.Glass.Back, stringResource(UiR.string.app_back))
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!catalog.loaded) {
                CircularProgressIndicator()
            } else if (book == null) {
                Text(stringResource(R.string.library_book_missing))
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = 128.dp).fillMaxWidth()) {
                        BookCover(book, model, Modifier)
                    }
                }
                Text(book.title, style = MiuixTheme.textStyles.title2)
                book.author?.let { Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                Text(book.format.uppercase(), style = MiuixTheme.textStyles.footnote1)
                Text(stringResource(R.string.library_shelves), style = MiuixTheme.textStyles.title4)
                if (catalog.shelves.isEmpty()) {
                    Text(stringResource(R.string.library_shelf_empty), style = MiuixTheme.textStyles.body2)
                }
                catalog.shelves.forEach { shelf ->
                    SwitchPreference(
                        title = shelf.name,
                        checked = catalog.memberships.any { it.bookId == bookId && it.shelfId == shelf.id },
                        onCheckedChange = { model.setMembership(bookId, shelf.id, it) },
                    )
                }
                error?.let { Text(stringResource(it), color = MiuixTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun BookCover(book: LocalBookCard, model: LibraryViewModel, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, book.sha256) {
        value = withContext(Dispatchers.IO) {
            model.coverFile(book.sha256)?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
        }
    }
    Card(modifier.fillMaxWidth().aspectRatio(3f / 4f), insideMargin = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val cover = image
            if (cover != null) {
                Image(cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(
                    book.title,
                    Modifier.padding(8.dp).clearAndSetSemantics {},
                    style = MiuixTheme.textStyles.body2,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun LibraryImportPanel(
    visible: Boolean,
    model: LibraryViewModel,
    backdrop: Backdrop,
    onChooseFiles: () -> Unit,
    onOpenLibrary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val status by model.progress.collectAsStateWithLifecycle()
    GlassBottomSheet(
        show = visible,
        title = stringResource(R.string.add_books_title),
        onDismissRequest = onDismiss,
        backdrop = backdrop,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val progress = status
            if (progress?.running == true) {
                Text(stringResource(R.string.library_import_progress, progress.processed, progress.total))
                LinearProgressIndicator(progress = { progress.processed.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
                TextButton(stringResource(R.string.library_import_cancel), model::cancelImport, Modifier.fillMaxWidth())
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
                        TextButton(
                            stringResource(R.string.library_import_matches_action),
                            model::importMatches, Modifier.fillMaxWidth(),
                        )
                    }
                }
                TextButton(
                    stringResource(R.string.library_import_files), onChooseFiles, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                if ((progress?.added ?: 0) > 0) {
                    TextButton(stringResource(R.string.library_open), onOpenLibrary, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
