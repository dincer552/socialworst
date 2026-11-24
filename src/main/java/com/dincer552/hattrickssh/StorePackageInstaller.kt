package com.dincer552.hattrickssh

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

@Suppress("DEPRECATION")
internal fun readInstalledVersionCode(context: Context, packageName: String): Long? = try {
    val info = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        context.packageManager.getPackageInfo(packageName, 0)
    }
    if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
} catch (_: PackageManager.NameNotFoundException) {
    null
}

internal fun verifyInstalledVersionMatches(expected: Long?, actual: Long?): Boolean =
    expected != null && actual != null && expected == actual

/**
 * Starts Android's regular APK installation screen.
 *
 * Deliberately does not create a PackageInstaller session, request install
 * status callbacks, or attempt a silent install. Android displays its own
 * confirmation UI and the user taps Install/Update to approve the operation.
 */
fun launchStoreInstallOrUpdate(
    context: Context,
    apkUri: Uri,
    packageName: String,
    expectedVersionCode: Long? = null
): String? {
    StoreUpdateTrace.event(
        context,
        stage = "install",
        status = "launching_system_ui",
        message = "Opening Android installer; waiting for user confirmation",
        extra = mapOf(
            "packageName" to packageName,
            "expectedVersion" to expectedVersionCode,
            "silentAttempted" to false
        )
    )

    val error = launchApkInstaller(context, apkUri)
    if (error == null) {
        StoreUpdateTrace.event(
            context,
            stage = "install",
            status = "awaiting_user_confirmation",
            message = "Android installer opened; user must approve installation",
            extra = mapOf("packageName" to packageName, "silentAttempted" to false)
        )
    } else {
        StoreUpdateTrace.event(
            context,
            stage = "install",
            status = "error",
            message = error,
            extra = mapOf("packageName" to packageName, "silentAttempted" to false)
        )
    }
    return error
}
