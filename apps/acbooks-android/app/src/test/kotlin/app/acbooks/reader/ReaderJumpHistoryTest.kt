package app.acbooks.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderJumpHistoryTest {
    @Test
    fun successiveLinksReturnInReverseOrderWithoutCreatingNewHistory() {
        val history = ReaderJumpHistory<String>()
        assertFalse(history.canReturn)
        history.record("chapter#paragraph")
        history.record("notes#note1")
        val targets = mutableListOf<String>()
        assertTrue(history.returnToPrevious { targets.add(it) })
        assertTrue(history.canReturn)
        assertTrue(history.returnToPrevious { targets.add(it) })
        assertFalse(history.canReturn)
        assertFalse(history.returnToPrevious { targets.add(it) })
        assertEquals(listOf("notes#note1", "chapter#paragraph"), targets)
    }

    @Test
    fun failedReturnDoesNotDiscardSource() {
        val history = ReaderJumpHistory<String>()
        history.record("source")
        assertFalse(history.returnToPrevious { false })
        assertTrue(history.canReturn)
        assertTrue(history.returnToPrevious { it == "source" })
        assertFalse(history.canReturn)
    }

    @Test
    fun duplicateSourcesAreNotRecordedAndHistoryIsBounded() {
        val history = ReaderJumpHistory<String>(capacity = 2)
        history.record("first")
        history.record("first")
        history.record("second")
        history.record("third")
        val targets = mutableListOf<String>()
        assertTrue(history.returnToPrevious { targets.add(it) })
        assertTrue(history.returnToPrevious { targets.add(it) })
        assertEquals(listOf("third", "second"), targets)
        assertFalse(history.canReturn)
    }

    @Test
    fun clearingSessionRemovesReturnAction() {
        val history = ReaderJumpHistory<String>()
        history.record("source")
        history.clear()
        assertFalse(history.canReturn)
    }
}
