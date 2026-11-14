package app.acbooks.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@Entity
data class RoomSmokeEntry(@PrimaryKey val id: Int, val value: String)

@Dao
interface RoomSmokeDao {
    @Insert
    suspend fun insert(entry: RoomSmokeEntry)

    @Query("SELECT * FROM RoomSmokeEntry WHERE id = :id")
    suspend fun find(id: Int): RoomSmokeEntry?
}

@Database(entities = [RoomSmokeEntry::class], version = 1, exportSchema = false)
abstract class RoomSmokeDatabase : RoomDatabase() {
    abstract fun entries(): RoomSmokeDao
}

@RunWith(AndroidJUnit4::class)
class RoomSmokeTest {
    @Test
    fun generatedDaoWritesAndReadsAnEntry() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RoomSmokeDatabase::class.java).build()
        try {
            val entry = RoomSmokeEntry(1, "acbooks")
            database.entries().insert(entry)
            assertEquals(entry, database.entries().find(entry.id))
        } finally {
            database.close()
        }
    }
}
