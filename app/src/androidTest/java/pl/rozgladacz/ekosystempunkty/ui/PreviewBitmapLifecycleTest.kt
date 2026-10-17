package pl.rozgladacz.ekosystempunkty.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class PreviewBitmapLifecycleTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun leavingPreviewDoesNotRecycleImageStillReferencedByCompose() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("preview-test-", ".jpg", context.cacheDir)
        val fixture = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        fixture.eraseColor(Color.GREEN)
        file.outputStream().use { fixture.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        fixture.recycle()
        val loaded = AtomicReference<Bitmap?>()
        var visible by mutableStateOf(true)
        try {
            rule.setContent {
                if (visible) {
                    rememberPreviewBitmap(Uri.fromFile(file).toString())?.let { bitmap ->
                        loaded.set(bitmap)
                        Image(bitmap.asImageBitmap(), contentDescription = "Podgląd testowy")
                    }
                }
            }
            rule.waitUntil(5_000) { loaded.get() != null }
            rule.runOnIdle { visible = false }
            rule.waitForIdle()
            assertNotNull(loaded.get())
            assertFalse(loaded.get()!!.isRecycled)
        } finally {
            file.delete()
        }
    }
}
