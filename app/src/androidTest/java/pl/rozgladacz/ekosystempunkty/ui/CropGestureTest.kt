package pl.rozgladacz.ekosystempunkty.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint
import pl.rozgladacz.ekosystempunkty.vision.CropProposal
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class CropGestureTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun oneContinuousDragMovesCornerAcrossThePhoto() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("crop-gesture-", ".jpg", context.cacheDir)
        val fixture = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        fixture.eraseColor(Color.WHITE)
        file.outputStream().use { fixture.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        fixture.recycle()
        val proposal = CropProposal(
            Uri.fromFile(file),
            listOf(
                NormalizedPoint(.2f, .2f),
                NormalizedPoint(.8f, .2f),
                NormalizedPoint(.8f, .8f),
                NormalizedPoint(.2f, .8f),
            ),
            confidence = 1f,
        )
        val confirmed = AtomicReference<CropProposal?>()
        try {
            rule.setContent {
                CropScreen(proposal, additionalShot = false, onConfirm = confirmed::set, onRetake = {})
            }
            rule.waitUntil(5_000) {
                runCatching { rule.onNodeWithTag("crop-grid").fetchSemanticsNode() }.isSuccess
            }
            rule.onNodeWithTag("crop-grid").performTouchInput {
                swipe(
                    start = percentOffset(.2f, .2f),
                    end = percentOffset(.5f, .45f),
                    durationMillis = 700,
                )
            }
            rule.onNodeWithText("Rozpoznaj").performClick()
            assertNotNull(confirmed.get())
            assertTrue(confirmed.get()!!.corners[0].x > .4f)
            assertTrue(confirmed.get()!!.corners[0].y > .35f)
        } finally {
            file.delete()
        }
    }
}
