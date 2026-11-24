package com.dincer552.hattrickssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class GithubBuildJob(
    val name: String,
    val conclusion: String?,
    val failedSteps: List<String>
)

data class GithubBuildStatus(
    val id: Long,
    val workflowName: String,
    val displayTitle: String,
    val branch: String,
    val event: String,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String,
    val createdAt: String,
    val updatedAt: String,
    val jobs: List<GithubBuildJob>
) {
    val isActive: Boolean get() = status == "queued" || status == "in_progress"

    val statusLabel: String
        get() = when (status) {
            "queued", "pending" -> "Sırada bekliyor"
            "in_progress" -> "Çalışıyor"
            "completed" -> when (conclusion) {
                "success" -> "Başarılı"
                "failure" -> "HATALI"
                "cancelled" -> "İptal edildi"
                "timed_out" -> "Zaman aşımına uğradı"
                "action_required" -> "İşlem gerekiyor"
                "skipped" -> "Atlandı"
                else -> "Tamamlandı: ${conclusion ?: "sonuç bilinmiyor"}"
            }
            else -> status.ifBlank { "Durum bilinmiyor" }
        }
}

object GithubBuildStatusClient {
    suspend fun fetchLatest(repository: String): GithubBuildStatus? = withContext(Dispatchers.IO) {
        val parts = repository.split('/')
        if (parts.size != 2 || parts.any { it.isBlank() }) {
            throw IOException("GitHub depo adı geçersiz: $repository")
        }

        val base = "https://api.github.com/repos/$repository"
        val runs = getJson("$base/actions/runs?branch=main&per_page=1")
            .optJSONArray("workflow_runs")
            ?: throw IOException("GitHub Actions yanıtı okunamadı.")

        if (runs.length() == 0) return@withContext null

        val run = runs.getJSONObject(0)
        val runId = run.optLong("id")
        val state = run.optString("status")
        val result = run.optString("conclusion").takeUnless { it.isBlank() || it == "null" }

        val jobs = if (result == "failure" || state == "in_progress") {
            fetchJobs("$base/actions/runs/$runId/jobs?per_page=30")
        } else {
            emptyList()
        }

        GithubBuildStatus(
            id = runId,
            workflowName = run.optString("name", "GitHub Actions"),
            displayTitle = run.optString("display_title", "Build"),
            branch = run.optString("head_branch", "main"),
            event = run.optString("event", ""),
            status = state,
            conclusion = result,
            htmlUrl = run.optString("html_url"),
            createdAt = run.optString("created_at"),
            updatedAt = run.optString("updated_at"),
            jobs = jobs
        )
    }

    private fun fetchJobs(url: String): List<GithubBuildJob> {
        val response = getJson(url)
        val jobs = response.optJSONArray("jobs") ?: return emptyList()
        return (0 until jobs.length()).map { index ->
            val job = jobs.getJSONObject(index)
            val steps = job.optJSONArray("steps")
            val failedSteps = if (steps == null) emptyList() else {
                (0 until steps.length()).mapNotNull { stepIndex ->
                    val step = steps.getJSONObject(stepIndex)
                    if (step.optString("conclusion") == "failure") {
                        step.optString("name").takeIf { it.isNotBlank() }
                    } else null
                }
            }
            GithubBuildJob(
                name = job.optString("name", "Build job"),
                conclusion = job.optString("conclusion").takeUnless { it.isBlank() || it == "null" },
                failedSteps = failedSteps
            )
        }
    }

    private fun getJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "My-App-Store-Android/1.0")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val description = when (code) {
                    403, 429 -> "GitHub API sınırı doldu (HTTP $code). Biraz sonra yeniden deneyin."
                    404 -> "Depo bulunamadı veya GitHub Actions erişime kapalı (HTTP 404)."
                    else -> "GitHub API yanıtı: HTTP $code."
                }
                throw IOException(description)
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
