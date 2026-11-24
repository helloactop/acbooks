package app.acbooks.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import top.yukonga.miuix.kmp.glass.GlassCard
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
    bookId: String,
    profile: ReaderTypographyProfile?,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onSaveGlobal: (ReaderTypographySettings) -> Unit,
    onSaveBook: (ReaderTypographySettings) -> Unit,
    onClearBook: () -> Unit,
) {
    var draft by remember(bookId) { mutableStateOf<ReaderTypographySettings?>(null) }
    var editingScope by remember(bookId) { mutableStateOf(TypographyEditingScope.GLOBAL) }

    LaunchedEffect(show, bookId) {
        if (!show) {
            draft = null
        } else {
            editingScope = if (profile?.hasBookOverride == true) TypographyEditingScope.BOOK
                else TypographyEditingScope.GLOBAL
            draft = if (editingScope == TypographyEditingScope.BOOK) profile?.bookOverride else profile?.global
        }
    }
    // The first DataStore emission can arrive after the sheet animation starts.
    LaunchedEffect(show, bookId, profile) {
        if (show && draft == null && profile != null) {
            editingScope = if (profile.hasBookOverride) TypographyEditingScope.BOOK
                else TypographyEditingScope.GLOBAL
            draft = if (editingScope == TypographyEditingScope.BOOK) profile.bookOverride else profile.global
        }
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
                    if (editingScope == TypographyEditingScope.GLOBAL) onSaveGlobal(next)
                    else onSaveBook(next)
                }

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(max = 540.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TypographySection(R.string.reader_typography_scope)
                            GlassSegmentedTabRow(
                                tabs = listOf(
                                    stringResource(R.string.reader_typography_scope_global),
                                    stringResource(R.string.reader_typography_scope_book),
                                ),
                                selectedIndex = if (editingScope == TypographyEditingScope.GLOBAL) 0 else 1,
                                onSelect = { index ->
                                    editingScope = if (index == 0) TypographyEditingScope.GLOBAL
                                        else TypographyEditingScope.BOOK
                                    draft = if (editingScope == TypographyEditingScope.GLOBAL) profile?.global
                                        else profile?.bookOverride ?: profile?.global
                                },
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                stringResource(
                                    when {
                                        editingScope == TypographyEditingScope.GLOBAL && profile?.hasBookOverride == true ->
                                            R.string.reader_typography_global_override_note
                                        editingScope == TypographyEditingScope.GLOBAL ->
                                            R.string.reader_typography_global_note
                                        profile?.hasBookOverride == true ->
                                            R.string.reader_typography_book_note
                                        else -> R.string.reader_typography_book_new_note
                                    },
                                ),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TypographySection(R.string.reader_typography_paragraph)
                            GlassSegmentedTabRow(
                                tabs = listOf(
                                    stringResource(R.string.reader_typography_publisher),
                                    stringResource(R.string.reader_typography_optimized),
                                ),
                                selectedIndex = if (current.isOptimizedLayout) 1 else 0,
                                onSelect = { save(current.withPublisherLayout(original = it == 0)) },
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                stringResource(R.string.reader_typography_publisher_note),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }

                    // Publisher mode has no editable layout controls. The
                    // saved optimized values remain in draft and DataStore.
                    if (current.isOptimizedLayout) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                        save(current.copy(fontFamily = family))
                                    },
                                    backdrop = backdrop,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                TypographySlider(
                                    title = stringResource(R.string.reader_typography_size),
                                    value = current.fontSize,
                                    defaultValue = 1.3f,
                                    range = 0.75f..2f,
                                    steps = 24,
                                    format = { "${(it * 100).roundToInt()}%" },
                                    onPreview = { draft = current.copy(fontSize = it) },
                                    onCommit = { draft?.let { save(it) } },
                                )

                                TypographySlider(
                                    title = stringResource(R.string.reader_typography_line_height),
                                    value = current.lineHeight,
                                    defaultValue = 1.5f,
                                    range = 1f..2f,
                                    steps = 9,
                                    format = { "${((it * 10).roundToInt() / 10.0)}×" },
                                    onPreview = {
                                        draft = current.copy(lineHeight = it)
                                    },
                                    onCommit = { draft?.let { save(it) } },
                                )
                            }
                        }

                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                TypographySection(R.string.reader_typography_page)
                                TypographySlider(
                                    title = stringResource(R.string.reader_typography_margins),
                                    value = current.pageMargins,
                                    defaultValue = 1f,
                                    range = 0.5f..2f,
                                    steps = 14,
                                    format = { "${((it * 10).roundToInt() / 10.0)}×" },
                                    onPreview = { draft = current.copy(pageMargins = it) },
                                    onCommit = { draft?.let { save(it) } },
                                )
                            }
                        }


                    }

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        }
                    }

                    GlassDialogButton(
                        onClick = {
                            if (editingScope == TypographyEditingScope.GLOBAL) {
                                save(defaultReaderTypographySettings())
                            } else {
                                onClearBook()
                                editingScope = TypographyEditingScope.GLOBAL
                                draft = profile?.global ?: defaultReaderTypographySettings()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        minHeight = 48.dp,
                    ) {
                        Text(
                            stringResource(
                                if (editingScope == TypographyEditingScope.GLOBAL) R.string.reader_typography_reset
                                else R.string.reader_typography_book_inherit,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private enum class TypographyEditingScope { GLOBAL, BOOK }

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
