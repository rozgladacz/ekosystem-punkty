package pl.rozgladacz.ekosystempunkty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.rozgladacz.ekosystempunkty.domain.PlayerBoard

@Composable
fun SetupScreen(onStart: (List<String>) -> Unit) {
    var playerCount by rememberSaveable { mutableStateOf(2) }
    var names by rememberSaveable { mutableStateOf(List(6) { "" }) }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Nowa partia", style = MaterialTheme.typography.headlineMedium)
        Text("Wybierz liczbę graczy. Nazwy są opcjonalne i pozostają wyłącznie na tym urządzeniu.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = { if (playerCount > 2) playerCount-- }, enabled = playerCount > 2) { Text("−") }
            Text("$playerCount graczy", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { if (playerCount < 6) playerCount++ }, enabled = playerCount < 6) { Text("+") }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(playerCount) { index ->
                OutlinedTextField(
                    value = names[index],
                    onValueChange = { value -> names = names.toMutableList().also { it[index] = value } },
                    label = { Text("Gracz ${index + 1}") },
                    placeholder = { Text("Nazwa opcjonalna") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Button(
            onClick = { onStart(names.take(playerCount)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Rozpocznij i zrób zdjęcia") }
    }
}

@Composable
fun PlayersScreen(
    players: List<PlayerBoard>,
    onCapture: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onResults: () -> Unit,
    onNewGame: () -> Unit,
) {
    val complete = players.all { it.grid.isComplete }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Układy graczy", style = MaterialTheme.typography.headlineMedium)
        Text("Zdjęcie nie opuszcza telefonu i zostanie usunięte po zatwierdzeniu kart.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(players, key = { _, player -> player.id }) { index, player ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(player.name, style = MaterialTheme.typography.titleMedium)
                            Text(if (player.grid.isComplete) "20 kart zatwierdzonych" else "Oczekuje na zdjęcie")
                        }
                        Button(onClick = { if (player.grid.isComplete) onEdit(index) else onCapture(index) }) {
                            Text(if (player.grid.isComplete) "Popraw" else "Skanuj")
                        }
                    }
                }
            }
        }
        if (complete) Button(onClick = onResults, modifier = Modifier.fillMaxWidth()) { Text("Pokaż wyniki") }
        OutlinedButton(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) { Text("Rozpocznij nową partię") }
    }
}
