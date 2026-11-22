package app.acbooks.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.acbooks.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton

@Composable
internal fun LibraryImportDialog(
    progress: ImportProgress?,
    backdrop: Backdrop,
    onStop: () -> Unit,
    onImportMatches: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialog(
        show = progress != null,
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        title = stringResource(R.string.library_add_books),
    ) {
        if (progress != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (progress.running) {
                    Text(stringResource(R.string.library_import_progress, progress.processed, progress.total))
                    GlassDialogButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.library_import_cancel))
                    }
                } else {
                    if (progress.processed < progress.total) {
                        Text(stringResource(R.string.library_import_cancelled, progress.processed, progress.total))
                    }
                    Text(stringResource(
                        R.string.library_import_result,
                        progress.added,
                        progress.duplicate,
                        progress.unsupported,
                        progress.failed,
                    ))
                    if (progress.matching > 0) {
                        Text(stringResource(R.string.library_import_matches, progress.matching))
                        GlassDialogButton(onClick = onImportMatches, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.library_import_matches_action))
                        }
                    }
                    GlassDialogButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.library_done))
                    }
                }
            }
        }
    }
}
