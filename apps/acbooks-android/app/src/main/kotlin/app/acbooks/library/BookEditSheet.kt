package app.acbooks.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.acbooks.R
import java.io.File
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BookEditSheet(
    editor: BookEditorState?,
    cover: File?,
    backdrop: Backdrop,
    library: LibraryViewModel,
    onDismissFinished: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var confirmDiscard by remember(editor?.bookId) { mutableStateOf(false) }
    val saving = editor?.operation == BookEditorOperation.Save
    val ready = editor != null && editor.open && editor.draft != null && editor.operation == null
    val formScroll = key(editor?.bookId) { rememberScrollState() }
    val candidates = editor?.candidates
    fun dismiss() {
        if (saving) return
        if (candidates != null) {
            library.dismissBookCandidates()
            return
        }
        if (editor?.dirty == true) {
            focus.clearFocus()
            keyboard?.hide()
            confirmDiscard = true
        } else {
            library.dismissBookEditor()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) library.chooseBookCover(uri)
    }
    GlassBottomSheet(
        show = editor?.open == true && !confirmDiscard,
        onDismissRequest = ::dismiss,
        onDismissFinished = onDismissFinished,
        allowDismiss = !saving && candidates == null && !confirmDiscard,
        backdrop = backdrop,
    ) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            BookEditHeader(
                canCancel = editor?.open == true && !saving,
                ready = ready,
                onCancel = ::dismiss,
                onFill = {
                    focus.clearFocus()
                    keyboard?.hide()
                    library.fillBookDraft()
                },
                onSave = {
                    focus.clearFocus()
                    keyboard?.hide()
                    library.saveBookEditor()
                },
            )
            val pending = editor?.initial?.let { it.pendingCover || it.retryKeys.isNotEmpty() } == true
            val message = editor?.error ?: if (pending) R.string.book_edit_retry_sync else null
            if (message != null) {
                Text(
                    stringResource(message),
                    color = if (editor?.error != null) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (editor?.operation != null) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(formScroll)
                    .navigationBarsPadding().padding(top = 12.dp, bottom = 12.dp),
            ) {
                editor?.draft?.let { draft ->
                    BookEditForm(
                        draft = draft,
                        cover = cover,
                        backdrop = backdrop,
                        enabled = ready,
                        onChange = library::editBookDraft,
                        onChooseCover = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onUndoCover = library::undoBookCover,
                    )
                }
            }
        }
    }

    DoubanBookCandidates(
        candidates = candidates,
        backdrop = backdrop,
        onSelect = library::chooseBookCandidate,
        onDismiss = library::dismissBookCandidates,
    )
    GlassDialog(
        show = editor?.open == true && confirmDiscard,
        title = stringResource(R.string.book_edit_discard_title),
        backdrop = backdrop,
        onDismissRequest = { confirmDiscard = false },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EditAction(
                R.string.book_edit_keep_editing,
                onClick = { confirmDiscard = false },
                modifier = Modifier.weight(1f),
            )
            EditAction(
                R.string.book_edit_discard,
                onClick = {
                    confirmDiscard = false
                    library.dismissBookEditor()
                    library.finishBookEditorDismissal()
                },
                modifier = Modifier.weight(1f),
                role = GlassDialogButtonRole.Destructive,
            )
        }
    }
}

@Composable
private fun BookEditHeader(
    canCancel: Boolean,
    ready: Boolean,
    onCancel: () -> Unit,
    onFill: () -> Unit,
    onSave: () -> Unit,
) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            EditAction(R.string.book_edit_cancel, onCancel, enabled = canCancel)
            Text(
                stringResource(R.string.book_edit_title),
                modifier = Modifier.semantics { heading() },
                style = MiuixTheme.textStyles.title4,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                EditAction(R.string.book_edit_fill, onFill, enabled = ready)
                EditAction(R.string.book_edit_save, onSave, enabled = ready, role = GlassDialogButtonRole.Primary)
            }
        },
    ) { items, constraints ->
        val width = constraints.maxWidth
        val gap = 4.dp.roundToPx()
        val actionConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val start = items[0].measure(actionConstraints.copy(maxWidth = (width * 0.28f).toInt()))
        val end = items[2].measure(actionConstraints.copy(maxWidth = (width * 0.58f).toInt()))
        val left = start.width + gap
        val right = (width - end.width - gap).coerceAtLeast(left)
        val title = items[1].measure(actionConstraints.copy(maxWidth = right - left))
        val height = maxOf(start.height, title.height, end.height)
        val titleX = ((width - title.width) / 2).coerceIn(left, right - title.width)
        layout(width, height) {
            start.placeRelative(0, (height - start.height) / 2)
            title.placeRelative(titleX, (height - title.height) / 2)
            end.placeRelative(width - end.width, (height - end.height) / 2)
        }
    }
}

