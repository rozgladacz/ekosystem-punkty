package pl.rozgladacz.ekosystempunkty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.rozgladacz.ekosystempunkty.AppScreen
import pl.rozgladacz.ekosystempunkty.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcosystemApp(
    viewModel: MainViewModel,
    onInstallUpdate: (File) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showTopBar = state.screen !in setOf(AppScreen.CAMERA, AppScreen.CROP)

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Ekosystem Punkty") },
                    actions = {
                        if (state.screen != AppScreen.SETTINGS) {
                            TextButton(onClick = viewModel::openSettings) { Text("Ustawienia") }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(if (showTopBar) padding else androidx.compose.foundation.layout.PaddingValues())) {
            when (state.screen) {
                AppScreen.SETUP -> SetupScreen(onStart = viewModel::startGame)
                AppScreen.PLAYERS -> PlayersScreen(
                    players = state.players,
                    onCapture = viewModel::capturePlayer,
                    onEdit = viewModel::editConfirmedBoard,
                    onResults = viewModel::showResults,
                    onNewGame = viewModel::newGame,
                )
                AppScreen.CAMERA -> CameraScreen(
                    playerName = state.players.getOrNull(state.activePlayerIndex)?.name.orEmpty(),
                    onCaptured = viewModel::onPhotoCaptured,
                    onCancel = viewModel::cancelCaptureFlow,
                )
                AppScreen.CROP -> state.cropProposal?.let { proposal ->
                    CropScreen(proposal, onConfirm = viewModel::confirmCrop, onRetake = viewModel::retakePhoto)
                }
                AppScreen.REVIEW -> state.review?.let { review ->
                    ReviewScreen(
                        playerName = state.players[review.playerIndex].name,
                        review = review,
                        onCardChanged = viewModel::setReviewCard,
                        onConfirm = viewModel::confirmReview,
                        onRetake = viewModel::retakePhoto,
                        onCancel = viewModel::cancelCaptureFlow,
                    )
                }
                AppScreen.RESULTS -> state.score?.let { score ->
                    ResultsScreen(
                        score = score,
                        onEdit = viewModel::editConfirmedBoard,
                        onPlayers = viewModel::showPlayers,
                        onNewGame = viewModel::newGame,
                    )
                }
                AppScreen.SETTINGS -> SettingsScreen(
                    updateState = state.update,
                    onCheck = viewModel::checkForUpdates,
                    onDownload = viewModel::downloadUpdate,
                    onInstall = onInstallUpdate,
                    onBack = viewModel::closeSettings,
                )
            }

            if (state.busy) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text("Analizuję zdjęcie…", color = Color.White)
                    }
                }
            }
        }
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Nie udało się wykonać operacji") },
            text = { Text(message) },
            confirmButton = { Button(onClick = viewModel::clearError) { Text("OK") } },
        )
    }
}

