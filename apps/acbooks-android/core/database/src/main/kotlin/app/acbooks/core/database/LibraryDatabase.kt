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
    ],
    version = 7,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao

    companion object {
        fun open(context: Context): LibraryDatabase =
            Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "acbooks-reader.db")
                .configureLibrary()
                .build()
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
