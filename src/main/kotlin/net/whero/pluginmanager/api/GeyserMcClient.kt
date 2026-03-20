package net.whero.pluginmanager.api

import com.google.gson.Gson
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.logging.Logger

class GeyserMcClient(private val logger: Logger) {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val gson = Gson()

    companion object {
        private const val BASE_URL = "https://download.geysermc.org/v2"
        val SUPPORTED_PROJECTS = setOf("geyser", "floodgate")
    }

    fun getLatestBuild(project: String): GeyserBuild? {
        val response = get("$BASE_URL/projects/$project/versions/latest/builds/latest") ?: return null
        return gson.fromJson(response, GeyserBuild::class.java)
    }

    fun downloadPlugin(project: String, build: GeyserBuild, platform: String = "spigot", targetFile: File): Boolean {
        if (platform !in build.downloads) return false
        val url = "$BASE_URL/projects/$project/versions/${build.version}/builds/${build.build}/downloads/$platform"
        return downloadFile(url, targetFile)
    }

    private fun get(url: String): String? {
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
            429 -> throw RateLimitException("GeyserMC API rate limit exceeded. Please wait a moment.")
            else -> {
                logger.warning("GeyserMC API returned ${response.statusCode()} for $url")
                null
            }
        }
    }

    private fun downloadFile(url: String, targetFile: File): Boolean {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "WheroPluginManager")
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(targetFile.toPath()))
        return response.statusCode() == 200
    }
}
