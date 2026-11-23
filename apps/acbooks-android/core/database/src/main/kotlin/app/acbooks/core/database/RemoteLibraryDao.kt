package app.acbooks.core.database

import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

interface RemoteLibraryDao {
    @Query("SELECT * FROM server_profiles ORDER BY updatedAt DESC, id")
    fun observeServerProfiles(): Flow<List<ServerProfile>>

    @Query("SELECT * FROM server_profiles WHERE currentSlot = 1")
    fun observeCurrentServerProfile(): Flow<ServerProfile?>

    @Query("SELECT * FROM server_profiles WHERE currentSlot = 1")
    suspend fun currentServerProfile(): ServerProfile?

    @Query("SELECT * FROM server_profiles WHERE id = :profileId")
    suspend fun serverProfile(profileId: String): ServerProfile?

    @Query("""
        SELECT c.*, l.localBookId FROM remote_catalog c
        LEFT JOIN remote_book_links l ON l.profileId = c.profileId AND l.remoteBookId = c.remoteBookId
        WHERE c.profileId = :profileId
        ORDER BY c.updatedAt DESC, c.remoteBookId DESC LIMIT :limit OFFSET :offset
    """)
    fun observeRemoteCatalog(profileId: String, limit: Int, offset: Int = 0): Flow<List<RemoteCatalogBookEntry>>

    @Query("SELECT * FROM remote_catalog WHERE profileId = :profileId AND remoteBookId = :remoteBookId")
    suspend fun remoteCatalogBook(profileId: String, remoteBookId: String): RemoteCatalogBook?

    @Query("SELECT * FROM remote_catalog WHERE profileId = :profileId")
    suspend fun cachedRemoteBooks(profileId: String): List<RemoteCatalogBook>

    @Upsert
    suspend fun cacheRemoteBooks(books: List<RemoteCatalogBook>)

    @Query("DELETE FROM remote_catalog WHERE profileId = :profileId AND remoteBookId = :remoteBookId")
    suspend fun removeCachedRemoteBook(profileId: String, remoteBookId: String): Int

    @Query("DELETE FROM remote_catalog WHERE profileId = :profileId")
    suspend fun clearRemoteCatalog(profileId: String)

    @Query("""
        SELECT b.description FROM remote_catalog b
        JOIN remote_book_links l ON l.profileId = b.profileId AND l.remoteBookId = b.remoteBookId
        JOIN server_profiles p ON p.id = l.profileId AND p.currentSlot = 1
        WHERE l.localBookId = :bookId AND b.available = 1
    """)
    fun observeCloudDescription(bookId: String): Flow<String?>

    @Query("""
        SELECT f.* FROM remote_catalog_files f
        JOIN remote_book_links l ON l.profileId = f.profileId AND l.remoteBookId = f.remoteBookId
        WHERE f.profileId = :profileId AND l.localBookId = :bookId
        ORDER BY f.format = 'epub' DESC, f.remoteFileId
    """)
    suspend fun cloudFilesForBook(profileId: String, bookId: String): List<RemoteCatalogFile>

    @Query("""
        SELECT l.localFileId FROM remote_catalog_files f
        JOIN remote_file_links l ON l.profileId = f.profileId AND l.remoteFileId = f.remoteFileId
        JOIN book_files local ON local.id = l.localFileId AND local.format = f.format AND local.sha256 = f.sha256
        JOIN remote_book_links b ON b.profileId = f.profileId AND b.remoteBookId = f.remoteBookId AND b.localBookId = local.bookId
        WHERE f.profileId = :profileId AND local.bookId = :bookId AND f.fileStatus = 'present'
    """)
    suspend fun backedUpLocalFileIds(profileId: String, bookId: String): List<String>

