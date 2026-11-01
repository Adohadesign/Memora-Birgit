package de.adohadesign.memorasolo.data

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import android.provider.OpenableColumns
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class DiaryRepository(private val context: Context) {
    val db = DiaryDatabase.get(context)
    val dao = db.diaryDao()
    private val session = context.getSharedPreferences("memora_session", Context.MODE_PRIVATE)
    val mediaFolder: File = File(context.filesDir, "media").also { it.mkdirs() }
    val dayFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    fun today(): String = LocalDate.now().format(dayFormat)
    fun timeNow(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    fun entries(date: String): Flow<List<Entry>> = dao.watchEntries(date)
    fun dates(): Flow<List<String>> = dao.watchDates()
    fun elements(id: Long): Flow<List<DiaryElement>> = dao.watchElements(id)

    suspend fun foreground() {
        val leftAt = session.getLong("backgroundAt", 0)
        if (EntryPolicy.shouldClose(leftAt, System.currentTimeMillis())) dao.closeOpenEntries()
        session.edit().remove("backgroundAt").apply()
    }
    fun background() { session.edit().putLong("backgroundAt", System.currentTimeMillis()).apply() }

    suspend fun beginEntry(date: String, existingId: Long? = null, originalTime: String? = null): Long = db.withTransaction {
        if (existingId != null && dao.entry(existingId) != null) return@withTransaction existingId
        val open = dao.openEntry()
        if (open != null && open.entryDate == date) return@withTransaction open.id
        dao.closeOpenEntries()
        dao.insertEntry(Entry(entryDate = date, displayTime = originalTime ?: timeNow()))
    }

    suspend fun createNew(date: String): Long = db.withTransaction {
        dao.closeOpenEntries()
        dao.insertEntry(Entry(entryDate = date, displayTime = timeNow()))
    }

    suspend fun addText(date: String, type: String = "TEXT", intoEntry: Long? = null): Long = db.withTransaction {
        val id = beginEntry(date, intoEntry)
        dao.insertElement(DiaryElement(entryId = id, type = type, position = dao.nextPosition(id)))
    }

    /** Never overwrite an existing diary entry when importing a Briefstudio document. */
    suspend fun importBriefstudio(document: BriefstudioDocument): Boolean = db.withTransaction {
        if (dao.importedCount(document.id) > 0) return@withTransaction false
        val day = LocalDate.parse(document.date).toString()
        val time = LocalTime.parse(document.time).format(DateTimeFormatter.ofPattern("HH:mm"))
        val entryId = dao.insertEntry(Entry(entryDate = day, displayTime = time, isOpen = false))
        val text = if (document.title.isBlank()) document.body else "${document.title}\n\n${document.body}"
        dao.insertElement(DiaryElement(entryId = entryId, type = "TEXT", position = 1, text = text))
        check(dao.recordImport(ImportedBriefstudioDocument(document.id, entryId)) != -1L) { "Import bereits vorhanden" }
        true
    }

    suspend fun saveText(elementId: Long, text: String) {
        val old = dao.element(elementId) ?: return
        dao.updateElement(old.copy(text = text, updatedAt = System.currentTimeMillis()))
    }

    suspend fun changeDateTime(entryId: Long, date: String, time: String) {
        val old = dao.entry(entryId) ?: return
        dao.updateEntry(old.copy(entryDate = date, displayTime = time, updatedAt = System.currentTimeMillis()))
    }

    suspend fun moveElementOrder(elementId: Long, direction: Int) {
        db.withTransaction {
            val element = dao.element(elementId) ?: return@withTransaction
            val list = dao.allElements().filter { it.entryId == element.entryId }
                .sortedWith(compareBy<DiaryElement> { it.position }.thenBy { it.id })
            val index = list.indexOfFirst { it.id == elementId }
            val swapIndex = index + direction
            if (index !in list.indices || swapIndex !in list.indices) return@withTransaction
            // Assign unique positions to the whole group to avoid duplicate order values.
            val reordered = list.toMutableList()
            val current = reordered.removeAt(index)
            reordered.add(swapIndex, current)
            reordered.forEachIndexed { position, value ->
                dao.updateElement(value.copy(position = position + 1))
            }
        }
    }

    suspend fun moveElement(elementId: Long, toEntryId: Long) {
        db.withTransaction {
            val element = dao.element(elementId) ?: return@withTransaction
            dao.updateElement(element.copy(entryId = toEntryId, position = dao.nextPosition(toEntryId)))
        }
    }

    suspend fun mergeEntries(first: Long, second: Long) {
        if (first == second) return
        db.withTransaction {
            val all = dao.allElements().filter { it.entryId == second }
            for (e in all) dao.updateElement(e.copy(entryId = first, position = dao.nextPosition(first)))
            dao.deleteEntry(second)
        }
    }

    suspend fun splitAt(elementId: Long): Long = db.withTransaction {
        val element = dao.element(elementId) ?: return@withTransaction 0L
        val old = dao.entry(element.entryId) ?: return@withTransaction 0L
        val newId = dao.insertEntry(old.copy(id = 0, isOpen = false, createdAt = System.currentTimeMillis()))
        val after = dao.allElements().filter { it.entryId == old.id && it.position >= element.position }
        for (e in after) dao.updateElement(e.copy(entryId = newId, position = dao.nextPosition(newId)))
        newId
    }

    suspend fun addFile(date: String, uri: Uri, type: String, intoEntry: Long? = null, originalTime: String? = null, cameraCapture: Boolean = false): Long = withContext(Dispatchers.IO) {
        // Copy completely and verify before the file is referenced in the database.
        val resolver = context.contentResolver
        var name = "Anhang"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
            if (cur.moveToFirst()) name = cur.getString(0) ?: name
        }
        val mime = if (uri.authority == "${context.packageName}.provider") {
            when (type) { "PHOTO" -> "image/jpeg"; "VIDEO" -> "video/mp4"; else -> resolver.getType(uri) ?: "application/octet-stream" }
        } else resolver.getType(uri) ?: "application/octet-stream"
        val target = File(mediaFolder, "${UUID.randomUUID()}.bin")
        val temp = File(mediaFolder, ".${target.name}.part")
        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Datei kann nicht geöffnet werden" }
                FileOutputStream(temp).use { output -> input.copyTo(output, 64 * 1024); output.fd.sync() }
            }
            require(temp.renameTo(target)) { "Datei konnte nicht abgeschlossen werden" }
            // Camera-specific fallback: certain camera apps write landscape pixels without
            // an EXIF rotation tag even when a portrait photo was requested.
            // Apply only to direct camera captures, never imported gallery photos.
            if (type == "PHOTO" && cameraCapture) {
                val exif = ExifInterface(target.absolutePath)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED
                )
                if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(target.absolutePath, bounds)
                    if (bounds.outWidth > bounds.outHeight && bounds.outHeight > 0) {
                        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_270.toString())
                        exif.saveAttributes()
                    }
                }
            }
            val hash = sha256(target)
            val elementId = db.withTransaction {
                val entryId = beginEntry(date, intoEntry, originalTime)
                val elementId = dao.insertElement(DiaryElement(entryId = entryId, type = type, position = dao.nextPosition(entryId)))
                dao.insertAsset(FileAsset(elementId = elementId, relativePath = target.name, originalName = name, mimeType = mime,
                    sizeBytes = target.length(), sha256 = hash))
                elementId
            }
            elementId
        } catch (e: Exception) {
            temp.delete(); target.delete()
            throw e
        }
    }

    suspend fun deleteElement(elementId: Long) {
        val asset = dao.assetForElement(elementId)
        dao.deleteElement(elementId)
        if (asset != null) File(mediaFolder, asset.relativePath).delete()
    }
    suspend fun deleteEntry(entryId: Long) {
        val assets = dao.assetsForEntry(entryId)
        dao.deleteEntry(entryId)
        for (a in assets) File(mediaFolder, a.relativePath).delete()
    }
    fun file(asset: FileAsset): File = File(mediaFolder, asset.relativePath)
    companion object {
        fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { stream ->
                val buffer = ByteArray(65536)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    md.update(buffer, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
