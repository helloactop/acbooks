package app.acbooks.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.acbooks.R
import app.acbooks.library.LibraryViewModel
import app.acbooks.library.ReadableBookFile
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Menu
import top.yukonga.miuix.kmp.theme.MiuixTheme

private sealed interface ReaderState {
    data object Loading : ReaderState
    data object Unavailable : ReaderState
    data class Ready(val file: ReadableBookFile, val initialPosition: String?) : ReaderState
}

@Composable
internal fun LocalReaderScreen(
    model: LibraryViewModel,
    bookId: String,
) {
    val colors = MiuixTheme.colorScheme
    val state by produceState<ReaderState>(ReaderState.Loading, bookId) {
        val source = model.openBook(bookId)
        if (source != null) model.prepareReadingPosition(source.id)
        value = if (source == null) ReaderState.Unavailable
        else ReaderState.Ready(source, if (source.format == "pdf") model.readingPosition(source.id) else null)
    }
    val backdrop = rememberLayerBackdrop()
    var showContents by rememberSaveable(bookId) { mutableStateOf(false) }
    var toolbarVisible by rememberSaveable(bookId) { mutableStateOf(false) }
    var chapters by remember(bookId) { mutableStateOf<List<ReaderChapter>?>(null) }
    var currentHref by remember(bookId) { mutableStateOf<String?>(null) }
    var epubFragment by remember(bookId) { mutableStateOf<EpubDocumentFragment?>(null) }
    // Stable callbacks prevent navigator updates from repeatedly re-registering listeners.
    val contentsObserver: (List<ReaderChapter>?) -> Unit = remember(bookId) {
        { value -> chapters = value }
    }
    val hrefObserver: (String?) -> Unit = remember(bookId) {
        { value -> currentHref = value }
    }
    val centerTapHandler: () -> Unit = remember(bookId) {
        { if (!showContents) toolbarVisible = !toolbarVisible }
    }
    val readerInsets = WindowInsets.navigationBarsIgnoringVisibility
        .union(WindowInsets.displayCutout)
        .union(WindowInsets.ime)
    BackHandler(enabled = toolbarVisible && !showContents) {
        toolbarVisible = false
    }
    // Miuix glass sheets use the Scaffold overlay host; keep the reader's own insets.
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(readerInsets),
            contentAlignment = Alignment.Center,
        ) {
            // Record only the static paper surface. Capturing Readium's WebView inside
            // layerBackdrop causes repeated re-rendering and visible EPUB flicker.
            Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(colors.background))
            when (val reader = state) {
                ReaderState.Loading -> CircularProgressIndicator()
                ReaderState.Unavailable -> Text(stringResource(R.string.reader_open_failed))
                is ReaderState.Ready -> {
                    LifecycleResumeEffect(bookId, reader.file.id) {
                        model.startReadingSession(bookId, reader.file.id)
                        onPauseOrDispose { model.finishReadingSession(reader.file.id) }
                    }
                    if (reader.file.format == "epub") {
                        val dark = colors.background.luminance() < 0.5f
                        val args = remember(bookId, dark) { EpubDocumentFragment.arguments(bookId, dark) }
                        AndroidFragment<EpubDocumentFragment>(
                            modifier = Modifier.fillMaxSize(),
                            arguments = args,
                            onUpdate = { fragment ->
                                if (epubFragment !== fragment) epubFragment = fragment
                                fragment.onContentsChanged = contentsObserver
                                fragment.onCurrentHrefChanged = hrefObserver
                                fragment.onCenterTap = centerTapHandler
                            },
                        )
                    } else {
                        PdfBook(reader, model)
                    }
                }
            }
            val ready = state as? ReaderState.Ready
            if (ready?.file?.format == "epub") {
                if (toolbarVisible && !showContents) GlassIconButton(
                    onClick = {
                        toolbarVisible = false
                        showContents = true
                    },
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp),
                    backdrop = backdrop,
                ) {
                    Icon(
                        MiuixIcons.Glass.Menu,
                        stringResource(R.string.reader_contents),
                    )
                }
                ReaderContentsSheet(
                    show = showContents,
                    chapters = chapters,
                    currentHref = currentHref,
                    backdrop = backdrop,
                    onDismiss = { showContents = false },
                    onSelect = { chapter ->
                        if (epubFragment?.goToChapter(chapter.link) == true) {
                            showContents = false
                            toolbarVisible = false
                        }
                    },
                )
            }
        }
    }
}

