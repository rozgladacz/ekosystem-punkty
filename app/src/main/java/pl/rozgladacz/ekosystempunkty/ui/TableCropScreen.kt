package pl.rozgladacz.ekosystempunkty.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import pl.rozgladacz.ekosystempunkty.TableCaptureState
import pl.rozgladacz.ekosystempunkty.domain.BoardOutline
import pl.rozgladacz.ekosystempunkty.domain.BoardProjection
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun TableCropScreen(
    table: TableCaptureState,
    onBoardsChanged: (List<BoardOutline>) -> Unit,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
) {
    val bitmap = rememberPreviewBitmap(table.imageUri.toString())
    val boards = table.boards
    val currentBoards by rememberUpdatedState(boards)
    val changeBoards by rememberUpdatedState(onBoardsChanged)
    val focusManager = LocalFocusManager.current
    var selectedId by rememberSaveable(table.imageUri.toString()) { mutableStateOf(boards.firstOrNull()?.id) }
    val selected = boards.firstOrNull { it.id == selectedId } ?: boards.firstOrNull()
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    fun replace(board: BoardOutline) = onBoardsChanged(boards.map { if (it.id == board.id) board else it })

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        val previewHeight = (maxHeight * .36f).coerceIn(160.dp, 420.dp)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Układy na zdjęciu", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onCancel) { Text("Anuluj") }
            }
            Text(when {
                boards.isEmpty() -> "Nie znaleziono układów. Dodaj obrysy ręcznie albo powtórz zdjęcie."
                boards.size !in 2..6 -> "Układy: ${boards.size}. Do partii potrzeba od 2 do 6 graczy."
                boards.any { !it.isValid } -> "Popraw czerwone obrysy. Narożniki nie mogą się przecinać."
                else -> "Układy: ${boards.size}. Sprawdź granice i siatki 5×4 każdego gracza."
            }, style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.height(previewHeight).fillMaxWidth().clipToBounds().background(Color.Black)) {
                bitmap?.let { image ->
                    val imageBitmap = remember(image) { image.asImageBitmap() }
                    Canvas(Modifier.fillMaxSize().testTag("table-overview").semantics {
                        stateDescription = "Powiększenie ${(zoom * 100).roundToInt()}%"
                    }.pointerInput(table.imageUri, selected?.id, image) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val fit = minOf(size.width.toFloat() / image.width, size.height.toFloat() / image.height)
                            val base = Offset(image.width * fit, image.height * fit)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            var gestureZoom = zoom
                            var gesturePan = pan
                            fun screen(point: NormalizedPoint) = center + gesturePan + Offset((point.x - .5f) * base.x * gestureZoom, (point.y - .5f) * base.y * gestureZoom)
                            val active = currentBoards.firstOrNull { it.id == selected?.id }
                            var draggedCorners = active?.corners.orEmpty()
                            var corner = active?.corners?.indices?.minByOrNull { (screen(active.corners[it]) - down.position).getDistance() }
                                ?.takeIf { (screen(active.corners[it]) - down.position).getDistance() <= 32.dp.toPx() } ?: -1
                            if (corner < 0) {
                                currentBoards.lastOrNull { board -> contains(board.corners.map(::screen), down.position) }?.let { selectedId = it.id }
                            }
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    corner = -1
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    val nextZoom = (gestureZoom * event.calculateZoom()).coerceIn(1f, 5f)
                                    gesturePan = (gesturePan + center - centroid) * (nextZoom / gestureZoom) + centroid - center + event.calculatePan()
                                    gestureZoom = nextZoom
                                } else if (pressed.size == 1) {
                                    val delta = pressed.first().positionChange()
                                    if (corner >= 0 && active != null) {
                                        val latest = currentBoards.firstOrNull { it.id == active.id }
                                        if (latest != null) {
                                            val corners = draggedCorners.toMutableList()
                                            val previous = corners[corner]
                                            corners[corner] = NormalizedPoint((previous.x + delta.x / (base.x * gestureZoom)).coerceIn(0f, 1f),
                                                (previous.y + delta.y / (base.y * gestureZoom)).coerceIn(0f, 1f))
                                            draggedCorners = corners
                                            changeBoards(currentBoards.map { if (it.id == active.id) it.copy(corners = corners) else it })
                                        }
                                    } else gesturePan += delta
                                }
                                gesturePan = Offset(gesturePan.x.coerceIn(-base.x * gestureZoom / 2, base.x * gestureZoom / 2), gesturePan.y.coerceIn(-base.y * gestureZoom / 2, base.y * gestureZoom / 2))
                                zoom = gestureZoom
                                pan = gesturePan
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }) {
                        val fit = minOf(size.width / image.width, size.height / image.height)
                        val w = image.width * fit * zoom
                        val h = image.height * fit * zoom
                        val origin = Offset((size.width - w) / 2, (size.height - h) / 2) + pan
                        drawImage(imageBitmap, dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()), dstSize = IntSize(w.roundToInt(), h.roundToInt()))
                        fun screen(point: NormalizedPoint) = origin + Offset(point.x * w, point.y * h)
                        boards.forEachIndexed { index, board ->
                            val points = board.corners.map(::screen)
                            val color = if (!board.isValid) Color.Red else if (board.id == selected?.id) Color(0xFFFFD54F) else Color(0xFF9CFFB5)
                            points.indices.forEach { drawLine(color, points[it], points[(it + 1) % 4], 3.dp.toPx()) }
                            if (board.id == selected?.id) {
                                if (board.isValid) {
                                    val projection = BoardProjection(board.corners)
                                    for (c in 1..4) drawLine(color.copy(alpha = .7f), screen(projection.point(c / 5f, 0f)), screen(projection.point(c / 5f, 1f)), 1.dp.toPx())
                                    for (r in 1..3) drawLine(color.copy(alpha = .7f), screen(projection.point(0f, r / 4f)), screen(projection.point(1f, r / 4f)), 1.dp.toPx())
                                }
                                points.forEach { drawCircle(Color.White, 10.dp.toPx(), it); drawCircle(color, 10.dp.toPx(), it, style = Stroke(3.dp.toPx())) }
                                drawCircle(Color.Black, 5.dp.toPx(), points[0])
                            }
                            val label = points.reduce { a, b -> a + b } / 4f
                            drawCircle(Color.Black.copy(alpha = .8f), 16.dp.toPx(), label)
                            drawContext.canvas.nativeCanvas.drawText("${index + 1}", label.x, label.y + 5.dp.toPx(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = android.graphics.Color.WHITE; textSize = 18.dp.toPx(); textAlign = Paint.Align.CENTER
                            })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Powiększ dwoma palcami", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Dopasuj") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(boards, key = { _, board -> board.id }) { index, board ->
                    OutlinedButton(onClick = { selectedId = board.id }, modifier = Modifier.testTag("table-board-${board.id}")) {
                        Text("${index + 1}. ${board.name.ifBlank { "Gracz" }}${if (board.id == selected?.id) " ✓" else ""}")
                    }
                }
            }
            selected?.let { board ->
                OutlinedTextField(value = board.name, onValueChange = { replace(board.copy(name = it)) }, label = { Text("Nazwa gracza") },
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }), modifier = Modifier.fillMaxWidth().testTag("table-player-name"))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    val board = BoardOutline(UUID.randomUUID().toString(), "Gracz ${boards.size + 1}", listOf(
                        NormalizedPoint(.25f, .25f), NormalizedPoint(.75f, .25f), NormalizedPoint(.75f, .75f), NormalizedPoint(.25f, .75f)))
                    selectedId = board.id; onBoardsChanged(boards + board)
                }, enabled = boards.size < 6, modifier = Modifier.weight(1f)) { Text("Dodaj") }
                OutlinedButton(onClick = { selected?.let { board -> onBoardsChanged(boards.filter { it.id != board.id }); selectedId = null } },
                    enabled = selected != null, modifier = Modifier.weight(1f)) { Text("Usuń") }
                OutlinedButton(onClick = { selected?.let { replace(it.rotated()) } }, enabled = selected != null,
                    modifier = Modifier.weight(1f)) { Text("Obróć 90°") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text("Powtórz") }
                Button(onClick = onConfirm, enabled = boards.size in 2..6 && boards.all { it.isValid }, modifier = Modifier.weight(1f)) { Text("Rozpoznaj") }
            }
        }
    }
}

private fun contains(points: List<Offset>, point: Offset): Boolean {
    val crosses = points.indices.map { index ->
        val a = points[index]; val b = points[(index + 1) % points.size]
        (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
    }
    return crosses.all { it >= 0 } || crosses.all { it <= 0 }
}
