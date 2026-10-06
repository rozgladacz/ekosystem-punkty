package pl.rozgladacz.ekosystempunkty

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun setupStartsTwoPlayerSession() {
        rule.onNodeWithText("Nowa partia").assertIsDisplayed()
        rule.onNodeWithText("Rozpocznij i zrób zdjęcia").performClick()
        rule.onNodeWithText("Układy graczy").assertIsDisplayed()
        rule.onNodeWithText("Gracz 1").assertIsDisplayed()
        rule.onNodeWithText("Gracz 2").assertIsDisplayed()
    }

    @Test
    fun updatesAreExplicitlyManual() {
        rule.onNodeWithText("Ustawienia").performClick()
        rule.onNodeWithText("Aplikacja nigdy nie sprawdza aktualizacji automatycznie.").assertIsDisplayed()
        rule.onNodeWithText("Sprawdź aktualizacje").assertIsDisplayed()
    }
}
