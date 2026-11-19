package pl.rozgladacz.ekosystempunkty.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import pl.rozgladacz.ekosystempunkty.TableCaptureState
import pl.rozgladacz.ekosystempunkty.domain.BoardOutline
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint
import java.io.File
import java.util.UUID

class TableCropScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun board(left: Float, right: Float, name: String) = BoardOutline(UUID.randomUUID().toString(), name,
        listOf(NormalizedPoint(left, .2f), NormalizedPoint(right, .2f), NormalizedPoint(right, .8f), NormalizedPoint(left, .8f)))

    private fun photo(): File {
        val file = File.createTempFile("table-ui-", ".jpg", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.LTGRAY)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file
    }

    @Test fun editingNamesRotatingAndChangingCountPreservesOtherPlayers() {
        val file = photo()
        val first = board(.1f, .45f, "Gracz 1")
        val second = board(.55f, .9f, "Gracz 2")
        var table by mutableStateOf(TableCaptureState(Uri.fromFile(file), listOf(first, second)))
        try {
            rule.setContent { TableCropScreen(table, { table = table.copy(boards = it) }, {}, {}, {}) }
            rule.onNodeWithTag("table-player-name").performTextReplacement("Ala")
            rule.onNodeWithTag("table-player-name").performImeAction()
            rule.onNodeWithText("Obróć 90°").performScrollTo().performClick()
            rule.runOnIdle {
                assertEquals(first.id, table.boards[0].id)
                assertEquals("Ala", table.boards[0].name)
                assertEquals(first.corners[1], table.boards[0].corners[0])
                assertEquals(second, table.boards[1])
            }
            rule.onNodeWithText("Usuń").performScrollTo().performClick()
            rule.onNodeWithText("Rozpoznaj").assertIsNotEnabled()
            rule.onNodeWithText("Dodaj").performScrollTo().performClick()
            rule.onNodeWithText("Rozpoznaj").assertIsEnabled()
            rule.runOnIdle { assertEquals(second.id, table.boards[0].id); assertEquals(2, table.boards.size) }
        } finally { file.delete() }
    }

    @Test fun continuousCornerDragEditsTheOutlineInImageCoordinates() {
        val file = photo()
        val first = board(.2f, .8f, "Gracz 1")
        var table by mutableStateOf(TableCaptureState(Uri.fromFile(file), listOf(first)))
        try {
            rule.setContent { TableCropScreen(table, { table = table.copy(boards = it) }, {}, {}, {}) }
            rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("table-overview").fetchSemanticsNode() }.isSuccess }
            rule.onNodeWithTag("table-overview").performTouchInput {
                val side = minOf(width, height).toFloat()
                val origin = Offset((width - side) / 2, (height - side) / 2)
                swipe(origin + Offset(side * .2f, side * .2f), origin + Offset(side * .4f, side * .4f), 600)
            }
            rule.runOnIdle {
                assertTrue("${table.boards[0].corners}", table.boards[0].corners[0].x > .35f)
                assertTrue("${table.boards[0].corners}", table.boards[0].corners[0].y > .35f)
                assertEquals(first.id, table.boards[0].id)
            }
        } finally { file.delete() }
    }

    @Test fun pinchZoomKeepsCornerDraggingInImageCoordinates() {
        val file = photo()
        val first = BoardOutline(UUID.randomUUID().toString(), "Gracz 1", listOf(
            NormalizedPoint(.35f, .35f), NormalizedPoint(.65f, .35f), NormalizedPoint(.65f, .65f), NormalizedPoint(.35f, .65f)))
        var table by mutableStateOf(TableCaptureState(Uri.fromFile(file), listOf(first)))
        try {
            rule.setContent { TableCropScreen(table, { table = table.copy(boards = it) }, {}, {}, {}) }
            rule.waitUntil(5_000) { runCatching { rule.onNodeWithTag("table-overview").fetchSemanticsNode() }.isSuccess }
            rule.onNodeWithTag("table-overview").performTouchInput {
                val side = minOf(width, height).toFloat()
                pinch(start0 = center - Offset(side * .12f, 0f), end0 = center - Offset(side * .24f, 0f),
                    start1 = center + Offset(side * .12f, 0f), end1 = center + Offset(side * .24f, 0f), durationMillis = 500)
            }
            rule.onNodeWithTag("table-overview").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Powiększenie 200%"))
            rule.onNodeWithTag("table-overview").performTouchInput {
                val side = minOf(width, height).toFloat()
                swipe(center - Offset(side * .30f, side * .30f), center - Offset(side * .20f, side * .22f), 600)
            }
            rule.runOnIdle {
                val point = table.boards[0].corners[0]
                assertEquals(.40f, point.x, .02f)
                assertEquals(.39f, point.y, .02f)
            }
        } finally { file.delete() }
    }
}
