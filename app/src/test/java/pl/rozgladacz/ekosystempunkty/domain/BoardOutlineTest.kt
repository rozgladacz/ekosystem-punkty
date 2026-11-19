package pl.rozgladacz.ekosystempunkty.domain

import org.junit.Assert.*
import org.junit.Test

class BoardOutlineTest {
    private val corners = listOf(NormalizedPoint(.1f, .2f), NormalizedPoint(.7f, .1f), NormalizedPoint(.8f, .8f), NormalizedPoint(.2f, .9f))

    @Test fun rotatingPreservesIdentityAndRestoresLogicalGridAfterFourTurns() {
        val board = BoardOutline("stable-id", "Ala", corners)
        val rotated = board.rotated()
        assertEquals(board.id, rotated.id)
        assertEquals(board.name, rotated.name)
        assertEquals(corners[1], rotated.corners[0])
        assertTrue(rotated.isValid)
        assertEquals(corners, rotated.rotated().rotated().rotated().corners)
    }

    @Test fun rejectsCrossedCollapsedAndNonFiniteOutlines() {
        assertFalse(BoardGeometry.isValid(listOf(corners[0], corners[2], corners[1], corners[3])))
        assertFalse(BoardGeometry.isValid(List(4) { corners[0] }))
        assertFalse(BoardGeometry.isValid(corners.toMutableList().also { it[0] = NormalizedPoint(Float.NaN, .2f) }))
        assertFalse(BoardGeometry.isValid(corners.toMutableList().also { it[0] = NormalizedPoint(-.1f, .2f) }))
        assertTrue(BoardGeometry.isValid(corners))
    }
}
