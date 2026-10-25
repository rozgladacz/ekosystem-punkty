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
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.rozgladacz.ekosystempunkty.ReviewState
import pl.rozgladacz.ekosystempunkty.MainViewModel
import pl.rozgladacz.ekosystempunkty.domain.BoardGrid
import pl.rozgladacz.ekosystempunkty.domain.CardType
import pl.rozgladacz.ekosystempunkty.domain.CardSuggestions
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint
import pl.rozgladacz.ekosystempunkty.vision.BitmapLoader
import pl.rozgladacz.ekosystempunkty.vision.CropProposal
import kotlin.math.roundToInt

@Composable
fun CropScreen(
    proposal: CropProposal,
    additionalShot: Boolean,
    onConfirm: (CropProposal) -> Unit,
    onRetake: () -> Unit,
) {
    val bitmap = rememberPreviewBitmap(proposal.imageUri.toString())
    var corners by remember(proposal) { mutableStateOf(proposal.corners) }
    var activeCorner by remember(proposal) { mutableIntStateOf(-1) }
    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ustaw narożniki układu", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text(
            if (additionalShot) "Ustaw te same 20 pól w tej samej kolejności co na poprzednim zdjęciu."
            else "Przeciągnij punkty, aby obejmowały dokładnie 20 kart. Linie powinny biec między kartami.",
            color = Color.White,
        )
        if (proposal.confidence < .6f) {
            Text(
                "Wykrycie jest przybliżone. Sprawdź wszystkie linie siatki przed rozpoznaniem.",
                color = Color(0xFFFFD54F),
            )
        }
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
                    Modifier.fillMaxSize().testTag("crop-grid").pointerInput(proposal) {
                        var active = -1
                        var draggedCorners = corners
                        detectDragGestures(
                            onDragStart = { position ->
                                val hitRadius = 48.dp.toPx()
                                val nearest = corners.indices.minByOrNull { index ->
                                    val dx = corners[index].x * size.width - position.x
                                    val dy = corners[index].y * size.height - position.y
                                    dx * dx + dy * dy
                                }
                                active = nearest?.takeIf { index ->
                                    val dx = corners[index].x * size.width - position.x
                                    val dy = corners[index].y * size.height - position.y
                                    dx * dx + dy * dy <= hitRadius * hitRadius
                                } ?: -1
                                draggedCorners = corners
                                activeCorner = active
                            },
                            onDragEnd = { active = -1; activeCorner = -1 },
                            onDragCancel = { active = -1; activeCorner = -1 },
                            onDrag = { change, drag ->
                                if (active >= 0) {
                                    change.consume()
                                    draggedCorners = draggedCorners.toMutableList().also { list ->
                                        val previous = list[active]
                                        list[active] = NormalizedPoint(
                                            (previous.x + drag.x / size.width).coerceIn(0f, 1f),
                                            (previous.y + drag.y / size.height).coerceIn(0f, 1f),
                                        )
                                    }
                                    corners = draggedCorners
                                }
                            },
                        )
                    },
                ) {
                    val points = corners.map { Offset(it.x * size.width, it.y * size.height) }
                    fun between(a: Offset, b: Offset, fraction: Float) =
                        Offset(a.x + (b.x - a.x) * fraction, a.y + (b.y - a.y) * fraction)
                    for (column in 1..4) {
                        val fraction = column / 5f
                        drawLine(
                            Color(0xFFFFD54F),
                            between(points[0], points[1], fraction),
                            between(points[3], points[2], fraction),
                            3f,
                        )
                    }
                    for (row in 1..3) {
                        val fraction = row / 4f
                        drawLine(
                            Color(0xFFFFD54F),
                            between(points[0], points[3], fraction),
                            between(points[1], points[2], fraction),
                            3f,
                        )
                    }
                    points.indices.forEach { index ->
                        drawLine(Color(0xFF9CFFB5), points[index], points[(index + 1) % points.size], 5f)
                        val selected = index == activeCorner
                        drawCircle(if (selected) Color(0xFFFFD54F) else Color.White, if (selected) 26.dp.toPx() else 19.dp.toPx(), points[index])
                        drawCircle(Color(0xFF176B3A), if (selected) 21.dp.toPx() else 15.dp.toPx(), points[index], style = Stroke(3.dp.toPx()))
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
    onAddPhoto: () -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val missing = review.cells.count { it.type == null }
    val toReview = review.cells.count { it.needsReview }
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Sprawdź: $playerName", style = MaterialTheme.typography.headlineSmall)
        Text(
            when {
                missing > 0 -> "Uzupełnij $missing nierozpoznanych pól. Dotknij każdego pustego pola."
                toReview > 0 -> "Do sprawdzenia: $toReview pól zaznaczonych na czerwono. Najlepsze propozycje są już wpisane."
                else -> "Dotknij pola, aby poprawić rozpoznaną kartę."
            },
        )
        Text("Model jest prototypowy. Sprawdź wszystkie karty przed zatwierdzeniem.", style = MaterialTheme.typography.bodySmall)
        if (review.imageUris.isNotEmpty() && review.cells.all { it.probabilities.isEmpty() }) {
            Text("Model nie jest dostępny na tym urządzeniu. Uzupełnij karty ręcznie.", color = MaterialTheme.colorScheme.error)
        }
        if (review.imageUris.isNotEmpty()) {
            Text(
                "Zdjęcia tego układu: ${review.imageUris.size}. Sprzeczne lub prześwietlone pola " +
                    "pozostają do ręcznej poprawki.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
                val uncertain = cell.needsReview
                OutlinedButton(
                    onClick = { selectedIndex = index },
                    modifier = Modifier.height(72.dp).semantics {
                        stateDescription = if (uncertain) "Do sprawdzenia" else "Rozpoznana"
                    }.border(
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
        if (review.imageUris.isNotEmpty() && review.imageUris.size < MainViewModel.MAX_PHOTOS_PER_BOARD) {
            OutlinedButton(onClick = onAddPhoto, modifier = Modifier.fillMaxWidth()) {
                Text("Dodaj zdjęcie tego samego układu")
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
        val selectedCell = review.cells[selectedIndex]
        AlertDialog(
            onDismissRequest = { selectedIndex = -1 },
            title = { Text("Wybierz kartę") },
            text = {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    if (selectedCell.probabilities.isNotEmpty()) {
                        item { Text("Kolejność według modelu; wynik nie gwarantuje poprawności.") }
                    }
                    items(CardSuggestions.orderedTypes(selectedCell)) { type ->
                        val score = selectedCell.probabilities.getOrNull(type.ordinal)
                        val label = buildString {
                            append(type.polishName)
                            if (score != null) append(" · ${(score * 100).roundToInt()}%")
                            if (type == selectedCell.type) append(" · wybrana")
                        }
                        TextButton(
                            onClick = {
                                onCardChanged(selectedIndex, type)
                                selectedIndex = -1
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedIndex = -1 }) { Text("Anuluj") } },
        )
    }
}

@Composable
internal fun rememberPreviewBitmap(uri: String): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapLoader.load(context, android.net.Uri.parse(uri), maximumSide = 1400) }.getOrNull()
        }
    }
    // Compose may still draw the previous frame after this composable leaves the
    // composition. Let GC reclaim the bitmap when no Image retains it.
    return bitmap
}

