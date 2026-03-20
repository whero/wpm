package net.whero.pluginmanager.api

import com.google.gson.Gson
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class GitHubClient {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val gson = Gson()

    companion object {
        private const val BASE_URL = "https://api.github.com"
    }

    fun getLatestRelease(owner: String, repo: String): GitHubRelease? {
        val url = "$BASE_URL/repos/$owner/$repo/releases/latest"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "WheroPluginManager")
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return when (response.statusCode()) {
            200 -> gson.fromJson(response.body(), GitHubRelease::class.java)
            403, 429 -> throw RateLimitException("GitHub rate limit exceeded. Try again later.")
            else -> null
        }
    }

    fun downloadAsset(url: String, targetFile: File): Boolean {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "WheroPluginManager")
            .header("Accept", "application/octet-stream")
            .timeout(Duration.ofSeconds(120))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(targetFile.toPath()))
        return response.statusCode() == 200
    }

    fun findJarAsset(release: GitHubRelease, repo: String): GitHubAsset? {
        val jars = release.assets.filter { it.name.endsWith(".jar") }
        if (jars.isEmpty()) return null
        if (jars.size == 1) return jars.first()

        // Prefer asset whose name contains the repo name
        val repoLower = repo.lowercase()
        return jars.firstOrNull { it.name.lowercase().contains(repoLower) }
            ?: jars.maxByOrNull { it.size }
    }
}
