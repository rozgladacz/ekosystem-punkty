package pl.rozgladacz.ekosystempunkty.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.rozgladacz.ekosystempunkty.ReviewState
import pl.rozgladacz.ekosystempunkty.domain.BoardGrid
import pl.rozgladacz.ekosystempunkty.domain.CardType
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint
import pl.rozgladacz.ekosystempunkty.vision.BitmapLoader
import pl.rozgladacz.ekosystempunkty.vision.CropProposal
import kotlin.math.pow

@Composable
fun CropScreen(
    proposal: CropProposal,
    onConfirm: (CropProposal) -> Unit,
    onRetake: () -> Unit,
) {
    val bitmap = rememberPreviewBitmap(proposal.imageUri.toString())
    var corners by remember(proposal) { mutableStateOf(proposal.corners) }
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ustaw narożniki układu", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text("Przeciągnij punkty, aby obejmowały dokładnie 20 kart.", color = Color.White)
        bitmap?.let { image ->
            Box(
                Modifier.fillMaxWidth().weight(1f).aspectRatio(image.width.toFloat() / image.height),
            ) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "Zdjęcie układu kart",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                Canvas(
                    Modifier.fillMaxSize().pointerInput(corners) {
                        var active = -1
                        detectDragGestures(
                            onDragStart = { position ->
                                active = corners.indices.minBy { index ->
                                    val point = Offset(corners[index].x * size.width, corners[index].y * size.height)
                                    (point.x - position.x).pow(2) + (point.y - position.y).pow(2)
                                }
                            },
                            onDragEnd = { active = -1 },
                            onDragCancel = { active = -1 },
                            onDrag = { change, drag ->
                                if (active >= 0) {
                                    change.consume()
                                    corners = corners.toMutableList().also { list ->
                                        val previous = list[active]
                                        list[active] = NormalizedPoint(
                                            (previous.x + drag.x / size.width).coerceIn(0f, 1f),
                                            (previous.y + drag.y / size.height).coerceIn(0f, 1f),
                                        )
                                    }
                                }
                            },
                        )
                    },
                ) {
                    val points = corners.map { Offset(it.x * size.width, it.y * size.height) }
                    points.indices.forEach { index ->
                        drawLine(Color(0xFF9CFFB5), points[index], points[(index + 1) % points.size], 5f)
                        drawCircle(Color.White, 22f, points[index])
                        drawCircle(Color(0xFF176B3A), 16f, points[index], style = Stroke(6f))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text("Powtórz") }
            Button(
                onClick = { onConfirm(proposal.copy(corners = corners)) },
                modifier = Modifier.weight(1f),
            ) { Text("Rozpoznaj") }
        }
    }
}

@Composable
fun ReviewScreen(
    playerName: String,
    review: ReviewState,
    onCardChanged: (Int, CardType) -> Unit,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val missing = review.cells.count { it.type == null }
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Sprawdź: $playerName", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (missing == 0) "Dotknij pola, aby poprawić rozpoznaną kartę."
            else "Uzupełnij $missing nierozpoznanych pól. Dotknij każdego czerwonego pola.",
        )
        review.imageUri?.let { uri ->
            val image = rememberPreviewBitmap(uri.toString())
            image?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Zdjęcie gracza",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(BoardGrid.COLUMNS),
            modifier = Modifier.fillMaxWidth().height(310.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(review.cells) { index, cell ->
                val uncertain = cell.type == null || cell.confidence < 0.85f
                OutlinedButton(
                    onClick = { selectedIndex = index },
                    modifier = Modifier.height(72.dp).border(
                        width = if (uncertain) 2.dp else 0.dp,
                        color = if (uncertain) MaterialTheme.colorScheme.error else Color.Transparent,
                        shape = MaterialTheme.shapes.small,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(cell.type?.shortName ?: "?", style = MaterialTheme.typography.titleMedium)
                        Text("${index / 5 + 1},${index % 5 + 1}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (review.imageUri != null) {
                OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text("Powtórz") }
            } else {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Anuluj") }
            }
            Button(onClick = onConfirm, enabled = missing == 0, modifier = Modifier.weight(1f)) {
                Text("Zatwierdź")
            }
        }
    }

    if (selectedIndex >= 0) {
        AlertDialog(
            onDismissRequest = { selectedIndex = -1 },
            title = { Text("Wybierz kartę") },
            text = {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(CardType.entries) { type ->
                        TextButton(
                            onClick = {
                                onCardChanged(selectedIndex, type)
                                selectedIndex = -1
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(type.polishName) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedIndex = -1 }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun rememberPreviewBitmap(uri: String): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapLoader.load(context, android.net.Uri.parse(uri), maximumSide = 1400) }.getOrNull()
        }
    }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    return bitmap
}

