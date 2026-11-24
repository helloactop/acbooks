package app.acbooks.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.acbooks.R
import org.readium.r2.shared.publication.Link
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class ReaderChapter(
    val link: Link,
    val title: String?,
    val depth: Int,
)

@Composable
internal fun ReaderContentsSheet(
    show: Boolean,
    chapters: List<ReaderChapter>?,
    currentHref: String?,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onSelect: (ReaderChapter) -> Unit,
) {
    val selectedIndex = selectedReaderContentsIndex(
        chapters.orEmpty().map { it.link.url().toString() }, currentHref,
    )
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    // Reset the positioning guard for each opening. Wait for a valid chapter
    // if publication/locator data arrives asynchronously, then position once.
    // Subsequent locator emissions must not fight the user's manual scrolling.
    var positionedForOpen by remember(show) { mutableStateOf(false) }
    LaunchedEffect(show, selectedIndex) {
        if (show && selectedIndex >= 0 && !positionedForOpen) {
            listState.scrollToItem(selectedIndex)
            positionedForOpen = true
        }
    }

    GlassBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        backdrop = backdrop,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            Text(
                stringResource(R.string.reader_contents),
                style = MiuixTheme.textStyles.title4,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            if (chapters.isNullOrEmpty()) {
                Text(
                    stringResource(
                        if (chapters == null) R.string.reader_contents_loading
                        else R.string.reader_contents_empty,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
                ) {
                    itemsIndexed(chapters, key = { index, _ -> index }) { index, chapter ->
                        val selected = index == selectedIndex
                        Text(
                            text = chapter.title ?: stringResource(R.string.reader_chapter_fallback, index + 1),
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSelect(chapter) }
                                .heightIn(min = 48.dp)
                                .padding(
                                    start = 16.dp + (chapter.depth.coerceAtMost(6) * 16).dp,
                                    end = 16.dp,
                                    top = 12.dp,
                                    bottom = 12.dp,
                                ),
                            color = if (selected) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
