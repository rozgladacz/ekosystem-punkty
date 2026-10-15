package pl.rozgladacz.ekosystempunkty.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CardSuggestionsTest {
    private fun scores(vararg ranked: Pair<CardType, Float>): List<Float> =
        MutableList(CardType.entries.size) { 0f }.also { values ->
            ranked.forEach { (type, score) -> values[type.ordinal] = score }
        }

    @Test
    fun lowConfidenceCellStillRanksEveryCardByModelScore() {
        val cell = RecognitionCell(
            null, .6f,
            probabilities = scores(CardType.WOLF to .6f, CardType.FOX to .3f, CardType.BEE to .1f),
        )
        val ordered = CardSuggestions.orderedTypes(cell)
        assertEquals(CardType.entries.size, ordered.size)
        assertEquals(listOf(CardType.WOLF, CardType.FOX, CardType.BEE), ordered.take(3))
    }

    @Test
    fun manualCorrectionDoesNotEraseOriginalRanking() {
        val cell = RecognitionCell(
            CardType.FOX, 1f,
            probabilities = scores(CardType.WOLF to .7f, CardType.FOX to .2f),
        )
        assertEquals(CardType.WOLF, CardSuggestions.orderedTypes(cell).first())
    }

    @Test
    fun oldSessionWithoutScoresShowsSelectedCardFirst() {
        assertEquals(
            CardType.DEER,
            CardSuggestions.orderedTypes(RecognitionCell(CardType.DEER, 1f)).first(),
        )
    }
}
