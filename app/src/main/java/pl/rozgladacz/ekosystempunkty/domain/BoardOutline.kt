package pl.rozgladacz.ekosystempunkty.domain

import kotlin.math.abs

/** Corners follow the logical grid: top-left, top-right, bottom-right, bottom-left. */
data class BoardOutline(
    val id: String,
    val name: String,
    val corners: List<NormalizedPoint>,
    val confidence: Float = 0f,
) {
    val isValid: Boolean get() = BoardGeometry.isValid(corners)

    fun rotated(): BoardOutline = copy(corners = corners.drop(1) + corners.take(1))
}

object BoardGeometry {
    fun isValid(points: List<NormalizedPoint>): Boolean {
        if (points.size != 4 || points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) return false
        val crosses = points.indices.map { i ->
            val a = points[i]
            val b = points[(i + 1) % 4]
            val c = points[(i + 2) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }
        if (crosses.any { it <= .00001f }) return false
        val area = abs(points.indices.sumOf { i ->
            val a = points[i]
            val b = points[(i + 1) % 4]
            (a.x * b.y - b.x * a.y).toDouble()
        }) / 2
        return area >= .002
    }
}
