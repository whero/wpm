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

class ModrinthClient(private val logger: Logger) {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val gson = Gson()

    private val searchCache = mutableMapOf<String, Pair<Long, ModrinthSearchResult>>()
    private val cacheTtlMs = 60_000L

    companion object {
        private const val BASE_URL = "https://api.modrinth.com/v2"
    }

    fun searchProjects(query: String, limit: Int = 10): ModrinthSearchResult? {
        val cacheKey = "$query:$limit"
        val cached = searchCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.first < cacheTtlMs) {
            return cached.second
        }

        val encoded = URLEncoder.encode(query, Charsets.UTF_8)
        val facets = URLEncoder.encode("[[\"project_type:plugin\"]]", Charsets.UTF_8)
        val response = get("$BASE_URL/search?query=$encoded&limit=$limit&facets=$facets") ?: return null
        val result = gson.fromJson(response, ModrinthSearchResult::class.java)
        searchCache[cacheKey] = System.currentTimeMillis() to result
        return result
    }

    fun getProject(slugOrId: String): ModrinthProject? {
        val response = get("$BASE_URL/project/$slugOrId") ?: return null
        return gson.fromJson(response, ModrinthProject::class.java)
    }

    fun getLatestVersion(slugOrId: String): ModrinthVersion? {
        val loaders = URLEncoder.encode("[\"paper\",\"bukkit\",\"spigot\"]", Charsets.UTF_8)
        val response = get("$BASE_URL/project/$slugOrId/version?loaders=$loaders") ?: return null
        val versions: List<ModrinthVersion> = gson.fromJson(
            response,
            object : TypeToken<List<ModrinthVersion>>() {}.type
        )
        // Prefer release channel, fall back to any
        return versions.firstOrNull { it.versionType == "release" } ?: versions.firstOrNull()
    }

    fun getVersion(versionId: String): ModrinthVersion? {
        val response = get("$BASE_URL/version/$versionId") ?: return null
        return gson.fromJson(response, ModrinthVersion::class.java)
    }

    fun downloadPlugin(version: ModrinthVersion, targetFile: File): Boolean {
        val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull() ?: return false
        return downloadFile(file.url, targetFile.toPath())
    }

    fun getFileHash(version: ModrinthVersion): String? {
        val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull()
        return file?.hashes?.get("sha256")
    }

    fun getFileName(version: ModrinthVersion): String {
        val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull()
        return file?.filename ?: "plugin.jar"
    }

    private fun get(url: String, logErrors: Boolean = true): String? {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "WheroPluginManager/1.0 (github.com/whero/wpm)")
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return when (response.statusCode()) {
            200 -> response.body()
            429 -> throw RateLimitException("Modrinth rate limit exceeded. Please wait a moment.")
            else -> {
                if (logErrors) {
                    logger.warning("Modrinth API returned ${response.statusCode()} for $url")
                }
                null
            }
        }
    }

    private fun downloadFile(url: String, target: Path): Boolean {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "WheroPluginManager/1.0 (github.com/whero/wpm)")
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(target))
        return response.statusCode() == 200
    }
}
