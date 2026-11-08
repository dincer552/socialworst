package com.dincer552.hattrickssh.ssh

import android.content.Context
import com.dincer552.hattrickssh.security.SecureKeyStorage
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AuthenticationMethod { PRIVATE_KEY, PASSWORD }

data class SshServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authenticationMethod: AuthenticationMethod = AuthenticationMethod.PRIVATE_KEY,
    val password: String = "",
    val description: String = "",
    val useCloudflareAccess: Boolean = false
)

/** Stores profiles (including any password) in one AES-GCM encrypted app-private file. */
class SshServerProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val storage = SecureKeyStorage(context.applicationContext)

    /** Migrate the old one-server setup only on an install that was upgraded from that build. */
    @Synchronized
    fun migrateLegacyInstallIfNeeded() {
        val prefs = appContext.getSharedPreferences("profile_migration", Context.MODE_PRIVATE)
        if (prefs.getBoolean("legacy_migration_complete", false)) return

        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val upgradedInstall = packageInfo.lastUpdateTime > packageInfo.firstInstallTime
        if (upgradedInstall && all().isEmpty() && storage.hasPrivateKey()) {
            val profile = SshServerProfile(
                name = "Previous server",
                host = "",
                username = "",
                description = "Migrated key. Enter this server's host and username to reconnect.",
                useCloudflareAccess = false
            )
            if (!save(profile)) return
            storage.loadPrivateKey()?.let { legacyKey ->
                try {
                    if (storage.storePrivateKey(profile.id, legacyKey)) storage.deletePrivateKey()
                } finally {
                    legacyKey.fill(0)
                }
            }
        }
        prefs.edit().putBoolean("legacy_migration_complete", true).apply()
    }

    @Synchronized
    fun all(): List<SshServerProfile> = decode(storage.loadEncryptedProfileData())

    @Synchronized
    fun find(id: String): SshServerProfile? = all().firstOrNull { it.id == id }

    @Synchronized
    fun save(profile: SshServerProfile): Boolean {
        val profiles = all().filterNot { it.id == profile.id } + profile
        return storage.storeEncryptedProfileData(encode(profiles))
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val profiles = all().filterNot { it.id == id }
        return storage.storeEncryptedProfileData(encode(profiles)) && storage.deletePrivateKey(id)
    }

    private fun encode(profiles: List<SshServerProfile>): ByteArray {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("host", profile.host)
                    .put("port", profile.port)
                    .put("username", profile.username)
                    .put("authenticationMethod", profile.authenticationMethod.name)
                    .put("password", profile.password)
                    .put("description", profile.description)
                    .put("useCloudflareAccess", profile.useCloudflareAccess)
            )
        }
        return array.toString().toByteArray(Charsets.UTF_8)
    }

    private fun decode(bytes: ByteArray?): List<SshServerProfile> {
        if (bytes == null) return emptyList()
        return try {
            val array = JSONArray(bytes.toString(Charsets.UTF_8))
            (0 until array.length()).mapNotNull { index ->
                runCatching {
                    val item = array.getJSONObject(index)
                    SshServerProfile(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        host = item.getString("host"),
                        port = item.getInt("port"),
                        username = item.getString("username"),
                        authenticationMethod = AuthenticationMethod.valueOf(
                            item.optString("authenticationMethod", AuthenticationMethod.PRIVATE_KEY.name)
                        ),
                        password = item.optString("password"),
                        description = item.optString("description"),
                        useCloudflareAccess = item.optBoolean("useCloudflareAccess", false)
                    )
                }.getOrNull()
            }
        } finally {
            bytes.fill(0)
        }
    }
}
