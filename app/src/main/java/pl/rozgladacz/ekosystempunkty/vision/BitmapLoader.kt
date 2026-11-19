package pl.rozgladacz.ekosystempunkty.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import pl.rozgladacz.ekosystempunkty.domain.BoardGeometry
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint
import kotlin.math.ceil
import kotlin.math.floor

internal data class BoardRegion(val bitmap: Bitmap, val corners: List<NormalizedPoint>) : AutoCloseable {
    override fun close() = bitmap.recycle()
}

object BitmapLoader {
    fun load(context: Context, uri: Uri, maximumSide: Int = 2400): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(context, uri) { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maximumSide) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = open(context, uri) { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Nie można odczytać zdjęcia")
        return orient(bitmap, orientation(context, uri))
    }

    /** Decode only this board from the original, rather than enlarging a table thumbnail. */
    @Suppress("DEPRECATION")
    internal fun loadRegion(context: Context, uri: Uri, corners: List<NormalizedPoint>, maximumSide: Int = 1800): BoardRegion {
        require(BoardGeometry.isValid(corners)) { "Nieprawidłowe narożniki układu" }
        val orientation = orientation(context, uri)
        return open(context, uri) { stream ->
            val decoder = requireNotNull(BitmapRegionDecoder.newInstance(stream, false))
            try {
                val rawPoints = corners.map { ImageOrientation.raw(it, orientation) }
                val region = Rect(
                    floor(rawPoints.minOf { it.x }.toDouble() * decoder.width).toInt().coerceIn(0, decoder.width - 1),
                    floor(rawPoints.minOf { it.y }.toDouble() * decoder.height).toInt().coerceIn(0, decoder.height - 1),
                    ceil(rawPoints.maxOf { it.x }.toDouble() * decoder.width).toInt().coerceIn(1, decoder.width),
                    ceil(rawPoints.maxOf { it.y }.toDouble() * decoder.height).toInt().coerceIn(1, decoder.height),
                )
                var sample = 1
                while (maxOf(region.width(), region.height()) / (sample * 2) >= maximumSide) sample *= 2
                val bitmap = requireNotNull(decoder.decodeRegion(region, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })) { "Nie można odczytać fragmentu zdjęcia" }
                val localPoints = rawPoints.map { point ->
                    ImageOrientation.upright(NormalizedPoint(
                        (point.x * decoder.width - region.left) / region.width(),
                        (point.y * decoder.height - region.top) / region.height(),
                    ), orientation)
                }
                BoardRegion(orient(bitmap, orientation), localPoints)
            } finally {
                decoder.recycle()
            }
        }
    }

    private fun orientation(context: Context, uri: Uri): Int = open(context, uri) {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }

    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation !in 2..8) return bitmap
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> setValues(floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
                6 -> setRotate(90f)
                7 -> setValues(floatArrayOf(0f, -1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f))
                8 -> setRotate(270f)
            }
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } finally {
            bitmap.recycle()
        }
    }

    private fun <T> open(context: Context, uri: Uri, block: (java.io.InputStream) -> T): T {
        val stream = if (uri.scheme == "file") {
            java.io.FileInputStream(requireNotNull(uri.path))
        } else {
            requireNotNull(context.contentResolver.openInputStream(uri))
        }
        return stream.use(block)
    }
}

