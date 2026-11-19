package com.dincer552.hattrickssh

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject

class StoreUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val manager = applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val prefs = applicationContext.getSharedPreferences(StoreUpdateNotifications.PREFS, Context.MODE_PRIVATE)
            val pending = JSONObject(prefs.getString(StoreUpdateNotifications.PENDING, "{}") ?: "{}")
            val apps = StoreCatalogManager.loadCatalog()
            
            var hasWork = false
            apps.forEach { app ->
                val release = app.release ?: return@forEach
                val installed = installedVersion(app.packageName) ?: return@forEach
                if (installed >= release.versionCode.toLong()) return@forEach
                val key = "${app.id}:${release.versionCode}"
                if (pending.has(key)) return@forEach
                
                // Check if already downloaded and ready
                if (StoreUpdateNotifications.isReady(applicationContext, app.id)) return@forEach
                
                val request = DownloadManager.Request(Uri.parse(release.downloadUrl))
                    .setTitle("${app.name} güncellemesi")
                    .setDescription("${release.versionName} arka planda indiriliyor")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalFilesDir(applicationContext, Environment.DIRECTORY_DOWNLOADS, "${app.id}-${release.versionCode}.apk")
                    .setRequiresCharging(false)
                    .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                
                val id = manager.enqueue(request)
                pending.put(key, JSONObject()
                    .put("downloadId", id)
                    .put("appId", app.id)
                    .put("name", app.name)
                    .put("packageName", app.packageName)
                    .put("versionCode", release.versionCode)
                    .put("versionName", release.versionName)
                    .put("sha256", release.sha256 ?: "")
                )
                hasWork = true
            }
            
            prefs.edit().putString(StoreUpdateNotifications.PENDING, pending.toString()).apply()
            
            // Clean up old pending entries (older than 24 hours)
            cleanupOldPending(prefs, pending)
            
            if (hasWork) {
                Result.success()
            } else {
                Result.success() // No updates needed is also success
            }
        } catch (e: Exception) {
            // Check if it's a rate limit error (HTTP 403/429)
            if (e.message?.contains("403") == true || e.message?.contains("429") == true || e.message?.contains("rate limit") == true) {
                // For rate limiting, retry with longer backoff
                Result.retry()
            } else {
                // For other errors, retry with exponential backoff (handled by WorkManager)
                Result.retry()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun installedVersion(packageName: String): Long? {
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                applicationContext.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else applicationContext.packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        } catch (_: Exception) { null }
    }

    private fun cleanupOldPending(prefs: android.content.SharedPreferences, pending: JSONObject) {
        // Remove entries older than 24 hours (we don't track timestamps, so we limit by count)
        if (pending.length() > 20) {
            val keys = pending.keys()
            val keysList = mutableListOf<String>()
            while (keys.hasNext()) {
                keysList.add(keys.next().toString())
            }
            // Keep only the most recent 20 entries
            if (keysList.size > 20) {
                keysList.take(keysList.size - 20).forEach { pending.remove(it) }
                prefs.edit().putString(StoreUpdateNotifications.PENDING, pending.toString()).apply()
            }
        }
    }

    companion object {
        const val WORK_NAME = "store_update_check"
        const val WORKER_TAG = "store_updates"

        fun enqueuePeriodicCheck(context: Context) {
            val workManager = WorkManager.getInstance(context)
            
            // Cancel any existing periodic work with same name
            workManager.cancelAllWorkByTag(WORKER_TAG)
            
            val request = PeriodicWorkRequest.Builder(StoreUpdateWorker::class.java, 6, java.util.concurrent.TimeUnit.HOURS)
                .addTag(WORKER_TAG)
                .setConstraints(androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.MINUTES)
                .build()
            
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun enqueueImmediateCheck(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val request = androidx.work.OneTimeWorkRequest.Builder(StoreUpdateWorker::class.java)
                .addTag(WORKER_TAG)
                .setConstraints(androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, java.util.concurrent.TimeUnit.MINUTES)
                .build()
            workManager.enqueue(request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORKER_TAG)
        }
    }
}