package pl.rozgladacz.ekosystempunkty.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.opencv.android.OpenCVLoader

class BoardLocatorTest {
    @Test
    fun detectsSeparatedFiveByFourCardsWithoutOuterFrame() {
        val bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(20, 28, 48))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (row in 0 until 4) {
            for (column in 0 until 5) {
                paint.color = Color.rgb(110 + row * 20, 100 + column * 18, 55 + row * 10)
                val left = 110f + column * 195f
                val top = 290f + row * 275f
                canvas.drawRoundRect(left, top, left + 170f, top + 240f, 9f, 9f, paint)
            }
        }
        val result = BoardLocator.locate(bitmap)
        assertTrue(result.confidence > 0f)
        assertEquals(4, result.corners.size)
        assertEquals(110f / 1200f, result.corners[0].x, .07f)
        assertEquals(290f / 1600f, result.corners[0].y, .07f)
        assertEquals(1060f / 1200f, result.corners[2].x, .07f)
        assertEquals(1355f / 1600f, result.corners[2].y, .07f)
        bitmap.recycle()
    }

    @Test
    fun emptyImageFallsBackToEditableCrop() {
        val bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val result = BoardLocator.locate(bitmap)
        assertEquals(0f, result.confidence)
        assertEquals(.05f, result.corners[0].x)
        assertEquals(.95f, result.corners[2].y)
        bitmap.recycle()
    }

    companion object {
        @BeforeClass @JvmStatic
        fun loadOpenCv() {
            assertTrue(OpenCVLoader.initLocal())
        }
    }
}
