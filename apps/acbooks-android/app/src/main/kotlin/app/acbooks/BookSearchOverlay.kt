package app.acbooks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.acbooks.core.database.LocalBookCard
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.glass.GlassSearchOverlayDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BookSearchOverlay(
    expanded: Boolean,
    books: List<LocalBookCard>,
    onDismiss: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val topInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
    val horizontalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    GlassSearchOverlay(
        expanded = expanded,
        onDismissRequest = onDismiss,
        label = stringResource(R.string.search_query_hint),
        topInsets = topInsets,
        horizontalInsetsModifier = Modifier.windowInsetsPadding(horizontalInsets),
        searchIcon = MiuixIcons.Glass.Search,
        closeIcon = MiuixIcons.Glass.Close,
    ) { input ->
        val query = input.trim()
        val results = remember(books, query) {
            if (query.isEmpty()) emptyList() else books.filter { book ->
                book.title.contains(query, ignoreCase = true) || book.author?.contains(query, ignoreCase = true) == true
            }
        }
        val searchBarBottom = topInsets.asPaddingValues().calculateTopPadding() +
            GlassSearchOverlayDefaults.BarHeight + GlassSearchOverlayDefaults.VerticalPadding * 2
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            ),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp)
                    .fillMaxSize()
                    .padding(top = searchBarBottom),
            ) {
                if (books.isEmpty() || (query.isNotEmpty() && results.isEmpty())) {
                    item {
                        Text(
                            text = stringResource(if (books.isEmpty()) R.string.search_no_books else R.string.search_no_matches),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(results, key = { it.id }) { book ->
                    ArrowPreference(title = book.title, summary = book.author, onClick = { onOpenBook(book.id) })
                }
            }
        }
    }
}
