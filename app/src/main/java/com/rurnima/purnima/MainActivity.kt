package com.rurnima.purnima

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private var status by androidx.compose.runtime.mutableStateOf("Ready to check GitHub Releases.")
    private var latestVersion by androidx.compose.runtime.mutableStateOf("")
    private var apkUrl by androidx.compose.runtime.mutableStateOf("")
    private var downloadedApk by androidx.compose.runtime.mutableStateOf<File?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (packageManager.canRequestPackageInstalls()) {
                downloadedApk?.let { launchInstaller(it) }
            } else {
                status = "Install permission was not granted."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Purnima", style = MaterialTheme.typography.headlineLarge)
                        Text("Self-update", style = MaterialTheme.typography.titleLarge)
                        Text(status)
                        Button(onClick = { checkForUpdate() }) { Text("Check for updates") }
                        if (apkUrl.isNotEmpty()) {
                            Button(onClick = { downloadAndInstall() }) {
                                Text("Download and install v" + latestVersion)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkForUpdate() {
        status = "Checking GitHub Releases…"
        Thread {
            try {
                val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "Purnima-Android-Updater")
                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                connection.disconnect()

                val tag = release.optString("tag_name").removePrefix("v")
                val assets = release.optJSONArray("assets")
                var foundApk = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name")
                        val link = asset.optString("browser_download_url")
                        if (name.endsWith(".apk", true) && link.startsWith("https://")) {
                            foundApk = link
                            break
                        }
                    }
                }
                runOnUiThread {
                    latestVersion = tag
                    apkUrl = foundApk
                    val comparison = compareVersions(tag, BuildConfig.VERSION_NAME)
                    status = when {
                        tag.isBlank() -> "The latest release has no version tag."
                        comparison <= 0 -> "Purnima is up to date (v" + BuildConfig.VERSION_NAME + ")."
                        foundApk.isBlank() -> "Release v" + tag + " exists, but no APK asset was attached."
                        else -> "Update available: v" + tag + " (installed v" + BuildConfig.VERSION_NAME + ")."
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { status = "Update check failed: " + (e.localizedMessage ?: "network error") }
            }
        }.start()
    }

    private fun downloadAndInstall() {
        val link = apkUrl
        if (link.isBlank()) return
        status = "Downloading update v" + latestVersion + "…"
        Thread {
            try {
                val connection = URL(link).openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.setRequestProperty("User-Agent", "Purnima-Android-Updater")
                val apk = File(filesDir, "purnima-update.apk")
                connection.inputStream.use { input ->
                    apk.outputStream().use { output -> input.copyTo(output) }
                }
                connection.disconnect()
                if (apk.length() < 1000000L) {
                    apk.delete()
                    throw IllegalStateException("Downloaded APK is unexpectedly small.")
                }
                runOnUiThread {
                    downloadedApk = apk
                    status = "Download complete. Opening Android installer…"
                    if (packageManager.canRequestPackageInstalls()) {
                        launchInstaller(apk)
                    } else {
                        status = "Allow Purnima to install updates in Android settings."
                        permissionLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + packageName)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { status = "Download failed: " + (e.localizedMessage ?: "network error") }
            }
        }.start()
    }

    private fun launchInstaller(apk: File) {
        try {
            val uri = FileProvider.getUriForFile(this, packageName + ".fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            status = "Android installer opened. Confirm to complete the update."
        } catch (e: Exception) {
            status = "Installer could not be opened: " + (e.localizedMessage ?: "unknown error")
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        val b = right.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val difference = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (difference != 0) return if (difference > 0) 1 else -1
        }
        return 0
    }

    private companion object {
        const val LATEST_RELEASE_API = "https://api.github.com/repos/rurnima/Purnima/releases/latest"
    }
}
