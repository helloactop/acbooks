package app.acbooks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.acbooks.library.LibraryCatalog
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ShelfSettingsScreen(
    catalog: LibraryCatalog,
    active: Boolean,
    onBack: () -> Unit,
    onSave: suspend (String?, String) -> Int?,
    onDelete: suspend (String) -> Int?,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var shelfId by rememberSaveable { mutableStateOf<String?>(null) }
    var originalName by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun edit(id: String?, initialName: String) {
        shelfId = id
        originalName = initialName
        name = initialName
        confirmingDelete = false
        error = null
        showDialog = true
    }

    fun submit() {
        if (saving) return
        val id = shelfId
        val deleting = confirmingDelete
        saving = true
        scope.launch {
            try {
                error = if (deleting && id != null) onDelete(id) else onSave(id, name)
                if (error == null) showDialog = false
            } finally {
                saving = false
            }
        }
    }

    SettingsPage(
        title = stringResource(R.string.settings_shelves),
        onBack = onBack,
        overlay = { backdrop ->
            GlassDialog(
                show = active && showDialog,
                title = stringResource(when {
                    confirmingDelete -> R.string.library_shelf_delete
                    shelfId != null -> R.string.library_shelf_rename
                    else -> R.string.library_shelf_add
                }),
                summary = if (confirmingDelete) {
                    stringResource(R.string.library_shelf_delete_summary, originalName)
                } else null,
                backdrop = backdrop,
                largeScreen = true,
                onDismissRequest = { if (!saving) showDialog = false },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!confirmingDelete) {
                        GlassTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                error = null
                            },
                            label = stringResource(R.string.library_shelf_name),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !saving,
                            singleLine = true,
                        )
                    }
                    error?.let { Text(stringResource(it), color = MiuixTheme.colorScheme.error) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassDialogButton(
                            onClick = { showDialog = false },
                            enabled = !saving,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.book_edit_cancel))
                        }
                        GlassDialogButton(
                            onClick = ::submit,
                            enabled = !saving && (confirmingDelete || name.trim() != originalName && name.isNotBlank()),
                            modifier = Modifier.weight(1f),
                            role = if (confirmingDelete) GlassDialogButtonRole.Destructive else GlassDialogButtonRole.Primary,
                        ) {
                            Text(stringResource(if (confirmingDelete) R.string.library_shelf_delete else R.string.book_edit_save))
                        }
                    }
                    if (shelfId != null && !confirmingDelete) {
                        GlassDialogButton(
                            onClick = {
                                confirmingDelete = true
                                error = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !saving,
                            role = GlassDialogButtonRole.Destructive,
                        ) {
                            Text(stringResource(R.string.library_shelf_delete))
                        }
                    }
                }
            }
        },
    ) { nestedScroll, padding ->
        LazyColumn(
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize().then(nestedScroll),
            contentPadding = padding,
        ) {
            item {
                CardSegment(true, true, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp)) {
                    ArrowPreference(
                        title = stringResource(R.string.library_shelf_add),
                        titleColor = BasicComponentDefaults.titleColor(color = LocalContentColor.current),
                        onClick = { edit(null, "") },
                    )
                }
            }
            if (catalog.failed) {
                item { Text(stringResource(R.string.library_load_failed), Modifier.padding(16.dp)) }
            }
            groupedCardItems(
                groupKey = "shelves",
                title = R.string.library_shelf_existing,
                rows = catalog.shelves.map { (shelf, books) ->
                    SettingsCardItem(shelf.id) {
                        ArrowPreference(
                            title = shelf.name,
                            summary = pluralStringResource(R.plurals.library_shelf_book_count, books.size, books.size),
                            titleColor = BasicComponentDefaults.titleColor(color = LocalContentColor.current),
                            onClick = { edit(shelf.id, shelf.name) },
                        )
                    }
                },
            )
        }
    }
}
