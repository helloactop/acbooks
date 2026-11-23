package app.acbooks.library

import android.app.Application
import android.content.Context
import android.database.SQLException
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.ImageBitmap
import app.acbooks.R
import app.acbooks.core.database.LibraryDatabase
import app.acbooks.core.database.LibraryShelf
import app.acbooks.core.database.LocalBookCard
import app.acbooks.core.database.LocalReadingActivity
import app.acbooks.core.database.CloudHomeSession
import app.acbooks.core.database.LocalReadingSession
import app.acbooks.reader.ReaderTypographySettings
import app.acbooks.reader.ReaderTypographyProfile
import app.acbooks.reader.ReaderTypographyStore
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject

internal data class LibraryCatalog(
    val books: List<LocalBookCard> = emptyList(),
    val shelves: List<LibraryShelf> = emptyList(),
    val loaded: Boolean = false,
    val failed: Boolean = false,
)

internal data class RecentBookReading(
    val bookId: String,
    val lastReadAt: Long,
    val fraction: Float?,
)

/** Local and server history use the same book identity; progress comes from positions, not sessions. */
internal fun recentBookReadings(
    events: List<LocalReadingActivity>,
    cloudSessions: List<CloudHomeSession>,
): List<RecentBookReading> {
    val sessions = cloudSessions.mapNotNull { record ->
        try {
            val payload = JSONObject(record.payload)
            if (payload.isNull("endedAt")) return@mapNotNull null
            val endedAt = payload.optLong("endedAt")
            if (endedAt <= 0L) null else LocalReadingActivity(record.bookId, endedAt, null)
        } catch (_: JSONException) {
            null
        }
    }
    return (events + sessions).asSequence()
        .filter { it.occurredAt > 0 }
        .groupBy { it.bookId }
        .map { (bookId, reads) ->
            val lastPosition = reads.asSequence()
                .filter { it.progress != null && it.progress.isFinite() }
                .maxByOrNull { it.occurredAt }
            RecentBookReading(
                bookId,
                reads.maxOf { it.occurredAt },
                lastPosition?.progress?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) },
            )
        }
        .sortedWith(compareByDescending<RecentBookReading> { it.lastReadAt }.thenBy { it.bookId })
}

internal enum class ServerConnectionStatus { Unknown, SignedOut, Connected, Unavailable }
internal enum class ServerOperation { Checking, Syncing, SigningOut }

internal data class CloudLibraryState(
    val connection: CloudConnection = CloudConnection(),
    val status: ServerConnectionStatus = ServerConnectionStatus.Unknown,
    val operation: ServerOperation? = null,
    val message: Int? = null,
) {
    val connected: Boolean get() = status == ServerConnectionStatus.Connected && connection.hasSession
    val busy: Boolean get() = operation != null
}

internal data class ProfileAvatarState(
    val image: ImageBitmap? = null,
    val busy: Boolean = false,
    val message: Int? = null,
)

internal enum class LibraryDisplayMode(val preference: String) {
    Compact("compact"), Comfortable("comfortable"),
}

private val Context.libraryDisplayPreferences by preferencesDataStore(name = "library_display")
private val displayModeKey = stringPreferencesKey("display_mode")
private const val ReadingIdleLimitMillis = 5 * 60 * 1000L

private class ForegroundReading(val bookId: String, val startedAt: Long, val startedElapsed: Long) {
    private var lastActivity = startedElapsed
    var activeMillis: Long = 0
        private set

    fun recordActivity(now: Long) {
        activeMillis += (now - lastActivity).coerceIn(0, ReadingIdleLimitMillis)
        lastActivity = now
    }
}

internal data class ImportProgress(
    val total: Int,
    val added: Int = 0,
    val duplicate: Int = 0,
    val matching: Int = 0,
    val unsupported: Int = 0,
    val failed: Int = 0,
    val running: Boolean = true,
) {
    val processed: Int get() = added + duplicate + matching + unsupported + failed

    fun record(outcome: ImportOutcome): ImportProgress = when (outcome) {
        is ImportOutcome.Added -> copy(added = added + 1)
        ImportOutcome.Duplicate -> copy(duplicate = duplicate + 1)
        ImportOutcome.MatchingContent -> copy(matching = matching + 1)
        ImportOutcome.Unsupported -> copy(unsupported = unsupported + 1)
        ImportOutcome.Failed -> copy(failed = failed + 1)
    }
}

