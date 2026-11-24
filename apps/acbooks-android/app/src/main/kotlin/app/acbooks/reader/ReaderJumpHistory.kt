package app.acbooks.reader

/**
 * Session-local LIFO history of explicit hyperlink jumps. No automatic
 * progression updates are recorded and a rejected return keeps its entry.
 */
internal class ReaderJumpHistory<T : Any>(private val capacity: Int = 64) {
    init { require(capacity > 0) }

    private val points = ArrayDeque<T>()
    val canReturn: Boolean get() = points.isNotEmpty()

    fun record(point: T) {
        if (points.lastOrNull() == point) return
        if (points.size == capacity) points.removeFirst()
        points.addLast(point)
    }

    fun returnToPrevious(navigate: (T) -> Boolean): Boolean {
        val point = points.lastOrNull() ?: return false
        if (!navigate(point)) return false
        points.removeLast()
        return true
    }

    fun clear() = points.clear()
}
