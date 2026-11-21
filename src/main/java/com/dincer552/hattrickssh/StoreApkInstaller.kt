package com.dincer552.hattrickssh

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** Stages DownloadManager content URIs behind the app's non-exported FileProvider. */
@Throws(IOException::class)
fun createInstallableApkUri(context: Context, source: Uri): Uri {
    val directory = File(context.cacheDir, "store-apks")
    if (!directory.exists() && !directory.mkdirs()) {
        throw IOException("APK önbellek klasörü oluşturulamadı.")
    }
    val target = File.createTempFile("install-", ".apk", directory)
    try {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("İndirilen APK okunamadı.")
        input.use { inputStream ->
            target.outputStream().use { output -> inputStream.copyTo(output) }
        }
        if (!target.isFile || target.length() <= 0L) {
            throw IOException("İndirilen APK boş veya geçersiz.")
        }
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", target)
    } catch (error: Exception) {
        target.delete()
        if (error is IOException) throw error
        throw IOException("APK kurulum için hazırlanamadı: ${error.message ?: error.javaClass.simpleName}", error)
    }
}

fun createApkInstallIntent(uri: Uri): Intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
    setDataAndType(uri, "application/vnd.android.package-archive")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    clipData = android.content.ClipData.newRawUri("APK", uri)
}

/** Parses the APK archive from a real local file and verifies its exact identity. */
fun validateApkIdentity(context: Context, source: Uri, expectedPackage: String, expectedVersionCode: Long): String? {
    val directory = File(context.cacheDir, "store-apks")
    if (!directory.exists() && !directory.mkdirs()) return "APK doğrulama klasörü oluşturulamadı."
    val temporary = File.createTempFile("verify-", ".apk", directory)
    return try {
        val input = context.contentResolver.openInputStream(source)
            ?: return "İndirilen APK okunamadı."
        input.use { stream -> temporary.outputStream().use { stream.copyTo(it) } }
        @Suppress("DEPRECATION")
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(temporary.absolutePath, PackageManager.PackageInfoFlags.of(0))
        } else {
            context.packageManager.getPackageArchiveInfo(temporary.absolutePath, 0)
        } ?: return "İndirilen dosya geçerli bir Android APK paketi değil."
        if (info.packageName != expectedPackage) {
            "Paket adı uyuşmuyor. Beklenen: $expectedPackage, bulunan: ${info.packageName}. Kurulum iptal edildi."
        } else {
            val actualVersion = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            if (actualVersion != expectedVersionCode) {
                "APK sürümü uyuşmuyor. Beklenen: $expectedVersionCode, bulunan: $actualVersion. Kurulum iptal edildi."
            } else null
        }
    } catch (error: Exception) {
        "APK kimliği doğrulanamadı: ${error.message ?: error.javaClass.simpleName}"
    } finally {
        temporary.delete()
    }
}
