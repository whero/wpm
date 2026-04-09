package net.whero.pluginmanager.managers

import net.whero.pluginmanager.WheroPluginManager
import net.whero.pluginmanager.api.*
import net.whero.pluginmanager.util.Messages.sendError
import net.whero.pluginmanager.util.Messages.sendInfo
import net.whero.pluginmanager.util.Messages.sendSuccess
import net.whero.pluginmanager.util.Messages.sendWarning
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import net.whero.pluginmanager.util.VersionUtils
import java.io.File
import java.security.MessageDigest

class PluginInstallManager(private val plugin: WheroPluginManager) {

    private val hangarClient get() = plugin.hangarClient
    private val gitHubClient get() = plugin.gitHubClient
    private val geyserMcClient get() = plugin.geyserMcClient
    private val tracker get() = plugin.pluginTracker
    private val pluginsDir = plugin.server.pluginsFolder
    private val backupsDir = File(plugin.dataFolder, "backups")

    companion object {
        private const val MAX_BACKUPS = 5
    }

    fun installFromHangar(sender: CommandSender, slug: String) {
        async {
            try {
                sender.sendInfo("Fetching latest version for $slug...")

                val versionName = hangarClient.getLatestVersion(slug)
                if (versionName == null) {
                    sync { sender.sendError("Plugin '$slug' not found on Hangar.") }
                    return@async
                }

                val version = hangarClient.getVersion(slug, versionName)
                val download = version?.downloads?.get("PAPER")
                val fileName = download?.fileInfo?.name ?: "$slug-$versionName.jar"
                val targetFile = File(pluginsDir, fileName)

                if (targetFile.exists()) {
                    sync { sender.sendWarning("File $fileName already exists. Remove it first.") }
                    return@async
                }

                sender.sendInfo("Downloading $slug v$versionName...")

                val success = hangarClient.downloadPlugin(slug, versionName, targetFile)
                if (!success) {
                    sync { sender.sendError("Failed to download $slug.") }
                    return@async
                }

                // Verify hash if enabled
                if (plugin.config.getBoolean("verify-hash", true)) {
                    val expectedHash = download?.fileInfo?.sha256Hash
                    if (expectedHash != null) {
                        val actualHash = sha256(targetFile)
                        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                            targetFile.delete()
                            sync { sender.sendError("SHA256 hash mismatch! Download deleted for safety.") }
                            return@async
                        }
                    }
                }

                tracker.track(
                    TrackedPlugin(
                        name = slug,
                        source = "hangar",
                        sourceIdentifier = slug,
                        installedVersion = versionName,
                        fileName = fileName,
                        installedAt = System.currentTimeMillis()
                    )
                )

                sync {
                    sender.sendSuccess("Installed $slug v$versionName. Restart the server to load it.")
                }
            } catch (e: RateLimitException) {
                sync { sender.sendError(e.message ?: "Rate limit exceeded.") }
            } catch (e: Exception) {
                sync { sender.sendError("Error installing $slug: ${e.message}") }
                plugin.logger.warning("Error installing $slug: ${e.stackTraceToString()}")
            }
        }
    }

    fun installFromGitHub(sender: CommandSender, ownerRepo: String) {
        val parts = ownerRepo.split("/", limit = 2)
        if (parts.size != 2) {
            sender.sendError("Invalid format. Use: /wpm github <owner/repo>")
            return
        }
        val (owner, repo) = parts

        async {
            try {
                sender.sendInfo("Fetching latest release for $ownerRepo...")

                val release = gitHubClient.getLatestRelease(owner, repo)
                if (release == null) {
                    sync { sender.sendError("No releases found for $ownerRepo.") }
                    return@async
                }

                val asset = gitHubClient.findJarAsset(release, repo)
                if (asset == null) {
                    sync { sender.sendError("No JAR file found in the latest release of $ownerRepo.") }
                    return@async
                }

                val targetFile = File(pluginsDir, asset.name)
                if (targetFile.exists()) {
                    sync { sender.sendWarning("File ${asset.name} already exists. Remove it first.") }
                    return@async
                }

                sender.sendInfo("Downloading ${asset.name}...")

                val success = gitHubClient.downloadAsset(asset.browserDownloadUrl, targetFile)
                if (!success) {
                    sync { sender.sendError("Failed to download ${asset.name}.") }
                    return@async
                }

                tracker.track(
                    TrackedPlugin(
                        name = repo,
                        source = "github",
                        sourceIdentifier = ownerRepo,
                        installedVersion = release.tagName,
                        fileName = asset.name,
                        installedAt = System.currentTimeMillis()
                    )
                )

                sync {
                    sender.sendSuccess("Installed $repo ${release.tagName}. Restart the server to load it.")
                }
            } catch (e: RateLimitException) {
                sync { sender.sendError(e.message ?: "Rate limit exceeded.") }
            } catch (e: Exception) {
                sync { sender.sendError("Error installing from GitHub: ${e.message}") }
                plugin.logger.warning("Error installing from GitHub: ${e.stackTraceToString()}")
            }
        }
    }

    fun installFromGeyserMc(sender: CommandSender, project: String) {
        val projectLower = project.lowercase()
        if (projectLower !in GeyserMcClient.SUPPORTED_PROJECTS) {
            sender.sendError("Unsupported GeyserMC project '$project'. Supported: ${GeyserMcClient.SUPPORTED_PROJECTS.joinToString(", ")}")
            return
        }

        async {
            try {
                sender.sendInfo("Fetching latest build for $project...")

                val build = geyserMcClient.getLatestBuild(projectLower)
                if (build == null) {
                    sync { sender.sendError("Could not fetch latest build for $project.") }
                    return@async
                }

                val download = build.downloads["spigot"]
                if (download == null) {
                    sync { sender.sendError("No Spigot download available for $project.") }
                    return@async
                }

                val targetFile = File(pluginsDir, download.name)
                if (targetFile.exists()) {
                    sync { sender.sendWarning("File ${download.name} already exists. Remove it first.") }
                    return@async
                }

                val versionString = "${build.version}-b${build.build}"
                sender.sendInfo("Downloading $project $versionString...")

                val success = geyserMcClient.downloadPlugin(projectLower, build, targetFile = targetFile)
                if (!success) {
                    sync { sender.sendError("Failed to download $project.") }
                    return@async
                }

                if (plugin.config.getBoolean("verify-hash", true)) {
                    val actualHash = sha256(targetFile)
                    if (!actualHash.equals(download.sha256, ignoreCase = true)) {
                        targetFile.delete()
                        sync { sender.sendError("SHA256 hash mismatch! Download deleted for safety.") }
                        return@async
                    }
                }

                tracker.track(
                    TrackedPlugin(
                        name = build.projectName,
                        source = "geysermc",
                        sourceIdentifier = projectLower,
                        installedVersion = versionString,
                        fileName = download.name,
                        installedAt = System.currentTimeMillis()
                    )
                )

                sync {
                    sender.sendSuccess("Installed ${build.projectName} $versionString. Restart the server to load it.")
                }
            } catch (e: RateLimitException) {
                sync { sender.sendError(e.message ?: "Rate limit exceeded.") }
            } catch (e: Exception) {
                sync { sender.sendError("Error installing $project: ${e.message}") }
                plugin.logger.warning("Error installing $project from GeyserMC: ${e.stackTraceToString()}")
            }
        }
    }

    fun disablePlugin(sender: CommandSender, name: String) {
        val tracked = tracker.getTracked(name)
        val currentFileName: String
        val isTracked: Boolean

        if (tracked != null) {
            currentFileName = tracked.fileName
            isTracked = true
        } else {
            val bp = Bukkit.getPluginManager().getPlugin(name)
            if (bp == null) {
                sender.sendError("Plugin '$name' is not tracked or loaded.")
                return
            }
            currentFileName = try {
                val getFileMethod = org.bukkit.plugin.java.JavaPlugin::class.java.getDeclaredMethod("getFile")
                getFileMethod.isAccessible = true
                (getFileMethod.invoke(bp) as? File)?.name ?: run {
                    sender.sendError("Could not resolve JAR file for '$name'.")
                    return
                }
            } catch (e: Exception) {
                sender.sendError("Could not resolve JAR file for '$name': ${e.message}")
                return
            }
            isTracked = false
        }

        if (currentFileName.endsWith(".disabled")) {
            sender.sendWarning("${tracked?.name ?: name} is already disabled.")
            return
        }

        val file = File(pluginsDir, currentFileName)
        if (!file.exists()) {
            sender.sendError("File $currentFileName not found in plugins folder.")
            return
        }

        val disabledFile = File(pluginsDir, "$currentFileName.disabled")
        if (disabledFile.exists()) disabledFile.delete()

        if (!file.renameTo(disabledFile)) {
            sender.sendError("Failed to rename $currentFileName. It may be locked.")
            return
        }

        if (isTracked && tracked != null) {
            tracker.track(tracked.copy(fileName = disabledFile.name))
        }

        sender.sendSuccess("Disabled ${tracked?.name ?: name}. Restart the server to unload it.")
    }

    fun enablePlugin(sender: CommandSender, name: String) {
        val tracked = tracker.getTracked(name)

        val disabledFile: File
        val restoredName: String

        if (tracked != null) {
            val current = File(pluginsDir, tracked.fileName)
            disabledFile = when {
                tracked.fileName.endsWith(".disabled") && current.exists() -> current
                File(pluginsDir, "${tracked.fileName}.disabled").exists() ->
                    File(pluginsDir, "${tracked.fileName}.disabled")
                else -> {
                    sender.sendWarning("${tracked.name} is not disabled.")
                    return
                }
            }
            restoredName = disabledFile.name.removeSuffix(".disabled")
        } else {
            // Search plugins folder for a matching .disabled file
            val candidates = pluginsDir.listFiles { f ->
                f.isFile && f.name.endsWith(".jar.disabled") &&
                    f.name.substringBeforeLast(".jar.disabled").contains(name, ignoreCase = true)
            } ?: emptyArray()
            if (candidates.isEmpty()) {
                sender.sendError("No disabled plugin file matching '$name' found.")
                return
            }
            if (candidates.size > 1) {
                sender.sendError("Multiple disabled files match '$name': ${candidates.joinToString(", ") { it.name }}")
                return
            }
            disabledFile = candidates[0]
            restoredName = disabledFile.name.removeSuffix(".disabled")
        }

        val target = File(pluginsDir, restoredName)
        if (target.exists()) {
            sender.sendError("Cannot enable: $restoredName already exists.")
            return
        }

        if (!disabledFile.renameTo(target)) {
            sender.sendError("Failed to rename ${disabledFile.name}. It may be locked.")
            return
        }

        if (tracked != null) {
            tracker.track(tracked.copy(fileName = restoredName))
        }

        sender.sendSuccess("Enabled ${tracked?.name ?: name}. Restart the server to load it.")
    }

    fun pinPlugin(sender: CommandSender, name: String) {
        val tracked = tracker.getTracked(name)
        if (tracked == null) {
            sender.sendError("Plugin '$name' is not tracked by WPM.")
            return
        }
        if (tracked.pinned) {
            sender.sendWarning("${tracked.name} is already pinned at v${tracked.installedVersion}.")
            return
        }
        tracker.track(tracked.copy(pinned = true))
        sender.sendSuccess("Pinned ${tracked.name} at v${tracked.installedVersion}. It will be skipped by /wpm update.")
    }

    fun unpinPlugin(sender: CommandSender, name: String) {
        val tracked = tracker.getTracked(name)
        if (tracked == null) {
            sender.sendError("Plugin '$name' is not tracked by WPM.")
            return
        }
        if (!tracked.pinned) {
            sender.sendWarning("${tracked.name} is not pinned.")
            return
        }
        tracker.track(tracked.copy(pinned = false))
        sender.sendSuccess("Unpinned ${tracked.name}. It will now be updated by /wpm update.")
    }

    fun removePlugin(sender: CommandSender, name: String) {
        val tracked = tracker.getTracked(name)
        if (tracked == null) {
            sender.sendError("Plugin '$name' is not tracked by WPM.")
            return
        }

        val file = File(pluginsDir, tracked.fileName)
        if (file.exists()) {
            if (!file.delete()) {
                sender.sendWarning("Could not delete ${tracked.fileName}. It may be locked. Delete it manually and restart.")
            } else {
                sender.sendSuccess("Deleted ${tracked.fileName}.")
            }
        } else {
            sender.sendWarning("File ${tracked.fileName} not found. Untracking anyway.")
        }

        tracker.untrack(name)
        sender.sendSuccess("Removed ${tracked.name} from tracked plugins. Restart the server to unload it.")
    }

    fun updatePlugin(sender: CommandSender, name: String?) {
        async {
            try {
                val plugins = if (name != null) {
                    val t = tracker.getTracked(name)
                    if (t == null) {
                        sync { sender.sendError("Plugin '$name' is not tracked by WPM.") }
                        return@async
                    }
                    listOf(t)
                } else {
                    tracker.getAllTracked()
                }

                if (plugins.isEmpty()) {
                    sync { sender.sendInfo("No tracked plugins to update.") }
                    return@async
                }

                sync { sender.sendInfo("Checking ${plugins.size} plugin(s) for updates...") }

                for (tracked in plugins) {
                    if (tracked.pinned) {
                        if (name != null) {
                            sync { sender.sendWarning("${tracked.name} is pinned at v${tracked.installedVersion}. Use /wpm unpin ${tracked.name} to allow updates.") }
                        } else {
                            sync { sender.sendInfo("${tracked.name} is pinned at v${tracked.installedVersion}, skipping.") }
                        }
                        continue
                    }
                    val latestVersion = when (tracked.source) {
                        "hangar" -> hangarClient.getLatestVersion(tracked.sourceIdentifier)
                        "github" -> {
                            val parts = tracked.sourceIdentifier.split("/", limit = 2)
                            if (parts.size == 2) {
                                gitHubClient.getLatestRelease(parts[0], parts[1])?.tagName
                            } else null
                        }
                        "geysermc" -> {
                            val build = geyserMcClient.getLatestBuild(tracked.sourceIdentifier)
                            build?.let { "${it.version}-b${it.build}" }
                        }
                        else -> null
                    }

                    if (latestVersion == null) {
                        sync { sender.sendWarning("Could not check ${tracked.name} for updates. No versions found on ${tracked.source} for '${tracked.sourceIdentifier}'.") }
                        plugin.logger.warning("Update check failed for ${tracked.name}: no versions returned from ${tracked.source} (identifier: ${tracked.sourceIdentifier})")
                        continue
                    }

                    if (VersionUtils.isSameVersion(tracked.installedVersion, latestVersion)) {
                        sync { sender.sendInfo("${tracked.name} is up to date (${tracked.installedVersion}).") }
                        continue
                    }

                    sync { sender.sendInfo("Updating ${tracked.name}: ${tracked.installedVersion} → $latestVersion") }

                    // Backup old file before replacing
                    val oldFile = File(pluginsDir, tracked.fileName)
                    if (oldFile.exists()) {
                        backupPlugin(tracked)
                        oldFile.delete()
                    }

                    when (tracked.source) {
                        "hangar" -> {
                            val version = hangarClient.getVersion(tracked.sourceIdentifier, latestVersion)
                            val download = version?.downloads?.get("PAPER")
                            val newFileName = download?.fileInfo?.name ?: "${tracked.sourceIdentifier}-$latestVersion.jar"
                            val targetFile = File(pluginsDir, newFileName)

                            val success = hangarClient.downloadPlugin(tracked.sourceIdentifier, latestVersion, targetFile)
                            if (success) {
                                tracker.track(tracked.copy(
                                    installedVersion = latestVersion,
                                    fileName = newFileName,
                                    installedAt = System.currentTimeMillis()
                                ))
                                sync { sender.sendSuccess("Updated ${tracked.name} to $latestVersion.") }
                            } else {
                                sync { sender.sendError("Failed to download update for ${tracked.name}.") }
                            }
                        }
                        "github" -> {
                            val parts = tracked.sourceIdentifier.split("/", limit = 2)
                            val release = gitHubClient.getLatestRelease(parts[0], parts[1])
                            val asset = release?.let { gitHubClient.findJarAsset(it, parts[1]) }
                            if (asset != null) {
                                val targetFile = File(pluginsDir, asset.name)
                                val success = gitHubClient.downloadAsset(asset.browserDownloadUrl, targetFile)
                                if (success) {
                                    tracker.track(tracked.copy(
                                        installedVersion = latestVersion,
                                        fileName = asset.name,
                                        installedAt = System.currentTimeMillis()
                                    ))
                                    sync { sender.sendSuccess("Updated ${tracked.name} to $latestVersion.") }
                                } else {
                                    sync { sender.sendError("Failed to download update for ${tracked.name}.") }
                                }
                            }
                        }
                        "geysermc" -> {
                            val build = geyserMcClient.getLatestBuild(tracked.sourceIdentifier)
                            val download = build?.downloads?.get("spigot")
                            if (build != null && download != null) {
                                val targetFile = File(pluginsDir, download.name)
                                val success = geyserMcClient.downloadPlugin(tracked.sourceIdentifier, build, targetFile = targetFile)
                                if (success) {
                                    tracker.track(tracked.copy(
                                        installedVersion = latestVersion,
                                        fileName = download.name,
                                        installedAt = System.currentTimeMillis()
                                    ))
                                    sync { sender.sendSuccess("Updated ${tracked.name} to $latestVersion.") }
                                } else {
                                    sync { sender.sendError("Failed to download update for ${tracked.name}.") }
                                }
                            } else {
                                sync { sender.sendError("No Spigot download available for ${tracked.name}.") }
                            }
                        }
                    }
                }

                sync { sender.sendSuccess("Update check complete.") }
            } catch (e: RateLimitException) {
                sync { sender.sendError(e.message ?: "Rate limit exceeded.") }
            } catch (e: Exception) {
                sync { sender.sendError("Error during update: ${e.message}") }
                plugin.logger.warning("Error during update: ${e.stackTraceToString()}")
            }
        }
    }

    fun rollbackPlugin(sender: CommandSender, name: String, version: String?) {
        val tracked = tracker.getTracked(name)
        if (tracked == null) {
            sender.sendError("Plugin '$name' is not tracked by WPM.")
            return
        }

        val pluginBackupDir = File(backupsDir, tracked.name.lowercase())
        if (!pluginBackupDir.exists()) {
            sender.sendError("No backups found for ${tracked.name}.")
            return
        }

        val backups = getBackups(tracked.name)
        if (backups.isEmpty()) {
            sender.sendError("No backups found for ${tracked.name}.")
            return
        }

        val backupFile = if (version != null) {
            backups.find { extractBackupVersion(it) == version }
                ?: run {
                    sender.sendError("No backup with version '$version' found for ${tracked.name}.")
                    return
                }
        } else {
            backups.first() // most recent
        }

        val backupVersion = extractBackupVersion(backupFile)

        // Delete current plugin file
        val currentFile = File(pluginsDir, tracked.fileName)
        if (currentFile.exists()) currentFile.delete()

        // Copy backup to plugins dir
        val restoredFileName = tracked.fileName
        val targetFile = File(pluginsDir, restoredFileName)
        backupFile.copyTo(targetFile, overwrite = true)

        // Update tracking
        tracker.track(tracked.copy(
            installedVersion = backupVersion,
            installedAt = System.currentTimeMillis()
        ))

        sender.sendSuccess("Rolled back ${tracked.name} to $backupVersion. Restart the server to apply.")
    }

    fun getBackups(pluginName: String): List<File> {
        val pluginBackupDir = File(backupsDir, pluginName.lowercase())
        if (!pluginBackupDir.exists()) return emptyList()
        return pluginBackupDir.listFiles { f -> f.isFile && f.name.endsWith(".jar") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    private fun backupPlugin(tracked: TrackedPlugin) {
        val pluginBackupDir = File(backupsDir, tracked.name.lowercase())
        pluginBackupDir.mkdirs()

        val sourceFile = File(pluginsDir, tracked.fileName)
        if (!sourceFile.exists()) return

        val safeVersion = tracked.installedVersion.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val backupFile = File(pluginBackupDir, "${tracked.name}-${safeVersion}.jar")
        sourceFile.copyTo(backupFile, overwrite = true)

        // Prune old backups beyond MAX_BACKUPS
        val backups = pluginBackupDir.listFiles { f -> f.isFile && f.name.endsWith(".jar") }
            ?.sortedByDescending { it.lastModified() }
            ?: return

        if (backups.size > MAX_BACKUPS) {
            backups.drop(MAX_BACKUPS).forEach { it.delete() }
        }
    }

    private fun extractBackupVersion(file: File): String {
        // Filename format: PluginName-version.jar
        val name = file.nameWithoutExtension
        val dashIndex = name.indexOf('-')
        return if (dashIndex >= 0) name.substring(dashIndex + 1) else name
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun async(block: () -> Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { block() })
    }

    private fun sync(block: () -> Unit) {
        Bukkit.getScheduler().runTask(plugin, Runnable { block() })
    }
}
