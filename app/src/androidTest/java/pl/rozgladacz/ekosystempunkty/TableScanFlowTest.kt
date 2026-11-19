package pl.rozgladacz.ekosystempunkty

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import pl.rozgladacz.ekosystempunkty.data.DevelopmentArchive
import pl.rozgladacz.ekosystempunkty.data.SessionStore
import pl.rozgladacz.ekosystempunkty.domain.*
import pl.rozgladacz.ekosystempunkty.vision.*
import java.io.File
import java.util.UUID

class TableScanFlowTest {
    private val application get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private lateinit var vm: MainViewModel
    private lateinit var owner: ViewModelStore
    private lateinit var fake: FakeRecognizer
    private var developmentWasEnabled = false

    @Before fun prepare() = runBlocking {
        SessionStore(application).clear()
        developmentWasEnabled = DevelopmentArchive(application).enabled
        DevelopmentArchive(application).setEnabled(false)
        fake = FakeRecognizer()
        withContext(Dispatchers.Main) {
            owner = ViewModelStore()
            vm = MainViewModel(application, fake)
            owner.put("flow", vm)
        }
    }

    @After fun cleanUp() = runBlocking {
        withContext(Dispatchers.Main) { vm.newGame(); owner.clear() }
        SessionStore(application).clear()
        DevelopmentArchive(application).setEnabled(developmentWasEnabled)
    }

    private suspend fun main(block: () -> Unit) = withContext(Dispatchers.Main) { block() }
    private suspend fun await(predicate: (AppUiState) -> Boolean) = withTimeout(15_000) {
        while (!predicate(vm.state.value)) delay(20)
    }
    private fun photo(): File = File(application.cacheDir, "captures/${UUID.randomUUID()}.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
    private suspend fun overview(): File {
        main { vm.captureTable() }
        val file = photo()
        main { vm.onPhotoCaptured(Uri.fromFile(file)) }
        await { it.screen == AppScreen.TABLE_CROP }
        return file
    }
    private suspend fun review(): File = overview().also {
        main { vm.confirmTable() }
        await { state -> state.screen == AppScreen.REVIEW && !state.busy }
    }

    @Test fun onePhotoSurvivesConfirmingFirstPlayerAndIsDeletedAfterLast() = runBlocking {
        val file = review()
        main { vm.confirmReview() }
        await { it.activePlayerIndex == 1 && !it.busy }
        assertTrue(file.exists())
        assertEquals(AppScreen.REVIEW, vm.state.value.screen)
        val partial = SessionStore(application).load()!!
        assertTrue(partial[0].grid.isComplete)
        assertFalse(partial[1].grid.isComplete)
        main { vm.confirmReview() }
        await { it.activePlayerIndex == 2 && !it.busy }
        main { vm.confirmReview() }
        await { it.screen == AppScreen.RESULTS }
        assertFalse(file.exists())
        assertEquals(3, vm.state.value.score!!.players.size)
    }

    @Test fun exitingKeepsConfirmedBoardsAndRestoresPendingPlayersAfterRestart() = runBlocking {
        val file = review()
        main { vm.confirmReview() }
        await { it.activePlayerIndex == 1 && !it.busy }
        main { vm.cancelCaptureFlow() }
        assertFalse(file.exists())
        assertEquals(AppScreen.PLAYERS, vm.state.value.screen)
        main { owner.clear(); vm = MainViewModel(application, fake); owner.put("restored", vm) }
        await { it.screen == AppScreen.PLAYERS }
        assertTrue(vm.state.value.players[0].grid.isComplete)
        assertTrue(vm.state.value.players.drop(1).all { !it.grid.isComplete })
    }

    @Test fun additionalPhotosPreserveManualCorrectionsAndStopAtFourShots() = runBlocking {
        val table = review()
        main { vm.setReviewCard(0, CardType.DEER) }
        repeat(3) {
            main { vm.addPhotoToReview() }
            val extra = photo()
            main { vm.onPhotoCaptured(Uri.fromFile(extra)) }
            await { it.screen == AppScreen.CROP && !it.busy }
            main { vm.confirmCrop(vm.state.value.cropProposal!!) }
            await { it.screen == AppScreen.REVIEW && !it.busy }
            assertEquals(CardType.DEER, vm.state.value.review!!.cells[0].type)
            assertTrue(table.exists())
        }
        assertEquals(4, vm.state.value.review!!.imageUris.size)
        main { vm.addPhotoToReview() }
        assertEquals(AppScreen.REVIEW, vm.state.value.screen)
        main { vm.confirmReview() }
        await { it.activePlayerIndex == 1 && !it.busy }
        assertTrue(table.exists())
    }

    @Test fun cancellingExtraCameraReturnsToCurrentTableReview() = runBlocking {
        val table = review()
        main { vm.addPhotoToReview(); vm.cancelCaptureFlow() }
        assertEquals(AppScreen.REVIEW, vm.state.value.screen)
        assertEquals(0, vm.state.value.review!!.playerIndex)
        assertTrue(table.exists())
    }

    @Test fun namesAndOrientationStayWithStableIdsAndInvalidOutlinesCannotStart() = runBlocking {
        overview()
        val boards = vm.state.value.tableCapture!!.boards
        main { vm.updateTableBoards(boards.take(1)); vm.confirmTable() }
        assertEquals(AppScreen.TABLE_CROP, vm.state.value.screen)
        val edited = listOf(boards[0].rotated().copy(name = "  Ala  "), boards[1].copy(name = ""), boards[2])
        main { vm.updateTableBoards(edited); vm.confirmTable() }
        await { it.screen == AppScreen.REVIEW }
        assertEquals(boards.map { it.id }, vm.state.value.players.map { it.id })
        assertEquals(listOf("Ala", "Gracz 2", "Gracz 3"), vm.state.value.players.map { it.name })
        assertEquals(edited[0].corners, vm.state.value.review!!.cornersByShot[0])
    }

    @Test fun cancelledDetectorCannotReopenAnOldPhoto() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        fake.detectionGate = gate
        main { vm.captureTable() }
        val file = photo()
        main { vm.onPhotoCaptured(Uri.fromFile(file)) }
        fake.detectionStarted.await()
        main { vm.cancelCaptureFlow() }
        gate.complete(Unit)
        delay(150)
        assertEquals(AppScreen.SETUP, vm.state.value.screen)
        assertFalse(vm.state.value.busy)
        assertFalse(file.exists())
    }

