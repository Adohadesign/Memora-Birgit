package de.adohadesign.memorasolo.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import de.adohadesign.memorasolo.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Versioned, streaming ZIP. Restore only appends records; it never overwrites existing diary data. */
class ArchiveManager(private val context: Context, private val repo: DiaryRepository) {
    private fun entryJson(e: Entry) = JSONObject().apply {
        put("id", e.id); put("date", e.entryDate); put("time", e.displayTime)
        put("created", e.createdAt); put("updated", e.updatedAt)
    }
    private fun elementJson(e: DiaryElement) = JSONObject().apply {
        put("id", e.id); put("entryId", e.entryId); put("type", e.type)
        put("position", e.position); put("text", e.text)
        put("created", e.createdAt); put("updated", e.updatedAt)
    }
    private fun assetJson(a: FileAsset) = JSONObject().apply {
        put("elementId", a.elementId); put("name", a.originalName); put("mime", a.mimeType)
        put("size", a.sizeBytes); put("sha256", a.sha256)
        put("zipName", "media/${a.elementId}.bin")
    }
    suspend fun backup(uri: Uri): String = withContext(Dispatchers.IO) {
        val entries = repo.dao.allEntries()
        val elements = repo.dao.allElements()
        val assets = repo.dao.allAssets()
        for (a in assets) {
            val file = repo.file(a)
            require(file.isFile && file.length() == a.sizeBytes && DiaryRepository.sha256(file) == a.sha256) {
                "Datei fehlt oder ist beschädigt: ${a.originalName}"
            }
        }
        val manifest = JSONObject().apply {
            put("format", "memora-solo")
            put("version", 1)
            put("entries", JSONArray().apply { entries.forEach { put(entryJson(it)) } })
            put("elements", JSONArray().apply { elements.forEach { put(elementJson(it)) } })
            put("assets", JSONArray().apply { assets.forEach { put(assetJson(it)) } })
        }
        val stream = context.contentResolver.openOutputStream(uri, "w") ?: error("Ziel nicht beschreibbar")
        ZipOutputStream(stream.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (asset in assets) {
                zip.putNextEntry(ZipEntry("media/${asset.elementId}.bin"))
                repo.file(asset).inputStream().use { it.copyTo(zip, 64 * 1024) }
                zip.closeEntry()
            }
        }
        "${entries.size} Einträge, ${assets.size} Dateien gesichert."
    }

    suspend fun restore(uri: Uri): String = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore_${UUID.randomUUID()}")
        staging.mkdirs()
        try {
            var manifest: JSONObject? = null
            val observed = mutableSetOf<String>()
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Sicherung nicht lesbar" }
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        require(name == "manifest.json" || Regex("media/[0-9]+\\.bin").matches(name)) { "Ungültiger ZIP-Pfad" }
                        require(observed.add(name)) { "Doppelter ZIP-Eintrag" }
                        if (name == "manifest.json") {
                            // The manifest is small; media is streamed to disk.
                            val bytes = zip.readBytes()
                            require(bytes.size <= 20_000_000) { "Sicherung enthält zu viele Metadaten" }
                            manifest = JSONObject(bytes.toString(Charsets.UTF_8))
                        } else {
                            val target = File(staging, name.substringAfter('/'))
                            target.outputStream().use { zip.copyTo(it, 64 * 1024) }
                        }
                        zip.closeEntry()
                    }
                }
            }
            val m = requireNotNull(manifest) { "Keine Manifestdatei" }
            require(m.optString("format") == "memora-solo" && m.optInt("version") == 1) { "Unbekanntes Sicherungsformat" }
            val srcEntries = m.getJSONArray("entries")
            val srcElements = m.getJSONArray("elements")
            val srcAssets = m.getJSONArray("assets")
            val ids = mutableSetOf<Long>()
            for (i in 0 until srcElements.length()) ids += srcElements.getJSONObject(i).getLong("id")
            for (i in 0 until srcAssets.length()) {
                val a = srcAssets.getJSONObject(i)
                val elementId = a.getLong("elementId")
                require(elementId in ids) { "Datei ohne Element" }
                val file = File(staging, "$elementId.bin")
                require(file.isFile && file.length() == a.getLong("size") && DiaryRepository.sha256(file) == a.getString("sha256")) {
                    "Beschädigte oder fehlende Datei: ${a.getString("name")}" 
                }
            }
            // Copy to new private paths first; a failed transaction removes the new copies.
            val copied = mutableListOf<File>()
            try {
                val fileMap = mutableMapOf<Long, File>()
                for (i in 0 until srcAssets.length()) {
                    val a = srcAssets.getJSONObject(i)
                    val src = File(staging, "${a.getLong("elementId")}.bin")
                    val dest = File(repo.mediaFolder, "${UUID.randomUUID()}.bin")
                    src.copyTo(dest)
                    copied += dest
                    fileMap[a.getLong("elementId")] = dest
                }
                repo.db.withTransaction {
                    val entryMap = mutableMapOf<Long, Long>()
                    for (i in 0 until srcEntries.length()) {
                        val obj = srcEntries.getJSONObject(i)
                        val oldId = obj.getLong("id")
                        val newId = repo.dao.insertEntry(Entry(entryDate = obj.getString("date"),
                            displayTime = obj.getString("time"), createdAt = obj.getLong("created"),
                            updatedAt = obj.getLong("updated"), isOpen = false))
                        entryMap[oldId] = newId
                    }
                    val elementMap = mutableMapOf<Long, Long>()
                    for (i in 0 until srcElements.length()) {
                        val obj = srcElements.getJSONObject(i)
                        val newParent = requireNotNull(entryMap[obj.getLong("entryId")]) { "Element ohne Eintrag" }
                        elementMap[obj.getLong("id")] = repo.dao.insertElement(DiaryElement(entryId = newParent,
                            type = obj.getString("type"), position = obj.getInt("position"), text = obj.getString("text"),
                            createdAt = obj.getLong("created"), updatedAt = obj.getLong("updated")))
                    }
                    for (i in 0 until srcAssets.length()) {
                        val obj = srcAssets.getJSONObject(i)
                        val oldElementId = obj.getLong("elementId")
                        val newElementId = requireNotNull(elementMap[oldElementId])
                        repo.dao.insertAsset(FileAsset(elementId = newElementId,
                            relativePath = requireNotNull(fileMap[oldElementId]).name,
                            originalName = obj.getString("name"), mimeType = obj.getString("mime"),
                            sizeBytes = obj.getLong("size"), sha256 = obj.getString("sha256")))
                    }
                }
                "${srcEntries.length()} Einträge aus der Sicherung hinzugefügt. Vorhandene Einträge blieben erhalten."
            } catch (e: Exception) {
                copied.forEach { it.delete() }
                throw e
            }
        } finally { staging.deleteRecursively() }
    }
}