@Composable
private fun EditAction(
    label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: GlassDialogButtonRole = GlassDialogButtonRole.Normal,
) {
    GlassDialogButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        role = role,
        minWidth = 48.dp,
        minHeight = 48.dp,
        insideMargin = PaddingValues(horizontal = 6.dp),
    ) {
        Text(
            stringResource(label),
            style = MiuixTheme.textStyles.footnote1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BookEditForm(
    draft: BookEditDraft,
    cover: File?,
    backdrop: Backdrop,
    enabled: Boolean,
    onChange: (BookEditDraft) -> Unit,
    onChooseCover: () -> Unit,
    onUndoCover: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(draft.cover ?: cover, Modifier.width(72.dp), title = draft.title)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EditAction(R.string.book_edit_choose_cover, onChooseCover, enabled = enabled)
                if (draft.cover != null) EditAction(R.string.book_edit_undo_cover, onUndoCover, enabled = enabled)
            }
        }
        EditField(R.string.book_details_book_name, draft.title, enabled) { onChange(draft.copy(title = it)) }
        Text(stringResource(R.string.book_edit_names_hint), style = MiuixTheme.textStyles.footnote1)
        EditField(R.string.book_details_author, draft.authors, enabled, multiline = true) { onChange(draft.copy(authors = it)) }
        EditField(R.string.book_details_translator, draft.translators, enabled, multiline = true) { onChange(draft.copy(translators = it)) }
        EditField(R.string.book_details_isbn, draft.isbn, enabled) { onChange(draft.copy(isbn = it)) }
        EditField(R.string.book_details_publisher, draft.publisher, enabled) { onChange(draft.copy(publisher = it)) }
        EditField(R.string.book_details_publish_date, draft.publicationDate, enabled) { onChange(draft.copy(publicationDate = it)) }
        EditField(R.string.book_details_description, draft.description, enabled, multiline = true, minLines = 3) {
            onChange(draft.copy(description = it))
        }
        EditField(R.string.book_edit_series, draft.series, enabled) { onChange(draft.copy(series = it)) }
        EditField(R.string.book_details_tags, draft.tags, enabled, multiline = true) { onChange(draft.copy(tags = it)) }
        EditField(R.string.book_details_shelves, draft.shelves, enabled, multiline = true) { onChange(draft.copy(shelves = it)) }
        Text(stringResource(R.string.book_details_status), modifier = Modifier.semantics { heading() })
        GlassSegmentedTabRow(
            tabs = listOf(
                stringResource(R.string.book_details_status_unread),
                stringResource(R.string.book_details_status_reading),
                stringResource(R.string.book_details_status_finished),
            ),
            selectedIndex = readingStatuses.indexOf(draft.readingStatus).coerceAtLeast(0),
            onSelect = { if (enabled) onChange(draft.copy(readingStatus = readingStatuses[it])) },
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun EditField(
    label: Int,
    value: String,
    enabled: Boolean,
    multiline: Boolean = false,
    minLines: Int = 1,
    onChange: (String) -> Unit,
) {
    GlassTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(label),
        enabled = enabled,
        singleLine = !multiline,
        minLines = minLines,
        maxLines = if (multiline) 6 else 1,
    )
}

@Composable
private fun DoubanBookCandidates(
    candidates: List<DoubanMetadataCandidate>?,
    backdrop: Backdrop,
    onSelect: (DoubanMetadataCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialog(
        show = candidates != null,
        title = stringResource(R.string.book_edit_douban_candidates),
        backdrop = backdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (candidate in candidates.orEmpty()) {
                GlassDialogButton(onClick = { onSelect(candidate) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(candidate.title)
                        val details = listOfNotNull(candidate.authors.joinToString(", ").ifBlank { null },
                            candidate.publisher, candidate.publicationDate, candidate.isbn).joinToString(" · ")
                        if (details.isNotEmpty()) Text(details, style = MiuixTheme.textStyles.footnote1)
                    }
                }
            }
        }
        EditAction(
            R.string.book_edit_cancel,
            onDismiss,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

private val readingStatuses = listOf("unread", "reading", "done")
