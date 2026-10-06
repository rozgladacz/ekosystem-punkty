package pl.rozgladacz.ekosystempunkty.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EcosystemColors = lightColorScheme(
    primary = Color(0xFF176B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2B8),
    onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF4F6353),
    background = Color(0xFFF6FBF3),
    surface = Color(0xFFF6FBF3),
    surfaceVariant = Color(0xFFDDE5DA),
    error = Color(0xFFBA1A1A),
)

@Composable
fun EcosystemTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EcosystemColors, content = content)
}

