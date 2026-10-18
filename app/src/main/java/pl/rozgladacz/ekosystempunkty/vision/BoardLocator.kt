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
import kotlin.math.abs

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
            if (xProfile.sum() == 0.0 || yProfile.sum() == 0.0) {
                val baseline = fallback()
                return textureLocation(source, baseline) ?: baseline
            }

            var left = (weightedPosition(xProfile, .01) / width - .02).coerceAtLeast(0.0)
            var top = (weightedPosition(yProfile, .01) / height - .02).coerceAtLeast(0.0)
            val right = (weightedPosition(xProfile, .99) / width + .035).coerceAtMost(1.0)
            val bottom = (weightedPosition(yProfile, .99) / height + .035).coerceAtMost(1.0)
            if (right - left < .45 || bottom - top < .35) {
                val baseline = fallback()
                return textureLocation(source, baseline) ?: baseline
            }

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
            val baseline = BoardLocation(corners, confidence)
            return textureLocation(source, baseline) ?: baseline
        } finally {
            source.release()
            resized.release()
            gray.release()
            edges.release()
        }
    }

    /**
     * Card illustrations have much stronger local texture than a table or floor.
     * A dense 5×4 cluster is located from that texture, then checked against the
     * old edge proposal. The latter remains the fallback on patterned surfaces.
     */
    private fun textureLocation(source: Mat, baseline: BoardLocation): BoardLocation? {
        val small = Mat()
        val gray = Mat()
        val floatGray = Mat()
        val mean = Mat()
        val squared = Mat()
        val variance = Mat()
        val meanSquared = Mat()
        val mask = Mat()
        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(21.0, 21.0))
        val openKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        try {
            val scale = min(600.0 / source.cols(), 800.0 / source.rows()).coerceAtMost(1.0)
            Imgproc.resize(source, small, Size(
                max(1, (source.cols() * scale).roundToInt()).toDouble(),
                max(1, (source.rows() * scale).roundToInt()).toDouble(),
            ))
            if (small.cols() < 100 || small.rows() < 100) return null
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            gray.convertTo(floatGray, CvType.CV_32F)
            Imgproc.boxFilter(floatGray, mean, CvType.CV_32F, Size(25.0, 25.0))
            Core.multiply(floatGray, floatGray, squared)
            Imgproc.boxFilter(squared, variance, CvType.CV_32F, Size(25.0, 25.0))
            Core.multiply(mean, mean, meanSquared)
            Core.subtract(variance, meanSquared, variance)
            Imgproc.threshold(variance, mask, 230.0, 255.0, Imgproc.THRESH_BINARY)
            mask.convertTo(mask, CvType.CV_8U)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, closeKernel)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, openKernel)

            val width = mask.cols()
            val height = mask.rows()
            val xProfile = profileForBand(mask, Rect(0, (height * .30).toInt(), width, (height * .40).toInt()), 0)
            val yProfile = profileForBand(mask, Rect((width * .25).toInt(), 0, (width * .50).toInt(), height), 1)
            val xSpan = textureSpan(xProfile, .35) ?: return null
            // Pale cards can have much less texture than dark cards in other
            // rows, so the vertical profile uses a lower relative threshold.
            val ySpan = textureSpan(yProfile, .20) ?: return null
            val proposed = intArrayOf(xSpan.first, ySpan.first, xSpan.second, ySpan.second)
            if (proposed[2] - proposed[0] !in (width * .40).toInt()..(width * .95).toInt() ||
                proposed[3] - proposed[1] !in (height * .35).toInt()..(height * .95).toInt()
            ) return null
            val ratio = (proposed[2] - proposed[0]).toDouble() / (proposed[3] - proposed[1])
            if (ratio !in .60..1.25) return null

            val refined = extendWeakOuterEdges(mask, refineTextureBounds(mask, proposed))
            val textureScores = sideScores(mask, refined)
            val baselineBounds = intArrayOf(
                (baseline.corners.minOf { it.x } * width).toInt().coerceIn(0, width - 1),
                (baseline.corners.minOf { it.y } * height).toInt().coerceIn(0, height - 1),
                (baseline.corners.maxOf { it.x } * width).toInt().coerceIn(1, width),
                (baseline.corners.maxOf { it.y } * height).toInt().coerceIn(1, height),
            )
            val baselineAverage = sideScores(mask, baselineBounds).average()
            val textureAverage = textureScores.average()
            val weakestSide = textureScores.minOrNull() ?: return null
            val advantage = textureAverage - baselineAverage
            if (textureAverage < .40 || textureScores.count { it >= .35 } < 3 ||
                !(weakestSide >= .30 && advantage >= .07 ||
                    weakestSide >= .20 && advantage >= .12)
            ) return null

            return BoardLocation(
                listOf(
                    NormalizedPoint(refined[0].toFloat() / width, refined[1].toFloat() / height),
                    NormalizedPoint(refined[2].toFloat() / width, refined[1].toFloat() / height),
                    NormalizedPoint(refined[2].toFloat() / width, refined[3].toFloat() / height),
                    NormalizedPoint(refined[0].toFloat() / width, refined[3].toFloat() / height),
                ),
                .55f,
            )
        } finally {
            small.release(); gray.release(); floatGray.release(); mean.release()
            squared.release(); variance.release(); meanSquared.release(); mask.release()
            closeKernel.release(); openKernel.release()
        }
    }

    private fun profileForBand(mask: Mat, region: Rect, dimension: Int): DoubleArray {
        val band = Mat(mask, region)
        try {
            return edgeProfile(band, dimension)
        } finally {
            band.release()
        }
    }

    private fun textureSpan(profile: DoubleArray, fraction: Double): Pair<Int, Int>? {
        if (profile.isEmpty()) return null
        val prefix = DoubleArray(profile.size + 1)
        for (index in profile.indices) prefix[index + 1] = prefix[index] + profile[index]
        val halfWindow = 19
        val smooth = DoubleArray(profile.size) { index ->
            val start = max(0, index - halfWindow)
            val end = min(profile.size, index + halfWindow + 1)
            // Zero padding matches the development probe's moving average.
            (prefix[end] - prefix[start]) / 39.0
        }
        val sorted = smooth.sorted()
        val low = sorted[(sorted.size * .15).toInt().coerceAtMost(sorted.lastIndex)]
        val high = sorted[(sorted.size * .90).toInt().coerceAtMost(sorted.lastIndex)]
        if (high - low < 255.0) return null
        val threshold = low + fraction * (high - low)
        val search = ((smooth.size * .35).toInt()..(smooth.size * .65).toInt().coerceAtMost(smooth.lastIndex))
        val center = search.maxByOrNull { smooth[it] } ?: return null
        if (smooth[center] <= threshold) return null
        var left = center
        while (left > 0 && smooth[left] > threshold) left--
        var right = center
        while (right < smooth.lastIndex && smooth[right] > threshold) right++
        return left to right
    }

    private fun refineTextureBounds(mask: Mat, initial: IntArray): IntArray {
        val result = initial.copyOf()
        val indices = intArrayOf(0, 2, 1, 3) // left, right, top, bottom
        for (side in 0..3) {
            val index = indices[side]
            val original = result[index]
            val dimension = if (side < 2) mask.cols() else mask.rows()
            val radius = max(1, (dimension * .055).toInt())
            var bestValue = original
            var bestScore = Double.NEGATIVE_INFINITY
            for (candidate in max(1, original - radius)..min(dimension - 1, original + radius) step 2) {
                result[index] = candidate
                if (result[2] - result[0] < mask.cols() * .40 ||
                    result[3] - result[1] < mask.rows() * .35
                ) continue
                val score = sideScore(mask, result, side) - .08 * abs(candidate - original) / radius
                if (score > bestScore) {
                    bestScore = score
                    bestValue = candidate
                }
            }
            result[index] = bestValue
        }
        return result
    }

    /** A gap before the last row or column can initially look like the outer edge. */
    private fun extendWeakOuterEdges(mask: Mat, initial: IntArray): IntArray {
        val scores = sideScores(mask, initial)
        if (scores.count { it < .35 } < 2) return initial
        val result = initial.copyOf()
        val indices = intArrayOf(0, 2, 1, 3)
        for (side in 0..3) {
            if (scores[side] >= .35) continue
            val index = indices[side]
            val original = result[index]
            val dimension = if (side < 2) mask.cols() else mask.rows()
            val radius = (dimension * .22).toInt()
            val direction = if (side == 0 || side == 2) -1 else 1
            var bestValue = original
            var bestScore = sideScore(mask, result, side)
            for (distance in 2..radius step 2) {
                val candidate = original + direction * distance
                if (candidate < 1 || candidate >= dimension) break
                result[index] = candidate
                if (result[2] - result[0] > mask.cols() * .95 ||
                    result[3] - result[1] > mask.rows() * .95
                ) continue
                val score = sideScore(mask, result, side) - .12 * distance / radius
                if (score > bestScore) {
                    bestScore = score
                    bestValue = candidate
                }
            }
            result[index] = bestValue
        }
        val improved = sideScores(mask, result)
        val aspect = (result[2] - result[0]).toDouble() / (result[3] - result[1])
        return if (improved.count { it >= .35 } >= 3 && aspect in .65..1.10) result else initial
    }

    private fun sideScores(mask: Mat, box: IntArray): List<Double> = (0..3).map { sideScore(mask, box, it) }

    private fun sideScore(mask: Mat, box: IntArray, side: Int): Double {
        val (x0, y0, x1, y1) = box
        val dx = max(6, ((x1 - x0) * .06).toInt())
        val dy = max(6, ((y1 - y0) * .06).toInt())
        val inner: Rect
        val outer: Rect
        when (side) {
            0 -> {
                inner = Rect(x0, y0 + dy, dx, y1 - y0 - 2 * dy)
                outer = Rect(max(0, x0 - dx), y0 + dy, x0 - max(0, x0 - dx), inner.height)
            }
            1 -> {
                inner = Rect(x1 - dx, y0 + dy, dx, y1 - y0 - 2 * dy)
                outer = Rect(x1, y0 + dy, min(mask.cols(), x1 + dx) - x1, inner.height)
            }
            2 -> {
                inner = Rect(x0 + dx, y0, x1 - x0 - 2 * dx, dy)
                outer = Rect(x0 + dx, max(0, y0 - dy), inner.width, y0 - max(0, y0 - dy))
            }
            else -> {
                inner = Rect(x0 + dx, y1 - dy, x1 - x0 - 2 * dx, dy)
                outer = Rect(x0 + dx, y1, inner.width, min(mask.rows(), y1 + dy) - y1)
            }
        }
        return occupancy(mask, inner) - occupancy(mask, outer)
    }

    private fun occupancy(mask: Mat, region: Rect): Double {
        if (region.width <= 0 || region.height <= 0 || region.x < 0 || region.y < 0 ||
            region.x + region.width > mask.cols() || region.y + region.height > mask.rows()
        ) return 0.0
        val sub = Mat(mask, region)
        try {
            return Core.countNonZero(sub).toDouble() / (region.width * region.height)
        } finally {
            sub.release()
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
