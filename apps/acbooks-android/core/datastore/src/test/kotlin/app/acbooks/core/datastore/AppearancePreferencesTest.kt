package app.acbooks.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppearancePreferencesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun accentIsValidatedPersistedAndReset() = runBlocking {
        val file = File(temporaryFolder.root, "appearance.preferences_pb")
        withStore(file) { store ->
            val preferences = AppearancePreferences(store)
            assertNull(preferences.accent.first())
            preferences.setAccent("#a1b2c3")
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { preferences.setAccent("#ABC") }
            }
        }
        withStore(file) { store ->
            val preferences = AppearancePreferences(store)
            assertEquals("#A1B2C3", preferences.accent.first())
            preferences.setAccent(null)
            assertNull(preferences.accent.first())
        }
    }

    @Test
    fun defaultsToSystemAndPersistsAnExplicitModeAcrossStoreInstances() = runBlocking {
        val file = File(temporaryFolder.root, "appearance.preferences_pb")
        withStore(file) { store ->
            val preferences = AppearancePreferences(store)
            assertEquals(ThemeMode.SYSTEM, preferences.themeMode.first())
            preferences.setThemeMode(ThemeMode.DARK)
        }
        withStore(file) { store ->
            assertEquals(ThemeMode.DARK, AppearancePreferences(store).themeMode.first())
        }
    }

    @Test
    fun unknownStoredModeFallsBackToSystem() = runBlocking {
        val file = File(temporaryFolder.root, "appearance.preferences_pb")
        withStore(file) { store ->
            store.edit { it[stringPreferencesKey("theme_mode")] = "unknown" }
            assertEquals(ThemeMode.SYSTEM, AppearancePreferences(store).themeMode.first())
        }
    }

    private suspend fun withStore(
        file: File,
        check: suspend (DataStore<Preferences>) -> Unit,
    ) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { file },
        )
        try {
            check(store)
        } finally {
            job.cancelAndJoin()
        }
    }
}
