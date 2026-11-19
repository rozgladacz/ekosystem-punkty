package pl.rozgladacz.ekosystempunkty.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import pl.rozgladacz.ekosystempunkty.domain.BoardGeometry
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Multiple independent proposals. In particular, an empty table never becomes a board. */
internal object MultiBoardLocator {
    private data class Box(val x: Double, val y: Double, val w: Double, val h: Double, val angle: Double) {
        fun point(u: Double, v: Double): Point {
            val a = Math.toRadians(angle)
            return Point(x + u * w * cos(a) - v * h * sin(a), y + u * w * sin(a) + v * h * cos(a))
        }
        fun corners() = listOf(point(-.5, -.5), point(.5, -.5), point(.5, .5), point(-.5, .5))
    }
    private data class Seed(val box: Box, val threshold: Double)
    private data class Fit(val box: Box, val score: Double)

    fun locate(bitmap: Bitmap, checkCancellation: () -> Unit = {}, diagnostics: ((String) -> Unit)? = null): List<BoardLocation> {
        val source = Mat()
        val small = Mat()
        val gray = Mat()
        val floatGray = Mat()
        val mean = Mat()
        val square = Mat()
        val variance = Mat()
        val dx = Mat()
        val dy = Mat()
        try {
            Utils.bitmapToMat(bitmap, source)
            val scale = min(1.0, 1600.0 / max(bitmap.width, bitmap.height))
            Imgproc.resize(source, small, Size(max(1, (bitmap.width * scale).roundToInt()).toDouble(), max(1, (bitmap.height * scale).roundToInt()).toDouble()))
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            gray.convertTo(floatGray, CvType.CV_32F)
            val window = max(7, (max(gray.cols(), gray.rows()) / 64) or 1)
            Imgproc.boxFilter(floatGray, mean, CvType.CV_32F, Size(window.toDouble(), window.toDouble()))
            Core.multiply(floatGray, floatGray, square)
            Imgproc.boxFilter(square, variance, CvType.CV_32F, Size(window.toDouble(), window.toDouble()))
            Core.multiply(mean, mean, square)
            Core.subtract(variance, square, variance)
            Imgproc.Sobel(gray, dx, CvType.CV_32F, 1, 0, 3, .125)
            Imgproc.Sobel(gray, dy, CvType.CV_32F, 0, 1, 3, .125)
            val evidence = Evidence(variance, mean, dx, dy)
            val seeds = seeds(variance, checkCancellation).sortedWith(compareByDescending<Seed> { it.threshold }.thenByDescending { it.box.w * it.box.h })
            val cards = cardHints(gray, checkCancellation)
            // Prefer separate high-contrast cores. A lower-threshold component
            // can connect neighbours through shadows and must not merge them.
            val anchors = mutableListOf<Seed>()
            for (seed in seeds) {
                checkCancellation()
                if (anchors.none { hypot(it.box.x - seed.box.x, it.box.y - seed.box.y) < .45 * max(min(it.box.w, it.box.h), min(seed.box.w, seed.box.h)) }) {
                    anchors += seed
                }
            }
            val allFits = anchors.map { seed -> checkCancellation(); refine(seed.box, evidence, cards, checkCancellation) }
            val fits = allFits.filter { it.score >= .30 }
                .sortedByDescending { it.score }
            diagnostics?.invoke("seeds=$seeds\ncards=$cards\nanchors=$anchors\nfits=$allFits")
            val kept = mutableListOf<Fit>()
            for (fit in fits) {
                if (kept.none { overlap(fit.box, it.box) > .5 }) kept += fit
            }
            return kept.mapNotNull { fit ->
                val corners = refinePerspective(fit.box, evidence, checkCancellation).map { NormalizedPoint((it.x / gray.cols()).toFloat(), (it.y / gray.rows()).toFloat()) }
                if (!BoardGeometry.isValid(corners)) null else BoardLocation(corners, fit.score.toFloat().coerceIn(0f, .75f))
            }.sortedWith(compareBy<BoardLocation> { it.corners.map { p -> p.y }.average() }.thenBy { it.corners.map { p -> p.x }.average() })
        } finally {
            source.release(); small.release(); gray.release(); floatGray.release(); mean.release()
            square.release(); variance.release(); dx.release(); dy.release()
        }
    }

