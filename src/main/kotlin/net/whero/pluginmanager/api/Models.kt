package net.whero.pluginmanager.api

import com.google.gson.annotations.SerializedName

// ── Hangar API Models ──

data class HangarSearchResult(
    val pagination: Pagination,
    val result: List<HangarProject>
)

data class Pagination(
    val limit: Int,
    val offset: Int,
    val count: Int
)

data class HangarProject(
    val name: String,
    val namespace: HangarNamespace,
    val description: String,
    val stats: HangarStats,
    @SerializedName("lastUpdated") val lastUpdated: String? = null
)

data class HangarNamespace(
    val owner: String,
    val slug: String
)

data class HangarStats(
    val views: Int,
    val downloads: Int,
    val recentViews: Int,
    val recentDownloads: Int,
    val stars: Int,
    val watchers: Int
)

data class HangarVersion(
    val name: String,
    val description: String? = null,
    val stats: HangarVersionStats? = null,
    val downloads: Map<String, HangarDownload>,
    val channel: HangarChannel
)

data class HangarVersionStats(
    val totalDownloads: Int,
    val platformDownloads: Map<String, Int>? = null
)

data class HangarDownload(
    val fileInfo: HangarFileInfo?,
    val externalUrl: String? = null
)

data class HangarFileInfo(
    val name: String,
    val sizeBytes: Long,
    @SerializedName("sha256Hash") val sha256Hash: String
)

data class HangarChannel(
    val name: String,
    val color: String? = null
)

data class HangarVersionsResult(
    val pagination: Pagination,
    val result: List<HangarVersion>
)

// ── GitHub API Models ──

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val assets: List<GitHubAsset>,
    val body: String? = null
)

data class GitHubAsset(
    val name: String,
    val size: Long,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("content_type") val contentType: String? = null
)

// ── GeyserMC API Models ──

data class GeyserBuild(
    @SerializedName("project_id") val projectId: String,
    @SerializedName("project_name") val projectName: String,
    val version: String,
    val build: Int,
    val time: String,
    val channel: String,
    val downloads: Map<String, GeyserDownload>
)

data class GeyserDownload(
    val name: String,
    val sha256: String
)

// ── Modrinth API Models ──

data class ModrinthSearchResult(
    val hits: List<ModrinthSearchHit>,
    val offset: Int,
    val limit: Int,
    @com.google.gson.annotations.SerializedName("total_hits") val totalHits: Int
)

data class ModrinthSearchHit(
    val slug: String,
    val title: String,
    val description: String,
    val author: String,
    val downloads: Int,
    @com.google.gson.annotations.SerializedName("project_type") val projectType: String,
    @com.google.gson.annotations.SerializedName("project_id") val projectId: String
)

data class ModrinthProject(
    val slug: String,
    val title: String,
    val description: String,
    val downloads: Int,
    val followers: Int,
    @com.google.gson.annotations.SerializedName("project_type") val projectType: String
)

data class ModrinthVersion(
    val id: String,
    @com.google.gson.annotations.SerializedName("version_number") val versionNumber: String,
    val name: String?,
    @com.google.gson.annotations.SerializedName("version_type") val versionType: String,
    val loaders: List<String>,
    val files: List<ModrinthFile>
)

data class ModrinthFile(
    val hashes: Map<String, String>,
    val url: String,
    val filename: String,
    val size: Long,
    val primary: Boolean
)

// ── Internal Tracking ──

data class TrackedPlugin(
    val name: String,
    val source: String,
    val sourceIdentifier: String,
    val installedVersion: String,
    val fileName: String,
    val installedAt: Long,
    val pinned: Boolean = false
)
