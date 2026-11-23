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
import app.acbooks.core.database.LocalBookCard
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class LibraryCatalog(
    val books: List<LocalBookCard> = emptyList(),
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
        ImportOutcome.Added -> copy(added = added + 1)
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
    val catalog = repository.books
        .map { books -> LibraryCatalog(books, loaded = true) }
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
    private var importJob: Job? = null
    private val matches = mutableListOf<Uri>()

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
                    _progress.value = progress
                }
            } finally {
                _progress.value = progress.copy(running = false)
                if (progress.added > 0 || progress.duplicate > 0) syncLibrary()
            }
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
        database.close()
    }
}
