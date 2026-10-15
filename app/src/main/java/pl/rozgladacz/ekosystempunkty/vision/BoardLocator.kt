package pl.rozgladacz.ekosystempunkty.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class BoardLocation(val corners: List<NormalizedPoint>, val confidence: Float)

/** Locates a 5×4 group of separated cards; no outer printed border is assumed. */
internal object BoardLocator {
    private data class CardOutline(val area: Double, val rect: Rect)

    fun locate(bitmap: Bitmap): BoardLocation {
        val source = Mat()
        val resized = Mat()
        val gray = Mat()
        val edges = Mat()
        try {
            Utils.bitmapToMat(bitmap, source)
            // Fixed analysis scale keeps Canny and contour thresholds stable
            // across camera resolutions and limits native memory use.
            val scale = min(1200.0 / bitmap.width, 1600.0 / bitmap.height).coerceAtMost(1.0)
            Imgproc.resize(
                source, resized,
                Size((bitmap.width * scale).roundToInt().toDouble(), (bitmap.height * scale).roundToInt().toDouble()),
            )
            Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(gray, edges, 80.0, 200.0)
            val width = gray.cols()
            val height = gray.rows()
            val xProfile = edgeProfile(edges, 0)
            val yProfile = edgeProfile(edges, 1)
            if (xProfile.sum() == 0.0 || yProfile.sum() == 0.0) return fallback()

            var left = (weightedPosition(xProfile, .01) / width - .02).coerceAtLeast(0.0)
            var top = (weightedPosition(yProfile, .01) / height - .02).coerceAtLeast(0.0)
            val right = (weightedPosition(xProfile, .99) / width + .035).coerceAtMost(1.0)
            val bottom = (weightedPosition(yProfile, .99) / height + .035).coerceAtMost(1.0)
            if (right - left < .45 || bottom - top < .35) return fallback()

            val outlines = cardOutlines(gray, width, height)
            if (outlines.size >= 10) {
                val cardLeft = outlines.minOf { it.rect.x }.toDouble() / width
                val cardRight = outlines.maxOf { it.rect.x + it.rect.width }.toDouble() / width
                val cardTop = outlines.minOf { it.rect.y }.toDouble() / height
                val cardBottom = outlines.maxOf { it.rect.y + it.rect.height }.toDouble() / height
                // Ignore missing outer cards. Outlines only trim a clearly false
                // edge (for example, a seam on the fabric behind the board).
                if ((cardRight - cardLeft) / (right - left) >= .85 && cardLeft - left > .045) {
                    left = (cardLeft - .01).coerceAtLeast(0.0)
                }
                if ((cardBottom - cardTop) / (bottom - top) >= .7 && cardTop - top > .045) {
                    top = (cardTop - .01).coerceAtLeast(0.0)
                }
            }

            val bandHeight = (bottom - top) * .22
            val upper = horizontalSpan(edges, top, top + bandHeight, left, right)
            val lower = horizontalSpan(edges, bottom - bandHeight, bottom, left, right)
            val corners = listOf(
                NormalizedPoint(upper.first.toFloat(), top.toFloat()),
                NormalizedPoint(upper.second.toFloat(), top.toFloat()),
                NormalizedPoint(lower.second.toFloat(), bottom.toFloat()),
                NormalizedPoint(lower.first.toFloat(), bottom.toFloat()),
            )
            // This is only a crop proposal. A card grid can still be irregular,
            // so the user must confirm it before classification.
            val confidence = if (outlines.size >= 10) .55f else .35f
            return BoardLocation(corners, confidence)
        } finally {
            source.release()
            resized.release()
            gray.release()
            edges.release()
        }
    }

    private fun edgeProfile(edges: Mat, dimension: Int): DoubleArray {
        val reduced = Mat()
        try {
            Core.reduce(edges, reduced, dimension, Core.REDUCE_SUM, CvType.CV_64F)
            val values = DoubleArray(if (dimension == 0) edges.cols() else edges.rows())
            reduced.get(0, 0, values)
            return values
        } finally {
            reduced.release()
        }
    }

    private fun weightedPosition(profile: DoubleArray, fraction: Double): Double {
        val target = profile.sum() * fraction
        var accumulated = 0.0
        for (index in profile.indices) {
            accumulated += profile[index]
            if (accumulated >= target) return index.toDouble()
        }
        return (profile.size - 1).toDouble()
    }

    private fun horizontalSpan(
        edges: Mat,
        start: Double,
        end: Double,
        fallbackLeft: Double,
        fallbackRight: Double,
    ): Pair<Double, Double> {
        val firstRow = (start * edges.rows()).toInt().coerceIn(0, edges.rows() - 1)
        val lastRow = (end * edges.rows()).toInt().coerceIn(firstRow + 1, edges.rows())
        val band = Mat(edges, Rect(0, firstRow, edges.cols(), lastRow - firstRow))
        try {
            val profile = edgeProfile(band, 0)
            if (profile.sum() == 0.0) return fallbackLeft to fallbackRight
            val left = (weightedPosition(profile, .01) / edges.cols() - .02).coerceAtLeast(0.0)
            val right = (weightedPosition(profile, .99) / edges.cols() + .02).coerceAtMost(1.0)
            return if (right - left in .55.. .98) left to right else fallbackLeft to fallbackRight
        } finally {
            band.release()
        }
    }

    private fun cardOutlines(gray: Mat, width: Int, height: Int): List<CardOutline> {
        val cardEdges = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        try {
            Imgproc.Canny(gray, cardEdges, 40.0, 120.0)
            Imgproc.morphologyEx(cardEdges, cardEdges, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.findContours(cardEdges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            val imageArea = width.toDouble() * height
            val candidates = contours.mapNotNull { contour ->
                val area = Imgproc.contourArea(contour)
                val rect = Imgproc.boundingRect(contour)
                val boxArea = rect.width.toDouble() * rect.height
                if (boxArea == 0.0 || area / imageArea !in .003.. .06 ||
                    rect.width.toDouble() / rect.height !in .42..1.2 ||
                    area / boxArea <= .55 || rect.width.toDouble() / width <= .08 ||
                    rect.height.toDouble() / height <= .08
                ) null else CardOutline(area, rect)
            }.sortedByDescending { it.area }
            val kept = mutableListOf<CardOutline>()
            for (candidate in candidates) {
                if (kept.none { overlapFraction(candidate.rect, it.rect) > .65 }) kept += candidate
            }
            return kept
        } finally {
            contours.forEach { it.release() }
            cardEdges.release()
            kernel.release()
            hierarchy.release()
        }
    }

    private fun overlapFraction(a: Rect, b: Rect): Double {
        val width = max(0, min(a.x + a.width, b.x + b.width) - max(a.x, b.x))
        val height = max(0, min(a.y + a.height, b.y + b.height) - max(a.y, b.y))
        return width.toDouble() * height / min(a.width * a.height, b.width * b.height)
    }

    private fun fallback(): BoardLocation = BoardLocation(
        listOf(
            NormalizedPoint(.05f, .05f), NormalizedPoint(.95f, .05f),
            NormalizedPoint(.95f, .95f), NormalizedPoint(.05f, .95f),
        ),
        0f,
    )
}
