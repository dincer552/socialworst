package com.dincer552.hattrickssh

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SignerSettings {
    private const val PREFS = "central_signer_settings"
    private const val KEY_ALIAS = "my_app_store_signer_token_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun baseUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("base_url", "") ?: ""

    fun adminToken(context: Context): String {
        val encrypted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("admin_token", null) ?: return ""
        return try {
            val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            val ciphertext = bytes.copyOfRange(12, bytes.size)
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                String(doFinal(ciphertext), Charsets.UTF_8)
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun save(context: Context, url: String, token: String) {
        require(url.startsWith("https://", ignoreCase = true)) { "İmza servisi adresi HTTPS olmalı." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("base_url", url.trim().trimEnd('/'))
            .putString("admin_token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
            generateKey()
        }
    }
}

object SignerKeyUploader {
    suspend fun upload(
        context: Context,
        baseUrl: String,
        adminToken: String,
        keyId: String,
        packageName: String,
        repository: String,
        keyAlias: String,
        keyPassword: String,
        keystoreUri: Uri
    ): String = withContext(Dispatchers.IO) {
        require(baseUrl.startsWith("https://", ignoreCase = true)) {
            "İmza servisi adresi HTTPS olmalı."
        }
        require(keyId.matches(Regex("[a-z0-9][a-z0-9._-]{1,63}"))) { "Key ID geçersiz." }
        require(adminToken.isNotBlank() && keyAlias.isNotBlank() && keyPassword.isNotBlank()) {
            "Yönetici token'ı, alias ve parola gerekli."
        }
        val bytes = context.contentResolver.openInputStream(keystoreUri)?.use { it.readBytes() }
            ?: throw IOException("JKS dosyası okunamadı.")
        require(bytes.isNotEmpty() && bytes.size <= 150 * 1024 * 1024) { "JKS dosya boyutu geçersiz." }

        val boundary = "----MyAppStore-${UUID.randomUUID()}"
        val url = URL("${baseUrl.trimEnd('/')}/v1/admin/keys/$keyId")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $adminToken")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        fun writeField(output: java.io.OutputStream, name: String, value: String) {
            output.write("--$boundary\r\n".toByteArray())
            output.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
            output.write(value.toByteArray(Charsets.UTF_8))
            output.write("\r\n".toByteArray())
        }
        try {
            connection.outputStream.use { output ->
                writeField(output, "package_name", packageName)
                writeField(output, "repository", repository)
                writeField(output, "key_alias", keyAlias)
                writeField(output, "key_password", keyPassword)
                output.write("--$boundary\r\n".toByteArray())
                output.write("Content-Disposition: form-data; name=\"keystore\"; filename=\"signing-key.jks\"\r\n".toByteArray())
                output.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
                output.write(bytes)
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("İmza servisi HTTP $code: $body")
            body
        } finally {
            connection.disconnect()
        }
    }

    suspend fun registerRepository(
        baseUrl: String,
        adminToken: String,
        keyId: String,
        repositoryUrl: String
    ): String = withContext(Dispatchers.IO) {
        require(baseUrl.startsWith("https://", ignoreCase = true)) {
            "İmza servisi adresi HTTPS olmalı."
        }
        require(adminToken.isNotBlank()) { "Yönetici token gerekli." }
        require(keyId.matches(Regex("[a-z0-9][a-z0-9._-]{1,63}"))) { "Key ID geçersiz." }
        require(repositoryUrl.isNotBlank()) { "GitHub repo linki gerekli." }

        val boundary = "----MyAppStore-${UUID.randomUUID()}"
        val connection = (URL("${baseUrl.trimEnd('/')}/v1/admin/repositories").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $adminToken")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { output ->
                fun writeField(name: String, value: String) {
                    output.write("--$boundary\\r\\n".toByteArray())
                    output.write("Content-Disposition: form-data; name=\\"$name\\"\\r\\n\\r\\n".toByteArray())
                    output.write(value.toByteArray(Charsets.UTF_8))
                    output.write("\\r\\n".toByteArray())
                }
                writeField("repository", repositoryUrl.trim())
                writeField("key_id", keyId.trim())
                output.write("--$boundary--\\r\\n".toByteArray())
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("Repo bağlanamadı (HTTP $code): $body")
            body
        } finally {
            connection.disconnect()
        }
    }

}
