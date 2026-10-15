package pl.rozgladacz.ekosystempunkty.data

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.rozgladacz.ekosystempunkty.domain.BoardGrid
import pl.rozgladacz.ekosystempunkty.domain.CardType
import pl.rozgladacz.ekosystempunkty.domain.PlayerBoard

class SessionStoreTest {
    @Test
    fun restoresModelScoresForLaterCorrections() = runBlocking {
        val store = SessionStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val scores = List(BoardGrid.CELL_COUNT) {
            List(CardType.entries.size) { type -> if (type == CardType.FOX.ordinal) .8f else .02f }
        }
        try {
            store.save(listOf(PlayerBoard("test", "Gracz", BoardGrid.empty(), cardProbabilities = scores)))
            assertEquals(scores, store.load()?.single()?.cardProbabilities)
        } finally {
            store.clear()
        }
    }
}
