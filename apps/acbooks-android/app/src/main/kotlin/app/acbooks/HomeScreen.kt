package app.acbooks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.localToWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.acbooks.core.database.LocalBookCard
import app.acbooks.library.BookCover
import app.acbooks.library.BookMenuTarget
import app.acbooks.library.LibraryCatalog
import app.acbooks.library.RecentBookReading
import coil3.compose.AsyncImage
import java.io.File
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HomeTopBar(
    avatar: ImageBitmap?,
    backdrop: Backdrop,
    scrollBehavior: ScrollBehavior,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hour by produceState(initialValue = LocalTime.now().hour) {
        while (true) {
            val time = LocalTime.now()
            value = time.hour
            delay(60_000L - time.second * 1_000L - time.nano / 1_000_000L)
        }
    }
    val greetingResource = when {
        hour < 12 -> R.string.home_good_morning
        hour < 18 -> R.string.home_good_afternoon
        else -> R.string.home_good_evening
    }
    val greeting = stringResource(greetingResource)

    GlassTopAppBar(
        title = greeting,
        largeTitle = greeting,
        modifier = Modifier.semantics { heading() },
        contentModifier = modifier,
        expandable = true,
        progressiveBlur = true,
        backdrop = backdrop,
        scrollBehavior = scrollBehavior,
        actions = {
            GlassIconButton(onClick = onOpenSettings) {
                ProfileAvatar(
                    avatar = avatar,
                    contentDescription = stringResource(R.string.home_open_settings),
                    modifier = Modifier.size(32.dp),
                )
            }
        },
    )
}

@Composable
internal fun HomeScreen(
    catalog: LibraryCatalog,
    readingHistory: List<RecentBookReading>,
    coverFile: (LocalBookCard) -> File?,
    scrollBehavior: ScrollBehavior,
    scaffoldPadding: PaddingValues,
    onOpenLibrary: () -> Unit,
    onReadBook: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onShowMenu: (BookMenuTarget) -> Unit,
) {
    val booksById = remember(catalog.books) { catalog.books.associateBy { it.id } }
    val recent = remember(booksById, readingHistory) {
        readingHistory.mapNotNull { history ->
            booksById[history.bookId]?.let { book -> book to history }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(scaffoldPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(vertical = 16.dp)) {
            if (!catalog.loaded || catalog.failed || recent.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.home_continue_reading),
                            modifier = Modifier.semantics { heading() },
                            style = MiuixTheme.textStyles.title2,
                        )
                        Text(
                            text = stringResource(
                                if (catalog.failed) R.string.library_load_failed else R.string.home_no_reading_history,
                            ),
                        )
                        TextButton(
                            text = stringResource(R.string.home_open_library),
                            onClick = onOpenLibrary,
                            minHeight = 48.dp,
                        )
                    }
                }
            } else {
                val (currentBook, currentReading) = recent.first()
                ContinueReadingCard(
                    book = currentBook,
                    fraction = currentReading.fraction,
                    cover = coverFile(currentBook),
                    onRead = { onReadBook(currentBook.id) },
                    onMenu = onShowMenu,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                if (recent.size > 1) {
                    Text(
                        text = stringResource(R.string.home_recently_read),
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 16.dp)
                            .semantics { heading() },
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.Bold,
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(recent.drop(1), key = { it.first.id }) { (book, _) ->
                            RecentlyReadBook(
                                book = book,
                                cover = coverFile(book),
                                onRead = { onReadBook(book.id) },
                                onDetails = { onOpenBook(book.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Cover-tinted, blurred hero with a separate menu action anchored to the existing book popup. */
@Composable
private fun ContinueReadingCard(
    book: LocalBookCard,
    fraction: Float?,
    cover: File?,
    onRead: () -> Unit,
    onMenu: (BookMenuTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    val menuPosition = remember(book.id) { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        modifier = modifier
            .clip(shape)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.reader_open_book, book.title),
                onClick = onRead,
            ),
    ) {
        if (cover?.isFile == true) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(38.dp),
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF726775).copy(alpha = 0.74f),
                        Color(0xFF413B49).copy(alpha = 0.82f),
                    ),
                ),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 168.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BookCover(
                file = cover,
                title = book.title,
                modifier = Modifier.width(96.dp),
                cloudOnly = book.cloudBacked && !book.hasLocalCopy,
            )
            Column(
                modifier = Modifier.weight(1f).padding(end = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    book.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    book.author ?: stringResource(R.string.library_unknown_author),
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (fraction != null) {
                    Text(
                        stringResource(R.string.home_percent, (fraction * 100).roundToInt()),
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 16.sp,
                    )
                }
            }
        }
        GlassIconButton(
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 12.dp)
                .onGloballyPositioned { menuPosition[0] = it },
            size = 40.dp,
            onClick = {
                menuPosition[0]?.takeIf { it.isAttached }?.let { coordinates ->
                    onMenu(
                        BookMenuTarget(
                            book,
                            coordinates.localToWindow(
                                Offset(coordinates.size.width / 2f, coordinates.size.height / 2f),
                            ),
                        ),
                    )
                }
            },
        ) {
            Icon(
                imageVector = MiuixIcons.Glass.More,
                contentDescription = stringResource(R.string.library_book_actions),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun RecentlyReadBook(
    book: LocalBookCard,
    cover: File?,
    onRead: () -> Unit,
    onDetails: () -> Unit,
) {
    val availability = when {
        book.cloudBacked && !book.hasLocalCopy -> R.string.home_cloud_only
        book.cloudBacked -> R.string.home_downloaded
        else -> R.string.home_local_book
    }
    Column(Modifier.width(122.dp)) {
        BookCover(
            file = cover,
            title = book.title,
            cloudOnly = book.cloudBacked && !book.hasLocalCopy,
            modifier = Modifier.fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.reader_open_book, book.title),
                    onClick = onRead,
                ),
        )
        Column(
            modifier = Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.library_book_details), onClick = onDetails)
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                book.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                book.author ?: stringResource(R.string.library_unknown_author),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(availability),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
    }
}
