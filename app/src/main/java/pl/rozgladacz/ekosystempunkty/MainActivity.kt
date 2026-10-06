package pl.rozgladacz.ekosystempunkty

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.rozgladacz.ekosystempunkty.ui.EcosystemApp
import pl.rozgladacz.ekosystempunkty.ui.EcosystemTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EcosystemTheme {
                val viewModel: MainViewModel = viewModel()
                var pendingInstallation by remember { mutableStateOf<File?>(null) }
                val settingsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    pendingInstallation?.takeIf { packageManager.canRequestPackageInstalls() }?.let(::launchInstaller)
                    pendingInstallation = null
                }
                EcosystemApp(
                    viewModel = viewModel,
                    onInstallUpdate = { file ->
                        if (packageManager.canRequestPackageInstalls()) {
                            launchInstaller(file)
                        } else {
                            pendingInstallation = file
                            settingsLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:$packageName"),
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    private fun launchInstaller(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }
}

