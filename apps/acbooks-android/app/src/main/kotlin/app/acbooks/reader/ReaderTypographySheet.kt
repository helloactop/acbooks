package app.acbooks.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import app.acbooks.R
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The sheet owns a local draft so a drag doesn't write DataStore and repaginate
 * the EPUB on every pointer move. Slider changes commit on gesture completion;
 * the other controls apply immediately through the same persistence callback.
 */
@Composable
internal fun ReaderTypographySheet(
    show: Boolean,
    settings: ReaderTypographySettings?,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onSave: (ReaderTypographySettings) -> Unit,
) {
    var draft by remember { mutableStateOf<ReaderTypographySettings?>(null) }
    LaunchedEffect(show, settings) {
        if (!show) draft = null
        else if (draft == null) draft = settings
    }

    GlassBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        backdrop = backdrop,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            Text(
                stringResource(R.string.reader_typography_title),
                modifier = Modifier.fillMaxWidth().semantics { heading() },
                style = MiuixTheme.textStyles.title4,
                textAlign = TextAlign.Center,
            )

            val current = draft
            if (current == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            } else {
                fun save(next: ReaderTypographySettings) {
                    draft = next
                    onSave(next)
                }

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(max = 540.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    TypographySection(R.string.reader_typography_font)
                    GlassSegmentedTabRow(
                        tabs = listOf(
                            stringResource(R.string.reader_typography_font_book),
                            stringResource(R.string.reader_typography_font_serif),
                            stringResource(R.string.reader_typography_font_sans),
                            stringResource(R.string.reader_typography_font_mono),
                        ),
                        selectedIndex = ReaderFontFamily.entries.indexOf(current.fontFamily),
                        onSelect = {
                            val family = ReaderFontFamily.entries[it]
                            save(current.copy(
                                fontFamily = family,
                                publisherStyles = if (family != ReaderFontFamily.BOOK) false
                                    else current.publisherStyles,
                            ))
                        },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    TypographySlider(
                        title = stringResource(R.string.reader_typography_size),
                        value = current.fontSize,
                        defaultValue = 1f,
                        range = 0.75f..2f,
                        steps = 24,
                        format = { "${(it * 100).roundToInt()}%" },
                        onPreview = { draft = current.copy(fontSize = it) },
                        onCommit = { draft?.let(onSave) },
                    )

                    TypographySection(R.string.reader_typography_paragraph)
                    GlassSegmentedTabRow(
                        tabs = listOf(
                            stringResource(R.string.reader_typography_publisher),
                            stringResource(R.string.reader_typography_optimized),
                        ),
                        selectedIndex = if (current.publisherStyles == false) 1 else 0,
                        onSelect = { save(current.copy(publisherStyles = it == 0)) },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.reader_typography_publisher_note),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )

                    TypographySlider(
                        title = stringResource(R.string.reader_typography_line_height),
                        value = current.lineHeight,
                        defaultValue = 1.2f,
                        range = 1f..2f,
                        steps = 9,
                        format = { "${((it * 10).roundToInt() / 10.0)}×" },
                        onPreview = {
                            draft = current.copy(lineHeight = it, publisherStyles = false)
                        },
                        onCommit = { draft?.let(onSave) },
                    )

                    TypographySection(R.string.reader_typography_page)
                    TypographySlider(
                        title = stringResource(R.string.reader_typography_margins),
                        value = current.pageMargins,
                        defaultValue = 1f,
                        range = 0.5f..2f,
                        steps = 14,
                        format = { "${((it * 10).roundToInt() / 10.0)}×" },
                        onPreview = { draft = current.copy(pageMargins = it) },
                        onCommit = { draft?.let(onSave) },
                    )

                    TypographySection(R.string.reader_typography_appearance)
                    GlassSegmentedTabRow(
                        tabs = listOf(
                            stringResource(R.string.reader_typography_theme_system),
                            stringResource(R.string.reader_typography_theme_light),
                            stringResource(R.string.reader_typography_theme_dark),
                            stringResource(R.string.reader_typography_theme_sepia),
                        ),
                        selectedIndex = ReaderAppearance.entries.indexOf(current.appearance),
                        onSelect = { save(current.copy(appearance = ReaderAppearance.entries[it])) },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    GlassDialogButton(
                        onClick = { save(ReaderTypographySettings()) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        minHeight = 48.dp,
                    ) {
                        Text(stringResource(R.string.reader_typography_reset))
                    }
                }
            }
        }
    }
}

@Composable
private fun TypographySection(title: Int) {
    Text(
        stringResource(title),
        style = MiuixTheme.textStyles.body1,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun TypographySlider(
    title: String,
    value: Double?,
    defaultValue: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Double) -> String,
    onPreview: (Double) -> Unit,
    onCommit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title)
            Text(
                format(value ?: defaultValue.toDouble()),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Slider(
            value = value?.toFloat() ?: defaultValue,
            onValueChange = { onPreview(it.toDouble()) },
            onValueChangeFinished = onCommit,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
