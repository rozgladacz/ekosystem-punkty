package pl.rozgladacz.ekosystempunkty.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint
import java.io.File
import kotlin.math.abs

class BitmapRegionTest {
    @Test fun regionAndLogicalGridMatchTheUprightImageForEveryExifTransform() {
        check(OpenCVLoader.initLocal())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("region-exif-", ".jpg", context.cacheDir)
        val source = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        val paint = Paint()
        for ((index, color) in listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW).withIndex()) {
            paint.color = color
            canvas.drawRect(index % 2 * 400f, index / 2 * 300f, (index % 2 + 1) * 400f, (index / 2 + 1) * 300f, paint)
        }
        try {
            for (orientation in 1..8) {
                file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
                val uri = Uri.fromFile(file)
                val full = BitmapLoader.load(context, uri)
                try {
                    val corners = listOf(NormalizedPoint(.1f, .1f), NormalizedPoint(.9f, .1f), NormalizedPoint(.9f, .9f), NormalizedPoint(.1f, .9f))
                    for (turn in 0..3) {
                        val logical = corners.drop(turn) + corners.take(turn)
                        BitmapLoader.loadRegion(context, uri, logical).use { region ->
                            val warped = BoardRecognizer.warp(region.bitmap, region.corners)
                            try {
                                for (corner in 0..3) {
                                    val point = logical[corner]
                                    val expected = full.getPixel((point.x * full.width).toInt(), (point.y * full.height).toInt())
                                    val positions = listOf(.12f to .12f, .88f to .12f, .88f to .88f, .12f to .88f)
                                    val actual = warped.getPixel((positions[corner].first * warped.width).toInt(), (positions[corner].second * warped.height).toInt())
                                    assertTrue("orientation=$orientation turn=$turn corner=$corner", abs(Color.red(actual) - Color.red(expected)) < 20 &&
                                        abs(Color.green(actual) - Color.green(expected)) < 20 && abs(Color.blue(actual) - Color.blue(expected)) < 20)
                                }
                            } finally { warped.recycle() }
                        }
                    }
                } finally { full.recycle() }
            }
        } finally { source.recycle(); file.delete() }
    }
}
