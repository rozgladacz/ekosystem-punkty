package pl.rozgladacz.ekosystempunkty.vision

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint

class ImageOrientationTest {
    @Test fun allExifTransformsRoundTripIncludingMirroredCoordinates() {
        for (orientation in 1..8) for (point in listOf(NormalizedPoint(0f, 0f), NormalizedPoint(1f, 1f), NormalizedPoint(.23f, .71f))) {
            val restored = ImageOrientation.raw(ImageOrientation.upright(point, orientation), orientation)
            assertEquals(point.x, restored.x, .00001f)
            assertEquals(point.y, restored.y, .00001f)
        }
    }

    @Test fun clockwiseExifMapsRawUpperLeftToUprightUpperRight() {
        assertEquals(NormalizedPoint(1f, 0f), ImageOrientation.upright(NormalizedPoint(0f, 0f), 6))
        assertEquals(NormalizedPoint(.7f, .2f), ImageOrientation.upright(NormalizedPoint(.2f, .3f), 6))
    }
}
