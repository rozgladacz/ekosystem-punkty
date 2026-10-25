package pl.rozgladacz.ekosystempunkty.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.rozgladacz.ekosystempunkty.ReviewState
import pl.rozgladacz.ekosystempunkty.domain.BoardGrid
import pl.rozgladacz.ekosystempunkty.domain.CardType
import pl.rozgladacz.ekosystempunkty.domain.RecognitionCell
import java.util.concurrent.atomic.AtomicBoolean

class ReviewSuggestionsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun uncertainSuggestionsAreFilledMarkedAndCanBeConfirmed() {
        val cells = MutableList(BoardGrid.CELL_COUNT) { RecognitionCell(CardType.RABBIT, .99f) }
        cells[0] = RecognitionCell(CardType.STREAM, .6f)
        cells[1] = RecognitionCell(CardType.WOLF, .96f, reviewRequired = true)
        val confirmed = AtomicBoolean(false)
        rule.setContent {
            ReviewScreen("Gracz", ReviewState(0, cells), { _, _ -> },
                onConfirm = { confirmed.set(true) }, onAddPhoto = {}, onRetake = {}, onCancel = {})
        }
        rule.onNodeWithText("PO").assertIsDisplayed()
        rule.onNodeWithText("WI").assertIsDisplayed()
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Do sprawdzenia"))
            .assertCountEquals(2)
        rule.onNodeWithText("Zatwierdź").assertIsEnabled().performClick()
        assertTrue(confirmed.get())
    }

    @Test
    fun missingModelOutputStillNeedsManualAssignment() {
        val cells = MutableList(BoardGrid.CELL_COUNT) { RecognitionCell(CardType.RABBIT, .99f) }
        cells[0] = RecognitionCell(null, 0f)
        rule.setContent {
            ReviewScreen("Gracz", ReviewState(0, cells), { _, _ -> },
                onConfirm = {}, onAddPhoto = {}, onRetake = {}, onCancel = {})
        }
        rule.onNodeWithText("?").assertIsDisplayed()
        rule.onNodeWithText("Zatwierdź").assertIsNotEnabled()
    }
}
