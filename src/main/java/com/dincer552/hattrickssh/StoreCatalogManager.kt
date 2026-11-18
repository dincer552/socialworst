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
    val release: StoreRelease?
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
                release = fetchLatestRelease(repository, assetName)
            )
        }
    }

    private fun fetchLatestRelease(repository: String, assetName: String): StoreRelease? {
        val response = getTextOrNull("https://api.github.com/repos/$repository/releases/latest") ?: return null
        val release = JSONObject(response)
        val tag = release.optString("tag_name")
        val versionCode = tag.substringAfterLast('.').toIntOrNull()
            ?: throw IOException("Sürüm etiketi okunamadı: $tag")
        val assets = release.optJSONArray("assets") ?: return null
        val asset = (0 until assets.length()).asSequence()
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == assetName } ?: return null
        val url = asset.optString("browser_download_url")
        if (!url.startsWith("https://")) return null
        val digest = asset.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 }
        return StoreRelease(versionCode, tag.removePrefix("v"), url, digest)
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
