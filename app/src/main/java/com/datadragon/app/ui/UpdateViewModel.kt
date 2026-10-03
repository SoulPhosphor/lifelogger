package com.datadragon.app.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class UpdateUiState(
    val status: String? = null,
    val working: Boolean = false,
)

internal data class AvailableUpdate(
    val buildNumber: Int,
    val apkUrl: String,
)

/** Checks GitHub Releases, downloads the newest APK, and hands it to Android's installer. */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val preferences = app.getSharedPreferences(PREFERENCES_NAME, 0)
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state

    val versionLine: String = "Version ${BuildConfig.VERSION_NAME} • Build ${BuildConfig.VERSION_CODE}"

    init {
        val pendingBuild = preferences.getInt(KEY_PENDING_BUILD, 0)
        if (pendingBuild > 0 && BuildConfig.VERSION_CODE >= pendingBuild) {
            preferences.edit().remove(KEY_PENDING_BUILD).apply()
            _state.value = UpdateUiState(status = "Update Installed Successfully")
        }
    }

    fun checkForUpdates() {
        if (_state.value.working) return

        viewModelScope.launch {
            _state.value = UpdateUiState(status = "Checking for Updates...", working = true)
            val update = runCatching { fetchNewestUpdate(BuildConfig.VERSION_CODE) }
                .getOrElse {
                    _state.value = UpdateUiState(status = "Unable to Check for Updates")
                    return@launch
                }

            if (update == null) {
                _state.value = UpdateUiState(status = "App Is Up to Date")
                return@launch
            }

            _state.value = UpdateUiState(status = "Downloading Updates...", working = true)
            val apk = runCatching { downloadUpdate(update) }
                .getOrElse {
                    _state.value = UpdateUiState(status = "Unable to Download Update")
                    return@launch
                }

            _state.value = UpdateUiState(status = "Installing Updates...", working = true)
            runCatching { openInstaller(apk, update.buildNumber) }
                .onSuccess {
                    // Android's package installer does not reliably report whether the
                    // user installed or cancelled. Re-enable the button immediately so
                    // returning from the installer can never strand this screen.
                    _state.value = UpdateUiState(status = "Installing Updates...")
                }
                .onFailure {
                    _state.value = UpdateUiState(status = "Unable to Start Installation")
                }
        }
    }

    private suspend fun fetchNewestUpdate(currentBuild: Int): AvailableUpdate? = withContext(Dispatchers.IO) {
        val connection = openConnection(RELEASES_URL)
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        val body = connection.readTextResponse()
        findNewestUpdate(body, currentBuild)
    }

    private suspend fun downloadUpdate(update: AvailableUpdate): File = withContext(Dispatchers.IO) {
        val directory = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
        val destination = File(directory, "data-dragon-build-${update.buildNumber}.apk")
        val connection = openConnection(update.apkUrl)
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            error("Update download failed")
        }
        connection.inputStream.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        connection.disconnect()
        destination
    }

    private fun openInstaller(apk: File, buildNumber: Int) {
        val app = getApplication<Application>()
        val uri = FileProvider.getUriForFile(app, "${BuildConfig.APPLICATION_ID}.fileprovider", apk)
        preferences.edit().putInt(KEY_PENDING_BUILD, buildNumber).apply()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        app.startActivity(intent)
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Data-Dragon-Android")
        }

    private fun HttpURLConnection.readTextResponse(): String {
        if (responseCode !in 200..299) {
            disconnect()
            error("GitHub release check failed")
        }
        return inputStream.bufferedReader().use { it.readText() }.also { disconnect() }
    }

    private companion object {
        const val RELEASES_URL =
            "https://api.github.com/repos/SoulPhosphor/lifelogger/releases?per_page=30"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val PREFERENCES_NAME = "app_updates"
        const val KEY_PENDING_BUILD = "pending_build"
    }
}

/** Includes both full and pre-releases, while ignoring drafts and releases without an APK. */
internal fun findNewestUpdate(releasesJson: String, currentBuild: Int): AvailableUpdate? {
    val releases = Json.parseToJsonElement(releasesJson).jsonArray
    return releases.mapNotNull { element ->
        val release = element.jsonObject
        val isDraft = release["draft"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
        if (isDraft) return@mapNotNull null

        val tag = release["tag_name"]?.jsonPrimitive?.content.orEmpty()
        val buildNumber = BUILD_TAG.matchEntire(tag)?.groupValues?.get(1)?.toIntOrNull()
            ?: return@mapNotNull null
        val apkUrl = release["assets"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { asset ->
                asset["name"]?.jsonPrimitive?.content?.endsWith(".apk", ignoreCase = true) == true
            }
            ?.get("browser_download_url")
            ?.jsonPrimitive
            ?.content
            ?: return@mapNotNull null

        AvailableUpdate(buildNumber, apkUrl)
    }.filter { it.buildNumber > currentBuild }
        .maxByOrNull { it.buildNumber }
}

private val BUILD_TAG = Regex("build-(\\d+)", RegexOption.IGNORE_CASE)
