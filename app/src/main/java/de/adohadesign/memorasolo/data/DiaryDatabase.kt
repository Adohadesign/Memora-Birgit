package de.adohadesign.memorasolo.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "entries", indices = [Index(value = ["entryDate"])])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryDate: String,
    val displayTime: String,
    @ColumnInfo(defaultValue = "'Eintrag'") val title: String = "Eintrag",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isOpen: Boolean = true
)

@Entity(
    tableName = "elements",
    foreignKeys = [ForeignKey(entity = Entry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId")]
)
data class DiaryElement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val type: String,
    val position: Int,
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "assets",
    foreignKeys = [ForeignKey(entity = DiaryElement::class, parentColumns = ["id"], childColumns = ["elementId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["elementId"], unique = true)]
)
data class FileAsset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val elementId: Long,
    val relativePath: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String
)

@Entity(tableName = "imported_briefstudio_documents")
data class ImportedBriefstudioDocument(
    @PrimaryKey val documentId: String,
    val entryId: Long,
    val importedAt: Long = System.currentTimeMillis()
)

@Dao
interface DiaryDao {
    @Query("SELECT * FROM entries WHERE entryDate = :date ORDER BY displayTime ASC, createdAt ASC, id ASC")
    fun watchEntries(date: String): Flow<List<Entry>>

    @Query("SELECT * FROM entries ORDER BY entryDate ASC, displayTime ASC, id ASC")
    suspend fun allEntries(): List<Entry>

    @Query("SELECT * FROM elements ORDER BY entryId ASC, position ASC, id ASC")
    suspend fun allElements(): List<DiaryElement>

    @Query("SELECT * FROM assets ORDER BY id ASC")
    suspend fun allAssets(): List<FileAsset>

    @Query("SELECT DISTINCT entryDate FROM entries")
    fun watchDates(): Flow<List<String>>

    @Query("SELECT * FROM elements WHERE entryId = :entryId ORDER BY position ASC, id ASC")
    fun watchElements(entryId: Long): Flow<List<DiaryElement>>

    @Query("SELECT * FROM assets WHERE elementId = :elementId LIMIT 1")
    fun watchAsset(elementId: Long): Flow<FileAsset?>

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    suspend fun entry(id: Long): Entry?

    @Query("SELECT * FROM elements WHERE id = :id LIMIT 1")
    suspend fun element(id: Long): DiaryElement?

    @Query("SELECT * FROM assets WHERE elementId = :id LIMIT 1")
    suspend fun assetForElement(id: Long): FileAsset?

    @Query("SELECT * FROM assets WHERE elementId IN (SELECT id FROM elements WHERE entryId = :entryId)")
    suspend fun assetsForEntry(entryId: Long): List<FileAsset>

    @Query("SELECT * FROM entries WHERE isOpen = 1 ORDER BY id DESC LIMIT 1")
    suspend fun openEntry(): Entry?

    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM elements WHERE entryId = :entryId")
    suspend fun nextPosition(entryId: Long): Int

    @Insert suspend fun insertEntry(entry: Entry): Long
    @Insert suspend fun insertElement(element: DiaryElement): Long
    @Insert suspend fun insertAsset(asset: FileAsset): Long
    @Query("SELECT COUNT(*) FROM imported_briefstudio_documents WHERE documentId = :id")
    suspend fun importedCount(id: String): Int
    @Query("SELECT entryId FROM imported_briefstudio_documents WHERE documentId = :id LIMIT 1")
    suspend fun importedEntryId(id: String): Long?
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordImport(item: ImportedBriefstudioDocument): Long
    @Update suspend fun updateEntry(entry: Entry)
    @Query("UPDATE entries SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEntryTitle(id: Long, title: String, updatedAt: Long = System.currentTimeMillis())
    @Update suspend fun updateElement(element: DiaryElement)
    @Query("UPDATE entries SET isOpen = 0 WHERE isOpen = 1")
    suspend fun closeOpenEntries()
    @Query("DELETE FROM elements WHERE id = :id")
    suspend fun deleteElement(id: Long)
    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)
}

@Database(entities = [Entry::class, DiaryElement::class, FileAsset::class, ImportedBriefstudioDocument::class], version = 3, exportSchema = true)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `imported_briefstudio_documents` (`documentId` TEXT NOT NULL, `entryId` INTEGER NOT NULL, `importedAt` INTEGER NOT NULL, PRIMARY KEY(`documentId`))")
            }
        }
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN title TEXT NOT NULL DEFAULT 'Eintrag'")
            }
        }
        @Volatile private var instance: DiaryDatabase? = null
        fun get(context: Context): DiaryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, DiaryDatabase::class.java, "memora.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
