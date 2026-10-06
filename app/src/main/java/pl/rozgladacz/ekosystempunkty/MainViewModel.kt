package pl.rozgladacz.ekosystempunkty

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.rozgladacz.ekosystempunkty.data.SessionStore
import pl.rozgladacz.ekosystempunkty.domain.BoardGrid
import pl.rozgladacz.ekosystempunkty.domain.CardType
import pl.rozgladacz.ekosystempunkty.domain.GameScore
import pl.rozgladacz.ekosystempunkty.domain.PlayerBoard
import pl.rozgladacz.ekosystempunkty.domain.RecognitionCell
import pl.rozgladacz.ekosystempunkty.domain.ScoreEngine
import pl.rozgladacz.ekosystempunkty.update.UpdateCheckResult
import pl.rozgladacz.ekosystempunkty.update.UpdateDownloadResult
import pl.rozgladacz.ekosystempunkty.update.UpdateInfo
import pl.rozgladacz.ekosystempunkty.update.UpdateManager
import pl.rozgladacz.ekosystempunkty.vision.BoardRecognizer
import pl.rozgladacz.ekosystempunkty.vision.CropProposal
import java.io.File
import java.util.UUID

enum class AppScreen { SETUP, PLAYERS, CAMERA, CROP, REVIEW, RESULTS, SETTINGS }

data class ReviewState(
    val playerIndex: Int,
    val cells: List<RecognitionCell>,
    val imageUri: Uri?,
)

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
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SessionStore(application)
    private val recognizer by lazy { BoardRecognizer(application) }
    private val updateManager = UpdateManager(application)
    private val mutableState = MutableStateFlow(AppUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = store.load()
            if (!restored.isNullOrEmpty()) {
                mutableState.update { it.copy(screen = AppScreen.PLAYERS, players = restored) }
            }
        }
    }

    fun startGame(names: List<String>) {
        require(names.size in 2..6)
        val players = names.mapIndexed { index, name ->
            PlayerBoard(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "Gracz ${index + 1}" },
                grid = BoardGrid.empty(),
            )
        }
        mutableState.value = AppUiState(screen = AppScreen.PLAYERS, players = players)
        save(players)
    }

    fun capturePlayer(index: Int) {
        mutableState.update { it.copy(screen = AppScreen.CAMERA, activePlayerIndex = index, error = null) }
    }

    fun onPhotoCaptured(uri: Uri) {
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { recognizer.detect(uri) }
                .onSuccess { proposal -> mutableState.update { it.copy(screen = AppScreen.CROP, cropProposal = proposal, busy = false) } }
                .onFailure { error -> mutableState.update { it.copy(busy = false, error = error.message ?: "Nie udało się odczytać zdjęcia") } }
        }
    }

    fun confirmCrop(proposal: CropProposal) {
        mutableState.update { it.copy(busy = true, cropProposal = proposal, error = null) }
        viewModelScope.launch {
            runCatching { recognizer.recognize(proposal.imageUri, proposal.corners) }
                .onSuccess { result ->
                    mutableState.update {
                        it.copy(
                            screen = AppScreen.REVIEW,
                            busy = false,
                            review = ReviewState(it.activePlayerIndex, result.cells, proposal.imageUri),
                        )
                    }
                }
                .onFailure { error -> mutableState.update { it.copy(busy = false, error = error.message ?: "Nie udało się rozpoznać kart") } }
        }
    }

    fun setReviewCard(index: Int, type: CardType) {
        mutableState.update { state ->
            val review = state.review ?: return@update state
            state.copy(review = review.copy(cells = review.cells.toMutableList().also {
                it[index] = RecognitionCell(type, 1f)
            }))
        }
    }

    fun confirmReview() {
        val state = mutableState.value
        val review = state.review ?: return
        if (review.cells.any { it.type == null }) return
        val updated = state.players.toMutableList().also { players ->
            players[review.playerIndex] = players[review.playerIndex].copy(
                grid = BoardGrid.complete(review.cells.map { requireNotNull(it.type) }),
            )
        }
        deleteCapture(review.imageUri)
        val next = updated.indexOfFirst { !it.grid.isComplete }
        val score = if (next < 0) ScoreEngine.score(updated) else null
        mutableState.update {
            it.copy(
                players = updated,
                review = null,
                cropProposal = null,
                score = score,
                activePlayerIndex = next.coerceAtLeast(0),
                screen = if (next < 0) AppScreen.RESULTS else AppScreen.CAMERA,
            )
        }
        save(updated)
    }

    fun editConfirmedBoard(index: Int) {
        val player = mutableState.value.players[index]
        mutableState.update {
            it.copy(
                screen = AppScreen.REVIEW,
                activePlayerIndex = index,
                review = ReviewState(index, player.grid.cells.map { type -> RecognitionCell(type, 1f) }, null),
            )
        }
    }

    fun retakePhoto() {
        val reviewUri = mutableState.value.review?.imageUri
        val cropUri = mutableState.value.cropProposal?.imageUri
        deleteCapture(reviewUri ?: cropUri)
        mutableState.update { it.copy(screen = AppScreen.CAMERA, review = null, cropProposal = null, error = null) }
    }

    fun cancelCaptureFlow() {
        deleteCapture(mutableState.value.cropProposal?.imageUri ?: mutableState.value.review?.imageUri)
        mutableState.update { it.copy(screen = AppScreen.PLAYERS, cropProposal = null, review = null, busy = false, error = null) }
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
        mutableState.update { it.copy(returnScreen = it.screen, screen = AppScreen.SETTINGS) }
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
        val current = mutableState.value
        deleteCapture(current.cropProposal?.imageUri ?: current.review?.imageUri)
        mutableState.value = AppUiState()
        viewModelScope.launch { store.clear() }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    private fun save(players: List<PlayerBoard>) {
        viewModelScope.launch { store.save(players) }
    }

    private fun deleteCapture(uri: Uri?) {
        if (uri?.scheme == "file") runCatching { uri.path?.let(::File)?.delete() }
    }
}
