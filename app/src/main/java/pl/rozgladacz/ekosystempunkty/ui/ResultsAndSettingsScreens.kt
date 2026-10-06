package pl.rozgladacz.ekosystempunkty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.rozgladacz.ekosystempunkty.BuildConfig
import pl.rozgladacz.ekosystempunkty.UpdateUiState
import pl.rozgladacz.ekosystempunkty.domain.GameScore
import pl.rozgladacz.ekosystempunkty.update.UpdateInfo
import java.io.File

@Composable
fun ResultsScreen(
    score: GameScore,
    onEdit: (Int) -> Unit,
    onPlayers: () -> Unit,
    onNewGame: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tabela punktów", style = MaterialTheme.typography.headlineMedium)
        val winners = score.players.filter { it.playerId in score.winningPlayerIds }.joinToString { it.playerName }
        Text(
            if (score.winningPlayerIds.size == 1) "Wygrywa: $winners" else "Remis: $winners",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().horizontalScroll(scroll).verticalScroll(rememberScrollState()),
        ) {
            Row {
                TableCell("Kategoria", 170.dp, true)
                score.players.forEachIndexed { index, player ->
                    TableCell(player.playerName, 100.dp, player.playerId in score.winningPlayerIds) { onEdit(index) }
                }
            }
            score.players.firstOrNull()?.categories?.indices?.forEach { categoryIndex ->
                Row {
                    TableCell(score.players.first().categories[categoryIndex].category.polishName, 170.dp, false)
                    score.players.forEach { player -> TableCell(player.categories[categoryIndex].points.toString(), 100.dp, false) }
                }
            }
            Row {
                TableCell("SUMA", 170.dp, true)
                score.players.forEach { player -> TableCell(player.total.toString(), 100.dp, true) }
            }
        }
        OutlinedButton(onClick = onPlayers, modifier = Modifier.fillMaxWidth()) { Text("Układy graczy") }
        Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) { Text("Nowa partia") }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    emphasized: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val modifier = Modifier.width(width).height(48.dp).padding(1.dp).background(
        if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (onClick == null) {
            Text(text, textAlign = TextAlign.Center, fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal)
        } else {
            androidx.compose.material3.TextButton(onClick = onClick) {
                Text(text, textAlign = TextAlign.Center, fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun SettingsScreen(
    updateState: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Ustawienia", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Prywatność", style = MaterialTheme.typography.titleMedium)
                Text("Zdjęcia są analizowane wyłącznie na urządzeniu i usuwane po zatwierdzeniu. Brak kont, chmury i telemetrii.")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Aktualizacje", style = MaterialTheme.typography.titleMedium)
                Text("Zainstalowana wersja: ${BuildConfig.VERSION_NAME}")
                Text("Aplikacja nigdy nie sprawdza aktualizacji automatycznie.")
                when (updateState) {
                    UpdateUiState.Idle -> Text("Naciśnij przycisk, aby połączyć się z publicznym repozytorium GitHub.")
                    UpdateUiState.Checking -> Text("Sprawdzanie…")
                    UpdateUiState.Current -> Text("Masz najnowszą wersję.", color = MaterialTheme.colorScheme.primary)
                    is UpdateUiState.Available -> {
                        Text("Dostępna wersja ${updateState.info.versionName}")
                        if (updateState.info.notes.isNotBlank()) Text(updateState.info.notes)
                        Button(onClick = { onDownload(updateState.info) }) { Text("Pobierz aktualizację") }
                    }
                    is UpdateUiState.Downloading -> Text("Pobieranie wersji ${updateState.info.versionName}…")
                    is UpdateUiState.Ready -> {
                        Text("Wersja ${updateState.versionName} jest gotowa do instalacji.")
                        Button(onClick = { onInstall(updateState.file) }) { Text("Zainstaluj") }
                    }
                    is UpdateUiState.Error -> Text(updateState.message, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = onCheck,
                    enabled = updateState !is UpdateUiState.Checking && updateState !is UpdateUiState.Downloading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sprawdź aktualizacje") }
            }
        }
        Text("Nieoficjalna aplikacja pomocnicza. Nazwa i materiały gry należą do ich właścicieli.")
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Wróć") }
    }
}
