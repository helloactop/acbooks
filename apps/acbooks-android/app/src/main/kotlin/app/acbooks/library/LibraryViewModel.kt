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
import app.acbooks.R
import app.acbooks.core.database.LibraryDatabase
import app.acbooks.core.database.LocalBookCard
import app.acbooks.core.database.LocalReadingSession
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal data class LibraryCatalog(
    val books: List<LocalBookCard> = emptyList(),
    val loaded: Boolean = false,
    val failed: Boolean = false,
)

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
            null
        }

    fun testServerConnection() = launchCloudAction(ServerOperation.Checking)

    fun refreshServerConnection() = launchCloudAction(ServerOperation.Checking, reportSuccess = false)

    fun disconnectServer() = launchCloudAction(ServerOperation.SigningOut)

    fun syncLibrary() = launchCloudAction(ServerOperation.Syncing)

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
}
