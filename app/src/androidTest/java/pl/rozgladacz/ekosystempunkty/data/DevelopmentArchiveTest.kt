package pl.rozgladacz.ekosystempunkty.data

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.rozgladacz.ekosystempunkty.domain.CardType
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipFile

class DevelopmentArchiveTest {
    @Test
    fun tablePhotoKeepsIndependentBoardLabelsAndGroupsInOneImage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "table-archive-${UUID.randomUUID()}")
        val source = File.createTempFile("table-source-", ".jpg", context.cacheDir)
        val exported = File.createTempFile("table-export-", ".zip", context.cacheDir)
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        try {
            val archive = DevelopmentArchive(context, root)
            val capture = archive.saveTablePhoto(Uri.fromFile(source))
            val first = ArchiveBoard(UUID.randomUUID().toString(), UUID.randomUUID().toString(), listOf(
                NormalizedPoint(.05f, .1f), NormalizedPoint(.45f, .1f), NormalizedPoint(.45f, .9f), NormalizedPoint(.05f, .9f)))
            val second = ArchiveBoard(UUID.randomUUID().toString(), UUID.randomUUID().toString(), listOf(
                NormalizedPoint(.55f, .1f), NormalizedPoint(.95f, .1f), NormalizedPoint(.95f, .9f), NormalizedPoint(.55f, .9f)))
            archive.registerTableBoards(capture, listOf(first, second))
            fun metadata() = JSONObject(File(root, "records/$capture.json").readText())
            val probabilities = List(20) { List(11) { .1f } }
            archive.labelPhoto(capture, List(20) { CardType.FOX }, first.corners, List(20) { .8f }, probabilities, setOf(2), first.boardId)
            assertEquals("partial", metadata().getString("status"))
            assertEquals("unlabeled", metadata().getJSONArray("boards").getJSONObject(1).getString("status"))
            archive.labelPhoto(capture, List(20) { CardType.DEER }, second.corners, List(20) { .9f }, probabilities, emptySet(), second.boardId)
            archive.updateLabeledGroup(first.groupId, List(20) { CardType.WOLF })
            val saved = metadata()
            assertEquals(2, saved.getInt("schema_version"))
            assertEquals("labeled", saved.getString("status"))
            assertFalse(saved.has("group_id"))
            assertEquals("WOLF", saved.getJSONArray("boards").getJSONObject(0).getJSONArray("labels").getString(0))
            assertEquals("DEER", saved.getJSONArray("boards").getJSONObject(1).getJSONArray("labels").getString(0))
            assertEquals(1, archive.count())
            assertEquals(1, archive.exportTo(Uri.fromFile(exported)))
            ZipFile(exported).use { zip ->
                assertEquals(1, zip.entries().asSequence().count { it.name.startsWith("images/") })
                val text = zip.getInputStream(zip.getEntry("metadata/$capture.json")).bufferedReader().use { it.readText() }
                assertFalse(text.contains("player_name"))
                assertTrue(text.contains(first.boardId))
                assertTrue(text.contains(second.boardId))
            }
        } finally { root.deleteRecursively(); source.delete(); exported.delete() }
    }

    @Test
    fun savesExifFreePhotoAndPrivateLabelsForManualZipExport() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val root = File(context.cacheDir, "development-test-$suffix")
        val source = File(context.cacheDir, "development-source-$suffix.jpg")
        val exported = File(context.cacheDir, "development-export-$suffix.zip")
        try {
            val bitmap = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.GREEN)
            FileOutputStream(source).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            bitmap.recycle()
            ExifInterface(source).apply {
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "52/1,0/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "21/1,0/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
                saveAttributes()
            }
            assertNotNull(ExifInterface(source).latLong)

            val archive = DevelopmentArchive(context, root)
            val group = UUID.randomUUID().toString()
            val id = archive.savePhoto(Uri.fromFile(source), group, 1)
            assertEquals(1, archive.count())
            assertNull(ExifInterface(File(root, "images/$id.jpg")).latLong)
            val labels = List(20) { CardType.MEADOW }
            val corners = listOf(
                NormalizedPoint(.1f, .1f), NormalizedPoint(.9f, .1f),
                NormalizedPoint(.9f, .9f), NormalizedPoint(.1f, .9f),
            )
            archive.labelPhoto(
                id, labels, corners, List(20) { .8f },
                List(20) { List(CardType.entries.size) { .1f } }, setOf(3),
            )
            archive.updateLabeledGroup(group, labels.toMutableList().also { it[4] = CardType.FOX })
            assertEquals(1, archive.exportTo(Uri.fromFile(exported)))
            ZipFile(exported).use { zip ->
                assertNotNull(zip.getEntry("images/$id.jpg"))
                assertNotNull(zip.getEntry("card_labels.txt"))
                val metadata = zip.getInputStream(zip.getEntry("metadata/$id.json"))
                    .bufferedReader().use { JSONObject(it.readText()) }
                assertEquals("labeled", metadata.getString("status"))
                assertEquals(group, metadata.getString("group_id"))
                assertEquals("FOX", metadata.getJSONArray("labels").getString(4))
                assertTrue(metadata.getJSONArray("manually_corrected_indices").toString().contains("4"))
                assertEquals(CardType.entries.size, metadata.getJSONArray("class_probabilities").getJSONArray(0).length())
                assertFalse(metadata.has("player_name"))
                assertFalse(metadata.has("captured_at"))
                assertFalse(metadata.has("location"))
                assertFalse(metadata.has("device_model"))
            }
            archive.deleteAll()
            assertEquals(0, archive.count())
        } finally {
            root.deleteRecursively()
            source.delete()
            exported.delete()
        }
    }
}
