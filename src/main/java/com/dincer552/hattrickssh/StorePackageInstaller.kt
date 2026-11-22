package com.dincer552.hattrickssh

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.io.IOException

private const val TAG = "StorePackageInstaller"
private const val PREFS = "store_package_installer"
private const val KEY_PENDING_SESSIONS = "pending_sessions"

/**
 * Returns true when the platform API level supports PackageInstaller silent-update
 * attempts. Ownership and target-SDK rules are enforced by the OS at commit time;
 * this helper only gates the attempt itself.
 */
internal fun shouldAttemptPackageInstallerUpdate(platformApi: Int, isInstalled: Boolean): Boolean =
    platformApi >= 31 && isInstalled

@Suppress("DEPRECATION")
private fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
    if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        context.packageManager.getPackageInfo(packageName, 0)
    }
    true
} catch (_: Exception) {
    false
}

/**
 * True when this process is allowed by the platform to request a silent update:
 * - self-update, or
 * - we are the recorded update owner / installer-of-record for an already installed package.
 *
 * On Android 14+ the OS still applies targetSdk thresholds; a false return means we
 * must use the interactive confirmation path.
 */
internal fun canRequestSilentUpdate(context: Context, packageName: String): Boolean {
    if (packageName == context.packageName) return true
    if (!isPackageInstalled(context, packageName)) return false
    if (Build.VERSION.SDK_INT < 30) return false
    return try {
        val info: InstallSourceInfo = context.packageManager.getInstallSourceInfo(packageName)
        val store = context.packageName
        val updateOwner = info.updateOwnerPackageName
        val installing = info.installingPackageName
        val initiating = info.initiatingPackageName
        Log.i(
            TAG,
            "InstallSource for $packageName: updateOwner=$updateOwner installing=$installing initiating=$initiating"
        )
        updateOwner == store || installing == store || initiating == store
    } catch (error: Exception) {
        Log.w(TAG, "InstallSourceInfo unavailable for $packageName: ${error.message}")
        false
    }
}

/**
 * Single entry point for install and update.
 *
 * - API < 31: classic ACTION_INSTALL_PACKAGE UI.
 * - API >= 31: PackageInstaller session. USER_ACTION_NOT_REQUIRED is set only when
 *   canRequestSilentUpdate() is true; otherwise the OS returns STATUS_PENDING_USER_ACTION
 *   and the receiver launches the confirmation Intent.
 *
 * Returns null on successful session start (or classic intent launch), otherwise an
 * error message for the UI.
 */
fun launchStoreInstallOrUpdate(context: Context, apkUri: Uri, packageName: String): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return launchApkInstaller(context, apkUri)
    }

    val installed = isPackageInstalled(context, packageName)
    val silentEligible = installed && canRequestSilentUpdate(context, packageName)

    return try {
        startPackageInstallerSession(
            context = context,
            apkUri = apkUri,
            packageName = packageName,
            requireUserAction = !silentEligible
        )
        if (silentEligible) {
            Log.i(TAG, "Silent update session started for $packageName (ownership confirmed)")
        } else {
            Log.i(
                TAG,
                "Interactive PackageInstaller session started for $packageName " +
                    "(installed=$installed, ownership=${canRequestSilentUpdate(context, packageName)})"
            )
        }
        null
    } catch (error: Exception) {
        Log.e(TAG, "PackageInstaller session failed, falling back to classic installer", error)
        val fallbackError = launchApkInstaller(context, apkUri)
        if (fallbackError == null) null
        else "Otomatik kurulum başlatılamadı: ${error.message ?: error.javaClass.simpleName}. $fallbackError"
    }
}

private fun startPackageInstallerSession(
    context: Context,
    apkUri: Uri,
    packageName: String,
    requireUserAction: Boolean
) {
    val installer = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRequireUserAction(
                if (requireUserAction) {
                    PackageInstaller.SessionParams.USER_ACTION_REQUIRED
                } else {
                    PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                }
            )
        }
    }
    val sessionId = installer.createSession(params)
    var committed = false

    try {
        installer.openSession(sessionId).use { session ->
            val input = context.contentResolver.openInputStream(apkUri)
                ?: throw IOException("Doğrulanmış APK artık okunamıyor.")
            input.use { source ->
                session.openWrite("base.apk", 0L, -1L).use { destination ->
                    source.copyTo(destination)
                    session.fsync(destination)
                }
            }

            rememberPendingSession(context, sessionId, packageName, apkUri)

            val callback = Intent(context, StorePackageInstallReceiver::class.java)
                .setAction(StorePackageInstallReceiver.ACTION_INSTALL_STATUS)
                .putExtra(StorePackageInstallReceiver.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(StorePackageInstallReceiver.EXTRA_APK_URI, apkUri.toString())
                .putExtra(StorePackageInstallReceiver.EXTRA_SESSION_ID, sessionId)
                .putExtra(StorePackageInstallReceiver.EXTRA_SILENT_ATTEMPTED, !requireUserAction)
            val statusIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(statusIntent.intentSender)
            committed = true
        }
    } catch (error: Exception) {
        if (!committed) {
            try {
                installer.abandonSession(sessionId)
            } catch (_: Exception) {
                // Preserve the original installation error.
            }
            clearPendingSession(context, sessionId)
        }
        throw error
    }
}

