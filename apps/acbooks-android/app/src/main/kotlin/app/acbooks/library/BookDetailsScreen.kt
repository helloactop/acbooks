package app.acbooks.library

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acbooks.R
import app.acbooks.core.database.LocalBookCard
import java.io.File
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.springAnimateToPage

@Composable
internal fun BookDetailsScreen(
    book: LocalBookCard?,
    loading: Boolean,
    library: LibraryViewModel,
    onBack: () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val scrollBehavior = MiuixScrollBehavior()
    val surface = MiuixTheme.colorScheme.surface
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expandableTitle = maxHeight >= 520.dp
        // Only the flat page surface is recorded. Cards and both glass controls draw above it.
        Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(surface))
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopAppBar(
                    title = stringResource(R.string.book_details_title),
                    modifier = Modifier.semantics { heading() },
                    expandable = expandableTitle,
                    scrollBehavior = if (expandableTitle) scrollBehavior else null,
                    backdrop = backdrop,
                    navigationIcon = {
                        GlassIconButton(onClick = onBack) {
                            Icon(MiuixIcons.Glass.Back, stringResource(R.string.navigate_back))
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                when {
                    loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    book == null -> Text(
                        stringResource(R.string.library_book_missing),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> key(book.id) {
                        val details by remember(library, book.id) { library.observeBookDetails(book.id) }
                            .collectAsStateWithLifecycle(BookDetailsData())
                        BookDetailsContent(
                            book = book,
                            cover = library.coverFile(book),
                            details = details,
                            backdrop = backdrop,
                            scrollBehavior = if (expandableTitle) scrollBehavior else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookDetailsContent(
    book: LocalBookCard,
    cover: File?,
    details: BookDetailsData,
    backdrop: Backdrop,
    scrollBehavior: ScrollBehavior?,
) {
    val pager = rememberPagerState { 2 }
    val infoList = rememberLazyListState()
    val readingList = rememberLazyListState()
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }
    val headerScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 568.dp
        // Keep the summary reachable when the available height or the font scale is extreme.
        val summaryMaxHeight = if (twoColumns) maxHeight else maxHeight * 0.48f
        val summary: @Composable (Modifier) -> Unit = { modifier ->
            BookDetailsSummary(
                book = book,
                cover = cover,
                progress = details.readingLocation?.fraction,
                modifier = modifier.heightIn(max = summaryMaxHeight).verticalScroll(headerScroll),
            )
        }
        val pages: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                val dragged by pager.interactionSource.collectIsDraggedAsState()
                GlassSegmentedTabRow(
                    tabs = listOf(
                        stringResource(R.string.book_details_info_tab),
                        stringResource(R.string.book_details_reading_tab),
                    ),
                    selectedIndex = if (dragged) pager.settledPage else pager.targetPage,
                    onSelect = { page -> scope.launch { pager.springAnimateToPage(page) } },
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = PagerNavigationSpringSpec),
                ) { page ->
                    LazyColumn(
                        state = if (page == 0) infoList else readingList,
                        modifier = Modifier.fillMaxSize().then(
                            if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                            else Modifier,
                        ),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (page == 0) {
                            item(key = "description") {
                                BookDescriptionCard(details.metadata?.description, descriptionExpanded) {
                                    descriptionExpanded = !descriptionExpanded
                                }
                            }
                            item(key = "bibliography") { BookBibliographyCard(book, details) }
                            item(key = "library") { BookLibraryCard(book, details) }
                        } else {
                            item(key = "statistics") { BookReadingStatisticsCard(details.readingStatistics) }
                        }
                    }
                }
            }
        }

        if (twoColumns) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                summary(Modifier.widthIn(max = 360.dp).fillMaxWidth(0.4f))
                pages(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                summary(Modifier.fillMaxWidth())
                pages(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun BookDetailsSummary(
    book: LocalBookCard,
    cover: File?,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BookCover(
            cover, Modifier.size(width = 72.dp, height = 100.dp), aspectRatio = 72f / 100f,
            cloudOnly = book.cloudBacked && !book.hasLocalCopy,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                book.title,
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                book.author?.takeIf(String::isNotBlank) ?: stringResource(R.string.library_unknown_author),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress != null) ReadingProgressBar(progress)
        }
    }
}