internal class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = LibraryDatabase.open(application)
    private val dao = database.library()
    private val repository = LibraryRepository(application, dao)
    private val cloud = LibraryCloudSync(application, dao, repository)
    private val cloudActions = Mutex()
    private val _cloudState = MutableStateFlow(CloudLibraryState())
    val cloudState = _cloudState.asStateFlow()
    private val avatarStore = ProfileAvatarStore(application)
    private val avatarActions = Mutex()
    private val _profileAvatar = MutableStateFlow(ProfileAvatarState())
    val profileAvatar = _profileAvatar.asStateFlow()
    private val preferences = application.libraryDisplayPreferences
    private val readerTypographyStore = ReaderTypographyStore(application)
    val readerTypography = readerTypographyStore.settings

    init {
        viewModelScope.launch {
            avatarActions.withLock {
                try {
                    val image = withContext(Dispatchers.IO) { avatarStore.image() }
                    val pending = withContext(Dispatchers.IO) { avatarStore.isPending() }
                    _profileAvatar.value = ProfileAvatarState(
                        image = image,
                        message = if (pending) R.string.settings_avatar_pending else null,
                    )
                } catch (failure: Exception) {
                    Log.w("ProfileAvatar", "Could not load the cached avatar", failure)
                }
            }
        }
    }

    fun chooseAvatar(uri: Uri) {
        viewModelScope.launch {
            avatarActions.withLock {
                _profileAvatar.value = _profileAvatar.value.copy(busy = true, message = null)
                try {
                    val image = withContext(Dispatchers.IO) { avatarStore.select(uri) }
                    _profileAvatar.value = ProfileAvatarState(image = image, busy = true)
                    syncAvatarLocked()
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    Log.w("ProfileAvatar", "Could not select avatar", failure)
                    _profileAvatar.value = _profileAvatar.value.copy(message = R.string.settings_avatar_select_failed)
                } finally {
                    _profileAvatar.value = _profileAvatar.value.copy(busy = false)
                }
            }
        }
    }

    fun refreshAvatar() {
        viewModelScope.launch {
            avatarActions.withLock { syncAvatarLocked() }
        }
    }

    private suspend fun syncAvatarLocked() {
        try {
            cloud.syncAvatar(avatarStore)
            val image = withContext(Dispatchers.IO) { avatarStore.image() }
            val pending = withContext(Dispatchers.IO) { avatarStore.isPending() }
            _profileAvatar.value = _profileAvatar.value.copy(
                image = image,
                message = if (pending) R.string.settings_avatar_pending else null,
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            Log.w("ProfileAvatar", "Avatar sync failed; retaining local data", failure)
            val pending = withContext(Dispatchers.IO) { avatarStore.isPending() }
            _profileAvatar.value = _profileAvatar.value.copy(
                message = if (pending) R.string.settings_avatar_pending else R.string.settings_avatar_sync_failed,
            )
        }
    }

    /** Read from the store before creating Readium; avoid applying an optimistic default. */
    suspend fun loadReaderTypographySettings(): ReaderTypographySettings = readerTypography.first()

    /** Resolve the actual book-scoped layout, inheriting global when no override exists. */
    fun readerTypographyFor(bookId: String): Flow<ReaderTypographyProfile> =
        readerTypographyStore.forBook(bookId)

    suspend fun loadReaderTypographyProfile(bookId: String): ReaderTypographyProfile =
        readerTypographyFor(bookId).first()

    /** Editing an individual book never modifies the shared/global configuration. */
    fun saveBookReaderTypographySettings(bookId: String, value: ReaderTypographySettings) {
        viewModelScope.launch {
            try {
                readerTypographyStore.saveBook(bookId, value)
            } catch (failure: IOException) {
                Log.w("Reader", "Could not save book-specific typography", failure)
            }
        }
    }

    /** Remove only this book's override, allowing global changes to take effect. */
    fun clearBookReaderTypographySettings(bookId: String) {
        viewModelScope.launch {
            try {
                readerTypographyStore.clearBook(bookId)
            } catch (failure: IOException) {
                Log.w("Reader", "Could not clear book-specific typography", failure)
            }
        }
    }

    /** Called by the future typography sheet. Existing EPUBs can observe readerTypography. */
    fun saveReaderTypographySettings(value: ReaderTypographySettings) {
        viewModelScope.launch {
            try {
                readerTypographyStore.save(value)
            } catch (failure: IOException) {
                Log.w("Reader", "Could not save typography preferences", failure)
            }
        }
    }

    val catalog = combine(repository.books, dao.observeLibraryShelves()) { books, shelves ->
        LibraryCatalog(books = books, shelves = shelves, loaded = true)
    }
        .catch { failure ->
            if (failure !is SQLException) throw failure
            Log.e("Library", "Could not read the local catalog", failure)
            emit(LibraryCatalog(loaded = true, failed = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryCatalog())
    val recentReading = combine(dao.observeReadingActivity(), dao.observeHomeCloudSessions(), ::recentBookReadings)
        .catch { failure ->
            if (failure is CancellationException) throw failure
            Log.e("Home", "Could not load reading history", failure)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val displayMode = preferences.data
        .catch { failure ->
            if (failure !is IOException) throw failure
            Log.w("Library", "Could not read the library display preference", failure)
            emit(emptyPreferences())
        }
        .map { saved ->
            LibraryDisplayMode.entries.firstOrNull { it.preference == saved[displayModeKey] }
                ?: LibraryDisplayMode.Compact
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryDisplayMode.Compact)
    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress = _progress.asStateFlow()
    private val _bookEditor = MutableStateFlow<BookEditorState?>(null)
    val bookEditor = _bookEditor.asStateFlow()
    private var editorJob: Job? = null
    private var importJob: Job? = null
    private val matches = mutableListOf<Uri>()
    private val importReviews = ArrayDeque<BookEditorState>()
    private var syncAfterImport = false

    suspend fun testServerAddress(address: String): Int = try {
        cloud.testAddress(address)
        R.string.cloud_address_reachable
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: CloudSetupRequired) {
        R.string.cloud_setup_required
    } catch (_: IllegalArgumentException) {
        R.string.cloud_invalid_address
    } catch (_: Exception) {
        R.string.cloud_test_failed
    }

    suspend fun connectServer(address: String, username: String, password: String, replace: Boolean): Int? =
        cloudActions.withLock {
            val connection = try {
                cloud.login(address, username, password, replace)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: CloudIdentityChanged) {
                return@withLock R.string.cloud_confirm_new_identity
            } catch (_: IllegalArgumentException) {
                return@withLock R.string.cloud_invalid_address
            } catch (failure: CloudHttpException) {
                return@withLock when (failure.status) {
                    401 -> R.string.cloud_invalid_credentials
                    429 -> R.string.cloud_too_many_attempts
                    else -> R.string.cloud_connect_failed
                }
            } catch (_: Exception) {
                return@withLock R.string.cloud_connect_failed
            }
            _cloudState.value = CloudLibraryState(connection, ServerConnectionStatus.Connected, message = R.string.cloud_login_done)
            CloudSyncJobs.request(getApplication())
            refreshAvatar()
            null
        }

    fun testServerConnection() = launchCloudAction(ServerOperation.Checking)

    fun refreshServerConnection() = launchCloudAction(ServerOperation.Checking, reportSuccess = false)

    fun disconnectServer() = launchCloudAction(ServerOperation.SigningOut)

    fun syncLibrary() {
        launchCloudAction(ServerOperation.Syncing)
        refreshAvatar()
    }

    private fun syncMessage(result: Boolean?): Int? = when (result) {
        true -> R.string.cloud_sync_done
        false -> R.string.cloud_sync_partial
        null -> null
    }

    private fun launchCloudAction(operation: ServerOperation, reportSuccess: Boolean = true) {
        viewModelScope.launch {
            cloudActions.withLock {
                val before = _cloudState.value
                val saved = savedConnection(before.connection)
                if (!saved.hasSession) {
                    _cloudState.value = CloudLibraryState(saved, ServerConnectionStatus.SignedOut, message = before.message)
                    return@withLock
                }
                val previousMessage = before.message.takeIf { !reportSuccess || it == R.string.cloud_login_done }
                _cloudState.value = before.copy(connection = saved, operation = operation, message = previousMessage)
                var authenticated = false
                var revokeFailed = false
                val message = try {
                    when (operation) {
                        ServerOperation.Checking -> {
                            authenticated = cloud.testConnection()
                            if (authenticated && reportSuccess) R.string.cloud_connection_verified else previousMessage
                        }
                        ServerOperation.Syncing -> {
                            CloudSyncJobs.request(getApplication())
                            val result = cloud.sync()
                            authenticated = result != null
                            syncMessage(result)
                        }
                        ServerOperation.SigningOut -> {
                            cloud.logout()
                            R.string.cloud_signed_out
                        }
                    }
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: CloudIdentityChanged) {
                    R.string.cloud_identity_changed
                } catch (failure: CloudHttpException) {
                    revokeFailed = operation == ServerOperation.SigningOut
                    if (failure.status == 401 && !revokeFailed) R.string.cloud_session_expired else operation.failureMessage()
                } catch (_: Exception) {
                    revokeFailed = operation == ServerOperation.SigningOut
                    operation.failureMessage()
                }
                val connection = savedConnection(saved)
                if (operation == ServerOperation.SigningOut && !connection.hasSession) CloudSyncJobs.cancel(getApplication())
                val status = when {
                    !connection.hasSession -> ServerConnectionStatus.SignedOut
                    authenticated -> ServerConnectionStatus.Connected
                    else -> ServerConnectionStatus.Unavailable
                }
                _cloudState.value = CloudLibraryState(connection, status,
                    message = if (revokeFailed && !connection.hasSession) R.string.cloud_disconnect_warning else message)
            }
        }
    }

    private suspend fun savedConnection(fallback: CloudConnection): CloudConnection = try {
        cloud.connection()
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        fallback
    }

    private fun ServerOperation.failureMessage(): Int = when (this) {
        ServerOperation.Checking -> R.string.cloud_test_failed
        ServerOperation.Syncing -> R.string.cloud_sync_failed
        ServerOperation.SigningOut -> R.string.cloud_sign_out_failed
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        viewModelScope.launch {
            try {
                preferences.edit { it[displayModeKey] = mode.preference }
            } catch (failure: IOException) {
                Log.w("Library", "Could not save the library display preference", failure)
            }
        }
    }

    fun importBooks(uris: List<Uri>) = importBatch(uris, allowMatchingContent = false)

    fun importMatches() = importBatch(matches.toList(), allowMatchingContent = true)

    private fun importBatch(uris: List<Uri>, allowMatchingContent: Boolean) {
        val selected = uris.distinct()
        if (selected.isEmpty() || _progress.value?.running == true) return
        matches.clear()
        var progress = ImportProgress(selected.size)
        _progress.value = progress
        importJob = viewModelScope.launch {
            try {
                for (uri in selected) {
                    val outcome = repository.import(uri, allowMatchingContent)
                    if (outcome == ImportOutcome.MatchingContent) matches.add(uri)
                    progress = progress.record(outcome)
                    if (outcome is ImportOutcome.Added) {
                        try {
                            autoFillImportedBook(outcome)?.let(importReviews::addLast)
                        } catch (failure: CancellationException) {
                            importReviews.addLast(BookEditorState(outcome.bookId, error = R.string.library_import_review_interrupted))
                            throw failure
                        }
                    }
                    _progress.value = progress
                }
            } finally {
                _progress.value = progress.copy(running = false)
                syncAfterImport = syncAfterImport || progress.added > 0 || progress.duplicate > 0
                continueImportReviews()
            }
        }
    }

    private suspend fun autoFillImportedBook(imported: ImportOutcome.Added): BookEditorState? {
        val review = BookEditorState(imported.bookId)
        val isbn = imported.isbn ?: return review.copy(error = R.string.library_import_review_isbn)
        val candidates = try {
            cloud.searchDoubanMetadata(isbn).distinctBy { it.doubanId }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: CloudSignInRequired) {
            return review.copy(error = R.string.book_edit_douban_sign_in)
        } catch (failure: Exception) {
            val error = if (failure is CloudHttpException && failure.status == 401) R.string.book_edit_douban_sign_in
            else R.string.book_edit_search_failed
            return review.copy(error = error)
        }

        val candidate = candidates.singleOrNull()?.takeIf {
            it.isbn?.validImportedIsbn()?.bookIsbnMatchKey() == isbn.bookIsbnMatchKey()
        } ?: return review.copy(error = R.string.library_import_review_no_exact_match)

        val initial = try {
            repository.loadBookEdit(imported.bookId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            return review.copy(error = R.string.book_edit_load_failed)
        }

        val (draft, coverError) = prepareBookCandidate(initial.draft, candidate)
        val filled = draft.copy(isbn = isbn)
        try {
            val error = coverError ?: filled.validationError()
            if (error != null) return review.copy(initial = initial, draft = filled.copy(cover = null), error = error)
            cloud.saveBookEdit(imported.bookId, initial, filled)
            return null
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            return review.copy(initial = initial, draft = filled.copy(cover = null), error = R.string.book_edit_save_failed)
        } finally {
            filled.cover?.delete()
        }
    }

    private fun continueImportReviews() {
        if (_progress.value?.running == true || _bookEditor.value != null) return
        val review = importReviews.removeFirstOrNull()
        if (review != null) {
            openBookEditor(review)
        } else if (syncAfterImport) {
            syncAfterImport = false
            syncLibrary()
        }
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    fun dismissImport() {
        if (_progress.value?.running == true) return
        matches.clear()
        _progress.value = null
    }

    fun openBookEditor(bookId: String) = openBookEditor(BookEditorState(bookId))

    private fun openBookEditor(editor: BookEditorState) {
        if (_bookEditor.value != null) return
        _bookEditor.value = editor
        if (editor.draft == null) {
            runEditorOperation(editor, BookEditorOperation.Load, R.string.book_edit_load_failed) {
                val session = repository.loadBookEdit(editor.bookId)
                copy(initial = session, draft = session.draft, error = editor.error)
            }
        }
    }

    fun editBookDraft(draft: BookEditDraft) {
        val editor = _bookEditor.value ?: return
        if (!editor.open || editor.operation != null) return
        _bookEditor.value = editor.copy(draft = draft, error = null)
    }

    fun chooseBookCover(uri: Uri) {
        val editor = _bookEditor.value ?: return
        val draft = editor.draft ?: return
        runEditorOperation(editor, BookEditorOperation.Cover, R.string.book_edit_cover_failed) {
            val staged = repository.stageBookCover(uri)
            draft.cover?.delete()
            copy(draft = draft.copy(cover = staged))
        }
    }

    fun undoBookCover() {
        val editor = _bookEditor.value ?: return
        val draft = editor.draft ?: return
        if (!editor.open || editor.operation != null) return
        draft.cover?.delete()
        _bookEditor.value = editor.copy(draft = draft.copy(cover = null), error = null)
    }

    fun fillBookDraft() {
        val editor = _bookEditor.value ?: return
        val draft = editor.draft ?: return
        val isbn = draft.isbn.normalizedBookIsbn()
        val title = draft.title.trim()
        val query = isbn.ifEmpty { title }.take(200)
        if (query.isEmpty()) {
            _bookEditor.value = editor.copy(error = R.string.book_edit_query_required)
            return
        }
        runEditorOperation(editor, BookEditorOperation.Fill, R.string.book_edit_search_failed) {
            val preferredResults = cloud.searchDoubanMetadata(query)
            val candidates = if (preferredResults.isEmpty() && isbn.isNotEmpty() && title.isNotEmpty() && title != query) {
                cloud.searchDoubanMetadata(title.take(200))
            } else preferredResults
            val unique = candidates.distinctBy { it.doubanId }
            fun exact(candidate: DoubanMetadataCandidate): Boolean =
                if (isbn.isNotEmpty()) candidate.isbn?.normalizedBookIsbn()?.bookIsbnMatchKey() == isbn.bookIsbnMatchKey()
                else candidate.title.trim().equals(title, ignoreCase = true)
            val match = unique.singleOrNull()?.takeIf(::exact)
            when {
                unique.isEmpty() -> copy(error = R.string.book_edit_no_matches)
                match != null -> applyCandidate(draft, match)
                else -> copy(candidates = unique.sortedByDescending(::exact))
            }
        }
    }

    fun chooseBookCandidate(candidate: DoubanMetadataCandidate) {
        val editor = _bookEditor.value ?: return
        val draft = editor.draft ?: return
        if (editor.candidates?.contains(candidate) != true) return
        runEditorOperation(editor.copy(candidates = null), BookEditorOperation.Fill, R.string.book_edit_search_failed) {
            applyCandidate(draft, candidate)
        }
    }

    fun dismissBookCandidates() {
        val editor = _bookEditor.value ?: return
        _bookEditor.value = editor.copy(candidates = null)
    }

    private suspend fun BookEditorState.applyCandidate(
        draft: BookEditDraft,
        candidate: DoubanMetadataCandidate,
    ): BookEditorState {
        val (filled, error) = prepareBookCandidate(draft, candidate)
        return copy(draft = filled, error = error)
    }

    private suspend fun prepareBookCandidate(
        draft: BookEditDraft,
        candidate: DoubanMetadataCandidate,
    ): Pair<BookEditDraft, Int?> {
        val filled = draft.fill(candidate)
        val coverUrl = candidate.coverUrl ?: return filled to null
        if (!doubanCoverUrl.matches(coverUrl)) return filled to R.string.book_edit_douban_cover_failed
        val staged = try {
            repository.stageDoubanCover(coverUrl)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            return filled to R.string.book_edit_douban_cover_failed
        }
        draft.cover?.delete()
        return filled.copy(cover = staged) to null
    }

    fun saveBookEditor() {
        val editor = _bookEditor.value ?: return
        if (!editor.open || editor.operation != null) return
        val initial = editor.initial ?: return
        val draft = editor.draft ?: return
        if (!editor.dirty && !initial.pendingCover && initial.retryKeys.isEmpty()) {
            dismissBookEditor()
            return
        }
        val validation = draft.validationError()
        if (validation != null) {
            _bookEditor.value = editor.copy(error = validation)
            return
        }
        runEditorOperation(editor, BookEditorOperation.Save, R.string.book_edit_save_failed) {
            val pending = cloud.saveBookEdit(editor.bookId, initial, draft)
            if (pending) {
                CloudSyncJobs.request(getApplication())
            }
            copy(open = false, savedMessage = if (pending) R.string.book_edit_saved_pending else R.string.book_edit_saved)
        }
    }

    fun dismissBookEditor() {
        val editor = _bookEditor.value ?: return
        if (editor.operation == BookEditorOperation.Save) return
        editorJob?.cancel()
        _bookEditor.value = editor.copy(open = false, candidates = null)
    }

    fun finishBookEditorDismissal() {
        val editor = _bookEditor.value ?: return
        if (editor.open) return
        editor.draft?.cover?.delete()
        _bookEditor.value = null
        continueImportReviews()
    }

    private fun runEditorOperation(
        editor: BookEditorState,
        operation: BookEditorOperation,
        failureMessage: Int,
        action: suspend BookEditorState.() -> BookEditorState,
    ) {
        if (!editor.open || editor.operation != null) return
        val running = editor.copy(operation = operation, error = null)
        _bookEditor.value = running
        editorJob = viewModelScope.launch {
            val result = try {
                running.action()
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: CloudSignInRequired) {
                running.copy(error = R.string.book_edit_douban_sign_in)
            } catch (failure: Exception) {
                val error = if (failure is CloudHttpException && failure.status == 401 &&
                    operation == BookEditorOperation.Fill
                ) R.string.book_edit_douban_sign_in else failureMessage
                running.copy(error = error)
            }
            if (_bookEditor.value === running) _bookEditor.value = result.copy(operation = null)
        }
    }

    fun coverFile(book: LocalBookCard) = repository.coverFile(book)

    suspend fun saveShelf(id: String?, rawName: String): Int? {
        val name = rawName.trim()
        if (name.isEmpty() || !name.validText(200)) return R.string.library_shelf_invalid_name
        return shelfAction {
            if (!cloud.saveShelf(id, name)) return@shelfAction R.string.library_shelf_name_conflict
            CloudSyncJobs.request(getApplication())
            null
        }
    }

    suspend fun deleteShelf(id: String): Int? = shelfAction {
        cloud.deleteShelf(id)
        CloudSyncJobs.request(getApplication())
        null
    }

    suspend fun saveBookShelves(bookId: String, ids: Set<String>): Int? = shelfAction {
        if (cloud.saveBookShelves(bookId, ids)) CloudSyncJobs.request(getApplication())
        null
    }

    fun observePendingBookShelves(bookId: String) = dao.observePendingBookEdit(bookId).map { edit ->
        edit?.let { org.json.JSONObject(it.patchJson).has("shelves") } == true
    }

    private suspend fun shelfAction(action: suspend () -> Int?): Int? = try {
        action()
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: CloudSignInRequired) {
        R.string.library_shelf_sign_in
    } catch (failure: CloudHttpException) {
        when (failure.status) {
            401, 403 -> R.string.library_shelf_sign_in
            409 -> R.string.library_shelf_name_conflict
            422 -> R.string.library_shelf_invalid_name
            else -> R.string.library_shelf_save_failed
        }
    } catch (failure: Exception) {
        Log.w("Library", "Could not save shelf change", failure)
        R.string.library_shelf_save_failed
    }

    fun observeBookDetails(bookId: String) = repository.observeBookDetails(bookId).catch { failure ->
        if (failure !is SQLException) throw failure
        Log.w("Library", "Could not read local book details", failure)
        emit(BookDetailsData())
    }

    suspend fun deleteLocalBook(bookId: String): Boolean = try {
        cloud.removeLocal(bookId)
    } catch (failure: Exception) {
        if (failure is kotlinx.coroutines.CancellationException) throw failure
        Log.e("LibraryDelete", "Local book deletion failed", failure)
        false
    }

    /** Remote deletion must succeed before removing the local copy. */
    suspend fun deleteBookEverywhere(bookId: String): Boolean = try {
        cloud.deleteEverywhere(bookId)
    } catch (failure: Exception) {
        if (failure is kotlinx.coroutines.CancellationException) throw failure
        Log.e("LibraryDelete", "Cloud book deletion failed", failure)
        false
    }

    suspend fun openBook(bookId: String) = repository.openBook(bookId)

    suspend fun downloadForReading(bookId: String): Boolean = try {
        cloud.openOrDownload(bookId)
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        false
    }

    private val readingPositionLock = Mutex()
    private val readingSyncRequests = Channel<Unit>(Channel.CONFLATED)
    private val foregroundReading = mutableMapOf<String, ForegroundReading>()

    init {
        viewModelScope.launch {
            for (request in readingSyncRequests) {
                delay(1_000)
                readingSyncRequests.tryReceive()
                tryReadingSync { cloud.syncReaderData() }
            }
        }
    }

    suspend fun prepareReadingPosition(fileId: String) {
        val refresh = viewModelScope.async { tryReadingSync { cloud.refreshReadingPosition(fileId) } }
        withTimeoutOrNull(1_200) { refresh.await() }
    }

    private suspend fun tryReadingSync(sync: suspend () -> Unit) {
        try {
            sync()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            // Local positions and any in-flight request are durable across connectivity failures.
            Log.d("ReaderSync", "Reading position sync will retry later")
        }
    }

    suspend fun readingPosition(fileId: String): String? = readingPositionLock.withLock {
        try {
            repository.readingPosition(fileId)
        } catch (_: SQLException) {
            null
        }
    }

    fun saveReadingPosition(fileId: String, locator: String, percent: Double?) {
        foregroundReading[fileId]?.recordActivity(SystemClock.elapsedRealtime())
        // Finish writes after the reader's view is gone; reopening waits for queued positions.
        viewModelScope.launch {
            readingPositionLock.withLock {
                try {
                    if (repository.saveReadingPosition(fileId, locator, percent)) requestReadingSync()
                } catch (_: SQLException) {
                    Log.w("Reader", "Could not save the reading position")
                }
            }
        }
    }

    fun startReadingSession(bookId: String, fileId: String) {
        foregroundReading.getOrPut(fileId) { ForegroundReading(bookId, System.currentTimeMillis(), SystemClock.elapsedRealtime()) }
    }

    fun finishReadingSession(fileId: String) {
        val reading = foregroundReading.remove(fileId) ?: return
        val now = SystemClock.elapsedRealtime()
        reading.recordActivity(now)
        val seconds = reading.activeMillis / 1000
        if (seconds == 0L) return
        val session = LocalReadingSession(UUID.randomUUID().toString(), reading.bookId, fileId, null,
            reading.startedAt, reading.startedAt + (now - reading.startedElapsed), seconds, "automatic")
        viewModelScope.launch {
            try {
                repository.saveReadingSession(session)
                requestReadingSync()
            } catch (_: SQLException) {
                Log.w("Reader", "Could not save the reading session")
            }
        }
    }

    private fun requestReadingSync() {
        readingSyncRequests.trySend(Unit)
        if (_cloudState.value.connection.hasSession) CloudSyncJobs.request(getApplication())
    }

    override fun onCleared() {
        _bookEditor.value?.draft?.cover?.delete()
    }
}