private fun rememberPendingSession(context: Context, sessionId: Int, packageName: String, apkUri: Uri) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_PENDING_SESSIONS, "{}") ?: "{}"
    try {
        val json = org.json.JSONObject(raw)
        json.put(
            sessionId.toString(),
            org.json.JSONObject()
                .put("packageName", packageName)
                .put("apkUri", apkUri.toString())
                .put("createdAt", System.currentTimeMillis())
        )
        prefs.edit().putString(KEY_PENDING_SESSIONS, json.toString()).apply()
    } catch (error: Exception) {
        Log.w(TAG, "Could not persist pending session $sessionId", error)
    }
}

private fun clearPendingSession(context: Context, sessionId: Int) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_PENDING_SESSIONS, "{}") ?: return
    try {
        val json = org.json.JSONObject(raw)
        json.remove(sessionId.toString())
        prefs.edit().putString(KEY_PENDING_SESSIONS, json.toString()).apply()
    } catch (_: Exception) {
        // Best-effort cleanup.
    }
}

/** Receives the final PackageInstaller result and opens Android's approval UI when required. */
class StorePackageInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        val silentAttempted = intent.getBooleanExtra(EXTRA_SILENT_ATTEMPTED, false)

        if (sessionId >= 0) clearPendingSession(context, sessionId)

        Log.i(
            TAG,
            "Install result for $packageName session=$sessionId status=$status " +
                "silentAttempted=$silentAttempted message=$message"
        )

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = extractConfirmationIntent(intent)
                if (confirmation != null) {
                    try {
                        confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(confirmation)
                        val note = if (silentAttempted) {
                            "Android sessiz güncellemeyi reddetti (ownership veya targetSdk). Onay ekranı açıldı."
                        } else {
                            "Kurulum onayı gerekli."
                        }
                        Toast.makeText(context, note, Toast.LENGTH_LONG).show()
                    } catch (error: Exception) {
                        val fallbackError = openInteractiveFallback(context, intent)
                        val detail = fallbackError ?: "Android'in normal kurulum ekranı açıldı."
                        Toast.makeText(
                            context,
                            "Onay ekranı doğrudan açılamadı (${error.javaClass.simpleName}). $detail",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    val fallbackError = openInteractiveFallback(context, intent)
                    val detail = fallbackError ?: "Android'in normal kurulum ekranı açıldı."
                    Toast.makeText(context, "Kurulum onayı gerekli. $detail", Toast.LENGTH_LONG).show()
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                val text = if (silentAttempted) {
                    "Uygulama sessizce güncellendi."
                } else {
                    "Uygulama başarıyla kuruldu / güncellendi."
                }
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                context.sendBroadcast(Intent(ACTION_INSTALL_FINISHED).setPackage(context.packageName))
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Toast.makeText(context, "Kurulum iptal edildi.", Toast.LENGTH_SHORT).show()
                context.sendBroadcast(Intent(ACTION_INSTALL_FINISHED).setPackage(context.packageName))
            }

            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                Toast.makeText(
                    context,
                    "Kurulum çakışması (imza veya paket uyuşmazlığı). Kod $status.",
                    Toast.LENGTH_LONG
                ).show()
                context.sendBroadcast(Intent(ACTION_INSTALL_FINISHED).setPackage(context.packageName))
            }

            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                Toast.makeText(
                    context,
                    "APK bu cihazla uyumlu değil (ABI veya minimum SDK).",
                    Toast.LENGTH_LONG
                ).show()
                context.sendBroadcast(Intent(ACTION_INSTALL_FINISHED).setPackage(context.packageName))
            }

            PackageInstaller.STATUS_FAILURE_INVALID -> {
                Toast.makeText(
                    context,
                    "Geçersiz APK veya bozuk paket.",
                    Toast.LENGTH_LONG
                ).show()
                context.sendBroadcast(Intent(ACTION_INSTALL_FINISHED).setPackage(context.packageName))
            }

            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                Toast.makeText(context, "Yetersiz depolama alanı.", Toast.LENGTH_LONG).show()
                context.sendBroadcast(Intent(ACTION_INSTALL_FINISHED).setPackage(context.packageName))
            }

            else -> {
                val detail = message.ifBlank { "Android kurulum hatası (kod $status)." }
                Toast.makeText(context, "Kurulum tamamlanamadı: $detail", Toast.LENGTH_LONG).show()
                context.sendBroadcast(Intent(ACTION_INSTALL_FINISHED).setPackage(context.packageName))
            }
        }
    }

    private fun openInteractiveFallback(context: Context, source: Intent): String? {
        val uri = source.getStringExtra(EXTRA_APK_URI)?.let(Uri::parse)
            ?: return "Doğrulanmış APK adresi bulunamadı."
        return launchApkInstaller(context, uri)
    }

    @Suppress("DEPRECATION")
    private fun extractConfirmationIntent(source: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) {
            source.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            source.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
        }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.dincer552.hattrickssh.PACKAGE_INSTALL_STATUS"
        const val ACTION_INSTALL_FINISHED = "com.dincer552.hattrickssh.PACKAGE_INSTALL_FINISHED"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APK_URI = "apk_uri"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_SILENT_ATTEMPTED = "silent_attempted"
    }
}
