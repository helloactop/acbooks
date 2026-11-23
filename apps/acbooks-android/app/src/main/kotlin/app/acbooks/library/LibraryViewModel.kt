package app.acbooks.library

import android.app.Application
import android.content.Context
import android.database.SQLException
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.acbooks.R
import app.acbooks.core.database.LibraryDatabase
import app.acbooks.core.database.LibraryShelf
import app.acbooks.core.database.LocalBookCard
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class LibraryCatalog(
    val books: List<LocalBookCard> = emptyList(),
    val shelves: List<LibraryShelf> = emptyList(),
    val loaded: Boolean = false,
    val failed: Boolean = false,
)

internal data class CloudLibraryState(
    val origin: String? = null,
    val connected: Boolean = false,
    val busy: Boolean = false,
    val message: Int? = null,
)

internal enum class LibraryDisplayMode(val preference: String) {
    Compact("compact"), Comfortable("comfortable"),
}

private val Context.libraryDisplayPreferences by preferencesDataStore(name = "library_display")
private val displayModeKey = stringPreferencesKey("display_mode")

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
    private val preferences = application.libraryDisplayPreferences
    val catalog = combine(repository.books, dao.observeLibraryShelves()) { books, shelves ->
        LibraryCatalog(books = books, shelves = shelves, loaded = true)
    }
        .catch { failure ->
            if (failure !is SQLException) throw failure
            Log.e("Library", "Could not read the local catalog", failure)
            emit(LibraryCatalog(loaded = true, failed = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryCatalog())
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

    fun connectServer(address: String, username: String, password: String) {
        launchCloudAction(R.string.cloud_connect_failed) {
            cloud.login(address, username, password)
            syncLibrary()
            null
        }
    }

    fun disconnectServer() {
        launchCloudAction(R.string.cloud_disconnect_warning) {
            cloud.logout()
            null
        }
    }

    fun syncLibrary() {
        launchCloudAction(R.string.cloud_sync_failed) { syncMessage(cloud.sync()) }
    }

    private fun syncMessage(result: Boolean?): Int? = when (result) {
        true -> R.string.cloud_sync_done
        false -> R.string.cloud_sync_partial
        null -> null
    }

    private fun launchCloudAction(errorMessage: Int, action: suspend () -> Int?) {
        viewModelScope.launch {
            cloudActions.withLock {
                if (_cloudState.value.connected || errorMessage != R.string.cloud_sync_failed) {
                    _cloudState.value = _cloudState.value.copy(busy = true, message = null)
                }
                val message = try {
                    action()
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: CloudIdentityChanged) {
                    R.string.cloud_identity_changed
                } catch (_: Exception) {
                    errorMessage
                }
                val connection = try {
                    cloud.connection()
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    _cloudState.value.origin to false
                }
                _cloudState.value = CloudLibraryState(connection.first, connection.second, message = message)
            }
        }
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
                launchCloudAction(R.string.cloud_sync_failed) { syncMessage(cloud.sync(uploadOfflineBooks = false)) }
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

    suspend fun readingPosition(fileId: String): String? = readingPositionLock.withLock {
        try {
            repository.readingPosition(fileId)
        } catch (_: SQLException) {
            null
        }
    }

    fun saveReadingPosition(fileId: String, locator: String, percent: Double?) {
        // Finish writes after the reader's view is gone; reopening waits for queued positions.
        viewModelScope.launch {
            readingPositionLock.withLock {
                try {
                    repository.saveReadingPosition(fileId, locator, percent)
                } catch (_: SQLException) {
                    Log.w("Reader", "Could not save the reading position")
                }
            }
        }
    }

    override fun onCleared() {
        _bookEditor.value?.draft?.cover?.delete()
        database.close()
    }
}