    private fun seeds(variance: Mat, checkCancellation: () -> Unit): List<Seed> {
        val mask = Mat()
        val hierarchy = Mat()
        val result = mutableListOf<Seed>()
        val imageArea = variance.rows().toDouble() * variance.cols()
        val unit = max(variance.rows(), variance.cols()) / 1600.0
        try {
            for (threshold in listOf(160.0, 230.0, 320.0, 400.0, 500.0, 650.0, 850.0, 1000.0)) {
                for (kernelSize in listOf(11, 21, 35)) {
                    checkCancellation()
                    Imgproc.threshold(variance, mask, threshold, 255.0, Imgproc.THRESH_BINARY)
                    mask.convertTo(mask, CvType.CV_8U)
                    val close = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(max(3.0, kernelSize * unit), max(3.0, kernelSize * unit)))
                    val open = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(max(3.0, 9 * unit), max(3.0, 9 * unit)))
                    val contours = mutableListOf<MatOfPoint>()
                    try {
                        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, close)
                        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, open)
                        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                        for (contour in contours) {
                            val area = Imgproc.contourArea(contour)
                            if (area < imageArea * .01) continue
                            val points = MatOfPoint2f(*contour.toArray())
                            val rect = try { Imgproc.minAreaRect(points) } finally { points.release() }
                            val w = rect.size.width
                            val h = rect.size.height
                            if (min(w, h) < 35 * unit || min(w, h) / max(w, h) < .55 || area / (w * h) < .22) continue
                            result += Seed(Box(rect.center.x, rect.center.y, w, h, rect.angle), threshold)
                        }
                    } finally { contours.forEach { it.release() }; close.release(); open.release() }
                }
            }
            return result
        } finally { mask.release(); hierarchy.release() }
    }

    private fun cardHints(gray: Mat, checkCancellation: () -> Unit): List<Box> {
        val blurred = Mat()
        val enhanced = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val result = mutableListOf<Box>()
        val contrast = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        try {
            Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)
            contrast.apply(blurred, enhanced)
            for (input in listOf(blurred, enhanced)) for ((low, high) in listOf(30.0 to 90.0, 40.0 to 120.0, 60.0 to 160.0)) {
                checkCancellation()
                val contours = mutableListOf<MatOfPoint>()
                try {
                    Imgproc.Canny(input, edges, low, high)
                    Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
                    Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
                    for (contour in contours.sortedByDescending { Imgproc.contourArea(it) }) {
                        val area = Imgproc.contourArea(contour)
                        if (area / (gray.rows().toDouble() * gray.cols()) !in .001.. .025) continue
                        val points = MatOfPoint2f(*contour.toArray())
                        val rect = try { Imgproc.minAreaRect(points) } finally { points.release() }
                        val w = rect.size.width; val h = rect.size.height
                        if (min(w, h) < 20 || max(w, h) > max(gray.cols(), gray.rows()) * .18 || min(w, h) / max(w, h) !in .52.. .84 || area / (w * h) < .65) continue
                        val card = Box(rect.center.x, rect.center.y, w, h, rect.angle)
                        result += card
                    }
                } finally { contours.forEach { it.release() } }
            }
            val kept = mutableListOf<Box>()
            for (card in result.sortedByDescending { it.w * it.h }) {
                if (kept.none { hypot(card.x - it.x, card.y - it.y) < min(it.w, it.h) * .35 }) kept += card
            }
            return kept
        } finally { blurred.release(); enhanced.release(); edges.release(); hierarchy.release(); kernel.release(); contrast.collectGarbage() }
    }

    private fun refine(seed: Box, evidence: Evidence, cards: List<Box>, checkCancellation: () -> Unit): Fit {
        val hint = cards.filter { card ->
            val turns = ((seed.angle - card.angle) / 90).roundToInt()
            abs(seed.angle - card.angle - turns * 90) < 40 && hypot(seed.x - card.x, seed.y - card.y) < max(seed.w, seed.h) * .85
        }.maxByOrNull { it.w * it.h }?.let { card ->
            val turns = ((seed.angle - card.angle) / 90).roundToInt()
            if (abs(turns) % 2 == 1) card.copy(w = card.h, h = card.w, angle = card.angle + turns * 90) else card.copy(angle = card.angle + turns * 90)
        }
        val columns = if (hint != null && hint.w > hint.h) 4 else 5
        val rows = 9 - columns
        val expectedW = hint?.w?.times(columns * 1.05)
        val expectedH = hint?.h?.times(rows * 1.05)
        fun score(box: Box): Double = evidence.score(box) - if (expectedW != null && expectedH != null) {
            .25 * (abs(ln(box.w / expectedW)) + abs(ln(box.h / expectedH)))
        } else 0.0
        var best = Fit(seed, score(seed))
        // Even a single clear card supplies a scale and 20 possible grid phases.
        // This prevents a bright subset from becoming a smaller false board
        // when shadows or glare hide the outer rows.
        if (hint != null && expectedW != null && expectedH != null) {
            for (r in 0 until rows) for (c in 0 until columns) {
                val center = hint.copy(w = expectedW, h = expectedH).point(.5 - (c + .5) / columns, .5 - (r + .5) / rows)
                val candidate = Box(center.x, center.y, expectedW, expectedH, hint.angle)
                if (hypot(center.x - seed.x, center.y - seed.y) > max(seed.w, seed.h) * .7) continue
                val value = score(candidate)
                if (value > best.score) best = Fit(candidate, value)
            }
        }
        // A core can omit pale outer cards; search beyond it before the local fit.
        for (factor in listOf(1.0, 1.25, 1.55, 1.85)) {
            checkCancellation()
            for (shiftX in listOf(-.12, 0.0, .12)) for (shiftY in listOf(-.12, 0.0, .12)) {
                val center = seed.point(shiftX, shiftY)
                for (angle in listOf(seed.angle - 10, seed.angle, seed.angle + 10)) {
                    val candidate = seed.copy(x = center.x, y = center.y, w = seed.w * factor, h = seed.h * factor, angle = angle)
                    val value = score(candidate)
                    if (value > best.score) best = Fit(candidate, value)
                }
            }
        }
        for (step in listOf(.10, .05, .025)) {
            repeat(3) {
                val box = best.box
                val variants = listOf(
                    box.copy(x = box.x - box.w * step), box.copy(x = box.x + box.w * step),
                    box.copy(y = box.y - box.h * step), box.copy(y = box.y + box.h * step),
                    box.copy(w = box.w * (1 - step)), box.copy(w = box.w * (1 + step)),
                    box.copy(h = box.h * (1 - step)), box.copy(h = box.h * (1 + step)),
                    box.copy(angle = box.angle - step * 30), box.copy(angle = box.angle + step * 30),
                )
                for (candidate in variants) {
                    val value = score(candidate)
                    if (value > best.score) best = Fit(candidate, value)
                }
            }
        }
        return best
    }

    private fun refinePerspective(box: Box, evidence: Evidence, checkCancellation: () -> Unit): List<Point> {
        val initial = box.corners().map(evidence::clamp)
        var points = initial
        fun score(candidate: List<Point>): Double = evidence.scoreQuad(candidate, box) -
            .08 * candidate.indices.sumOf { hypot(candidate[it].x - initial[it].x, candidate[it].y - initial[it].y) } / hypot(box.w, box.h)
        var best = score(points)
        for (step in listOf(.035, .018, .009)) repeat(2) {
            checkCancellation()
            for (index in 0..3) {
                for ((dx, dy) in listOf(-box.w * step to 0.0, box.w * step to 0.0, 0.0 to -box.h * step, 0.0 to box.h * step)) {
                    val candidate = points.toMutableList()
                    candidate[index] = evidence.clamp(Point(points[index].x + dx, points[index].y + dy))
                    val value = score(candidate)
                    if (value > best) { points = candidate; best = value }
                }
            }
        }
        return points
    }

    /** Unit-square homography, used to score straight grid lines in perspective. */
    private class ProjectiveGrid(p: List<Point>) {
        private val dx1 = p[1].x - p[2].x
        private val dx2 = p[3].x - p[2].x
        private val dy1 = p[1].y - p[2].y
        private val dy2 = p[3].y - p[2].y
        private val dx3 = p[0].x - p[1].x + p[2].x - p[3].x
        private val dy3 = p[0].y - p[1].y + p[2].y - p[3].y
        private val determinant = dx1 * dy2 - dx2 * dy1
        private val g = if (abs(determinant) < 1e-8) 0.0 else (dx3 * dy2 - dx2 * dy3) / determinant
        private val h = if (abs(determinant) < 1e-8) 0.0 else (dx1 * dy3 - dx3 * dy1) / determinant
        private val a = p[1].x - p[0].x + g * p[1].x
        private val b = p[3].x - p[0].x + h * p[3].x
        private val c = p[0].x
        private val d = p[1].y - p[0].y + g * p[1].y
        private val e = p[3].y - p[0].y + h * p[3].y
        private val f = p[0].y
        fun point(x: Double, y: Double): Point {
            val u = x + .5; val v = y + .5
            val denominator = g * u + h * v + 1
            return Point((a * u + b * v + c) / denominator, (d * u + e * v + f) / denominator)
        }
    }

    private class Evidence(variance: Mat, mean: Mat, dx: Mat, dy: Mat) {
        private val width = variance.cols()
        private val height = variance.rows()
        private val texture = FloatArray(width * height).also { variance.get(0, 0, it) }
        private val luminance = FloatArray(width * height).also { mean.get(0, 0, it) }
        private val gx = FloatArray(width * height).also { dx.get(0, 0, it) }
        private val gy = FloatArray(width * height).also { dy.get(0, 0, it) }
        private fun at(values: FloatArray, p: Point): Double =
            if (!p.x.isFinite() || !p.y.isFinite() || p.x < 0 || p.y < 0 || p.x >= width || p.y >= height) 0.0 else values[p.y.toInt() * width + p.x.toInt()].toDouble()
        private fun textured(p: Point): Double {
            val value = max(0.0, at(texture, p))
            // Relative contrast keeps real, shaded cards competitive with a
            // bright subset. A noise floor prevents boosting near-black noise.
            val light = (at(luminance, p) + 25) / 175
            return value / (value + max(45.0, 230 * light * light))
        }

        fun clamp(p: Point) = Point(p.x.coerceIn(0.0, width - 1.0), p.y.coerceIn(0.0, height - 1.0))

        fun scoreQuad(points: List<Point>, box: Box): Double {
            if (!BoardGeometry.isValid(points.map { NormalizedPoint((it.x / width).toFloat(), (it.y / height).toFloat()) })) return -1.0
            val grid = ProjectiveGrid(points)
            return max(gridScore(box, 5, 4, grid::point), gridScore(box, 4, 5, grid::point))
        }

        fun score(box: Box): Double {
            if (box.corners().any { it.x < -width * .035 || it.y < -height * .035 || it.x >= width * 1.035 || it.y >= height * 1.035 } ||
                min(box.w, box.h) / max(box.w, box.h) < .60) return -1.0
            // Both assignments of the physical axes are checked; logical reading
            // direction is subsequently chosen by the classifier.
            return max(gridScore(box, 5, 4), gridScore(box, 4, 5))
        }

        private fun gridScore(box: Box, columns: Int, rows: Int, project: (Double, Double) -> Point = box::point): Double {
            val cells = DoubleArray(columns * rows) { index ->
                val c = index % columns
                val r = index / columns
                var value = 0.0
                for (u in listOf(.25, .5, .75)) for (v in listOf(.25, .5, .75)) {
                    value += textured(project((c + u) / columns - .5, (r + v) / rows - .5))
                }
                value / 9
            }
            if (cells.count { it > .20 } < 10 || (0 until rows).any { r -> (0 until columns).none { cells[r * columns + it] > .16 } } ||
                (0 until columns).any { c -> (0 until rows).none { cells[it * columns + c] > .16 } }) return -1.0
            var outside = 0.0
            for (i in 0 until 12) {
                val t = (i + .5) / 12 - .5
                outside += textured(project(t, -.56)) + textured(project(t, .56)) +
                    textured(project(-.56, t)) + textured(project(.56, t))
            }
            outside /= 48
            val a = Math.toRadians(box.angle)
            var edges = 0.0
            var count = 0
            for (c in 0..columns) for (j in 0 until 16) {
                var strongest = 0.0
                for (offset in listOf(-.03, 0.0, .03)) {
                    val point = project(c.toDouble() / columns - .5 + offset / columns, (j + .5) / 16 - .5)
                    strongest = max(strongest, abs(at(gx, point) * cos(a) + at(gy, point) * sin(a)))
                }
                edges += strongest / (strongest + 12); count++
            }
            for (r in 0..rows) for (j in 0 until 16) {
                var strongest = 0.0
                for (offset in listOf(-.03, 0.0, .03)) {
                    val point = project((j + .5) / 16 - .5, r.toDouble() / rows - .5 + offset / rows)
                    strongest = max(strongest, abs(-at(gx, point) * sin(a) + at(gy, point) * cos(a)))
                }
                edges += strongest / (strongest + 12); count++
            }
            return cells.average() * .65 + edges / count * .45 - outside * .40
        }
    }

    private fun overlap(a: Box, b: Box): Double {
        val first = MatOfPoint2f(*a.corners().toTypedArray())
        val second = MatOfPoint2f(*b.corners().toTypedArray())
        val intersection = Mat()
        return try { Imgproc.intersectConvexConvex(first, second, intersection).toDouble() / min(a.w * a.h, b.w * b.h) }
        finally { first.release(); second.release(); intersection.release() }
    }
}