    @Query("""
        SELECT c.*, l.localFileId, EXISTS (
            SELECT 1 FROM book_files f JOIN file_sources s ON s.sha256 = f.sha256 AND s.format = f.format
            WHERE f.id = l.localFileId AND f.localCopyRemoved = 0
            AND f.format = c.format AND (c.sha256 IS NULL OR c.sha256 = f.sha256)
            AND s.kind IN ('managed', 'downloaded') AND s.status = 'present'
        ) AS hasLocalCopy
        FROM remote_catalog_files c
        LEFT JOIN remote_file_links l ON l.profileId = c.profileId AND l.remoteFileId = c.remoteFileId
        WHERE c.profileId = :profileId AND c.remoteBookId = :remoteBookId
        ORDER BY c.remoteFileId
    """)
    fun observeRemoteCatalogFiles(profileId: String, remoteBookId: String): Flow<List<RemoteCatalogFileEntry>>

    @Upsert
    suspend fun cacheRemoteFiles(files: List<RemoteCatalogFile>)

    @Query("SELECT * FROM remote_catalog_files WHERE profileId = :profileId AND remoteFileId = :remoteFileId")
    suspend fun remoteCatalogFile(profileId: String, remoteFileId: String): RemoteCatalogFile?

    @Query("DELETE FROM remote_catalog_files WHERE profileId = :profileId AND remoteFileId = :remoteFileId")
    suspend fun removeCachedRemoteFile(profileId: String, remoteFileId: String): Int

    @Query("SELECT * FROM remote_book_links WHERE profileId = :profileId AND localBookId = :localBookId")
    suspend fun remoteBookLink(profileId: String, localBookId: String): RemoteBookLink?

    @Query("SELECT * FROM remote_book_links WHERE profileId = :profileId")
    suspend fun remoteBookLinks(profileId: String): List<RemoteBookLink>

    @Query("SELECT * FROM remote_book_links WHERE profileId = :profileId ORDER BY localBookId LIMIT :limit OFFSET :offset")
    fun observeRemoteBookLinks(profileId: String, limit: Int, offset: Int = 0): Flow<List<RemoteBookLink>>

    @Query("SELECT * FROM remote_book_links WHERE profileId = :profileId AND remoteBookId = :remoteBookId ORDER BY localBookId")
    suspend fun localBookLinks(profileId: String, remoteBookId: String): List<RemoteBookLink>

    @Query("""
        SELECT l.* FROM server_profiles p JOIN remote_book_links l ON p.id = l.profileId
        WHERE l.localBookId = :localBookId AND p.currentSlot = 1 AND p.verification = 'verified'
        AND p.origin = rtrim(:origin, '/')
        AND p.serverInstanceId = :serverInstanceId AND p.accountId = :accountId
    """)
    suspend fun verifiedRemoteBookLink(localBookId: String, serverInstanceId: String, accountId: String, origin: String): RemoteBookLink?

    @Upsert
    suspend fun linkRemoteBook(link: RemoteBookLink)

    @Query("DELETE FROM remote_book_links WHERE profileId = :profileId AND localBookId = :localBookId")
    suspend fun unlinkRemoteBook(profileId: String, localBookId: String): Int

    @Query("SELECT * FROM remote_file_links WHERE profileId = :profileId AND localFileId = :localFileId")
    suspend fun remoteFileLink(profileId: String, localFileId: String): RemoteFileLink?

    @Query("SELECT * FROM remote_file_links WHERE profileId = :profileId")
    suspend fun remoteFileLinks(profileId: String): List<RemoteFileLink>

    @Query("SELECT * FROM remote_file_links WHERE profileId = :profileId AND remoteFileId = :remoteFileId")
    suspend fun localFileLink(profileId: String, remoteFileId: String): RemoteFileLink?

    @Upsert
    suspend fun linkRemoteFile(link: RemoteFileLink)

    @Query("DELETE FROM remote_file_links WHERE profileId = :profileId AND localFileId = :localFileId")
    suspend fun unlinkRemoteFile(profileId: String, localFileId: String): Int


}
