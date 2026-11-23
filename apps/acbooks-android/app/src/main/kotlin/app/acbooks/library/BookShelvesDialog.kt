package app.acbooks.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acbooks.R
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BookShelvesDialog(
    bookId: String?,
    catalog: LibraryCatalog,
    backdrop: Backdrop,
    library: LibraryViewModel,
    onDismiss: () -> Unit,
) {
    val book = catalog.books.firstOrNull { it.id == bookId }
    val current = catalog.shelves.filter { bookId != null && bookId in it.bookIds }.map { it.shelf.id }.toSet()
    val pending = bookId?.let { id ->
        val changes = remember(id) { library.observePendingBookShelves(id) }
        changes.collectAsStateWithLifecycle(initialValue = false).value
    } ?: false
    var selection by rememberSaveable(bookId) { mutableStateOf(ArrayList(current)) }
    var saving by remember(bookId) { mutableStateOf(false) }
    var error by rememberSaveable(bookId) { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val selected = catalog.shelves.mapNotNull { it.shelf.id.takeIf(selection::contains) }.toSet()
    GlassDialog(
        show = book != null,
        title = stringResource(R.string.library_change_shelves),
        summary = book?.title,
        backdrop = backdrop,
        largeScreen = true,
        onDismissRequest = { if (!saving) onDismiss() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (catalog.shelves.isEmpty()) {
                Text(stringResource(R.string.library_shelf_picker_empty))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(catalog.shelves, key = { it.shelf.id }) { shelf ->
                        val id = shelf.shelf.id
                        CheckboxPreference(
                            title = shelf.shelf.name,
                            checked = id in selected,
                            onCheckedChange = { checked ->
                                selection = ArrayList(if (checked) selected + id else selected - id)
                                error = null
                            },
                            enabled = !saving && (id in selected || selected.size < 20),
                        )
                    }
                }
                if (selected.size >= 20) Text(stringResource(R.string.library_shelf_limit))
            }
            if (pending) Text(stringResource(R.string.library_shelf_sync_pending))
            error?.let { Text(stringResource(it), color = MiuixTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassDialogButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.book_edit_cancel))
                }
                GlassDialogButton(
                    onClick = {
                        val id = book?.id ?: return@GlassDialogButton
                        if (saving) return@GlassDialogButton
                        saving = true
                        scope.launch {
                            try {
                                error = library.saveBookShelves(id, selected)
                                if (error == null) onDismiss()
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving && selected.size <= 20 && (selected != current || pending),
                    modifier = Modifier.weight(1f),
                    role = GlassDialogButtonRole.Primary,
                ) {
                    Text(stringResource(R.string.book_edit_save))
                }
            }
        }
    }
}
