package app.acbooks.core.database

import java.time.Instant
import java.time.ZoneId
import java.util.PriorityQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class LocalReadingStatistics(
    val activeSeconds: Double,
    val sessionCount: Int,
    val readingDays: Int,
    val lastReadAt: Long,
)

fun ReadingSessionDao.observeReadingStatistics(bookId: String, timeZone: ZoneId): Flow<LocalReadingStatistics?> =
    observeCompletedReadingSessions(bookId).map { sessions -> summarizeReadingSessions(sessions, timeZone) }

private data class ReadingInterval(val id: String, val start: Long, val end: Long, val activeRatio: Double)

fun summarizeReadingSessions(
    sessions: List<LocalReadingSession>,
    timeZone: ZoneId,
): LocalReadingStatistics? {
    val intervals = sessions.mapNotNull { session ->
        val end = session.endedAt ?: return@mapNotNull null
        if (end <= session.startedAt || session.activeSeconds <= 0) return@mapNotNull null
        ReadingInterval(session.id, session.startedAt, end, session.activeSeconds * 1000.0 / (end - session.startedAt))
    }.sortedBy { it.start }
    if (intervals.isEmpty()) return null

    val boundaries = intervals.flatMap { listOf(it.start, it.end) }.distinct().sorted()
    val active = PriorityQueue(
        compareByDescending<ReadingInterval> { it.activeRatio }.thenBy { it.end }.thenBy { it.id },
    )
    var next = 0
    var seconds = 0.0
    for (index in 0 until boundaries.lastIndex) {
        val left = boundaries[index]
        while (next < intervals.size && intervals[next].start == left) {
            active.add(intervals[next++])
        }
        while (active.peek()?.let { it.end <= left } == true) active.remove()
        val interval = active.peek() ?: continue
        seconds += (boundaries[index + 1] - left) * interval.activeRatio / 1000.0
    }
    return LocalReadingStatistics(
        activeSeconds = seconds,
        sessionCount = intervals.size,
        readingDays = intervals.map { Instant.ofEpochMilli(it.start).atZone(timeZone).toLocalDate() }.toSet().size,
        lastReadAt = intervals.maxOf { it.end },
    )
}
