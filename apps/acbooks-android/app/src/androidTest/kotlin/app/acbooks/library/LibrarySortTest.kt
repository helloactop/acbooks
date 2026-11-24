package app.acbooks.library

import app.acbooks.core.database.LibraryShelf
import app.acbooks.core.database.LocalBookCard
import app.acbooks.core.database.LocalShelf
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {
    private fun book(id: String, title: String, author: String?, importedAt: Long = 0) =
        LocalBookCard(id, title, author, importedAt, "epub", null)

    @Test
    fun defaultAndUnknownPreferenceUseRecentlyRead() {
        assertEquals(LibrarySortMode.RecentReading, LibrarySortMode.fromPreference(null))
        assertEquals(LibrarySortMode.RecentReading, LibrarySortMode.fromPreference("unknown"))
        LibrarySortMode.entries.forEach { assertEquals(it, LibrarySortMode.fromPreference(it.preference)) }
    }

    @Test
    fun recentReadingUsesActualReadingThenImportTime() {
        val books = listOf(
            book("new", "New", null, 100),
            book("old", "Old", null, 1),
            book("other", "Other", null, 90),
            book("unread", "Unread", null, 50),
        )
        val readings = listOf(
            RecentBookReading("other", 10, null),
            RecentBookReading("old", 20, null),
        )
        assertEquals(listOf("old", "other", "new", "unread"),
            sortLibraryBooks(books, LibrarySortMode.RecentReading, readings).map { it.id })
    }

    @Test
    fun authorAndTitleUsePinyinAndMissingAuthorsAreLast() {
        val books = listOf(
            book("z", "中国", "张三"),
            book("b", "北京", "李四"),
            book("a", "安徽", "李四"),
            book("x", "上海", null),
        )
        assertEquals(listOf("a", "b", "x", "z"),
            sortLibraryBooks(books, LibrarySortMode.Title, emptyList()).map { it.id })
        assertEquals(listOf("a", "b", "z", "x"),
            sortLibraryBooks(books, LibrarySortMode.Author, emptyList()).map { it.id })
    }

    @Test
    fun tiesAreDeterministicAndCaseInsensitive() {
        val books = listOf(book("z", "alpha", "Author"), book("a", "Alpha", "author"))
        assertEquals(listOf("a", "z"),
            sortLibraryBooks(books, LibrarySortMode.Title, emptyList()).map { it.id })
    }

    @Test
    fun foldersFollowTheirFirstSortedMemberAndKeepSortedContents() {
        val books = listOf(book("a", "A", null), book("b", "B", null), book("c", "C", null))
        val shelf = LibraryShelf(LocalShelf("s", "Shelf", "shelf"), listOf("a", "c"))
        val readings = listOf(RecentBookReading("c", 30, null), RecentBookReading("b", 20, null))
        val sorted = sortLibraryBooks(books, LibrarySortMode.RecentReading, readings)
        val catalog = LibraryCatalog(books = sorted, shelves = listOf(shelf))
        val entries = catalog.gridEntries(null)
        assertEquals(listOf("shelf:s", "book:b"), entries.map { it.key })
        assertEquals(listOf("c", "a"), (entries.first() as LibraryGridEntry.Folder).books.map { it.id })
        assertEquals(listOf("book:c", "book:a"), catalog.gridEntries("s").map { it.key })
    }
}
