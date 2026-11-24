package app.acbooks.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderContentsModelTest {
    @Test
    fun repairsFlatPrintedTocAndPreservesExactTargets() {
        val source = listOf(
            ReaderContentsEntry("a.html", "一 艺术... 4", 0),
            ReaderContentsEntry("a.html#x", "（一）形态... 4", 0),
            ReaderContentsEntry("a.html#y", "（二）色彩……13", 0),
            ReaderContentsEntry("b.html", "结语... 211", 0),
            ReaderContentsEntry("b.html#note", "[1]", 0),
        )
        val items = normalizeReaderContents(source)
        assertEquals(listOf(0, 1, 2, 3), items.map { it.sourceIndex })
        assertEquals(listOf(0, 1, 1, 0), items.map { it.entry.depth })
        assertEquals(listOf("一 艺术", "（一）形态", "（二）色彩", "结语"), items.map { it.entry.title })
        assertEquals(source.take(4).map { it.href }, items.map { it.entry.href })
    }

    @Test
    fun respectsExplicitHierarchyAndStandaloneNumericEntries() {
        val source = listOf(
            ReaderContentsEntry("a.html", "Chapter 2026", 0, true),
            ReaderContentsEntry("a.html#x", "（一）Part", 1),
            ReaderContentsEntry("a.html#y", "[1]", 1),
            ReaderContentsEntry("b.html#z", "（二）Other", 0),
            ReaderContentsEntry("b.html#note", "[2]", 0),
        )
        assertEquals(source, normalizeReaderContents(source).map { it.entry })
    }

    @Test
    fun selectsOnlyExactAnchorOrResourceRoot() {
        val hrefs = listOf("a.html", "a.html#x", "a.html#y", "b.html#z", "b.html#w", "c.html#v")
        assertEquals(2, selectedReaderContentsIndex(hrefs, "a.html#y"))
        assertEquals(0, selectedReaderContentsIndex(hrefs, "a.html"))
        assertEquals(0, selectedReaderContentsIndex(hrefs, "a.html#unknown"))
        assertEquals(-1, selectedReaderContentsIndex(hrefs, "b.html"))
        assertEquals(5, selectedReaderContentsIndex(hrefs, "c.html"))
        assertEquals(-1, selectedReaderContentsIndex(hrefs, null))
    }
}
