package com.dincer552.hattrickssh

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseUpdate(val versionCode: Int, val versionName: String, val apkUrl: String)
data class DownloadSnapshot(val status: Int, val progressPercent: Int)

/** GitHub Release updater for user-approved signed APK installations. */
object AppUpdateManager {
    private const val RELEASE_API = "https://api.github.com/repos/dincer552/socialworst/releases/latest"
    private const val APK_ASSET_NAME = "ssh-client.apk"

    suspend fun checkForUpdate(): ReleaseUpdate? = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "SSH-Client-Android")
        }
        try {
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> return@withContext null
                in 200..299 -> Unit
                else -> throw IOException("GitHub update server returned HTTP $status")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = release.optString("tag_name")
            val versionCode = tag.substringAfterLast('.').toIntOrNull()
                ?: throw IOException("Could not read release version: $tag")
            if (versionCode <= BuildConfig.VERSION_CODE) return@withContext null
            val assets = release.optJSONArray("assets") ?: throw IOException("Release APK is missing")
            val assetUrl = (0 until assets.length()).asSequence()
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == APK_ASSET_NAME }
                ?.optString("browser_download_url")
                ?.takeIf { it.startsWith("https://") }
                ?: throw IOException("Release APK download URL is missing")
            ReleaseUpdate(versionCode, tag.removePrefix("v"), assetUrl)
        } finally {
            connection.disconnect()
        }
    }

    fun enqueueDownload(context: Context, update: ReleaseUpdate): Long {
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("SSH Client update")
            .setDescription("Downloading version ${update.versionName}")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "ssh-client-${update.versionCode}.apk")
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    fun getDownloadSnapshot(context: Context, id: Long): DownloadSnapshot? {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val current = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            DownloadSnapshot(status, if (total > 0) (current * 100 / total).toInt().coerceIn(0, 100) else 0)
        }
    }

    fun getDownloadedUri(context: Context, id: Long): Uri? =
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).getUriForDownloadedFile(id)
}