    @Test fun failedDetectionAllowsAnotherTablePhoto() = runBlocking {
        fake.failDetection = true
        main { vm.captureTable() }
        val first = photo()
        main { vm.onPhotoCaptured(Uri.fromFile(first)) }
        await { !it.busy && it.error != null }
        assertEquals(AppScreen.CAMERA, vm.state.value.screen)
        assertFalse(first.exists())

        fake.failDetection = false
        val second = photo()
        main { vm.onPhotoCaptured(Uri.fromFile(second)) }
        await { it.screen == AppScreen.TABLE_CROP }
        assertEquals(Uri.fromFile(second), vm.state.value.tableCapture?.imageUri)
    }

    private class FakeRecognizer : BoardRecognitionService {
        var failDetection = false
        var detectionGate: CompletableDeferred<Unit>? = null
        val detectionStarted = CompletableDeferred<Unit>()
        private val cells get() = List(20) { RecognitionCell(CardType.RABBIT, .99f, probabilities = List(11) { i -> if (i == 0) .99f else .001f }) }
        private val corners get() = listOf(NormalizedPoint(.1f, .1f), NormalizedPoint(.9f, .1f), NormalizedPoint(.9f, .9f), NormalizedPoint(.1f, .9f))
        override suspend fun detect(uri: Uri) = CropProposal(uri, corners, 1f)
        override suspend fun detectAll(uri: Uri): List<BoardOutline> {
            check(!failDetection) { "Nie można odczytać zdjęcia" }
            detectionStarted.complete(Unit)
            detectionGate?.let { withContext(NonCancellable) { it.await() } }
            return List(3) { index -> BoardOutline(UUID.randomUUID().toString(), "Gracz ${index + 1}", corners) }
        }
        override suspend fun recognize(uri: Uri, corners: List<NormalizedPoint>) = RecognitionResult(cells, corners, .99f)
        override suspend fun recognizeAll(uri: Uri, boards: List<BoardOutline>, onProgress: (Int, Int) -> Unit) = boards.mapIndexed { index, board ->
            onProgress(index + 1, boards.size)
            BoardRecognition(board, RecognitionResult(cells, board.corners, .99f))
        }
    }
}
