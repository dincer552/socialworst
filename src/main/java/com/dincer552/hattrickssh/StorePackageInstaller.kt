package com.dincer552.hattrickssh

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.widget.Toast
import java.io.IOException

/**
 * The OS only permits user-action-free updates when both the platform's target-SDK
 * threshold and installer/update-owner rules are satisfied. The installer still
 * handles ownership enforcement and reports STATUS_PENDING_USER_ACTION otherwise.
 */
internal fun minimumTargetSdkForNoUserActionUpdate(platformApi: Int): Int? = when {
    platformApi >= 37 -> 35
    platformApi >= 36 -> 34
    platformApi >= 35 -> 33
    platformApi >= 34 -> 31
    platformApi >= 33 -> 30
    platformApi >= 31 -> 29
    else -> null
}

internal fun supportsNoUserActionUpdateTarget(platformApi: Int, installedTargetSdk: Int): Boolean {
    val minimum = minimumTargetSdkForNoUserActionUpdate(platformApi) ?: return false
    return installedTargetSdk >= minimum
}

@Suppress("DEPRECATION")
private fun installedTargetSdk(context: Context, packageName: String): Int = try {
    val info = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
            packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        context.packageManager.getPackageInfo(packageName, 0)
    }
    info.applicationInfo?.targetSdkVersion ?: 0
} catch (_: Exception) {
    0
}

/**
 * Updates an installed package through PackageInstaller when the Android version
 * and the installed app's target SDK allow the API. The OS decides whether the
 * store is the installer/update owner; if not, it sends STATUS_PENDING_USER_ACTION.
 * New installs and unsupported platforms use the regular Android installer UI.
 */
fun launchStoreInstallOrUpdate(context: Context, apkUri: Uri, packageName: String): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return launchApkInstaller(context, apkUri)
    }

    val installedTargetSdk = installedTargetSdk(context, packageName)

    if (!supportsNoUserActionUpdateTarget(Build.VERSION.SDK_INT, installedTargetSdk)) {
        return launchApkInstaller(context, apkUri)
    }

    return try {
        startPackageInstallerUpdateSession(context, apkUri, packageName)
        null
    } catch (error: Exception) {
        val fallbackError = launchApkInstaller(context, apkUri)
        if (fallbackError == null) null
        else "Otomatik güncelleme başlatılamadı: ${error.message ?: error.javaClass.simpleName}. $fallbackError"
    }
}

private fun startPackageInstallerUpdateSession(context: Context, apkUri: Uri, packageName: String) {
    val installer = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(packageName)
        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
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

            val callback = Intent(context, StorePackageInstallReceiver::class.java)
                .setAction(StorePackageInstallReceiver.ACTION_INSTALL_STATUS)
                .putExtra(StorePackageInstallReceiver.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(StorePackageInstallReceiver.EXTRA_APK_URI, apkUri.toString())
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
        }
        throw error
    }
}

/** Receives the final PackageInstaller result and opens Android's approval UI when required. */
class StorePackageInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = extractConfirmationIntent(intent)
                if (confirmation != null) {
                    try {
                        confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(confirmation)
                    } catch (error: Exception) {
                        val fallbackError = openInteractiveFallback(context, intent)
                        val detail = fallbackError ?: "Android'in normal kurulum ekranı açıldı."
                        Toast.makeText(
                            context,
                            "Onay ekranı doğrudan açılamadı (\${error.javaClass.simpleName}). $detail",
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
                Toast.makeText(context, "Uygulama başarıyla güncellendi.", Toast.LENGTH_SHORT).show()
                context.sendBroadcast(Intent(ACTION_INSTALL_FINISHED).setPackage(context.packageName))
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Toast.makeText(context, "Uygulama kurulumu iptal edildi.", Toast.LENGTH_SHORT).show()
                context.sendBroadcast(Intent(ACTION_INSTALL_FINISHED).setPackage(context.packageName))
            }

            else -> {
                val detail = message.ifBlank { "Android kurulum hatası (kod $status)." }
                Toast.makeText(context, "Otomatik güncelleme tamamlanamadı: $detail", Toast.LENGTH_LONG).show()
                context.sendBroadcast(Intent(ACTION_INSTALL_FINISHED).setPackage(context.packageName))
            }
        }
    }

    private fun openInteractiveFallback(context: Context, source: Intent): String? {
        val uri = source.getStringExtra(EXTRA_APK_URI)?.let(Uri::parse) ?: return "Doğrulanmış APK adresi bulunamadı."
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
    }
}
