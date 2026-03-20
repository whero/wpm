package net.whero.pluginmanager.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.logging.Logger

class HangarClient(private val logger: Logger) {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val gson = Gson()

    private val searchCache = mutableMapOf<String, Pair<Long, HangarSearchResult>>()
    private val cacheTtlMs = 60_000L

    companion object {
        private const val BASE_URL = "https://hangar.papermc.io/api/v1"
    }

    fun searchProjects(query: String, limit: Int = 10): HangarSearchResult? {
        val cacheKey = "$query:$limit"
        val cached = searchCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.first < cacheTtlMs) {
            return cached.second
        }

        val encoded = URLEncoder.encode(query, Charsets.UTF_8)
        val response = get("$BASE_URL/projects?q=$encoded&limit=$limit") ?: return null
        val result = gson.fromJson(response, HangarSearchResult::class.java)
        searchCache[cacheKey] = System.currentTimeMillis() to result
        return result
    }

    fun getProject(slug: String): HangarProject? {
        val response = get("$BASE_URL/projects/$slug") ?: return null
        return gson.fromJson(response, HangarProject::class.java)
    }

    fun getLatestVersion(slug: String): String? {
        // Try the latestrelease endpoint first (only returns "Release" channel versions)
        val release = get("$BASE_URL/projects/$slug/latestrelease", logErrors = false)?.trim('"')
        if (release != null) return release

        // Fall back to fetching the most recent Release channel version for PAPER
        val releaseResponse = get("$BASE_URL/projects/$slug/versions?limit=1&offset=0&platform=PAPER&channel=Release", logErrors = false)
        if (releaseResponse != null) {
            val result = gson.fromJson(releaseResponse, HangarVersionsResult::class.java)
            val version = result.result.firstOrNull()?.name
            if (version != null) return version
        }

        // Last resort: latest version from any channel
        val response = get("$BASE_URL/projects/$slug/versions?limit=1&offset=0&platform=PAPER") ?: return null
        val versionsResult = gson.fromJson(response, HangarVersionsResult::class.java)
        return versionsResult.result.firstOrNull()?.name
    }

    fun getVersion(slug: String, version: String): HangarVersion? {
        val response = get("$BASE_URL/projects/$slug/versions/$version") ?: return null
        return gson.fromJson(response, HangarVersion::class.java)
    }

    fun downloadPlugin(slug: String, version: String, targetFile: File): Boolean {
        val url = "$BASE_URL/projects/$slug/versions/$version/PAPER/download"
        return downloadFile(url, targetFile.toPath())
    }

    private fun get(url: String, logErrors: Boolean = true): String? {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "WheroPluginManager")
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return when (response.statusCode()) {
            200 -> response.body()
            429 -> throw RateLimitException("Hangar rate limit exceeded. Please wait a moment.")
            else -> {
                if (logErrors) {
                    logger.warning("Hangar API returned ${response.statusCode()} for $url")
                }
                null
            }
        }
    }

    private fun downloadFile(url: String, target: Path): Boolean {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "WheroPluginManager")
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(target))
        return response.statusCode() == 200
    }
}

class RateLimitException(message: String) : RuntimeException(message)
