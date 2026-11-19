package pl.rozgladacz.ekosystempunkty.vision

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import pl.rozgladacz.ekosystempunkty.domain.BoardGrid
import pl.rozgladacz.ekosystempunkty.domain.BoardOutline
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint
import pl.rozgladacz.ekosystempunkty.domain.RecognitionCell
import pl.rozgladacz.ekosystempunkty.domain.RecognitionResult
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln

data class CropProposal(val imageUri: Uri, val corners: List<NormalizedPoint>, val confidence: Float)
data class BoardRecognition(val outline: BoardOutline, val result: RecognitionResult)

interface BoardRecognitionService {
    suspend fun detect(uri: Uri): CropProposal
    suspend fun detectAll(uri: Uri): List<BoardOutline>
    suspend fun recognize(uri: Uri, corners: List<NormalizedPoint>): RecognitionResult
    suspend fun recognizeAll(uri: Uri, boards: List<BoardOutline>, onProgress: (Int, Int) -> Unit): List<BoardRecognition>
}

class BoardRecognizer(private val context: Context) : BoardRecognitionService {
    init { check(OpenCVLoader.initLocal()) { "Nie udało się załadować OpenCV" } }

    override suspend fun detect(uri: Uri): CropProposal = withContext(Dispatchers.Default) {
        val bitmap = BitmapLoader.load(context, uri)
        try {
            val location = BoardLocator.locate(bitmap)
            CropProposal(uri, location.corners, location.confidence)
        } finally { bitmap.recycle() }
    }

    override suspend fun detectAll(uri: Uri): List<BoardOutline> = withContext(Dispatchers.Default) {
        // Decode above the analysis size: power-of-two JPEG sampling can
        // otherwise halve small cards before the locator sees them.
        val bitmap = BitmapLoader.load(context, uri, maximumSide = 3200)
        val jobContext = currentCoroutineContext()
        val locations = try {
            MultiBoardLocator.locate(bitmap, checkCancellation = { jobContext.ensureActive() })
        } finally {
            bitmap.recycle()
        }
        if (locations.isEmpty()) return@withContext emptyList()
        CardClassifier(context).use { classifier ->
            locations.mapIndexed { index, location ->
                currentCoroutineContext().ensureActive()
                val oriented = BitmapLoader.loadRegion(context, uri, location.corners).use { region ->
                    (0..3).map { turn ->
                        currentCoroutineContext().ensureActive()
                        val corners = region.corners.drop(turn) + region.corners.take(turn)
                        val warped = warp(region.bitmap, corners)
                        val score = try {
                            val cells = classify(warped, classifier)
                            val points = corners.map { Point((it.x * region.bitmap.width).toDouble(), (it.y * region.bitmap.height).toDouble()) }
                            val width = distance(points[0], points[1]) + distance(points[3], points[2])
                            val height = distance(points[0], points[3]) + distance(points[1], points[2])
                            cells.map { it.confidence }.average() - .35 * abs(ln((width / height) / BOARD_RATIO))
                        } finally { warped.recycle() }
                        turn to score
                    }.maxBy { it.second }.first
                }
                BoardOutline(
                    id = UUID.randomUUID().toString(), name = "Gracz ${index + 1}",
                    corners = location.corners.drop(oriented) + location.corners.take(oriented),
                    confidence = location.confidence,
                )
            }
        }
    }

    override suspend fun recognize(uri: Uri, corners: List<NormalizedPoint>): RecognitionResult =
        withContext(Dispatchers.Default) { CardClassifier(context).use { recognizeRegion(uri, corners, it) } }

    override suspend fun recognizeAll(uri: Uri, boards: List<BoardOutline>, onProgress: (Int, Int) -> Unit): List<BoardRecognition> =
        withContext(Dispatchers.Default) {
            CardClassifier(context).use { classifier ->
                boards.mapIndexed { index, board ->
                    currentCoroutineContext().ensureActive()
                    onProgress(index + 1, boards.size)
                    BoardRecognition(board, recognizeRegion(uri, board.corners, classifier))
                }
            }
        }

    private suspend fun recognizeRegion(uri: Uri, corners: List<NormalizedPoint>, classifier: CardClassifier): RecognitionResult {
        val warped = BitmapLoader.loadRegion(context, uri, corners).use { warp(it.bitmap, it.corners) }
        return try {
            val cells = classify(warped, classifier)
            RecognitionResult(cells, corners, cells.map { it.confidence }.average().toFloat())
        } finally { warped.recycle() }
    }

    private suspend fun classify(warped: Bitmap, classifier: CardClassifier): List<RecognitionCell> {
        val columns = BoardGrid.COLUMNS
        val cellWidth = warped.width / columns
        val cellHeight = warped.height / BoardGrid.ROWS
        val insetX = (cellWidth * .06f).toInt()
        val insetY = (cellHeight * .06f).toInt()
        return List(BoardGrid.CELL_COUNT) { index ->
            currentCoroutineContext().ensureActive()
            val crop = Bitmap.createBitmap(warped, index % columns * cellWidth + insetX, index / columns * cellHeight + insetY,
                cellWidth - insetX * 2, cellHeight - insetY * 2)
            try { classifier.classify(crop).copy(visualQuality = GlareQuality.score(crop)) }
            finally { crop.recycle() }
        }
    }

    companion object {
        private const val OUTPUT_WIDTH = 1000
        private const val OUTPUT_HEIGHT = 1118
        private const val BOARD_RATIO = 5.0 * 63 / (4 * 88)
        private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

        internal suspend fun preview(context: Context, uri: Uri, corners: List<NormalizedPoint>): Bitmap = withContext(Dispatchers.IO) {
            check(OpenCVLoader.initLocal())
            BitmapLoader.loadRegion(context, uri, corners, maximumSide = 1200).use { warp(it.bitmap, it.corners) }
        }

        internal fun warp(bitmap: Bitmap, corners: List<NormalizedPoint>): Bitmap {
            val source = Mat()
            val destination = Mat(OUTPUT_HEIGHT, OUTPUT_WIDTH, CvType.CV_8UC4, Scalar.all(0.0))
            val from = MatOfPoint2f(*corners.map { Point((it.x * bitmap.width).toDouble(), (it.y * bitmap.height).toDouble()) }.toTypedArray())
            val to = MatOfPoint2f(Point(0.0, 0.0), Point(OUTPUT_WIDTH.toDouble(), 0.0),
                Point(OUTPUT_WIDTH.toDouble(), OUTPUT_HEIGHT.toDouble()), Point(0.0, OUTPUT_HEIGHT.toDouble()))
            val transform = Imgproc.getPerspectiveTransform(from, to)
            try {
                Utils.bitmapToMat(bitmap, source)
                Imgproc.warpPerspective(source, destination, transform, Size(OUTPUT_WIDTH.toDouble(), OUTPUT_HEIGHT.toDouble()))
                val output = Bitmap.createBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
                try { Utils.matToBitmap(destination, output) } catch (error: Throwable) { output.recycle(); throw error }
                return output
            } finally {
                source.release(); destination.release(); transform.release(); from.release(); to.release()
            }
        }
    }
}
