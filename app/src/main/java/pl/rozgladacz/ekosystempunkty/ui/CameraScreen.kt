package pl.rozgladacz.ekosystempunkty.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

@Composable
fun CameraScreen(
    playerName: String,
    onCaptured: (Uri) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var error by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted) {
            var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            cameraProvider = provider
                            val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            DisposableEffect(Unit) { onDispose { cameraProvider?.unbindAll() } }
            GridGuide()
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Aparat jest potrzebny wyłącznie do sfotografowania układu kart.", color = Color.White)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Zezwól na aparat") }
            }
        }

        Column(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color.Black.copy(alpha = 0.65f)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(playerName, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text("Umieść wszystkie 20 kart wewnątrz prowadnicy", color = Color.White)
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).background(Color.White).padding(12.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.65f)).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Anuluj", color = Color.White) }
            Button(
                enabled = permissionGranted,
                onClick = {
                    val directory = File(context.cacheDir, "captures").apply { mkdirs() }
                    val file = File(directory, "${UUID.randomUUID()}.jpg")
                    imageCapture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                onCaptured(Uri.fromFile(file))
                            }

                            override fun onError(exception: ImageCaptureException) {
                                file.delete()
                                error = exception.message ?: "Nie udało się zrobić zdjęcia"
                            }
                        },
                    )
                },
                modifier = Modifier.weight(2f),
            ) { Text("Zrób zdjęcie") }
        }
    }
}

@Composable
private fun GridGuide() {
    Canvas(Modifier.fillMaxSize()) {
        val maximumWidth = size.width * 0.88f
        val maximumHeight = size.height * 0.68f
        val expectedRatio = 5f * 63f / (4f * 88f)
        val width = minOf(maximumWidth, maximumHeight * expectedRatio)
        val height = width / expectedRatio
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        val rect = Rect(left, top, left + width, top + height)
        drawRect(Color.White, rect.topLeft, rect.size, style = Stroke(width = 4f))
        repeat(4) { column ->
            val x = left + width * (column + 1) / 5f
            drawLine(Color.White.copy(alpha = 0.7f), Offset(x, top), Offset(x, top + height), 2f)
        }
        repeat(3) { row ->
            val y = top + height * (row + 1) / 4f
            drawLine(Color.White.copy(alpha = 0.7f), Offset(left, y), Offset(left + width, y), 2f)
        }
    }
}

