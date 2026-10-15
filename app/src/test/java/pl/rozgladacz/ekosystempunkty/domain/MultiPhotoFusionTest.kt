package pl.rozgladacz.ekosystempunkty.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultiPhotoFusionTest {
    private fun shot(vararg changes: Pair<Int, RecognitionCell>): List<RecognitionCell> =
        MutableList(BoardGrid.CELL_COUNT) { RecognitionCell(null, 0f) }.also { cells ->
            changes.forEach { (index, cell) -> cells[index] = cell }
        }

    @Test
    fun complementaryPhotosFillDifferentCells() {
        val first = shot(0 to RecognitionCell(CardType.RABBIT, 0.96f))
        val second = shot(1 to RecognitionCell(CardType.FOX, 0.94f))
        val result = MultiPhotoFusion.merge(listOf(first, second))
        assertEquals(CardType.RABBIT, result[0].type)
        assertEquals(CardType.FOX, result[1].type)
        assertNull(result[2].type)
    }

    @Test
    fun conflictingConfidentPhotosRequireManualReview() {
        val first = shot(3 to RecognitionCell(CardType.DEER, 0.97f))
        val second = shot(3 to RecognitionCell(CardType.EAGLE, 0.91f))
        assertNull(MultiPhotoFusion.merge(listOf(first, second))[3].type)
    }

    @Test
    fun glaredPhotoCannotOverrideClearPhoto() {
        val glare = shot(8 to RecognitionCell(CardType.BEAR, 0.99f, 0.2f))
        val clear = shot(8 to RecognitionCell(CardType.WOLF, 0.92f, 0.95f))
        assertEquals(CardType.WOLF, MultiPhotoFusion.merge(listOf(glare, clear))[8].type)
    }

    @Test
    fun onlyGlaredOrLowConfidencePhotosStayUnresolved() {
        val glare = shot(6 to RecognitionCell(CardType.BEE, 0.98f, 0.1f))
        val weak = shot(6 to RecognitionCell(CardType.BEE, 0.6f))
        assertNull(MultiPhotoFusion.merge(listOf(glare, weak))[6].type)
    }

    @Test
    fun manualCorrectionSurvivesAnotherPhoto() {
        val first = shot(5 to RecognitionCell(CardType.TROUT, 0.92f))
        val second = shot(5 to RecognitionCell(CardType.STREAM, 0.93f))
        val result = MultiPhotoFusion.merge(listOf(first, second), mapOf(5 to CardType.DRAGONFLY))
        assertEquals(CardType.DRAGONFLY, result[5].type)
    }

    @Test
    fun unresolvedCellKeepsScoresForManualRanking() {
        val scores = MutableList(CardType.entries.size) { 0f }.also {
            it[CardType.FOX.ordinal] = .55f
            it[CardType.DEER.ordinal] = .45f
        }
        val result = MultiPhotoFusion.merge(listOf(shot(4 to RecognitionCell(null, .55f, probabilities = scores))))
        assertNull(result[4].type)
        assertEquals(CardType.FOX, CardSuggestions.orderedTypes(result[4]).first())
    }

    @Test
    fun clearPhotoSuppliesRankingWhenOtherPhotoHasGlare() {
        fun scores(type: CardType): List<Float> = MutableList(CardType.entries.size) { 0f }.also { it[type.ordinal] = 1f }
        val glare = shot(8 to RecognitionCell(CardType.BEAR, .99f, .2f, scores(CardType.BEAR)))
        val clear = shot(8 to RecognitionCell(CardType.WOLF, .92f, .95f, scores(CardType.WOLF)))
        val merged = MultiPhotoFusion.merge(listOf(glare, clear))[8]
        assertEquals(CardType.WOLF, CardSuggestions.orderedTypes(merged).first())
    }
}
