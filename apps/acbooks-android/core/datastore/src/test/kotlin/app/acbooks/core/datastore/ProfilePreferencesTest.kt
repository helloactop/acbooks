package app.acbooks.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

class ProfilePreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun nicknamePersistsAcrossStoreInstancesAndRejectsBlankEdits() = runBlocking {
        val file = File(folder.root, "profile.preferences_pb")
        withProfile(file) { profile ->
            assertNull(profile.nickname.first())
            profile.setNickname("  本地读者  ")
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { profile.setNickname("  ") }
            }
        }
        withProfile(file) { profile ->
            assertEquals("本地读者", profile.nickname.first())
        }
    }

    private suspend fun withProfile(file: File, test: suspend (ProfilePreferences) -> Unit) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { file },
        )
        try {
            test(ProfilePreferences(store))
        } finally {
            job.cancelAndJoin()
        }
    }
}
