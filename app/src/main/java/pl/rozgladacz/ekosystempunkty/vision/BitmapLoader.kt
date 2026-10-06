package pl.rozgladacz.ekosystempunkty.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

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
        val orientation = open(context, uri) { ExifInterface(it).rotationDegrees }
        if (orientation == 0) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply {
            postRotate(orientation.toFloat())
        }, true).also { if (it !== bitmap) bitmap.recycle() }
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

