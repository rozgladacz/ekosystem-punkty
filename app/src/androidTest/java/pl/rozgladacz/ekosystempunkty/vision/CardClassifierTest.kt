package pl.rozgladacz.ekosystempunkty.vision

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.rozgladacz.ekosystempunkty.domain.CardType

class CardClassifierTest {
    @Test
    fun bundledFloatModelLoadsAndReturnsAllElevenScores() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        try {
            CardClassifier(context).use { classifier ->
                val cell = classifier.classify(bitmap)
                assertEquals(CardType.entries.size, cell.probabilities.size)
                assertTrue(cell.probabilities.all { it.isFinite() && it in 0f..1f })
                assertTrue(cell.probabilities.sum() in .99f..1.01f)
                assertEquals(cell.probabilities.maxOrNull(), cell.confidence)
                assertEquals(CardType.entries[cell.probabilities.indexOf(cell.confidence)], cell.type)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
