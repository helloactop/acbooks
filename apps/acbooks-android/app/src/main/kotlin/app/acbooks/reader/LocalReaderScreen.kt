package app.acbooks.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acbooks.R
import app.acbooks.core.designsystem.components.LiquidGlassButton
import app.acbooks.core.designsystem.components.AppProgress
import app.acbooks.core.designsystem.components.AppSecondaryTopBar
import app.acbooks.core.designsystem.components.ButtonKind
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.library.LibraryViewModel
import app.acbooks.library.ReadableBookFile
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private sealed interface ReaderState {
    data object Loading : ReaderState
    data object Unavailable : ReaderState
    data class Ready(val file: ReadableBookFile, val initialPosition: String?) : ReaderState
}

@Composable
internal fun LocalReaderScreen(
    model: LibraryViewModel,
    bookId: String,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val title = catalog.books.firstOrNull { it.id == bookId }?.title ?: stringResource(R.string.reader_title)
    val state by produceState<ReaderState>(ReaderState.Loading, bookId) {
        val source = model.openBook(bookId)
        value = if (source == null) ReaderState.Unavailable
        else ReaderState.Ready(source, if (source.format == "pdf") model.readingPosition(source.id) else null)
    }

    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        AppSecondaryTopBar(title, onBack)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (val reader = state) {
                ReaderState.Loading -> AppProgress()
                ReaderState.Unavailable -> Text(stringResource(R.string.reader_open_failed))
                is ReaderState.Ready -> if (reader.file.format == "epub") {
                    val dark = colors.background.luminance() < 0.5f
                    val args = remember(bookId, dark) { EpubDocumentFragment.arguments(bookId, dark) }
                    AndroidFragment<EpubDocumentFragment>(modifier = Modifier.fillMaxSize(), arguments = args)
                } else {
                    PdfBook(reader, model)
                }
            }
        }
    }
}

private data class RenderedPdf(val index: Int, val count: Int, val bitmap: Bitmap)

@Composable
private fun PdfBook(ready: ReaderState.Ready, model: LibraryViewModel) {
    val source = ready.file
    var requestedPage by rememberSaveable(source.id) {
        mutableIntStateOf(runCatching { JSONObject(ready.initialPosition.orEmpty()).optInt("page") }.getOrDefault(0))
    }
    val rendered by produceState<Result<RenderedPdf>?>(null, source.id, requestedPage) {
        value = withContext(Dispatchers.IO) {
            try {
                Result.success(renderPdf(source, requestedPage))
            } catch (failure: IOException) {
                Result.failure(failure)
            } catch (failure: SecurityException) {
                Result.failure(failure)
            } catch (failure: IllegalArgumentException) {
                Result.failure(failure)
            }
        }
    }
    val result = rendered
    val page = result?.getOrNull()
    if (page == null) {
        if (result == null) AppProgress() else Text(stringResource(R.string.reader_open_failed))
        return
    }
    LaunchedEffect(source.id, page.index) {
        model.saveReadingPosition(source.id, JSONObject().put("page", page.index).toString())
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            bitmap = page.bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.reader_page, page.index + 1, page.count),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentScale = ContentScale.Fit,
        )
        Row(
            Modifier.fillMaxWidth().padding(AppSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassButton(
                stringResource(R.string.reader_previous), { requestedPage = page.index - 1 },
                kind = ButtonKind.Secondary, enabled = page.index > 0,
            )
            Text(stringResource(R.string.reader_page, page.index + 1, page.count))
            LiquidGlassButton(
                stringResource(R.string.reader_next), { requestedPage = page.index + 1 },
                kind = ButtonKind.Secondary, enabled = page.index + 1 < page.count,
            )
        }
    }
}

private fun renderPdf(source: ReadableBookFile, requestedPage: Int): RenderedPdf {
    val descriptor = ParcelFileDescriptor.open(source.file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = try {
        PdfRenderer(descriptor)
    } catch (error: Exception) {
        descriptor.close()
        throw error
    }
    return renderer.use {
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
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            RenderedPdf(index, count, bitmap)
        }
    }
}
