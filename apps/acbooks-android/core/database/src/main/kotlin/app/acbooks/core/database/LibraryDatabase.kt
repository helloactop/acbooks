package app.acbooks.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LocalBook::class, LocalFileObject::class, LocalBookFile::class, LocalShelf::class, LocalShelfBook::class,
        LocalReadingPosition::class,
        LocalReadingSession::class, LocalBookmark::class, LocalAnnotation::class,
        LocalContributor::class, LocalBookContributor::class, LocalBookIdentifier::class,
        LocalTag::class, LocalBookTag::class, LocalLocation::class, LocalBookCopy::class,
        LocalFileSource::class, LocalAuthorizedFolder::class, LocalFolderEntry::class,
        ServerProfile::class, RemoteCatalogBook::class, RemoteCatalogFile::class,
        RemoteBookLink::class, RemoteFileLink::class,
        CloudSyncState::class, CloudCatalogVersion::class, PendingCloudUpload::class,
        CloudReadingPosition::class, PendingReadingPosition::class,
        CloudReaderRecord::class, PendingCloudDownload::class,
    ],
    version = 10,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao

    companion object {
        @Volatile private var instance: LibraryDatabase? = null

        fun open(context: Context): LibraryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "acbooks-reader.db")
                .configureLibrary().build().also { instance = it }
        }
    }
}

internal fun RoomDatabase.Builder<LibraryDatabase>.configureLibrary(): RoomDatabase.Builder<LibraryDatabase> =
    addMigrations(*libraryMigrations).addCallback(object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.installReadingConstraints()
            db.installCatalogConstraints()
            db.installFileSourceConstraints()
            db.installRemoteLibraryConstraints()
        }
    })
