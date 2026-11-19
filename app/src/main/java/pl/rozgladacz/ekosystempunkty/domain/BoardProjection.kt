package pl.rozgladacz.ekosystempunkty.domain

import kotlin.math.abs

/** Maps the logical 5×4 grid to its photographed quadrilateral. */
class BoardProjection(points: List<NormalizedPoint>) {
    init { require(BoardGeometry.isValid(points)) { "Nieprawidłowe narożniki siatki" } }
    private val p = points.map { it.x.toDouble() to it.y.toDouble() }
    private val dx1 = p[1].first - p[2].first
    private val dx2 = p[3].first - p[2].first
    private val dy1 = p[1].second - p[2].second
    private val dy2 = p[3].second - p[2].second
    private val dx3 = p[0].first - p[1].first + p[2].first - p[3].first
    private val dy3 = p[0].second - p[1].second + p[2].second - p[3].second
    private val determinant = dx1 * dy2 - dx2 * dy1
    private val g = if (abs(determinant) < 1e-10) 0.0 else (dx3 * dy2 - dx2 * dy3) / determinant
    private val h = if (abs(determinant) < 1e-10) 0.0 else (dx1 * dy3 - dx3 * dy1) / determinant
    private val a = p[1].first - p[0].first + g * p[1].first
    private val b = p[3].first - p[0].first + h * p[3].first
    private val c = p[0].first
    private val d = p[1].second - p[0].second + g * p[1].second
    private val e = p[3].second - p[0].second + h * p[3].second
    private val f = p[0].second

    fun point(u: Float, v: Float): NormalizedPoint {
        val denominator = g * u + h * v + 1
        return NormalizedPoint(((a * u + b * v + c) / denominator).toFloat(), ((d * u + e * v + f) / denominator).toFloat())
    }
}
