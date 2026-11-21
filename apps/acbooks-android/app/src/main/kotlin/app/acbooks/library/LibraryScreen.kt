package app.acbooks.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Back
import top.yukonga.miuix.kmp.icon.glass.Clear
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LibraryScreen(
    model: LibraryViewModel,
    showSearch: Boolean,
    onDismissSearch: () -> Unit,
    onReadBook: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    backdrop: Backdrop,
) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    var query by rememberSaveable { mutableStateOf("") }
    val books = remember(catalog.books, query) {
        val term = query.trim()
        catalog.books.filter { book ->
            book.title.contains(term, ignoreCase = true) || book.author?.contains(term, ignoreCase = true) == true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.library_title),
                modifier = Modifier.semantics(mergeDescendants = true) { heading() },
                defaultWindowInsetsPadding = false,
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

        GlassBottomSheet(
            show = showSearch,
            title = stringResource(R.string.library_search),
            onDismissRequest = onDismissSearch,
            backdrop = backdrop,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    query, { query = it }, Modifier.fillMaxWidth(),
                    label = stringResource(R.string.library_search_hint), singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }, minWidth = 48.dp, minHeight = 48.dp) {
                                Icon(MiuixIcons.Glass.Clear, stringResource(UiR.string.app_clear))
                            }
                        }
                    },
                )
                TextButton(
                    stringResource(R.string.library_done), onDismissSearch, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
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
