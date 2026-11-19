package pl.rozgladacz.ekosystempunkty.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BoardProjectionTest {
    @Test fun projectsGridThroughPerspectiveInsteadOfInterpolatingEdgeDistances() {
        val projection = BoardProjection(listOf(NormalizedPoint(0f, 0f), NormalizedPoint(1f, 0f),
            NormalizedPoint(.75f, 1f), NormalizedPoint(.25f, 1f)))
        assertEquals(.5f, projection.point(.5f, .5f).x, .00001f)
        assertEquals(2f / 3, projection.point(.5f, .5f).y, .00001f)
        assertEquals(1f / 6, projection.point(0f, .5f).x, .00001f)
    }

    @Test fun allLogicalCornersRemainExactAfterRotatingTheGrid() {
        val original = listOf(NormalizedPoint(.1f, .2f), NormalizedPoint(.8f, .1f), NormalizedPoint(.7f, .9f), NormalizedPoint(.2f, .7f))
        for (turn in 0..3) {
            val corners = original.drop(turn) + original.take(turn)
            val projection = BoardProjection(corners)
            val positions = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
            positions.forEachIndexed { index, (u, v) ->
                assertEquals(corners[index].x, projection.point(u, v).x, .00001f)
                assertEquals(corners[index].y, projection.point(u, v).y, .00001f)
            }
        }
    }
}
