package app.acbooks

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.acbooks.core.database.LocalBookCard
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
import top.yukonga.miuix.kmp.icon.glass.Cloud
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
) {
    val booksById = remember(catalog.books) { catalog.books.associateBy { it.id } }
    val recent = remember(booksById, readingHistory) {
        readingHistory.mapNotNull { reading ->
            booksById[reading.bookId]?.let { it to reading }
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
        Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(vertical = 12.dp)) {
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
                val (currentBook, reading) = recent.first()
                ContinueReadingCard(
                    book = currentBook,
                    fraction = reading.fraction,
                    cover = coverFile(currentBook),
                    onRead = { onReadBook(currentBook.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )

                if (recent.size > 1) {
                    Text(
                        text = stringResource(R.string.home_recently_read),
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp)
                            .semantics { heading() },
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.Bold,
                    )
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // Match mybooks-android's 96dp phone covers and 120dp wide covers.
                        val itemWidth = if (maxWidth >= 600.dp) 120.dp else 96.dp
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(recent.drop(1), key = { it.first.id }) { (book, _) ->
                                RecentlyReadBook(
                                    book = book,
                                    cover = coverFile(book),
                                    width = itemWidth,
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
}

/**
 * The native Miuix Card supplies the squircle and pressed-state behavior.
 * No custom RoundedCornerShape or separate menu button is needed.
 */
@Composable
private fun ContinueReadingCard(
    book: LocalBookCard,
    fraction: Float?,
    cover: File?,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasCover = cover?.isFile == true
    val colors = MiuixTheme.colorScheme
    val primaryTextColor = if (hasCover) Color.White else colors.onSurfaceContainer
    val secondaryTextColor = primaryTextColor.copy(alpha = 0.78f)

    Card(
        modifier = modifier,
        insideMargin = PaddingValues(0.dp),
        onClick = onRead,
        showIndication = true,
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (hasCover) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().blur(24.dp),
                )
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.50f)))
            } else {
                Box(
                    Modifier.matchParentSize().background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                colors.primary.copy(alpha = 0.12f),
                                colors.surfaceContainer,
                            ),
                        ),
                    ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeBookCover(
                    book = book,
                    cover = cover,
                    modifier = Modifier.width(72.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = book.title,
                        color = primaryTextColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = book.author ?: stringResource(R.string.library_unknown_author),
                        color = secondaryTextColor,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (fraction != null) {
                        Text(
                            text = stringResource(R.string.home_percent, (fraction * 100).roundToInt()),
                            color = secondaryTextColor,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

/** The book's native Miuix Card defines its corners; the content only draws the cover. */
@Composable
private fun HomeBookCover(
    book: LocalBookCard,
    cover: File?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.aspectRatio(3f / 4f),
        insideMargin = PaddingValues(0.dp),
        onClick = onClick,
        showIndication = onClick != null,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (cover?.isFile == true) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = book.title,
                    modifier = Modifier.padding(8.dp),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (book.cloudBacked && !book.hasLocalCopy) {
                Card(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(24.dp),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            MiuixIcons.Glass.Cloud,
                            stringResource(R.string.cloud_cover_description),
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyReadBook(
    book: LocalBookCard,
    cover: File?,
    width: androidx.compose.ui.unit.Dp,
    onRead: () -> Unit,
    onDetails: () -> Unit,
) {
    val availability = when {
        book.cloudBacked && !book.hasLocalCopy -> R.string.home_cloud_only
        book.cloudBacked -> R.string.home_downloaded
        else -> R.string.home_local_book
    }
    Column(Modifier.width(width)) {
        HomeBookCover(
            book = book,
            cover = cover,
            onClick = onRead,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.library_book_details), onClick = onDetails)
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = book.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = book.author ?: stringResource(R.string.library_unknown_author),
                fontSize = 10.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(availability),
                fontSize = 10.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
