package app.acbooks.core.database

import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

interface CloudReaderDao {
    @Query("""
        SELECT r.* FROM cloud_reader_records r
        JOIN server_profiles p ON p.id = r.profileId AND p.currentSlot = 1
        JOIN remote_book_links b ON b.profileId = r.profileId AND b.remoteBookId = r.remoteBookId
        WHERE b.localBookId = :bookId AND r.payload IS NOT NULL
        ORDER BY r.revision, r.id
    """)
    fun observeCloudReaderRecords(bookId: String): Flow<List<CloudReaderRecord>>

    @Query("SELECT * FROM cloud_reader_records WHERE profileId = :profileId AND kind = :kind AND id = :id")
    suspend fun cloudReaderRecord(profileId: String, kind: String, id: String): CloudReaderRecord?

    @Upsert
    suspend fun cacheCloudReaderRecord(record: CloudReaderRecord)

    @Query("DELETE FROM cloud_reader_records WHERE profileId = :profileId")
    suspend fun clearCloudReaderRecords(profileId: String)

    @Query("""
        SELECT s.* FROM reading_sessions s
        JOIN remote_book_links b ON b.profileId = :profileId AND b.localBookId = s.bookId
        WHERE NOT EXISTS (SELECT 1 FROM cloud_reader_records r WHERE r.profileId = :profileId AND r.kind = 'reading_session' AND r.id = s.id)
        AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.profileId = :profileId AND o.id = 'reader:reading_session:' || s.id)
        ORDER BY s.startedAt, s.id
    """)
    suspend fun unqueuedReadingSessions(profileId: String): List<LocalReadingSession>

    @Query("""
        SELECT m.* FROM bookmarks m
        JOIN remote_book_links b ON b.profileId = :profileId AND b.localBookId = m.bookId
        WHERE NOT EXISTS (SELECT 1 FROM cloud_reader_records r WHERE r.profileId = :profileId AND r.kind = 'bookmark' AND r.id = m.id)
        AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.profileId = :profileId AND o.id = 'reader:bookmark:' || m.id)
        ORDER BY m.createdAt, m.id
    """)
    suspend fun unqueuedBookmarks(profileId: String): List<LocalBookmark>

    @Query("""
        SELECT m.* FROM annotations m
        JOIN remote_book_links b ON b.profileId = :profileId AND b.localBookId = m.bookId
        WHERE NOT EXISTS (SELECT 1 FROM cloud_reader_records r WHERE r.profileId = :profileId AND r.kind = 'annotation' AND r.id = m.id)
        AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.profileId = :profileId AND o.id = 'reader:annotation:' || m.id)
        ORDER BY m.createdAt, m.id
    """)
    suspend fun unqueuedAnnotations(profileId: String): List<LocalAnnotation>
}
