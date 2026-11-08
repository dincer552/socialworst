package com.dincer552.hattrickssh.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.dincer552.hattrickssh.Constants
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts SSH private keys and known_hosts with a non-exportable Android Keystore key. */
class SecureKeyStorage(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, Constants.SSH_KEY_DIR).apply {
            if (!exists() && !mkdirs()) error("Unable to create secure key directory")
        }

    private fun keyStore() = KeyStore.getInstance(Constants.KEYSTORE_PROVIDER).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val store = keyStore()
        if (store.containsAlias(Constants.KEYSTORE_ALIAS)) {
            return store.getKey(Constants.KEYSTORE_ALIAS, null) as SecretKey
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            Constants.KEYSTORE_PROVIDER
        )
        val strongBoxAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        if (strongBoxAvailable) {
            try {
                generator.init(keySpec(useStrongBox = true))
                return generator.generateKey()
            } catch (_: Exception) {
                try { keyStore().deleteEntry(Constants.KEYSTORE_ALIAS) } catch (_: Exception) {}
            }
        }

        generator.init(keySpec(useStrongBox = false))
        return generator.generateKey()
    }

    private fun keySpec(useStrongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            Constants.KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)

        if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }

    private fun file(name: String) = File(directory, name)

    private fun profileKeyFile(profileId: String): String {
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}"))) { "Invalid profile identifier" }
        return "profile-$profileId.key.enc"
    }

    @Synchronized
    private fun store(name: String, cleartext: ByteArray): Boolean = try {
        require(cleartext.isNotEmpty())
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(cleartext)
        val bytes = ByteBuffer.allocate(iv.size + ciphertext.size).put(iv).put(ciphertext).array()

        val target = file(name)
        val temp = File(directory, "$name.tmp")
        temp.outputStream().use { it.write(bytes) }
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Atomic credential save failed" }
        }
        true
    } catch (_: Exception) {
        false
    }

    @Synchronized
    private fun load(name: String): ByteArray? {
        return try {
            val source = file(name)
            if (!source.isFile || source.length() < 29) {
                null
            } else {
                val bytes = source.readBytes()
                val iv = bytes.copyOfRange(0, 12)
                val ciphertext = bytes.copyOfRange(12, bytes.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                cipher.doFinal(ciphertext)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun storePrivateKey(keyBytes: ByteArray) = store(Constants.SSH_KEY_FILENAME, keyBytes)
    fun loadPrivateKey() = load(Constants.SSH_KEY_FILENAME)
    fun hasPrivateKey() = file(Constants.SSH_KEY_FILENAME).let { it.isFile && it.length() > 28 }
    fun deletePrivateKey() = !file(Constants.SSH_KEY_FILENAME).exists() || file(Constants.SSH_KEY_FILENAME).delete()

    fun storeKnownHosts(bytes: ByteArray) = store(Constants.KNOWN_HOSTS_FILENAME, bytes)
    fun loadKnownHosts() = load(Constants.KNOWN_HOSTS_FILENAME)
    fun deleteKnownHosts() = !file(Constants.KNOWN_HOSTS_FILENAME).exists() || file(Constants.KNOWN_HOSTS_FILENAME).delete()

    fun storePrivateKey(profileId: String, keyBytes: ByteArray) = store(profileKeyFile(profileId), keyBytes)
    fun loadPrivateKey(profileId: String) = load(profileKeyFile(profileId))
    fun hasPrivateKey(profileId: String) = file(profileKeyFile(profileId)).let { it.isFile && it.length() > 28 }
    fun deletePrivateKey(profileId: String) =
        !file(profileKeyFile(profileId)).exists() || file(profileKeyFile(profileId)).delete()

    fun storeEncryptedProfileData(bytes: ByteArray): Boolean = try {
        store("profiles.enc", bytes)
    } finally {
        bytes.fill(0)
    }
    fun loadEncryptedProfileData() = load("profiles.enc")
}
