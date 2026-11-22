package app.acbooks.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.acbooks.R
import app.acbooks.core.database.LocalBookCard
import coil3.compose.AsyncImage
import java.io.File
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.BookOutline
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val BookCoverCorner = 10.dp

@Composable
internal fun BookCover(
    file: File?,
    modifier: Modifier = Modifier,
    title: String? = null,
    aspectRatio: Float = 3f / 4f,
) {
    var loaded by remember(file) { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = modifier.aspectRatio(aspectRatio).clip(RoundedCornerShape(BookCoverCorner)),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Box(Modifier.matchParentSize().background(colors.surfaceContainer), contentAlignment = Alignment.Center) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        color = colors.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp),
                    )
                } else {
                    Icon(
                        imageVector = MiuixIcons.Glass.BookOutline,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit,
                onLoading = { loaded = false },
                onSuccess = { loaded = true },
                onError = { loaded = false },
            )
        }
    }
}

internal data class BookMenuTarget(val book: LocalBookCard, val anchorPoint: Offset)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookGridItem(
    book: LocalBookCard,
    cover: File?,
    onRead: () -> Unit,
    onDetails: () -> Unit,
    onShowMenu: (BookMenuTarget) -> Unit,
) {
    val coverCoordinates = remember(book.id) { arrayOfNulls<LayoutCoordinates>(1) }
    val pressPosition = remember(book.id) { mutableStateOf<Offset?>(null) }
    val read = stringResource(R.string.reader_open_book, book.title)
    val actions = stringResource(R.string.library_book_actions)
    Column {
        BookCover(
            file = cover,
            title = book.title,
            modifier = Modifier.fillMaxWidth()
                .onGloballyPositioned { coverCoordinates[0] = it }
                // Record the touch location without consuming pointer events. CombinedClickable
                // still handles taps, long presses, haptics and accessibility semantics.
                .pointerInput(book.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        pressPosition.value = down.position
                        try {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                            } while (event.changes.any { it.id == down.id && it.pressed })
                        } finally {
                            pressPosition.value = null
                        }
                    }
                }
                .semantics { contentDescription = book.title }
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = read,
                    onLongClickLabel = actions,
                    onClick = {
                        pressPosition.value = null
                        onRead()
                    },
                    onLongClick = {
                        coverCoordinates[0]?.takeIf { it.isAttached }?.let { coordinates ->
                            val local = pressPosition.value ?: Offset(
                                coordinates.size.width / 2f,
                                coordinates.size.height / 2f,
                            )
                            onShowMenu(BookMenuTarget(book, coordinates.localToWindow(local)))
                        }
                        pressPosition.value = null
                    },
                ),
        )
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.library_book_details),
                    onClick = onDetails,
                )
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(book.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                book.author ?: stringResource(R.string.library_unknown_author),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun LibraryScreen(
    catalog: LibraryCatalog,
    mode: LibraryDisplayMode,
    scrollBehavior: ScrollBehavior,
    pagePadding: PaddingValues,
    coverFile: (String?) -> File?,
    onRead: (String) -> Unit,
    onDetails: (String) -> Unit,
    onShowMenu: (BookMenuTarget) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val compact = mode == LibraryDisplayMode.Compact
    val horizontalGap = if (compact) 12.dp else 16.dp
    val verticalGap = if (compact) 20.dp else 24.dp
    val phoneColumns = if (compact) 3 else 2
    val wideCellWidth = if (compact) 132.dp else 168.dp
    val layoutDirection = LocalLayoutDirection.current
    val startInset = pagePadding.calculateStartPadding(layoutDirection)
    val endInset = pagePadding.calculateEndPadding(layoutDirection)
    val gridMargin = 16.dp
    val gridPadding = PaddingValues(
        start = startInset + gridMargin,
        top = pagePadding.calculateTopPadding() + gridMargin,
        end = endInset + gridMargin,
        bottom = pagePadding.calculateBottomPadding() + gridMargin,
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val safeWidth = maxWidth - startInset - endInset
        val cols = if (safeWidth < 480.dp) phoneColumns else
            gridColumns(safeWidth - gridMargin * 2, horizontalGap, wideCellWidth, phoneColumns)
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = gridState,
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = gridPadding,
            horizontalArrangement = Arrangement.spacedBy(horizontalGap),
            verticalArrangement = Arrangement.spacedBy(verticalGap),
        ) {
            items(catalog.books, key = { it.id }) { book ->
                BookGridItem(
                    book = book,
                    cover = coverFile(book.sha256),
                    onRead = { onRead(book.id) },
                    onDetails = { onDetails(book.id) },
                    onShowMenu = onShowMenu,
                )
            }
        }
        if (catalog.books.isEmpty()) {
            Box(
                Modifier.matchParentSize().padding(gridPadding),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    !catalog.loaded -> CircularProgressIndicator()
                    catalog.failed -> Text(stringResource(R.string.library_load_failed))
                    else -> Text(stringResource(R.string.library_empty))
                }
            }
        }
    }
}

private fun gridColumns(width: Dp, gap: Dp, cellWidth: Dp, minimum: Int): Int =
    ((width + gap) / (cellWidth + gap)).toInt().coerceAtLeast(minimum)
