package pl.rozgladacz.ekosystempunkty.vision

import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint

/** EXIF's eight transforms, in normalized coordinates (including mirrored photos). */
internal object ImageOrientation {
    fun upright(point: NormalizedPoint, orientation: Int): NormalizedPoint = with(point) {
        when (orientation) {
            2 -> NormalizedPoint(1f - x, y)
            3 -> NormalizedPoint(1f - x, 1f - y)
            4 -> NormalizedPoint(x, 1f - y)
            5 -> NormalizedPoint(y, x)
            6 -> NormalizedPoint(1f - y, x)
            7 -> NormalizedPoint(1f - y, 1f - x)
            8 -> NormalizedPoint(y, 1f - x)
            else -> point
        }
    }

    fun raw(point: NormalizedPoint, orientation: Int): NormalizedPoint =
        upright(point, when (orientation) { 6 -> 8; 8 -> 6; else -> orientation })
}
