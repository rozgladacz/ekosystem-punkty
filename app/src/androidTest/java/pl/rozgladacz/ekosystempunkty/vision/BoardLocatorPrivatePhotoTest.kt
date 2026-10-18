package pl.rozgladacz.ekosystempunkty.vision

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File

/** Optional local regression: copy private photos to externalFilesDir/locator-test before running. */
@RunWith(AndroidJUnit4::class)
class BoardLocatorPrivatePhotoTest {
    @Test
    fun newFiveLayoutsNeedNoLargeCornerCorrection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "locator-test")
        assumeTrue("Private photos are not part of Git or CI", directory.isDirectory)
        assertTrue(OpenCVLoader.initLocal())
        val expected = mapOf(
            "20261017_103632.jpg" to listOf(.12f, .13f, .83f, .77f),
            "20261017_103639.jpg" to listOf(.11f, .14f, .80f, .76f),
            "20261017_103644.jpg" to listOf(.10f, .13f, .92f, .80f),
            "20261017_103651.jpg" to listOf(.08f, .15f, .85f, .78f),
            "20261018_140749.jpg" to listOf(.13f, .19f, .89f, .84f),
            "20261018_140754.jpg" to listOf(.12f, .27f, .87f, .91f),
            "20261018_181339.jpg" to listOf(.04f, .15f, .89f, .93f),
            "20261018_181344.jpg" to listOf(.09f, .25f, .81f, .86f),
            "20261018_181350.jpg" to listOf(.06f, .13f, .85f, .83f),
            "20261018_181356.jpg" to listOf(.13f, .17f, .83f, .76f),
        )
        for ((name, bounds) in expected) {
            val image = File(directory, name)
            assertTrue("Missing ${image.absolutePath}; visible=${directory.listFiles()?.map { it.name }}", image.isFile)
            val bitmap = BitmapLoader.load(context, Uri.fromFile(image))
            try {
                val result = BoardLocator.locate(bitmap)
                val actual = listOf(
                    result.corners[0].x, result.corners[0].y,
                    result.corners[2].x, result.corners[2].y,
                )
                for (index in 0..3) {
                    assertEquals("$name coordinate $index", bounds[index], actual[index], .055f)
                }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
