package app.acbooks.library

import android.app.Application
import android.database.SQLException
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.acbooks.R
import app.acbooks.core.database.LibraryDatabase
import app.acbooks.core.database.LocalBookCard
import app.acbooks.core.database.LocalShelf
import app.acbooks.core.database.LocalShelfBook
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class LibraryCatalog(
    val books: List<LocalBookCard> = emptyList(),
    val shelves: List<LocalShelf> = emptyList(),
    val memberships: List<LocalShelfBook> = emptyList(),
    val loaded: Boolean = false,
)

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
    private val repository = LibraryRepository(application, database.library())
    val catalog = combine(repository.books, repository.shelves, repository.memberships) { books, shelves, memberships ->
        LibraryCatalog(books, shelves, memberships, loaded = true)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryCatalog())
    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress = _progress.asStateFlow()
    private var importJob: Job? = null
    private val matches = mutableListOf<Uri>()
    private val _shelfError = MutableStateFlow<Int?>(null)
    val shelfError = _shelfError.asStateFlow()

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
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    fun clearShelfError() {
        _shelfError.value = null
    }

    fun createShelf(draft: String, onCreated: () -> Unit) {
        val name = draft.trim()
        if (name.isEmpty() || name.length > 80) return
        changeShelf {
            if (repository.createShelf(name)) onCreated()
            else _shelfError.value = R.string.library_shelf_exists
        }
    }

    fun deleteShelf(id: String) = changeShelf { repository.deleteShelf(id) }

    fun setMembership(bookId: String, shelfId: String, included: Boolean) =
        changeShelf { repository.setMembership(bookId, shelfId, included) }

    fun coverFile(sha256: String?) = repository.coverFile(sha256)

    suspend fun openBook(bookId: String) = repository.openBook(bookId)

    suspend fun readingPosition(fileId: String): String? = try {
        repository.readingPosition(fileId)
    } catch (_: SQLException) {
        null
    }

    suspend fun saveReadingPosition(fileId: String, locator: String) {
        try {
            repository.saveReadingPosition(fileId, locator)
        } catch (_: SQLException) {
            android.util.Log.w("Reader", "Could not save the reading position")
        }
    }

    private fun changeShelf(block: suspend () -> Unit) {
        viewModelScope.launch {
            clearShelfError()
            try {
                block()
            } catch (_: SQLException) {
                _shelfError.value = R.string.library_shelf_failed
            }
        }
    }

    override fun onCleared() {
        database.close()
    }
}
