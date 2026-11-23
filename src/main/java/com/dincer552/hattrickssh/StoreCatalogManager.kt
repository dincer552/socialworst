package com.dincer552.hattrickssh

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class StoreAppListing(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val packageName: String,
    val repository: String,
    val release: StoreRelease?,
    val updateHistory: List<StoreReleaseHistory> = emptyList()
) {
    val downloadUrl: String get() = release?.downloadUrl.orEmpty()
    val versionCode: Int get() = release?.versionCode ?: 0
    val versionName: String get() = release?.versionName.orEmpty()
}

data class StoreRelease(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val sha256: String?
)

data class StoreReleaseHistory(val versionName: String, val publishedAt: String, val notes: String)

object StoreCatalogManager {
    private const val CATALOG_URL = "https://raw.githubusercontent.com/dincer552/socialworst/main/app-catalog.json"
    private const val PREFS = "store_catalog_cache"
    private const val KEY_RELEASES = "releases_json"

    @Volatile var lastHttpCode: Int = 0
        private set
    @Volatile var lastErrorHint: String? = null
        private set
    @Volatile var usedCache: Boolean = false
        private set

    suspend fun loadCatalog(context: Context): List<StoreAppListing> = withContext(Dispatchers.IO) {
        lastErrorHint = null
        lastHttpCode = 0
        usedCache = false
        val catalogText = try {
            getText(CATALOG_URL, forGithubApi = false)
        } catch (error: IOException) {
            throw mapNetworkError(error)
        }
        val catalog = JSONArray(catalogText)
        val cached = loadCachedReleases(context)
        val liveReleases = JSONObject()
        var anyLive = false
        var anyCache = false

        val listings = (0 until catalog.length()).map { index ->
            val item = catalog.getJSONObject(index)
            val id = item.getString("id")
            val repository = item.getString("repository")
            val assetName = item.getString("assetName")
            // Küçük gecikme: ardışık isteklerle rate limit'e daha az takıl
            if (index > 0) {
                try { delay(120) } catch (_: Exception) {}
            }
            var release = fetchLatestRelease(repository, assetName)
            if (release != null) {
                anyLive = true
                liveReleases.put(id, releaseToJson(release))
            } else {
                val cachedRelease = cached.optJSONObject(id)?.let { jsonToRelease(it) }
                if (cachedRelease != null) {
                    release = cachedRelease
                    anyCache = true
                    liveReleases.put(id, releaseToJson(cachedRelease))
                }
            }
            StoreAppListing(
                id = id,
                name = item.getString("name"),
                description = item.optString("description"),
                icon = item.optString("icon", "📦"),
                packageName = item.getString("packageName"),
                repository = repository,
                release = release,
                updateHistory = emptyList()
            )
        }

        if (anyLive || liveReleases.length() > 0) {
            saveCachedReleases(context, liveReleases)
        }
        usedCache = anyCache && !anyLive
        if (usedCache) {
            lastErrorHint = "GitHub geçici olarak sürüm veremedi; son bilinen sürümler gösteriliyor."
        } else if (!anyLive && listings.any { it.release == null }) {
            lastErrorHint = "Sürüm bilgisi alınamadı (HTTP $lastHttpCode). Birkaç dakika sonra YENİLE."
        }
        listings
    }

    suspend fun loadReleaseHistory(repository: String): List<StoreReleaseHistory> =
        withContext(Dispatchers.IO) { fetchReleaseHistory(repository) }

    private fun releaseToJson(r: StoreRelease): JSONObject =
        JSONObject()
            .put("versionCode", r.versionCode)
            .put("versionName", r.versionName)
            .put("downloadUrl", r.downloadUrl)
            .put("sha256", r.sha256 ?: JSONObject.NULL)

    private fun jsonToRelease(o: JSONObject): StoreRelease? = try {
        val url = o.optString("downloadUrl")
        if (!url.startsWith("https://")) return null
        StoreRelease(
            versionCode = o.getInt("versionCode"),
            versionName = o.optString("versionName"),
            downloadUrl = url,
            sha256 = o.optString("sha256").takeIf { it.length == 64 }
        )
    } catch (_: Exception) {
        null
    }

