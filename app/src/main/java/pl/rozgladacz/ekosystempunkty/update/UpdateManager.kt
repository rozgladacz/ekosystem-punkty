package pl.rozgladacz.ekosystempunkty.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.rozgladacz.ekosystempunkty.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val size: Long,
    val sha256: String,
    val notes: String,
)

sealed interface UpdateCheckResult {
    data object Current : UpdateCheckResult
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data class Failure(val message: String) : UpdateCheckResult
}

sealed interface UpdateDownloadResult {
    data class Ready(val file: File) : UpdateDownloadResult
    data class Failure(val message: String) : UpdateDownloadResult
}

class UpdateManager(private val context: Context) {
    suspend fun checkOnUserRequest(): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val release = getJson("https://api.github.com/repos/${BuildConfig.REPOSITORY}/releases/latest")
            val manifestAsset = release.getJSONArray("assets").let { assets ->
                (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name") == MANIFEST_NAME }
            } ?: error("Najnowsze wydanie nie zawiera $MANIFEST_NAME")
            val manifest = getJson(manifestAsset.getString("browser_download_url"))
            val info = UpdateInfo(
                versionCode = manifest.getLong("versionCode"),
                versionName = manifest.getString("versionName"),
                apkUrl = manifest.getString("apkUrl"),
                size = manifest.optLong("size", 0),
                sha256 = manifest.getString("sha256").lowercase(),
                notes = manifest.optString("notes"),
            )
            if (info.versionCode > BuildConfig.VERSION_CODE) {
                UpdateCheckResult.Available(info)
            } else {
                UpdateCheckResult.Current
            }
        }.getOrElse { UpdateCheckResult.Failure(it.userMessage("Nie udało się sprawdzić aktualizacji")) }
    }

    suspend fun downloadOnUserRequest(info: UpdateInfo): UpdateDownloadResult = withContext(Dispatchers.IO) {
        runCatching {
            require(info.versionCode > BuildConfig.VERSION_CODE) { "Ta wersja nie jest nowsza od zainstalowanej" }
            require(info.apkUrl.startsWith("https://github.com/${BuildConfig.REPOSITORY}/releases/download/")) {
                "Nieprawidłowy adres pliku aktualizacji"
            }
            val directory = File(context.cacheDir, "updates").apply {
                mkdirs()
                listFiles()?.forEach(File::delete)
            }
            val target = File(directory, "ekosystem-punkty-${info.versionName}.apk")
            val connection = open(info.apkUrl)
            try {
                connection.inputStream.use { input -> target.outputStream().use(input::copyTo) }
            } finally {
                connection.disconnect()
            }
            require(target.length() > 0) { "Pobrany plik jest pusty" }
            if (info.size > 0) require(target.length() == info.size) { "Rozmiar pobranego pliku jest nieprawidłowy" }
            require(target.sha256() == info.sha256) { "Suma kontrolna aktualizacji jest nieprawidłowa" }
            verifyApk(target, info.versionCode)
            UpdateDownloadResult.Ready(target)
        }.getOrElse { UpdateDownloadResult.Failure(it.userMessage("Nie udało się pobrać aktualizacji")) }
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(apk: File, expectedVersionCode: Long) {
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("Plik nie jest prawidłowym pakietem APK")
        require(archive.packageName == context.packageName.removeSuffix(".debug")) {
            "Aktualizacja ma inny identyfikator aplikacji"
        }
        require(archive.longVersionCode == expectedVersionCode) { "Numer wersji APK nie zgadza się z manifestem" }

        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val installedSigner = installed.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        val archiveSigner = archive.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        require(installedSigner != null && archiveSigner != null && installedSigner.contentEquals(archiveSigner)) {
            "Aktualizacja nie jest podpisana tym samym kluczem"
        }
    }

    private fun getJson(url: String): JSONObject {
        val connection = open(url)
        return try {
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "EkosystemPunkty/${BuildConfig.VERSION_NAME}")
        connect()
        if (responseCode !in 200..299) {
            val body = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            disconnect()
            error("Serwer odpowiedział kodem $responseCode${body.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}")
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun Throwable.userMessage(prefix: String): String =
        "$prefix: ${message ?: javaClass.simpleName}"

    companion object {
        private const val MANIFEST_NAME = "update.json"
    }
}

