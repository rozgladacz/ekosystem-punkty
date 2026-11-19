package pl.rozgladacz.ekosystempunkty.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.opencv.android.OpenCVLoader
import pl.rozgladacz.ekosystempunkty.domain.BoardGeometry
import java.io.File

class MultiBoardLocatorTest {
    @Test fun recognizesEachPlayerFromTheSamePrivateTableSource() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "cat /data/local/tmp/ekosystem-table.jpg",
        )).use { it.readBytes() }
        assumeTrue("Private table photograph is not included in Git or CI", bytes.size > 100_000)
        val file = File(instrumentation.targetContext.cacheDir, "table-recognizer-private.jpg")
        file.writeBytes(bytes)
        try {
            val recognizer = BoardRecognizer(instrumentation.targetContext)
            val uri = Uri.fromFile(file)
            val outlines = recognizer.detectAll(uri)
            assertEquals(3, outlines.size)
            val progress = mutableListOf<Int>()
            val results = recognizer.recognizeAll(uri, outlines) { current, total ->
                assertEquals(3, total); progress += current
            }
            assertEquals(listOf(1, 2, 3), progress)
            assertEquals(outlines.map { it.id }, results.map { it.outline.id })
            results.forEach { board ->
                assertEquals(20, board.result.cells.size)
                assertEquals(board.outline.corners, board.result.corners)
                assertTrue(board.result.cells.all { it.probabilities.size == 11 })
            }
        } finally { file.delete() }
    }

    @Test fun blankTableDoesNotProduceAnEditableFullImageFallback() {
        val bitmap = Bitmap.createBitmap(1200, 1000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(160, 130, 100))
        try { assertTrue(MultiBoardLocator.locate(bitmap).isEmpty()) } finally { bitmap.recycle() }
    }

    @Test fun backgroundSeamsAndObjectsDoNotBecomePlayerBoards() {
        val bitmap = Bitmap.createBitmap(1200, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(174, 145, 115))
        val paint = Paint().apply { color = Color.rgb(105, 82, 65); strokeWidth = 4f }
        for (x in 0..6) canvas.drawLine(x * 200f, 0f, x * 200f, 1000f, paint)
        for (y in 0..5) canvas.drawLine(0f, y * 190f, 1200f, y * 190f, paint)
        paint.color = Color.DKGRAY
        canvas.drawRect(30f, 25f, 290f, 130f, paint)
        canvas.drawRect(1050f, 750f, 1200f, 1000f, paint)
        try { assertTrue(MultiBoardLocator.locate(bitmap).isEmpty()) } finally { bitmap.recycle() }
    }

    @Test fun separatesPerspectiveBoardsWithGlareAndShadow() {
        val bitmap = Bitmap.createBitmap(1200, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(165, 135, 105))
        val board = Bitmap.createBitmap(450, 504, Bitmap.Config.ARGB_8888)
        val boardCanvas = Canvas(board)
        boardCanvas.drawColor(Color.rgb(165, 135, 105))
        boardCanvas.translate(225f, 252f); boardCanvas.scale(1.5f, 1.5f)
        drawBoard(boardCanvas)
        val source = floatArrayOf(0f, 0f, 450f, 0f, 450f, 504f, 0f, 504f)
        val left = floatArrayOf(60f, 200f, 490f, 170f, 535f, 700f, 90f, 710f)
        val right = floatArrayOf(620f, 150f, 1080f, 230f, 1050f, 730f, 620f, 660f)
        for (points in listOf(left, right)) {
            val matrix = Matrix().apply { setPolyToPoly(source, 0, points, 0, 4) }
            canvas.drawBitmap(board, matrix, null)
        }
        val paint = Paint().apply { color = Color.WHITE }
        canvas.drawOval(80f, 220f, 170f, 330f, paint)
        paint.color = Color.argb(150, 0, 0, 0)
        canvas.drawRect(605f, 460f, 1100f, 780f, paint)
        try {
            val boards = MultiBoardLocator.locate(bitmap)
            assertEquals("$boards", 2, boards.size)
            assertTrue(boards.all { BoardGeometry.isValid(it.corners) })
            assertTrue(boards.any { it.corners.map { p -> p.x }.average() < .45 })
            assertTrue(boards.any { it.corners.map { p -> p.x }.average() > .6 })
        } finally { bitmap.recycle(); board.recycle() }
    }

    @Test fun detectsTwoToSixSeparateBoardsFacingDifferentPlayers() {
        for (count in 2..6) {
            val bitmap = Bitmap.createBitmap(1500, 1200, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(165, 135, 105))
            val centers = listOf(250f to 300f, 750f to 300f, 1250f to 300f, 250f to 900f, 750f to 900f, 1250f to 900f)
            val angles = listOf(0f, 90f, 180f, 270f, 20f, -25f)
            for (index in 0 until count) {
                canvas.save()
                canvas.translate(centers[index].first, centers[index].second)
                canvas.rotate(angles[index])
                drawBoard(canvas)
                canvas.restore()
            }
            try {
                val boards = MultiBoardLocator.locate(bitmap)
                assertEquals("$count players: $boards", count, boards.size)
                assertTrue(boards.all { BoardGeometry.isValid(it.corners) })
                for ((x, y) in centers.take(count)) {
                    assertTrue("Missing board at $x,$y", boards.any {
                        kotlin.math.abs(it.corners.map { p -> p.x }.average() * bitmap.width - x) < 120 &&
                            kotlin.math.abs(it.corners.map { p -> p.y }.average() * bitmap.height - y) < 120
                    })
                }
            } finally { bitmap.recycle() }
        }
    }

    @Test fun privateThreePlayerPhotoProducesThreeIndependentProposals() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "cat /data/local/tmp/ekosystem-table.jpg",
        )).use { it.readBytes() }
        assumeTrue("Private table photograph is not included in Git or CI", bytes.size > 100_000)
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "table-locator-private.jpg")
        file.writeBytes(bytes)
        try {
            val loaded = BitmapLoader.load(context, Uri.fromFile(file), maximumSide = 2400)
            val bitmap = loaded.copy(Bitmap.Config.ARGB_8888, true)
            loaded.recycle()
            try {
                val boards = MultiBoardLocator.locate(bitmap) { text ->
                    File(context.getExternalFilesDir(null), "table-locator-debug.txt").writeText(text)
                }
                val output = JSONArray()
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GREEN; strokeWidth = 3f; style = Paint.Style.STROKE }
                boards.forEach { board ->
                    output.put(JSONObject().put("confidence", board.confidence.toDouble()).put("corners", JSONArray().apply {
                        board.corners.forEach { put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) }
                    }))
                    board.corners.indices.forEach { i ->
                        val a = board.corners[i]; val b = board.corners[(i + 1) % 4]
                        canvas.drawLine(a.x * bitmap.width, a.y * bitmap.height, b.x * bitmap.width, b.y * bitmap.height, paint)
                    }
                }
                File(context.getExternalFilesDir(null), "table-locator-results.json").writeText(output.toString(2))
                File(context.getExternalFilesDir(null), "table-locator-review.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                assertEquals("$output", 3, boards.size)
                assertTrue(boards.all { BoardGeometry.isValid(it.corners) })
                // Approximate, visually reviewed bounds of the shadowed bottom
                // board. Counting a bright subset as one board is insufficient.
                val bottom = boards.maxBy { it.corners.map { p -> p.y }.average() }
                val expected = listOf(.34 to .55, .71 to .55, .73 to .88, .33 to .87)
                val delta = (0..3).minOf { turn -> expected.indices.maxOf { index ->
                    val point = bottom.corners[(index + turn) % 4]
                    maxOf(kotlin.math.abs(point.x - expected[index].first), kotlin.math.abs(point.y - expected[index].second))
                } }
                assertTrue("Shadowed board bounds differ by $delta: ${bottom.corners}", delta < .085)
            } finally { bitmap.recycle() }
        } finally { file.delete() }
    }

    private fun drawBoard(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (row in 0..3) for (column in 0..4) {
            val left = -150f + column * 60
            val top = -168f + row * 84
            paint.color = Color.rgb(35 + column * 20, 65 + row * 30, 160 - row * 15)
            canvas.drawRoundRect(left, top, left + 55, top + 77, 4f, 4f, paint)
            paint.color = Color.rgb(230 - row * 10, 210 - column * 15, 45 + column * 15)
            paint.strokeWidth = 3f
            for (stripe in 0..6) canvas.drawLine(left + 5, top + 9 + stripe * 9, left + 50, top + 14 + stripe * 9, paint)
        }
    }

    companion object {
        @BeforeClass @JvmStatic fun openCv() { check(OpenCVLoader.initLocal()) }
    }
}
