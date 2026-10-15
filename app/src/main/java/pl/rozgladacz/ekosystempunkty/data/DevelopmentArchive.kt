package pl.rozgladacz.ekosystempunkty.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pl.rozgladacz.ekosystempunkty.domain.CardType
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint
import pl.rozgladacz.ekosystempunkty.vision.BitmapLoader
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Private, opt-in training material. Never uploads or shares without a document picker. */
class DevelopmentArchive(
    private val context: Context,
    private val root: File = File(context.filesDir, "development_captures"),
) {
    private val preferences = context.getSharedPreferences("development_mode", Context.MODE_PRIVATE)
    private val images = File(root, "images")
    private val records = File(root, "records")

    val enabled: Boolean get() = preferences.getBoolean("enabled", false)

    fun setEnabled(value: Boolean) {
        check(preferences.edit().putBoolean("enabled", value).commit()) { "Nie udało się zapisać ustawienia" }
    }

    suspend fun savePhoto(uri: Uri, groupId: String, shotIndex: Int): String = withContext(Dispatchers.IO) {
        requireUuid(groupId)
        require(shotIndex > 0)
        check(images.mkdirs() || images.isDirectory)
        check(records.mkdirs() || records.isDirectory)
        val id = UUID.randomUUID().toString()
        val imageFile = File(images, "$id.jpg")
        val pending = File(images, "$id.part")
        try {
            val bitmap = BitmapLoader.load(context, uri)
            val width = bitmap.width
            val height = bitmap.height
            try {
                FileOutputStream(pending).use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream))
                    stream.fd.sync()
                }
            } finally {
                bitmap.recycle()
            }
            check(pending.renameTo(imageFile)) { "Nie udało się zachować zdjęcia" }
            val metadata = JSONObject().apply {
                put("schema_version", 1)
                put("capture_id", id)
                put("group_id", groupId)
                put("shot_index", shotIndex)
                put("image_file", "images/$id.jpg")
                put("image_width", width)
                put("image_height", height)
                put("image_sha256", sha256(imageFile))
                put("exif_removed", true)
                put("status", "unlabeled")
            }
            writeRecord(id, metadata)
            id
        } catch (error: Exception) {
            pending.delete()
            imageFile.delete()
            throw error
        }
    }

    suspend fun labelPhoto(
        id: String,
        labels: List<CardType>,
        corners: List<NormalizedPoint>,
        confidences: List<Float>,
        classProbabilities: List<List<Float>>,
        manuallyCorrectedIndices: Set<Int>,
    ) = withContext(Dispatchers.IO) {
        require(labels.size == 20 && corners.size == 4 && confidences.size == 20 && classProbabilities.size == 20)
        require(classProbabilities.all { it.isEmpty() || it.size == CardType.entries.size })
        val metadata = readRecord(id)
        metadata.put("status", "labeled")
        metadata.put("labels", JSONArray().apply { labels.forEach { put(it.name) } })
        metadata.put("corners", JSONArray().apply {
            corners.forEach { point -> put(JSONArray().put(point.x.toDouble()).put(point.y.toDouble())) }
        })
        metadata.put("recognition_confidence", JSONArray().apply { confidences.forEach { put(it.toDouble()) } })
        metadata.put("class_probabilities", JSONArray().apply {
            classProbabilities.forEach { scores ->
                put(JSONArray().apply { scores.forEach { put(it.toDouble()) } })
            }
        })
        metadata.put("manually_corrected_indices", JSONArray().apply {
            manuallyCorrectedIndices.sorted().forEach(::put)
        })
        writeRecord(id, metadata)
    }

    suspend fun updateLabeledGroup(groupId: String, labels: List<CardType>) = withContext(Dispatchers.IO) {
        requireUuid(groupId)
        require(labels.size == 20)
        records.listFiles { file -> file.extension == "json" }.orEmpty().forEach { file ->
            val metadata = JSONObject(file.readText(Charsets.UTF_8))
            if (metadata.optString("group_id") != groupId || metadata.optString("status") != "labeled") return@forEach
            val old = metadata.getJSONArray("labels")
            val changed = (0 until 20).filter { old.getString(it) != labels[it].name }
            val corrected = metadata.optJSONArray("manually_corrected_indices") ?: JSONArray()
            val indices = (0 until corrected.length()).map { corrected.getInt(it) }.toSet() + changed
            metadata.put("labels", JSONArray().apply { labels.forEach { put(it.name) } })
            metadata.put("manually_corrected_indices", JSONArray().apply { indices.sorted().forEach(::put) })
            writeRecord(metadata.getString("capture_id"), metadata)
        }
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        records.listFiles { file -> file.extension == "json" }?.count() ?: 0
    }

    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val files = records.listFiles { file -> file.extension == "json" }?.sortedBy { it.name }.orEmpty()
        check(files.isNotEmpty()) { "Brak zapisanych zdjęć" }
        // Validate before opening a user-chosen document, so a broken local
        // record cannot silently produce a partial training archive.
        val items = files.map { record ->
            val metadata = JSONObject(record.readText(Charsets.UTF_8))
            val id = metadata.getString("capture_id")
            requireUuid(id)
            val image = File(images, "$id.jpg")
            check(image.isFile && sha256(image) == metadata.getString("image_sha256")) {
                "Brak zdjęcia lub niezgodna suma kontrolna: $id"
            }
            record to image
        }
        val output = context.contentResolver.openOutputStream(uri, "w")
            ?: error("Nie można zapisać wybranego pliku")
        ZipOutputStream(output.buffered()).use { zip ->
            fun add(name: String, source: File) {
                zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                source.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            items.forEach { (record, image) ->
                add("metadata/${record.name}", record)
                add("images/${image.name}", image)
            }
            zip.putNextEntry(ZipEntry("card_labels.txt").apply { time = 0L })
            zip.write((CardType.entries.joinToString("\n") { it.name } + "\n").toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("README.txt").apply { time = 0L })
            zip.write(
                ("Ekosystem Punkty - material rozwojowy\n" +
                    "Pary laczy group_id. status=labeled oznacza potwierdzone 20 kart.\n" +
                    "class_probabilities: 20 pol x 11 klas w kolejnosci card_labels.txt.\n" +
                    "Nie ma nazw graczy, dat, lokalizacji ani EXIF.\n").toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()
        }
        items.size
    }

    suspend fun deleteAll(): Int = withContext(Dispatchers.IO) {
        val files = listOf(images, records).flatMap { folder -> folder.listFiles()?.filter { it.isFile }.orEmpty() }
        files.forEach { check(it.delete()) { "Nie udało się usunąć ${it.name}" } }
        files.size
    }

    private fun readRecord(id: String): JSONObject {
        requireUuid(id)
        return JSONObject(File(records, "$id.json").readText(Charsets.UTF_8))
    }

    private fun writeRecord(id: String, metadata: JSONObject) {
        requireUuid(id)
        val atomic = AtomicFile(File(records, "$id.json"))
        val stream = atomic.startWrite()
        try {
            stream.write(metadata.toString(2).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                digest.update(buffer, 0, size)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun requireUuid(value: String) {
        require(value.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")))
    }
}
