package com.dincer552.hattrickssh

import kotlinx.coroutines.Dispatchers
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

    suspend fun loadCatalog(): List<StoreAppListing> = withContext(Dispatchers.IO) {
        val catalog = JSONArray(getText(CATALOG_URL))
        (0 until catalog.length()).map { index ->
            val item = catalog.getJSONObject(index)
            val repository = item.getString("repository")
            val assetName = item.getString("assetName")
            StoreAppListing(
                id = item.getString("id"),
                name = item.getString("name"),
                description = item.optString("description"),
                icon = item.optString("icon", "📦"),
                packageName = item.getString("packageName"),
                repository = repository,
                release = fetchLatestRelease(repository, assetName),
                updateHistory = fetchReleaseHistory(repository)
            )
        }
    }

    private fun fetchReleaseHistory(repository: String): List<StoreReleaseHistory> {
        val response = getTextOrNull("https://api.github.com/repos/$repository/releases?per_page=20") ?: return emptyList()
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
        // Prefer GitHub's latest endpoint, but it excludes pre-releases. Fall back
        // to the releases collection so a valid published APK is still discoverable.
        val latest = getTextOrNull("https://api.github.com/repos/$repository/releases/latest")
        parseReleaseAsset(latest, assetName)?.let { return it }

        val response = getTextOrNull("https://api.github.com/repos/$repository/releases?per_page=20")
            ?: return null
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

    private fun getText(url: String): String =
        getTextOrNull(url) ?: throw IOException("Katalog veya GitHub servisi yanıt vermedi.")

    private fun getTextOrNull(address: String): String? {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 18_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "My-App-Store-Android")
        }
        try {
            return when (val code = connection.responseCode) {
                in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }
}
