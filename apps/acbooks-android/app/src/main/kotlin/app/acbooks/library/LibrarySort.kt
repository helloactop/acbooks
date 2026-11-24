package app.acbooks.library

import app.acbooks.core.database.LocalBookCard
import java.text.Collator
import java.util.Locale

internal enum class LibrarySortMode(val preference: String) {
    RecentReading("recent_reading"),
    Author("author"),
    Title("title");

    companion object {
        fun fromPreference(value: String?): LibrarySortMode =
            entries.firstOrNull { it.preference == value } ?: RecentReading
    }
}

/** Presentation-only order; Chinese titles and authors use pinyin collation. */
internal fun sortLibraryBooks(
    books: List<LocalBookCard>,
    mode: LibrarySortMode,
    recentReading: List<RecentBookReading>,
): List<LocalBookCard> {
    val comparator = when (mode) {
        LibrarySortMode.RecentReading -> {
            val lastRead = recentReading.associate { it.bookId to it.lastReadAt }
            compareByDescending<LocalBookCard> { lastRead[it.id] ?: Long.MIN_VALUE }
                .thenByDescending { it.importedAt }
        }
        LibrarySortMode.Author, LibrarySortMode.Title -> {
            val collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE).apply {
                strength = Collator.PRIMARY
            }
            val titleOrder = Comparator<LocalBookCard> { a, b -> collator.compare(a.title, b.title) }
            if (mode == LibrarySortMode.Author) {
                compareBy<LocalBookCard> { it.author.isNullOrBlank() }
                    .thenComparator { a, b ->
                        collator.compare(a.author.orEmpty().trim(), b.author.orEmpty().trim())
                    }
                    .thenComparator { a, b -> titleOrder.compare(a, b) }
            } else {
                titleOrder
            }
        }
    }
    return books.sortedWith(comparator.thenBy { it.id })
}
