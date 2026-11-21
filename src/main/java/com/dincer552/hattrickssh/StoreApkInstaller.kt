package com.dincer552.hattrickssh

import android.content.Context
import android.content.Intent
import android.net.Uri
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
    val target = File(directory, "install-${System.currentTimeMillis()}.apk")
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
    clipData = android.content.ClipData.newUri(null, "APK", uri)
}
