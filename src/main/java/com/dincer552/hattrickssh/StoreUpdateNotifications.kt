package com.dincer552.hattrickssh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.DownloadManager
import org.json.JSONObject
import java.security.MessageDigest

object StoreUpdateNotifications {
    const val PREFS = "store_background_updates"
    const val PENDING = "pending_downloads"
    const val READY = "ready_downloads"
    const val CHANNEL = "app_updates"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Uygulama güncellemeleri", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun installUri(context: Context, appId: String): Uri? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ready = JSONObject(prefs.getString(READY, "{}") ?: "{}")
        val item = ready.optJSONObject(appId) ?: return null
        return Uri.parse(item.optString("uri").takeIf { it.isNotBlank() } ?: return null)
    }

    fun isReady(context: Context, appId: String): Boolean = installUri(context, appId) != null
}

class StoreDownloadCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id < 0) return
        val prefs = context.getSharedPreferences(StoreUpdateNotifications.PREFS, Context.MODE_PRIVATE)
        val pending = JSONObject(prefs.getString(StoreUpdateNotifications.PENDING, "{}") ?: "{}")
        val keys = pending.keys()
        var matchedKey: String? = null
        var item: JSONObject? = null
        while (keys.hasNext()) {
            val key = keys.next()
            val candidate = pending.optJSONObject(key) ?: continue
            if (candidate.optLong("downloadId") == id) { matchedKey = key; item = candidate; break }
        }
        val metadata = item ?: return
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return
        val uri = cursor.use {
            if (!it.moveToFirst() || it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) return
            manager.getUriForDownloadedFile(id)
        } ?: return
        val expectedPackage = metadata.optString("packageName")
        val expectedVersion = metadata.optLong("versionCode")
        val identityError = validateApkIdentity(context, uri, expectedPackage, expectedVersion)
        if (identityError != null) {
            pending.remove(matchedKey)
            prefs.edit().putString(StoreUpdateNotifications.PENDING, pending.toString()).apply()
            return
        }
        // The archive identity and optional SHA-256 are checked before publishing it as installable.
        val expectedSha = metadata.optString("sha256")
        if (expectedSha.isNotBlank()) {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            } ?: return
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualSha.equals(expectedSha, true)) return
        }
        val ready = JSONObject(prefs.getString(StoreUpdateNotifications.READY, "{}") ?: "{}")
        ready.put(metadata.optString("appId"), JSONObject(metadata.toString()).put("uri", uri.toString()))
        pending.remove(matchedKey)
        prefs.edit().putString(StoreUpdateNotifications.PENDING, pending.toString()).putString(StoreUpdateNotifications.READY, ready.toString()).apply()
        StoreUpdateNotifications.ensureChannel(context)
        val open = Intent(context, StoreActivity::class.java).putExtra("install_app_id", metadata.optString("appId"))
        val pi = PendingIntent.getActivity(context, id.toInt(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, StoreUpdateNotifications.CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("${metadata.optString("name")} güncellemesi hazır")
            .setContentText("${metadata.optString("versionName")} sürümünü yüklemek için dokun")
            .setContentIntent(pi).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        try { NotificationManagerCompat.from(context).notify(id.toInt(), notification) } catch (_: SecurityException) { }
    }
}