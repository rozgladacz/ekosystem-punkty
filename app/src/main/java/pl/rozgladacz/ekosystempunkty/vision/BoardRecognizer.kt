package pl.rozgladacz.ekosystempunkty.vision

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import pl.rozgladacz.ekosystempunkty.domain.BoardGrid
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint
import pl.rozgladacz.ekosystempunkty.domain.RecognitionResult

data class CropProposal(
    val imageUri: Uri,
    val corners: List<NormalizedPoint>,
    val confidence: Float,
)

class BoardRecognizer(private val context: Context) {
    init {
        check(OpenCVLoader.initLocal()) { "Nie udało się załadować OpenCV" }
    }

    suspend fun detect(uri: Uri): CropProposal = withContext(Dispatchers.Default) {
        val bitmap = BitmapLoader.load(context, uri)
        try {
            val corners = detectCorners(bitmap)
            CropProposal(uri, corners.first, corners.second)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun recognize(uri: Uri, corners: List<NormalizedPoint>): RecognitionResult =
        withContext(Dispatchers.Default) {
            val bitmap = BitmapLoader.load(context, uri)
            val warped = warp(bitmap, corners)
            bitmap.recycle()
            val classifier = CardClassifier(context)
            try {
                val cells = List(BoardGrid.CELL_COUNT) { index ->
                    val row = index / BoardGrid.COLUMNS
                    val column = index % BoardGrid.COLUMNS
                    val cellWidth = warped.width / BoardGrid.COLUMNS
                    val cellHeight = warped.height / BoardGrid.ROWS
                    val insetX = (cellWidth * 0.06f).toInt()
                    val insetY = (cellHeight * 0.06f).toInt()
                    val crop = Bitmap.createBitmap(
                        warped,
                        column * cellWidth + insetX,
                        row * cellHeight + insetY,
                        cellWidth - insetX * 2,
                        cellHeight - insetY * 2,
                    )
                    classifier.classify(crop).also { crop.recycle() }
                }
                RecognitionResult(cells, corners, cells.map { it.confidence }.average().toFloat())
            } finally {
                classifier.close()
                warped.recycle()
            }
        }

    private fun detectCorners(bitmap: Bitmap): Pair<List<NormalizedPoint>, Float> {
        val source = Mat()
        val gray = Mat()
        val edges = Mat()
        Utils.bitmapToMat(bitmap, source)
        Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(7.0, 7.0), 0.0)
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        Imgproc.morphologyEx(
            edges,
            edges,
            Imgproc.MORPH_CLOSE,
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(15.0, 15.0)),
        )
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        val imageArea = bitmap.width.toDouble() * bitmap.height
        val best = contours.mapNotNull { contour ->
            val curve = MatOfPoint2f(*contour.toArray())
            val approximation = MatOfPoint2f()
            Imgproc.approxPolyDP(curve, approximation, Imgproc.arcLength(curve, true) * 0.02, true)
            approximation.takeIf { it.total() == 4L && Imgproc.isContourConvex(MatOfPoint(*it.toArray())) }
        }.maxByOrNull { kotlin.math.abs(Imgproc.contourArea(it)) }

        val default = listOf(
            NormalizedPoint(0.05f, 0.05f),
            NormalizedPoint(0.95f, 0.05f),
            NormalizedPoint(0.95f, 0.95f),
            NormalizedPoint(0.05f, 0.95f),
        )
        val result = if (best == null) default to 0f else {
            val ordered = orderCorners(best.toArray()).map {
                NormalizedPoint((it.x / bitmap.width).toFloat(), (it.y / bitmap.height).toFloat())
            }
            val areaRatio = (kotlin.math.abs(Imgproc.contourArea(best)) / imageArea).toFloat()
            ordered to ((areaRatio - 0.2f) / 0.6f).coerceIn(0f, 1f)
        }
        source.release(); gray.release(); edges.release(); contours.forEach { it.release() }; best?.release()
        return result
    }

    private fun warp(bitmap: Bitmap, corners: List<NormalizedPoint>): Bitmap {
        val source = Mat()
        val destination = Mat(OUTPUT_HEIGHT, OUTPUT_WIDTH, CvType.CV_8UC4, Scalar.all(0.0))
        Utils.bitmapToMat(bitmap, source)
        val points = corners.map { Point((it.x * bitmap.width).toDouble(), (it.y * bitmap.height).toDouble()) }
        val transform = Imgproc.getPerspectiveTransform(
            MatOfPoint2f(*points.toTypedArray()),
            MatOfPoint2f(
                Point(0.0, 0.0),
                Point(OUTPUT_WIDTH.toDouble(), 0.0),
                Point(OUTPUT_WIDTH.toDouble(), OUTPUT_HEIGHT.toDouble()),
                Point(0.0, OUTPUT_HEIGHT.toDouble()),
            ),
        )
        Imgproc.warpPerspective(source, destination, transform, Size(OUTPUT_WIDTH.toDouble(), OUTPUT_HEIGHT.toDouble()))
        val output = Bitmap.createBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(destination, output)
        source.release(); destination.release(); transform.release()
        return output
    }

    private fun orderCorners(points: Array<Point>): List<Point> {
        val topLeft = points.minBy { it.x + it.y }
        val bottomRight = points.maxBy { it.x + it.y }
        val topRight = points.minBy { it.y - it.x }
        val bottomLeft = points.maxBy { it.y - it.x }
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    companion object {
        private const val OUTPUT_WIDTH = 1000
        private const val OUTPUT_HEIGHT = 1118
    }
}
