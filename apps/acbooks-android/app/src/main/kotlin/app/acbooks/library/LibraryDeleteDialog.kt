package app.acbooks.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.acbooks.R
import app.acbooks.core.database.LocalBookCard
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Two equal-width Glass actions in an explicitly centered dialog. */
@Composable
internal fun LibraryDeleteDialog(
    book: LocalBookCard?,
    show: Boolean,
    busy: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onDeleteLocal: () -> Unit,
    onDeleteEverywhere: () -> Unit,
) {
    GlassDialog(
        show = show && book != null,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        backdrop = backdrop,
        largeScreen = true,
        title = stringResource(R.string.library_delete_title),
        summary = book?.let { stringResource(R.string.library_delete_summary, it.title) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassDialogButton(
                onClick = onDeleteLocal,
                modifier = Modifier.weight(1f),
                enabled = !busy,
                role = GlassDialogButtonRole.Normal,
                insideMargin = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    stringResource(R.string.library_delete_local),
                    style = MiuixTheme.textStyles.button,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GlassDialogButton(
                onClick = onDeleteEverywhere,
                modifier = Modifier.weight(1f),
                enabled = !busy,
                role = GlassDialogButtonRole.Destructive,
                insideMargin = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    stringResource(R.string.library_delete_everywhere),
                    style = MiuixTheme.textStyles.button,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