    private fun loadCachedReleases(context: Context): JSONObject {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RELEASES, "{}") ?: "{}"
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun saveCachedReleases(context: Context, releases: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RELEASES, releases.toString())
            .apply()
    }

    private fun fetchReleaseHistory(repository: String): List<StoreReleaseHistory> {
        val response = getTextOrNull(
            "https://api.github.com/repos/$repository/releases?per_page=10",
            forGithubApi = true
        ) ?: return emptyList()
        return try {
            val releases = JSONArray(response)
            (0 until releases.length()).map { index ->
                val item = releases.getJSONObject(index)
                val rawDate = item.optString("published_at")
                val date = rawDate.takeIf { it.length >= 10 && it[4] == '-' && it[7] == '-' }?.take(10).orEmpty()
                StoreReleaseHistory(item.optString("tag_name").removePrefix("v"), date, item.optString("body").trim())
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchLatestRelease(repository: String, assetName: String): StoreRelease? {
        val latest = getTextOrNull(
            "https://api.github.com/repos/$repository/releases/latest",
            forGithubApi = true
        )
        parseReleaseAsset(latest, assetName)?.let { return it }

        val response = getTextOrNull(
            "https://api.github.com/repos/$repository/releases?per_page=5",
            forGithubApi = true
        ) ?: return null
        return try {
            val releases = JSONArray(response)
            (0 until releases.length()).asSequence()
                .map { releases.getJSONObject(it) }
                .filterNot { it.optBoolean("draft", false) }
                .mapNotNull { parseReleaseAsset(it, assetName) }
                .firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseReleaseAsset(response: String?, assetName: String): StoreRelease? {
        if (response.isNullOrBlank()) return null
        return try {
            parseReleaseAsset(JSONObject(response), assetName)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseReleaseAsset(release: JSONObject, assetName: String): StoreRelease? {
        return try {
            val tag = release.optString("tag_name")
            val versionCode = tag.substringAfterLast('.').toIntOrNull() ?: return null
            val assets = release.optJSONArray("assets") ?: return null
            val asset = (0 until assets.length()).asSequence()
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == assetName } ?: return null
            val url = asset.optString("browser_download_url")
            if (!url.startsWith("https://")) return null
            val digest = asset.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 }
            StoreRelease(versionCode, tag.removePrefix("v"), url, digest)
        } catch (_: Exception) {
            null
        }
    }

    private fun mapNetworkError(error: IOException): IOException {
        val code = lastHttpCode
        val message = when (code) {
            403, 429 ->
                "GitHub istek sınırı (HTTP $code). Birkaç dakika sonra YENİLE. " +
                    "Çok sık yenilemek limiti doldurur."
            0 ->
                "Ağ hatası: ${error.message ?: "bağlantı yok"}. İnterneti kontrol et."
            else ->
                error.message ?: "GitHub yanıt vermedi (HTTP $code)."
        }
        lastErrorHint = message
        return IOException(message, error)
    }

    private fun getText(url: String, forGithubApi: Boolean): String =
        getTextOrNull(url, forGithubApi)
            ?: throw IOException(
                if (lastHttpCode == 403 || lastHttpCode == 429) {
                    "GitHub istek sınırı (HTTP $lastHttpCode). Birkaç dakika sonra YENİLE."
                } else {
                    "Katalog veya GitHub servisi yanıt vermedi (HTTP $lastHttpCode)."
                }
            )

    private fun getTextOrNull(address: String, forGithubApi: Boolean): String? {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 18_000
            setRequestProperty("User-Agent", "My-App-Store-Android/1.0")
            if (forGithubApi) {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            } else {
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
        }
        try {
            val code = connection.responseCode
            lastHttpCode = code
            return when (code) {
                in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND,
                HttpURLConnection.HTTP_FORBIDDEN,
                429 -> {
                    lastErrorHint = "GitHub HTTP $code"
                    null
                }
                else -> {
                    lastErrorHint = "GitHub HTTP $code"
                    null
                }
            }
        } catch (error: Exception) {
            lastHttpCode = 0
            lastErrorHint = error.message
            return null
        } finally {
            connection.disconnect()
        }
    }
}
