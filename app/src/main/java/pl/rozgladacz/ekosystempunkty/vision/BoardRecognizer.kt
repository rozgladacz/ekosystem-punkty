package pl.rozgladacz.ekosystempunkty.vision

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
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
            val location = BoardLocator.locate(bitmap)
            CropProposal(uri, location.corners, location.confidence)
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
                    try {
                        classifier.classify(crop).copy(visualQuality = GlareQuality.score(crop))
                    } finally {
                        crop.recycle()
                    }
                }
                RecognitionResult(cells, corners, cells.map { it.confidence }.average().toFloat())
            } finally {
                classifier.close()
                warped.recycle()
            }
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

    companion object {
        private const val OUTPUT_WIDTH = 1000
        private const val OUTPUT_HEIGHT = 1118
    }
}
