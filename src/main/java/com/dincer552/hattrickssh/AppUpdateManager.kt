package com.dincer552.hattrickssh

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseUpdate(
    val tagName: String,
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

data class DownloadSnapshot(
    val status: Int,
    val progressPercent: Int
)

object AppUpdateManager {
    private const val RELEASE_API =
        "https://api.github.com/repos/dincer552/socialworst/releases/latest"
    private const val APK_ASSET_NAME = "hattrick-ssh.apk"

    suspend fun checkForUpdate(): ReleaseUpdate? = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Hattrick-SSH-Android")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            if (responseCode !in 200..299) {
                throw IOException("GitHub güncelleme sunucusu HTTP $responseCode döndürdü.")
            }

            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = release.optString("tag_name")
            val remoteVersionCode = tag.substringAfterLast('.').toIntOrNull()
                ?: throw IOException("Release sürüm etiketi okunamadı: $tag")

            if (remoteVersionCode <= BuildConfig.VERSION_CODE) return@withContext null

            val assets = release.optJSONArray("assets")
                ?: throw IOException("Release içinde APK dosyası bulunamadı.")
            var apkUrl: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                if (asset.optString("name") == APK_ASSET_NAME) {
                    apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                    break
                }
            }

            ReleaseUpdate(
                tagName = tag,
                versionCode = remoteVersionCode,
                versionName = tag.removePrefix("v"),
                apkUrl = apkUrl ?: throw IOException("Release APK bağlantısı bulunamadı."),
                releaseNotes = release.optString("body")
            )
        } finally {
            connection.disconnect()
        }
    }

    fun enqueueDownload(context: Context, update: ReleaseUpdate): Long {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("Hattrick SSH güncelleniyor")
            .setDescription("Sürüm ${update.versionName} indiriliyor")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "hattrick-ssh-${update.versionCode}.apk"
            )
        return manager.enqueue(request)
    }

    fun getDownloadSnapshot(context: Context, downloadId: Long): DownloadSnapshot? {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId)) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(
                it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val percent = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
            DownloadSnapshot(status, percent)
        }
    }

    fun getDownloadedUri(context: Context, downloadId: Long): Uri? {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.getUriForDownloadedFile(downloadId)
    }
}