private data class RenderedPdf(val index: Int, val count: Int, val bitmap: Bitmap)

@Composable
private fun PdfBook(ready: ReaderState.Ready, model: LibraryViewModel) {
    val source = ready.file
    var lastReadPage by rememberSaveable(source.id) {
        mutableIntStateOf(runCatching { JSONObject(ready.initialPosition.orEmpty()).optInt("page") }.getOrDefault(0))
    }
    var requestedPage by remember(source.id) { mutableIntStateOf(lastReadPage) }
    var displayedPage by remember(source.id) { mutableStateOf<RenderedPdf?>(null) }
    var renderFailed by remember(source.id) { mutableStateOf(false) }

    LaunchedEffect(source.id, requestedPage) {
        val index = requestedPage
        if (displayedPage?.index == index) return@LaunchedEffect
        renderFailed = false
        var undelivered: RenderedPdf? = null
        try {
            val page = withContext(Dispatchers.IO) {
                renderPdf(source, index).also { undelivered = it }
            }
            // A displayed bitmap may still be in use by Compose's render thread after replacement.
            val moved = displayedPage != null
            displayedPage = page
            undelivered = null
            lastReadPage = page.index
            requestedPage = page.index
            if (moved) model.saveReadingPosition(
                source.id,
                JSONObject().put("page", page.index).toString(),
                (page.index + 1) * 100.0 / page.count,
            )
        } catch (failure: Exception) {
            if (failure !is IOException && failure !is SecurityException && failure !is IllegalArgumentException) {
                throw failure
            }
            renderFailed = true
            displayedPage?.let { requestedPage = it.index }
        } finally {
            // withContext may discard the result if the reader is closed while rendering.
            undelivered?.bitmap?.recycle()
        }
    }

    val page = displayedPage
    if (page == null) {
        if (renderFailed) Text(stringResource(R.string.reader_open_failed)) else CircularProgressIndicator()
        return
    }
    val canTurnPage = requestedPage == page.index
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Image(
                bitmap = remember(page.bitmap) { page.bitmap.asImageBitmap() },
                contentDescription = stringResource(R.string.reader_page, page.index + 1, page.count),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            if (renderFailed) {
                Text(
                    stringResource(R.string.reader_open_failed),
                    modifier = Modifier.background(MiuixTheme.colorScheme.surface).padding(8.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                stringResource(R.string.reader_previous), { requestedPage = page.index - 1 },
                enabled = canTurnPage && page.index > 0,
            )
            Text(stringResource(R.string.reader_page, page.index + 1, page.count))
            TextButton(
                stringResource(R.string.reader_next), { requestedPage = page.index + 1 },
                enabled = canTurnPage && page.index + 1 < page.count,
            )
        }
    }
}

private fun renderPdf(source: ReadableBookFile, requestedPage: Int): RenderedPdf =
    readPdf(source.file) { renderer ->
        val count = renderer.pageCount
        if (count == 0) throw IOException("Empty PDF")
        val index = requestedPage.coerceIn(0, count - 1)
        renderer.openPage(index).use { page ->
            val scale = minOf(1440f / page.width, 1920f / page.height)
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).toInt().coerceAtLeast(1),
                (page.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            } catch (failure: Throwable) {
                bitmap.recycle()
                throw failure
            }
            RenderedPdf(index, count, bitmap)
        }
    }
