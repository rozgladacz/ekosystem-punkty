package pl.rozgladacz.ekosystempunkty

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import pl.rozgladacz.ekosystempunkty.data.SessionStore
import pl.rozgladacz.ekosystempunkty.data.DevelopmentArchive
import pl.rozgladacz.ekosystempunkty.data.ArchiveReference
import pl.rozgladacz.ekosystempunkty.data.ArchiveBoard
import pl.rozgladacz.ekosystempunkty.domain.BoardGrid
import pl.rozgladacz.ekosystempunkty.domain.BoardOutline
import pl.rozgladacz.ekosystempunkty.domain.CardType
import pl.rozgladacz.ekosystempunkty.domain.GameScore
import pl.rozgladacz.ekosystempunkty.domain.MultiPhotoFusion
import pl.rozgladacz.ekosystempunkty.domain.NormalizedPoint
import pl.rozgladacz.ekosystempunkty.domain.PlayerBoard
import pl.rozgladacz.ekosystempunkty.domain.RecognitionCell
import pl.rozgladacz.ekosystempunkty.domain.ScoreEngine
import pl.rozgladacz.ekosystempunkty.update.UpdateCheckResult
import pl.rozgladacz.ekosystempunkty.update.UpdateDownloadResult
import pl.rozgladacz.ekosystempunkty.update.UpdateInfo
import pl.rozgladacz.ekosystempunkty.update.UpdateManager
import pl.rozgladacz.ekosystempunkty.vision.BoardRecognizer
import pl.rozgladacz.ekosystempunkty.vision.BoardRecognitionService
import pl.rozgladacz.ekosystempunkty.vision.CropProposal
import java.io.File
import java.util.UUID

enum class AppScreen { SETUP, PLAYERS, CAMERA, CROP, TABLE_CROP, REVIEW, RESULTS, SETTINGS }
enum class CaptureMode { PLAYER, TABLE }

data class TableCaptureState(val imageUri: Uri, val boards: List<BoardOutline>, val archiveId: String? = null)

