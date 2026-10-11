package pl.rozgladacz.ekosystempunkty.vision

import android.graphics.Bitmap

/** Ostrożna ocena przepalenia środka karty; wpływa na automatyczną akceptację. */
object GlareQuality {
    fun score(bitmap: Bitmap): Float {
        val left = bitmap.width / 5
        val top = bitmap.height / 5
        val right = bitmap.width * 4 / 5
        val bottom = bitmap.height * 4 / 5
        var sampled = 0
        var clipped = 0
        for (y in top until bottom step 4) {
            for (x in left until right step 4) {
                val color = bitmap.getPixel(x, y)
                val red = color shr 16 and 0xff
                val green = color shr 8 and 0xff
                val blue = color and 0xff
                sampled++
                // Odbicie lampy w dostarczonych zdjęciach bywa szare (230–245),
                // mimo że zakrywa ilustrację, więc próg 245 byłby zbyt wysoki.
                if (red >= 230 && green >= 230 && blue >= 230) clipped++
            }
        }
        if (sampled == 0) return 0f
        return (1f - 4f * clipped / sampled).coerceIn(0f, 1f)
    }
}
