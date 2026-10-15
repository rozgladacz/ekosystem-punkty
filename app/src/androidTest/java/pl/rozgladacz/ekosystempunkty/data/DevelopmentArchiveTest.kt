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
            archive.labelPhoto(id, labels, corners, List(20) { .8f }, setOf(3))
            archive.updateLabeledGroup(group, labels.toMutableList().also { it[4] = CardType.FOX })
            assertEquals(1, archive.exportTo(Uri.fromFile(exported)))
            ZipFile(exported).use { zip ->
                assertNotNull(zip.getEntry("images/$id.jpg"))
                val metadata = zip.getInputStream(zip.getEntry("metadata/$id.json"))
                    .bufferedReader().use { JSONObject(it.readText()) }
                assertEquals("labeled", metadata.getString("status"))
                assertEquals(group, metadata.getString("group_id"))
                assertEquals("FOX", metadata.getJSONArray("labels").getString(4))
                assertTrue(metadata.getJSONArray("manually_corrected_indices").toString().contains("4"))
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
