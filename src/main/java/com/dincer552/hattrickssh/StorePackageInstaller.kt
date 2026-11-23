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
 * Always opens Android's standard APK installer.
 *
 * No PackageInstaller session is created and no silent installation is attempted.
 * The user must review and confirm installation in Android's own installer UI.
 */
@Suppress("UNUSED_PARAMETER")
fun launchStoreInstallOrUpdate(
    context: Context,
    apkUri: Uri,
    packageName: String,
    expectedVersionCode: Long? = null
): String? = launchApkInstaller(context, apkUri)