data class ReviewState(
    val playerIndex: Int,
    val cells: List<RecognitionCell>,
    val imageUris: List<Uri> = emptyList(),
    val shotCells: List<List<RecognitionCell>> = emptyList(),
    val cornersByShot: List<List<NormalizedPoint>> = emptyList(),
    val archiveRefs: List<ArchiveReference?> = emptyList(),
    val manualOverrides: Map<Int, CardType> = emptyMap(),
    val groupId: String? = null,
) {
    val imageUri: Uri? get() = imageUris.lastOrNull()
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object Current : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo) : UpdateUiState
    data class Ready(val file: File, val versionName: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

data class AppUiState(
    val screen: AppScreen = AppScreen.SETUP,
    val returnScreen: AppScreen = AppScreen.SETUP,
    val players: List<PlayerBoard> = emptyList(),
    val activePlayerIndex: Int = 0,
    val cropProposal: CropProposal? = null,
    val review: ReviewState? = null,
    val score: GameScore? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val update: UpdateUiState = UpdateUiState.Idle,
    val captureGroupId: String? = null,
    val captureShotCount: Int = 0,
    val captureMode: CaptureMode = CaptureMode.PLAYER,
    val tableCapture: TableCaptureState? = null,
    val tableReviews: List<ReviewState> = emptyList(),
    val analysisProgress: String? = null,
    val developmentMode: Boolean = false,
    val developmentSaved: Int = 0,
    val developmentBusy: Boolean = false,
    val developmentMessage: String? = null,
)

class MainViewModel @JvmOverloads constructor(
    application: Application,
    recognitionService: BoardRecognitionService? = null,
) : AndroidViewModel(application) {
    private val store = SessionStore(application)
    private val developmentArchive = DevelopmentArchive(application)
    private val recognizer: BoardRecognitionService by lazy { recognitionService ?: BoardRecognizer(application) }
    private val updateManager = UpdateManager(application)
    private val mutableState = MutableStateFlow(AppUiState(developmentMode = developmentArchive.enabled))
    private val captureArchiveIds = mutableMapOf<String, String>()
    private var captureJob: Job? = null
    private var inFlightCapture: Uri? = null
    private var sessionRevision = 0
    val state = mutableState.asStateFlow()

    init {
        // Po restarcie procesu żadne zdjęcie nie jest już częścią aktywnego
        // przepływu, więc usuwamy ewentualne pliki pozostałe po awarii.
        File(application.cacheDir, "captures").listFiles()?.forEach { runCatching { it.delete() } }
        viewModelScope.launch {
            mutableState.update { it.copy(developmentSaved = developmentArchive.count()) }
            val restored = store.load()
            if (!restored.isNullOrEmpty() && sessionRevision == 0) {
                mutableState.update { it.copy(screen = AppScreen.PLAYERS, players = restored) }
            }
        }
    }

    fun startGame(names: List<String>) {
        require(names.size in 2..6)
        sessionRevision++
        val players = names.mapIndexed { index, name ->
            PlayerBoard(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "Gracz ${index + 1}" },
                grid = BoardGrid.empty(),
            )
        }
        val current = mutableState.value
        mutableState.value = AppUiState(
            screen = AppScreen.PLAYERS,
            players = players,
            developmentMode = current.developmentMode,
            developmentSaved = current.developmentSaved,
        )
        save(players)
    }

    fun capturePlayer(index: Int) {
        if (mutableState.value.busy || index !in mutableState.value.players.indices) return
        mutableState.update {
            it.copy(
                screen = AppScreen.CAMERA,
                activePlayerIndex = index,
                review = null,
                cropProposal = null,
                captureGroupId = UUID.randomUUID().toString(),
                captureShotCount = 0,
                captureMode = CaptureMode.PLAYER,
                error = null,
            )
        }
    }

    fun addPhotoToReview() {
        mutableState.update { state ->
            val review = state.review ?: return@update state
            if (state.screen != AppScreen.REVIEW || review.imageUris.isEmpty() ||
                review.imageUris.size >= MAX_PHOTOS_PER_BOARD
            ) return@update state
            state.copy(screen = AppScreen.CAMERA, cropProposal = null, error = null,
                captureMode = CaptureMode.PLAYER, captureGroupId = review.groupId ?: state.captureGroupId)
        }
    }

    fun captureTable() {
        if (mutableState.value.screen != AppScreen.SETUP || mutableState.value.busy) return
        sessionRevision++
        mutableState.update { it.copy(screen = AppScreen.CAMERA, captureMode = CaptureMode.TABLE, error = null, captureShotCount = 0) }
    }

    fun updateTableBoards(boards: List<BoardOutline>) {
        mutableState.update { state ->
            if (state.screen != AppScreen.TABLE_CROP || state.busy || boards.map { it.id }.distinct().size != boards.size) state
            else state.copy(tableCapture = state.tableCapture?.copy(boards = boards))
        }
    }

    fun confirmTable() {
        val table = mutableState.value.tableCapture ?: return
        if (mutableState.value.busy || mutableState.value.screen != AppScreen.TABLE_CROP || table.boards.size !in 2..6 || table.boards.any { !it.isValid }) return
        mutableState.update { it.copy(busy = true, error = null, analysisProgress = "Przygotowuję układy…") }
        captureJob = viewModelScope.launch {
            val players = table.boards.mapIndexed { index, board ->
                PlayerBoard(board.id, board.name.trim().ifBlank { "Gracz ${index + 1}" }, BoardGrid.empty())
            }
            val groups = table.boards.associate { it.id to UUID.randomUUID().toString() }
            captureAttempt {
                table.archiveId?.let { id -> developmentArchive.registerTableBoards(id,
                    table.boards.map { ArchiveBoard(it.id, groups.getValue(it.id), it.corners) }) }
                store.save(players)
                mutableState.update { it.copy(players = players) }
                recognizer.recognizeAll(table.imageUri, table.boards) { index, count ->
                    mutableState.update { if (it.busy && it.tableCapture?.imageUri == table.imageUri)
                        it.copy(analysisProgress = "Rozpoznaję układ $index z $count…") else it }
                }.also { results ->
                    currentCoroutineContext().ensureActive()
                    check(results.map { it.outline.id } == table.boards.map { it.id }) { "Niepełna lista rozpoznanych układów" }
                }
            }.onSuccess { recognized ->
                val reviews = recognized.mapIndexed { index, item ->
                    ReviewState(index, item.result.cells, listOf(table.imageUri), listOf(item.result.cells),
                        listOf(item.result.corners), listOf(table.archiveId?.let { ArchiveReference(it, item.outline.id) }),
                        groupId = groups.getValue(item.outline.id))
                }
                mutableState.update { it.copy(screen = AppScreen.REVIEW, players = players,
                    review = reviews.first(), tableReviews = reviews, busy = false, analysisProgress = null,
                    activePlayerIndex = 0, captureMode = CaptureMode.PLAYER, captureGroupId = reviews.first().groupId, captureShotCount = 1) }
            }.onFailure { error -> mutableState.update { it.copy(busy = false, analysisProgress = null, error = error.message ?: "Nie udało się rozpoznać układów") } }
        }
    }

    fun onPhotoCaptured(uri: Uri) {
        val snapshot = mutableState.value
        if (snapshot.screen != AppScreen.CAMERA || snapshot.busy) {
            deleteCapture(uri)
            return
        }
        inFlightCapture = uri
        val shotIndex = snapshot.captureShotCount + 1
        mutableState.update { it.copy(busy = true, error = null, captureShotCount = shotIndex) }
        captureJob = viewModelScope.launch {
            val wholeTable = snapshot.captureMode == CaptureMode.TABLE
            val archiveResult = captureAttempt {
                when {
                    !snapshot.developmentMode -> null
                    wholeTable -> developmentArchive.saveTablePhoto(uri)
                    snapshot.captureGroupId != null -> developmentArchive.savePhoto(uri, snapshot.captureGroupId, shotIndex)
                    else -> null
                }
            }
            val archiveId = archiveResult.getOrNull()
            if (archiveId != null) {
                val saved = developmentArchive.count()
                mutableState.update { it.copy(developmentSaved = saved) }
            }
            val archiveWarning = archiveResult.exceptionOrNull()?.let {
                "Zdjęcie nie zostało zachowane do treningu: ${it.message ?: "błąd zapisu"}"
            }

            captureAttempt {
                if (wholeTable) {
                    val boards = recognizer.detectAll(uri)
                    currentCoroutineContext().ensureActive()
                    mutableState.update {
                        it.copy(
                            screen = AppScreen.TABLE_CROP,
                            tableCapture = TableCaptureState(uri, boards, archiveId),
                            busy = false,
                            error = archiveWarning,
                        )
                    }
                } else {
                    val proposal = recognizer.detect(uri)
                    currentCoroutineContext().ensureActive()
                    if (archiveId != null) captureArchiveIds[uri.toString()] = archiveId
                    mutableState.update { it.copy(screen = AppScreen.CROP, cropProposal = proposal, busy = false, error = archiveWarning) }
                }
            }.onFailure { error ->
                deleteCapture(uri)
                mutableState.update { it.copy(busy = false, error = error.message ?: "Nie udało się odczytać zdjęcia") }
            }
            inFlightCapture = null
        }
    }

    fun confirmCrop(proposal: CropProposal) {
        if (mutableState.value.busy || mutableState.value.screen != AppScreen.CROP || mutableState.value.cropProposal?.imageUri != proposal.imageUri) return
        mutableState.update { it.copy(busy = true, cropProposal = proposal, error = null) }
        captureJob = viewModelScope.launch {
            captureAttempt { recognizer.recognize(proposal.imageUri, proposal.corners).also { currentCoroutineContext().ensureActive() } }
                .onSuccess { result ->
                    mutableState.update { state ->
                        val previous = state.review?.takeIf { it.playerIndex == state.activePlayerIndex }
                        val shots = previous?.shotCells.orEmpty() + listOf(result.cells)
                        val overrides = previous?.manualOverrides.orEmpty()
                        state.copy(
                            screen = AppScreen.REVIEW,
                            busy = false,
                            cropProposal = null,
                            review = ReviewState(
                                playerIndex = state.activePlayerIndex,
                                cells = MultiPhotoFusion.merge(shots, overrides),
                                imageUris = previous?.imageUris.orEmpty() + proposal.imageUri,
                                shotCells = shots,
                                cornersByShot = previous?.cornersByShot.orEmpty() + listOf(proposal.corners),
                                archiveRefs = previous?.archiveRefs.orEmpty() + listOf(captureArchiveIds[proposal.imageUri.toString()]?.let { ArchiveReference(it) }),
                                manualOverrides = overrides,
                                groupId = previous?.groupId ?: state.captureGroupId,
                            ),
                        )
                    }
                }
                .onFailure { error -> mutableState.update { it.copy(busy = false, error = error.message ?: "Nie udało się rozpoznać kart") } }
        }
    }

    fun setReviewCard(index: Int, type: CardType) {
        mutableState.update { state ->
            val review = state.review ?: return@update state
            if (state.busy) return@update state
            state.copy(review = review.copy(
                cells = review.cells.toMutableList().also {
                    it[index] = it[index].copy(type = type, confidence = 1f, reviewRequired = false)
                },
                manualOverrides = review.manualOverrides + (index to type),
            ))
        }
    }

    fun confirmReview() {
        val state = mutableState.value
        val review = state.review ?: return
        if (state.busy || state.screen != AppScreen.REVIEW || review.cells.any { it.type == null }) return
        val labels = review.cells.map { requireNotNull(it.type) }
        mutableState.update { it.copy(busy = true) }
        captureJob = viewModelScope.launch {
            val archiveResult = captureAttempt {
                review.archiveRefs.forEachIndexed { shot, reference ->
                    if (reference != null) {
                        developmentArchive.labelPhoto(
                            reference.captureId, labels, review.cornersByShot[shot],
                            review.shotCells[shot].map { it.confidence },
                            review.shotCells[shot].map { it.probabilities },
                            review.manualOverrides.keys,
                            reference.boardId,
                        )
                    }
                }
                if (review.archiveRefs.none { it != null }) {
                    state.players[review.playerIndex].developmentGroupId?.let { groupId ->
                        developmentArchive.updateLabeledGroup(groupId, labels)
                    }
                }
            }
            if (archiveResult.isFailure) {
                mutableState.update {
                    it.copy(busy = false, error = "Nie udało się zapisać etykiet zdjęć: ${archiveResult.exceptionOrNull()?.message}")
                }
                return@launch
            }
            val previousPlayer = state.players[review.playerIndex]
            val updated = state.players.toMutableList().also { players ->
                players[review.playerIndex] = previousPlayer.copy(
                    grid = BoardGrid.complete(labels),
                    cardProbabilities = review.cells.map { it.probabilities },
                    developmentGroupId = if (review.archiveRefs.any { it != null }) review.groupId
                        else previousPlayer.developmentGroupId,
                )
            }
            val saved = captureAttempt { store.save(updated) }
            if (saved.isFailure) {
                mutableState.update { it.copy(busy = false, error = "Nie udało się zapisać partii: ${saved.exceptionOrNull()?.message}") }
                return@launch
            }
            review.imageUris.forEach { uri ->
                if (uri != state.tableCapture?.imageUri) {
                    deleteCapture(uri)
                }
            }
            val queued = state.tableReviews.filter { !updated[it.playerIndex].grid.isComplete }
            val nextReview = queued.firstOrNull()
            val next = updated.indexOfFirst { !it.grid.isComplete }
            val score = if (next < 0) ScoreEngine.score(updated) else null
            if (nextReview == null) deleteCapture(state.tableCapture?.imageUri)
            mutableState.update {
                it.copy(
                    players = updated,
                    review = nextReview,
                    tableReviews = queued,
                    tableCapture = if (nextReview == null) null else it.tableCapture,
                    cropProposal = null,
                    score = score,
                    busy = false,
                    activePlayerIndex = next.coerceAtLeast(0),
                    captureGroupId = nextReview?.groupId ?: if (next < 0) null else UUID.randomUUID().toString(),
                    captureShotCount = nextReview?.imageUris?.size ?: 0,
                    screen = if (next < 0) AppScreen.RESULTS else if (nextReview != null) AppScreen.REVIEW else AppScreen.CAMERA,
                )
            }
        }
    }

    fun editConfirmedBoard(index: Int) {
        val player = mutableState.value.players[index]
        mutableState.update {
            it.copy(
                screen = AppScreen.REVIEW,
                activePlayerIndex = index,
                review = ReviewState(index, player.grid.cells.mapIndexed { cellIndex, type ->
                    RecognitionCell(type, 1f, probabilities = player.cardProbabilities.getOrNull(cellIndex).orEmpty())
                }),
            )
        }
    }

    fun retakePhoto() {
        val state = mutableState.value
        if (state.busy) return
        if (state.screen == AppScreen.TABLE_CROP) {
            deleteCapture(state.tableCapture?.imageUri)
            mutableState.update { it.copy(screen = AppScreen.CAMERA, tableCapture = null, captureMode = CaptureMode.TABLE, error = null) }
        } else if (state.screen == AppScreen.CROP) {
            deleteCapture(state.cropProposal?.imageUri)
            mutableState.update { it.copy(screen = AppScreen.CAMERA, cropProposal = null, error = null) }
        } else {
            state.review?.imageUris.orEmpty().filter { it != state.tableCapture?.imageUri }.forEach(::deleteCapture)
            mutableState.update { it.copy(screen = AppScreen.CAMERA, review = null, cropProposal = null, error = null,
                captureMode = CaptureMode.PLAYER, captureShotCount = 0) }
        }
    }

    fun cancelCaptureFlow() {
        val state = mutableState.value
        if (state.screen == AppScreen.REVIEW && state.busy) return
        captureJob?.cancel()
        captureJob = null
        deleteCapture(inFlightCapture)
        inFlightCapture = null
        deleteCapture(state.cropProposal?.imageUri)
        val keepReview = state.review?.takeIf { it.imageUris.isNotEmpty() && state.screen in setOf(AppScreen.CAMERA, AppScreen.CROP) }
        if (keepReview == null) {
            (state.review?.imageUris.orEmpty() + state.tableReviews.flatMap { it.imageUris } + listOfNotNull(state.tableCapture?.imageUri)).distinct().forEach(::deleteCapture)
            captureArchiveIds.clear()
        }
        mutableState.update {
            it.copy(
                screen = if (keepReview != null) AppScreen.REVIEW else if (it.players.isEmpty()) AppScreen.SETUP else AppScreen.PLAYERS,
                cropProposal = null,
                review = keepReview,
                tableCapture = if (keepReview == null) null else it.tableCapture,
                tableReviews = if (keepReview == null) emptyList() else it.tableReviews,
                busy = false,
                analysisProgress = null,
                error = null,
            )
        }
    }

    fun showResults() {
        val players = mutableState.value.players
        if (players.all { it.grid.isComplete }) {
            mutableState.update { it.copy(screen = AppScreen.RESULTS, score = ScoreEngine.score(players)) }
        }
    }

    fun showPlayers() {
        mutableState.update { it.copy(screen = AppScreen.PLAYERS) }
    }

    fun openSettings() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(returnScreen = it.screen, screen = AppScreen.SETTINGS) }
        viewModelScope.launch { mutableState.update { it.copy(developmentSaved = developmentArchive.count()) } }
    }

    fun closeSettings() {
        mutableState.update { it.copy(screen = it.returnScreen) }
    }

    fun checkForUpdates() {
        if (mutableState.value.update is UpdateUiState.Checking || mutableState.value.update is UpdateUiState.Downloading) return
        mutableState.update { it.copy(update = UpdateUiState.Checking) }
        viewModelScope.launch {
            val result = updateManager.checkOnUserRequest()
            mutableState.update {
                it.copy(update = when (result) {
                    UpdateCheckResult.Current -> UpdateUiState.Current
                    is UpdateCheckResult.Available -> UpdateUiState.Available(result.info)
                    is UpdateCheckResult.Failure -> UpdateUiState.Error(result.message)
                })
            }
        }
    }

    fun downloadUpdate(info: UpdateInfo) {
        val available = mutableState.value.update as? UpdateUiState.Available ?: return
        if (available.info != info) return
        mutableState.update { it.copy(update = UpdateUiState.Downloading(info)) }
        viewModelScope.launch {
            val result = updateManager.downloadOnUserRequest(info)
            mutableState.update {
                it.copy(update = when (result) {
                    is UpdateDownloadResult.Ready -> UpdateUiState.Ready(result.file, info.versionName)
                    is UpdateDownloadResult.Failure -> UpdateUiState.Error(result.message)
                })
            }
        }
    }

    fun newGame() {
        sessionRevision++
        captureJob?.cancel()
        val current = mutableState.value
        deleteCapture(inFlightCapture)
        inFlightCapture = null
        deleteCapture(current.cropProposal?.imageUri)
        current.review?.imageUris.orEmpty().forEach(::deleteCapture)
        deleteCapture(current.tableCapture?.imageUri)
        captureArchiveIds.clear()
        mutableState.value = AppUiState(
            developmentMode = current.developmentMode,
            developmentSaved = current.developmentSaved,
        )
        viewModelScope.launch { store.clear() }
    }

    fun setDevelopmentMode(enabled: Boolean) {
        runCatching { developmentArchive.setEnabled(enabled) }
            .onSuccess { mutableState.update { it.copy(developmentMode = enabled, developmentMessage = null) } }
            .onFailure { error -> mutableState.update { it.copy(error = error.message ?: "Nie udało się zapisać ustawienia") } }
    }

    fun exportDevelopmentArchive(uri: Uri) {
        mutableState.update { it.copy(developmentBusy = true, developmentMessage = null) }
        viewModelScope.launch {
            val result = runCatching { developmentArchive.exportTo(uri) }
            mutableState.update {
                it.copy(
                    developmentBusy = false,
                    developmentMessage = result.fold(
                        onSuccess = { count -> "Wyeksportowano $count zdjęć do wybranego pliku ZIP." },
                        onFailure = { error -> "Eksport nieudany: ${error.message ?: "błąd zapisu"}" },
                    ),
                )
            }
        }
    }

    fun deleteDevelopmentArchive() {
        mutableState.update { it.copy(developmentBusy = true, developmentMessage = null) }
        viewModelScope.launch {
            val result = runCatching { developmentArchive.deleteAll() }
            if (result.isSuccess) {
                captureArchiveIds.clear()
                val players = mutableState.value.players.map { it.copy(developmentGroupId = null) }
                mutableState.update { state ->
                    state.copy(
                        players = players,
                        review = state.review?.let { review ->
                            review.copy(archiveRefs = List(review.archiveRefs.size) { null })
                        },
                        tableCapture = state.tableCapture?.copy(archiveId = null),
                        tableReviews = state.tableReviews.map { it.copy(archiveRefs = List(it.archiveRefs.size) { null }) },
                    )
                }
                if (players.isNotEmpty()) save(players)
            }
            mutableState.update {
                it.copy(
                    developmentBusy = false,
                    developmentSaved = if (result.isSuccess) 0 else it.developmentSaved,
                    developmentMessage = result.fold(
                        onSuccess = { "Usunięto zapisany materiał rozwojowy z urządzenia." },
                        onFailure = { error -> "Nie udało się usunąć zdjęć: ${error.message ?: "błąd"}" },
                    ),
                )
            }
        }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    private fun save(players: List<PlayerBoard>) {
        viewModelScope.launch { store.save(players) }
    }

    private suspend fun <T> captureAttempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun deleteCapture(uri: Uri?) {
        uri?.let { captureArchiveIds.remove(it.toString()) }
        if (uri?.scheme == "file") runCatching { uri.path?.let(::File)?.delete() }
    }

    companion object {
        const val MAX_PHOTOS_PER_BOARD = 4
    }
}
