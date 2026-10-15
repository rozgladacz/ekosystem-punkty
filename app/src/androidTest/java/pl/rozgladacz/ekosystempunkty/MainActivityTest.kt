package pl.rozgladacz.ekosystempunkty

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

    @Test
    fun developmentModeCanBeToggledWithoutChangingUpdateSettings() {
        val initiallyEnabled = rule.activity.getSharedPreferences("development_mode", 0)
            .getBoolean("enabled", false)
        rule.onNodeWithText("Ustawienia").performClick()
        val toggle = rule.onNodeWithTag("development-mode-switch")
        if (initiallyEnabled) toggle.assertIsOn() else toggle.assertIsOff()
        toggle.performClick()
        if (initiallyEnabled) toggle.assertIsOff() else toggle.assertIsOn()
        rule.onNodeWithText("Sprawdź aktualizacje").assertIsDisplayed()
        toggle.performClick()
        if (initiallyEnabled) toggle.assertIsOn() else toggle.assertIsOff()
    }
}
