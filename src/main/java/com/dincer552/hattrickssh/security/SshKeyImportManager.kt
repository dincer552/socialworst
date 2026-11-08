package com.dincer552.hattrickssh.security

import android.content.Context
import android.net.Uri

/** Reads a selected key once and immediately encrypts it into app-private storage. */
class SshKeyImportManager(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val secureStorage = SecureKeyStorage(context.applicationContext)

    fun importFromUri(uri: Uri, profileId: String): Result<Unit> {
        val keyBytes = try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.failure(IllegalStateException("Dosya okunamadı."))
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("SSH anahtar dosyası okunamadı.", e))
        }

        return try {
            if (keyBytes.isEmpty() || keyBytes.size > MAX_KEY_SIZE_BYTES) {
                Result.failure(IllegalArgumentException("SSH anahtar dosyası boş veya çok büyük."))
            } else {
                val header = keyBytes.copyOfRange(0, minOf(keyBytes.size, 128))
                    .toString(Charsets.US_ASCII)
                val footer = keyBytes.copyOfRange(maxOf(0, keyBytes.size - 96), keyBytes.size)
                    .toString(Charsets.US_ASCII)
                val validPem = header.contains("-----BEGIN") &&
                    header.contains("PRIVATE KEY-----") &&
                    footer.contains("-----END")
                when {
                    !validPem -> Result.failure(
                        IllegalArgumentException("Dosya geçerli bir PEM/OpenSSH private key değil.")
                    )
                    else -> secureStorage.storePrivateKeyResult(profileId, keyBytes)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            keyBytes.fill(0)
        }
    }

    fun hasPrivateKey(profileId: String): Boolean = secureStorage.hasPrivateKey(profileId)

    companion object {
        private const val MAX_KEY_SIZE_BYTES = 1024 * 1024
    }
}
