package com.dincer552.hattrickssh

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Exact app-specific file used by DownloadManager's
 * setDestinationInExternalFilesDir(..., DIRECTORY_DOWNLOADS, filename).
 * Read this local file directly; never open DownloadProvider content:// URIs.
 */
@Throws(IOException::class)
fun storeDownloadFile(context: Context, appId: String, versionCode: Long): File {
    val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: throw IOException("Uygulama indirme klasörüne erişilemiyor.")
    val safeId = appId.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return File(directory, "$safeId-$versionCode.apk")
}

/** Stages an already downloaded app-private file behind our FileProvider. */
@Throws(IOException::class)
fun createInstallableApkUri(context: Context, source: File): Uri {
    if (!source.isFile || source.length() <= 0L) {
        throw IOException("İndirilen APK dosyası bulunamadı veya boş: ${source.name}")
    }
    return stageInstallableApk(context) { FileInputStream(source) }
}

/**
 * Compatibility path for older saved entries that already point to a
 * readable URI (for example, our own FileProvider URI).
 * New downloads must use the File overload above.
 */
@Throws(IOException::class)
fun createInstallableApkUri(context: Context, source: Uri): Uri =
    stageInstallableApk(context) {
        context.contentResolver.openInputStream(source)
            ?: throw IOException("İndirilen APK okunamadı.")
    }

@Throws(IOException::class)
private fun stageInstallableApk(context: Context, openInput: () -> InputStream): Uri {
    val directory = File(context.cacheDir, "store-apks")
    if (!directory.exists() && !directory.mkdirs()) {
        throw IOException("APK önbellek klasörü oluşturulamadı.")
    }
    val target = File.createTempFile("install-", ".apk", directory)
    try {
        openInput().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
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

/**
 * Parses the APK archive from a real local file and verifies identity:
 * package name, exact catalog version, not older than installed, and
 * matching signing certificates when the package is already on device.
 */
fun validateApkIdentity(
    context: Context,
    source: Uri,
    expectedPackage: String,
    expectedVersionCode: Long
): String? {
    val directory = File(context.cacheDir, "store-apks")
    if (!directory.exists() && !directory.mkdirs()) return "APK doğrulama klasörü oluşturulamadı."
    val temporary = File.createTempFile("verify-", ".apk", directory)
    return try {
        val input = context.contentResolver.openInputStream(source)
            ?: return "İndirilen APK okunamadı."
        input.use { stream -> temporary.outputStream().use { stream.copyTo(it) } }

        val archiveFlags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        @Suppress("DEPRECATION")
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(
                temporary.absolutePath,
                PackageManager.PackageInfoFlags.of(archiveFlags.toLong())
            )
        } else {
            context.packageManager.getPackageArchiveInfo(temporary.absolutePath, archiveFlags)
        } ?: return "İndirilen dosya geçerli bir Android APK paketi değil."

        if (info.packageName != expectedPackage) {
            return "Paket adı uyuşmuyor. Beklenen: $expectedPackage, bulunan: ${info.packageName}. Kurulum iptal edildi."
        }

        val actualVersion = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        if (actualVersion != expectedVersionCode) {
            return "APK sürümü uyuşmuyor. Beklenen: $expectedVersionCode, bulunan: $actualVersion. Kurulum iptal edildi."
        }

        val installedVersion = getInstalledVersionCodeOrNull(context, expectedPackage)
        if (installedVersion != null && actualVersion < installedVersion) {
            return "Daha eski sürüm yüklenemez. Kurulu: $installedVersion, APK: $actualVersion."
        }

        if (installedVersion != null) {
            val installedSigs = installedSigningDigests(context, expectedPackage)
            val apkSigs = archiveSigningDigests(info)
            if (installedSigs.isNotEmpty() && apkSigs.isNotEmpty()) {
                val match = apkSigs.any { it in installedSigs }
                if (!match) {
                    return "İmza uyuşmazlığı. Yüklü uygulamanın imzası ile APK imzası farklı. Kurulum iptal edildi."
                }
            }
        }

        null
    } catch (error: Exception) {
        "APK kimliği doğrulanamadı: ${error.message ?: error.javaClass.simpleName}"
    } finally {
        temporary.delete()
    }
}

@Suppress("DEPRECATION")
private fun getInstalledVersionCodeOrNull(context: Context, packageName: String): Long? = try {
    val info = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        context.packageManager.getPackageInfo(packageName, 0)
    }
    if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
} catch (_: PackageManager.NameNotFoundException) {
    null
}

@Suppress("DEPRECATION")
private fun installedSigningDigests(context: Context, packageName: String): Set<String> = try {
    val flags = if (Build.VERSION.SDK_INT >= 28) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
    val info = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(flags.toLong())
        )
    } else {
        context.packageManager.getPackageInfo(packageName, flags)
    }
    signaturesFromPackageInfo(info).map { sha256Hex(it.toByteArray()) }.toSet()
} catch (_: Exception) {
    emptySet()
}

@Suppress("DEPRECATION")
private fun archiveSigningDigests(info: android.content.pm.PackageInfo): Set<String> =
    signaturesFromPackageInfo(info).map { sha256Hex(it.toByteArray()) }.toSet()

@Suppress("DEPRECATION")
private fun signaturesFromPackageInfo(info: android.content.pm.PackageInfo): List<Signature> {
    if (Build.VERSION.SDK_INT >= 28) {
        val signingInfo = info.signingInfo ?: return emptyList()
        val signers = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
        return signers?.toList().orEmpty()
    }
    return info.signatures?.toList().orEmpty()
}

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}
