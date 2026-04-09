package net.whero.pluginmanager.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.whero.pluginmanager.WheroPluginManager
import net.whero.pluginmanager.api.GeyserMcClient
import net.whero.pluginmanager.api.RateLimitException
import net.whero.pluginmanager.api.TrackedPlugin
import net.whero.pluginmanager.util.Messages
import net.whero.pluginmanager.util.Messages.sendError
import net.whero.pluginmanager.util.Messages.sendInfo
import net.whero.pluginmanager.util.Messages.sendRaw
import net.whero.pluginmanager.util.Messages.sendSuccess
import net.whero.pluginmanager.util.VersionUtils
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class WpmCommand(private val plugin: WheroPluginManager) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "search" -> handleSearch(sender, args)
            "install" -> handleInstall(sender, args)
            "github" -> handleGitHub(sender, args)
            "geyser" -> handleGeyser(sender, args)
            "remove" -> handleRemove(sender, args)
            "disable" -> handleDisable(sender, args)
            "enable" -> handleEnable(sender, args)
            "pin" -> handlePin(sender, args)
            "unpin" -> handleUnpin(sender, args)
            "update" -> handleUpdate(sender, args)
            "list" -> handleList(sender)
            "info" -> handleInfo(sender, args)
            "identify" -> handleIdentify(sender, args)
            "unlink" -> handleUnlink(sender, args)
            "relink" -> handleRelink(sender, args)
            "rollback" -> handleRollback(sender, args)
            "reload" -> handleReload(sender)
            else -> sendUsage(sender)
        }

        return true
    }

    private fun handleSearch(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendError("Usage: /wpm search <query>")
            return
        }

        val query = args.drop(1).joinToString(" ")
        val maxResults = plugin.config.getInt("max-search-results", 10)

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val result = plugin.hangarClient.searchProjects(query, maxResults)
                if (result == null || result.result.isEmpty()) {
                    sync { sender.sendInfo("No results found for '$query'.") }
                    return@Runnable
                }

                sync {
                    sender.sendInfo("Search results for '$query' (${result.result.size}/${result.pagination.count}):")
                    sender.sendMessage(Component.empty())

                    for (project in result.result) {
                        val slug = project.namespace.slug

                        var line = Component.text(" ● ", NamedTextColor.DARK_GRAY)
                            .append(
                                Component.text(project.name, NamedTextColor.WHITE, TextDecoration.BOLD)
                                    .hoverEvent(HoverEvent.showText(Component.text("Click to install", NamedTextColor.GREEN)))
                                    .clickEvent(ClickEvent.suggestCommand("/wpm install $slug"))
                            )

                        if (!slug.equals(project.name, ignoreCase = true)) {
                            line = line.append(Component.text(" ($slug)", NamedTextColor.DARK_GRAY))
                        }

                        line = line
                            .append(Component.text(" by ", NamedTextColor.GRAY))
                            .append(Component.text(project.namespace.owner, NamedTextColor.AQUA))
                            .append(Component.text(" ⬇${project.stats.downloads}", NamedTextColor.DARK_GRAY))

                        sender.sendMessage(line)

                        if (project.description.isNotBlank()) {
                            sender.sendMessage(
                                Component.text("   ${project.description}", NamedTextColor.GRAY)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                sync { sender.sendError("Search failed: ${e.message}") }
            }
        })
    }

    private fun handleInstall(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendError("Usage: /wpm install <slug>")
            return
        }
        plugin.installManager.installFromHangar(sender, args[1])
    }

    private fun handleGitHub(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendError("Usage: /wpm github <owner/repo>")
            return
        }
        plugin.installManager.installFromGitHub(sender, args[1])
    }

    private fun handleGeyser(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendError("Usage: /wpm geyser <geyser|floodgate>")
            return
        }
        plugin.installManager.installFromGeyserMc(sender, args[1])
    }

    private fun handleRemove(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendError("Usage: /wpm remove <name>")
            return
        }
        plugin.installManager.removePlugin(sender, args[1])
    }

    private fun handleDisable(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendError("Usage: /wpm disable <name>")
            return
        }
        plugin.installManager.disablePlugin(sender, args[1])
    }

    private fun handleEnable(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendError("Usage: /wpm enable <name>")
            return
        }
        plugin.installManager.enablePlugin(sender, args[1])
    }

    private fun handlePin(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendError("Usage: /wpm pin <name>")
            return
        }
        plugin.installManager.pinPlugin(sender, args[1])
    }

    private fun handleUnpin(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendError("Usage: /wpm unpin <name>")
            return
        }
        plugin.installManager.unpinPlugin(sender, args[1])
    }

    private fun handleUpdate(sender: CommandSender, args: Array<out String>) {
        val name = if (args.size >= 2) args[1] else null
        plugin.installManager.updatePlugin(sender, name)
    }

    private fun handleList(sender: CommandSender) {
        val tracked = plugin.pluginTracker.getAllTracked()
        if (tracked.isEmpty()) {
            sender.sendInfo("No plugins are tracked by WPM.")
            return
        }

        sender.sendInfo("Tracked plugins (${tracked.size}):")
        sender.sendMessage(Component.empty())

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            var updatesAvailable = 0

            for (tp in tracked) {
                val sourceLabel = when (tp.source) {
                    "hangar" -> "Hangar"
                    "github" -> "GitHub"
                    "geysermc" -> "GeyserMC"
                    else -> tp.source
                }

                val latestVersion = try {
                    when (tp.source) {
                        "hangar" -> plugin.hangarClient.getLatestVersion(tp.sourceIdentifier)
                        "github" -> {
                            val parts = tp.sourceIdentifier.split("/", limit = 2)
                            if (parts.size == 2) {
                                plugin.gitHubClient.getLatestRelease(parts[0], parts[1])?.tagName
                            } else null
                        }
                        "geysermc" -> {
                            val build = plugin.geyserMcClient.getLatestBuild(tp.sourceIdentifier)
                            build?.let { "${it.version}-b${it.build}" }
                        }
                        else -> null
                    }
                } catch (_: Exception) {
                    null
                }

                val hasUpdate = latestVersion != null && !VersionUtils.isSameVersion(tp.installedVersion, latestVersion)
                if (hasUpdate && !tp.pinned) updatesAvailable++

                val isDisabled = tp.fileName.endsWith(".disabled")

                sync {
                    var line = Component.text(" ● ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(tp.name, NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text(" v${tp.installedVersion}", NamedTextColor.GREEN))
                        .append(Component.text(" ($sourceLabel: ${tp.sourceIdentifier})", NamedTextColor.GRAY))

                    if (isDisabled) {
                        line = line.append(Component.text(" [disabled]", NamedTextColor.RED, TextDecoration.BOLD))
                    }

                    if (tp.pinned) {
                        line = line.append(Component.text(" [pinned]", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    }

                    if (hasUpdate && tp.pinned) {
                        line = line.append(Component.text(" → $latestVersion available", NamedTextColor.DARK_GRAY))
                    } else if (hasUpdate) {
                        line = line
                            .append(Component.text(" → $latestVersion", NamedTextColor.YELLOW))
                            .append(
                                Component.text(" [Update]", NamedTextColor.GREEN, TextDecoration.BOLD)
                                    .hoverEvent(HoverEvent.showText(Component.text("Click to update ${tp.name}", NamedTextColor.GREEN)))
                                    .clickEvent(ClickEvent.suggestCommand("/wpm update ${tp.name}"))
                            )
                    }

                    sender.sendMessage(line)
                }
            }

            sync {
                sender.sendMessage(Component.empty())
                if (updatesAvailable > 0) {
                    sender.sendInfo("$updatesAvailable update(s) available. Use /wpm update to update all.")
                } else {
                    sender.sendInfo("All plugins are up to date.")
                }
            }
        })
    }

    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendError("Usage: /wpm info <slug>")
            return
        }

        val slug = args[1]
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val project = plugin.hangarClient.getProject(slug)
                if (project == null) {
                    sync { sender.sendError("Plugin '$slug' not found on Hangar.") }
                    return@Runnable
                }

                val latestVersion = plugin.hangarClient.getLatestVersion(slug)

                sync {
                    sender.sendInfo("Plugin info for ${project.name}:")
                    sender.sendMessage(Component.empty())
                    sender.sendMessage(Component.text(" Author: ", NamedTextColor.GRAY)
                        .append(Component.text(project.namespace.owner, NamedTextColor.WHITE)))
                    sender.sendMessage(Component.text(" Description: ", NamedTextColor.GRAY)
                        .append(Component.text(project.description, NamedTextColor.WHITE)))
                    sender.sendMessage(Component.text(" Downloads: ", NamedTextColor.GRAY)
                        .append(Component.text("${project.stats.downloads}", NamedTextColor.WHITE)))
                    sender.sendMessage(Component.text(" Stars: ", NamedTextColor.GRAY)
                        .append(Component.text("${project.stats.stars}", NamedTextColor.WHITE)))
                    if (latestVersion != null) {
                        sender.sendMessage(Component.text(" Latest: ", NamedTextColor.GRAY)
                            .append(Component.text(latestVersion, NamedTextColor.GREEN)))
                    }
                    sender.sendMessage(Component.empty())

                    val installBtn = Component.text(" [Install]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("Click to install", NamedTextColor.GREEN)))
                        .clickEvent(ClickEvent.suggestCommand("/wpm install $slug"))
                    sender.sendMessage(installBtn)
                }
            } catch (e: Exception) {
                sync { sender.sendError("Failed to fetch info: ${e.message}") }
            }
        })
    }

    private fun handleIdentify(sender: CommandSender, args: Array<out String>) {
        if (args.size >= 2 && args[1].equals("link", ignoreCase = true)) {
            handleIdentifyLink(sender, args)
            return
        }

        // Scan mode: find untracked plugins and search Hangar for matches
        val serverPlugins = Bukkit.getPluginManager().plugins
        val untracked = serverPlugins.filter { bp ->
            bp.name != plugin.pluginMeta.name && !plugin.pluginTracker.isTracked(bp.name)
        }

        if (untracked.isEmpty()) {
            sender.sendInfo("All loaded plugins are already tracked by WPM.")
            return
        }

        sender.sendInfo("Scanning ${untracked.size} untracked plugin(s)...")

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                var matched = 0
                var unmatched = 0

                for ((index, bp) in untracked.withIndex()) {
                    // Rate limit: pause every 15 plugins
                    if (index > 0 && index % 15 == 0) {
                        sync { sender.sendInfo("Searching... (${index}/${untracked.size})") }
                        Thread.sleep(5500)
                    }

                    val pluginName = bp.name
                    val version = bp.pluginMeta.version

                    // Resolve JAR filename via reflection
                    val jarFileName = try {
                        val getFileMethod = JavaPlugin::class.java.getDeclaredMethod("getFile")
                        getFileMethod.isAccessible = true
                        (getFileMethod.invoke(bp) as? java.io.File)?.name ?: "unknown.jar"
                    } catch (_: Exception) {
                        "unknown.jar"
                    }

                    val searchResult = try {
                        plugin.hangarClient.searchProjects(pluginName, 5)
                    } catch (_: RateLimitException) {
                        sync { sender.sendInfo("Rate limited, waiting...") }
                        Thread.sleep(5500)
                        try { plugin.hangarClient.searchProjects(pluginName, 5) } catch (_: Exception) { null }
                    } catch (_: Exception) {
                        null
                    }

                    sync {
                        sender.sendMessage(Component.empty())

                        val header = Component.text(" ● ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(pluginName, NamedTextColor.WHITE, TextDecoration.BOLD))
                            .append(Component.text(" ($jarFileName)", NamedTextColor.DARK_GRAY))
                            .append(Component.text(" v$version", NamedTextColor.GREEN))
                        sender.sendMessage(header)

                        if (searchResult == null || searchResult.result.isEmpty()) {
                            unmatched++
                            val noMatch = Component.text("   ✘ No match found  ", NamedTextColor.RED)
                                .append(
                                    Component.text("[Link manually]", NamedTextColor.YELLOW)
                                        .hoverEvent(HoverEvent.showText(Component.text("hangar:slug, github:owner/repo, or geysermc:project", NamedTextColor.YELLOW)))
                                        .clickEvent(ClickEvent.suggestCommand("/wpm identify link $pluginName "))
                                )
                            sender.sendMessage(noMatch)
                            return@sync
                        }

                        var shown = 0
                        for (project in searchResult.result) {
                            if (shown >= 3) break

                            val slug = project.namespace.slug
                            val confidence = when {
                                slug.equals(pluginName, ignoreCase = true) -> "high"
                                project.name.equals(pluginName, ignoreCase = true) -> "high"
                                slug.contains(pluginName, ignoreCase = true) || pluginName.contains(slug, ignoreCase = true) -> "medium"
                                project.name.contains(pluginName, ignoreCase = true) || pluginName.contains(project.name, ignoreCase = true) -> "medium"
                                shown == 0 && project.stats.downloads > 1000 -> "low"
                                else -> continue
                            }

                            if (confidence == "high" && shown == 0) matched++ else if (shown == 0) unmatched++

                            val prefix = if (confidence == "high") "   ✔ Match: " else "   ? Possible: "
                            val prefixColor = if (confidence == "high") NamedTextColor.GREEN else NamedTextColor.YELLOW

                            val line = Component.text(prefix, prefixColor)
                                .append(Component.text(project.name, NamedTextColor.WHITE))
                                .append(Component.text(" by ", NamedTextColor.GRAY))
                                .append(Component.text(project.namespace.owner, NamedTextColor.AQUA))
                                .append(Component.text(" (⬇${project.stats.downloads})  ", NamedTextColor.DARK_GRAY))
                                .append(
                                    Component.text("[Link]", NamedTextColor.GREEN)
                                        .hoverEvent(HoverEvent.showText(Component.text("Link $pluginName to hangar:$slug", NamedTextColor.GREEN)))
                                        .clickEvent(ClickEvent.suggestCommand("/wpm identify link $pluginName hangar:$slug"))
                                )

                            sender.sendMessage(line)
                            shown++
                        }

                        if (shown == 0) {
                            unmatched++
                            val noMatch = Component.text("   ✘ No match found  ", NamedTextColor.RED)
                                .append(
                                    Component.text("[Link manually]", NamedTextColor.YELLOW)
                                        .hoverEvent(HoverEvent.showText(Component.text("hangar:slug, github:owner/repo, or geysermc:project", NamedTextColor.YELLOW)))
                                        .clickEvent(ClickEvent.suggestCommand("/wpm identify link $pluginName "))
                                )
                            sender.sendMessage(noMatch)
                        }
                    }
                }

                sync {
                    sender.sendMessage(Component.empty())
                    sender.sendInfo("Done. $matched matched, $unmatched unmatched.")
                }
            } catch (e: Exception) {
                sync { sender.sendError("Identify scan failed: ${e.message}") }
                plugin.logger.warning("Identify scan error: ${e.stackTraceToString()}")
            }
        })
    }

    private fun handleIdentifyLink(sender: CommandSender, args: Array<out String>) {
        // /wpm identify link <plugin> <hangar:slug|github:owner/repo|geysermc:project>
        if (args.size < 4) {
            sender.sendError("Usage: /wpm identify link <plugin> <hangar:slug|github:owner/repo|geysermc:project>")
            return
        }

        val pluginName = args[2]
        val sourceArg = args[3]

        // Validate plugin is loaded
        val bp = Bukkit.getPluginManager().getPlugin(pluginName)
        if (bp == null) {
            sender.sendError("Plugin '$pluginName' is not loaded on this server.")
            return
        }

        // Validate not already tracked
        if (plugin.pluginTracker.isTracked(pluginName)) {
            sender.sendError("Plugin '$pluginName' is already tracked by WPM.")
            return
        }

        // Parse source
        val source: String
        val sourceIdentifier: String
        when {
            sourceArg.startsWith("hangar:", ignoreCase = true) -> {
                source = "hangar"
                sourceIdentifier = sourceArg.substringAfter(":")
            }
            sourceArg.startsWith("github:", ignoreCase = true) -> {
                source = "github"
                sourceIdentifier = sourceArg.substringAfter(":")
                if (!sourceIdentifier.contains("/")) {
                    sender.sendError("GitHub source must be in format github:owner/repo")
                    return
                }
            }
            sourceArg.startsWith("geysermc:", ignoreCase = true) -> {
                source = "geysermc"
                sourceIdentifier = sourceArg.substringAfter(":").lowercase()
                if (sourceIdentifier !in GeyserMcClient.SUPPORTED_PROJECTS) {
                    sender.sendError("Unsupported GeyserMC project. Supported: ${GeyserMcClient.SUPPORTED_PROJECTS.joinToString(", ")}")
                    return
                }
            }
            else -> {
                sender.sendError("Source must start with hangar:, github:, or geysermc: (e.g. hangar:EssentialsX, github:owner/repo, geysermc:floodgate)")
                return
            }
        }

        // Resolve JAR filename via reflection
        val jarFileName = try {
            val getFileMethod = JavaPlugin::class.java.getDeclaredMethod("getFile")
            getFileMethod.isAccessible = true
            (getFileMethod.invoke(bp) as? java.io.File)?.name ?: "${bp.name}.jar"
        } catch (_: Exception) {
            "${bp.name}.jar"
        }

        val version = bp.pluginMeta.version

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                // Validate source exists
                when (source) {
                    "hangar" -> {
                        val project = plugin.hangarClient.getProject(sourceIdentifier)
                        if (project == null) {
                            sync { sender.sendError("Hangar project '$sourceIdentifier' not found.") }
                            return@Runnable
                        }
                    }
                    "github" -> {
                        val parts = sourceIdentifier.split("/", limit = 2)
                        val release = plugin.gitHubClient.getLatestRelease(parts[0], parts[1])
                        if (release == null) {
                            sync { sender.sendError("GitHub repository '$sourceIdentifier' not found or has no releases.") }
                            return@Runnable
                        }
                    }
                    "geysermc" -> {
                        val build = plugin.geyserMcClient.getLatestBuild(sourceIdentifier)
                        if (build == null) {
                            sync { sender.sendError("GeyserMC project '$sourceIdentifier' not found.") }
                            return@Runnable
                        }
                    }
                }

                plugin.pluginTracker.track(
                    TrackedPlugin(
                        name = pluginName,
                        source = source,
                        sourceIdentifier = sourceIdentifier,
                        installedVersion = version,
                        fileName = jarFileName,
                        installedAt = System.currentTimeMillis()
                    )
                )

                sync {
                    sender.sendSuccess("Linked $pluginName to $source:$sourceIdentifier (v$version).")
                    sender.sendInfo("Use /wpm update $pluginName to check for updates.")
                }
            } catch (e: RateLimitException) {
                sync { sender.sendError(e.message ?: "Rate limit exceeded.") }
            } catch (e: Exception) {
                sync { sender.sendError("Failed to link plugin: ${e.message}") }
                plugin.logger.warning("Identify link error: ${e.stackTraceToString()}")
            }
        })
    }

    private fun handleUnlink(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendError("Usage: /wpm unlink <name>")
            return
        }

        val name = args[1]
        val tracked = plugin.pluginTracker.getTracked(name)
        if (tracked == null) {
            sender.sendError("Plugin '$name' is not tracked by WPM.")
            return
        }

        plugin.pluginTracker.untrack(name)
        sender.sendSuccess("Unlinked ${tracked.name} from ${tracked.source}:${tracked.sourceIdentifier}. The plugin file remains on disk.")
    }

    private fun handleRelink(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendError("Usage: /wpm relink <name> <hangar:slug|github:owner/repo|geysermc:project>")
            return
        }

        val name = args[1]
        val sourceArg = args[2]

        val tracked = plugin.pluginTracker.getTracked(name)
        if (tracked == null) {
            sender.sendError("Plugin '$name' is not tracked by WPM.")
            return
        }

        // Parse source
        val source: String
        val sourceIdentifier: String
        when {
            sourceArg.startsWith("hangar:", ignoreCase = true) -> {
                source = "hangar"
                sourceIdentifier = sourceArg.substringAfter(":")
            }
            sourceArg.startsWith("github:", ignoreCase = true) -> {
                source = "github"
                sourceIdentifier = sourceArg.substringAfter(":")
                if (!sourceIdentifier.contains("/")) {
                    sender.sendError("GitHub source must be in format github:owner/repo")
                    return
                }
            }
            sourceArg.startsWith("geysermc:", ignoreCase = true) -> {
                source = "geysermc"
                sourceIdentifier = sourceArg.substringAfter(":").lowercase()
                if (sourceIdentifier !in GeyserMcClient.SUPPORTED_PROJECTS) {
                    sender.sendError("Unsupported GeyserMC project. Supported: ${GeyserMcClient.SUPPORTED_PROJECTS.joinToString(", ")}")
                    return
                }
            }
            else -> {
                sender.sendError("Source must start with hangar:, github:, or geysermc:")
                return
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                // Validate source exists
                when (source) {
                    "hangar" -> {
                        val project = plugin.hangarClient.getProject(sourceIdentifier)
                        if (project == null) {
                            sync { sender.sendError("Hangar project '$sourceIdentifier' not found.") }
                            return@Runnable
                        }
                    }
                    "github" -> {
                        val parts = sourceIdentifier.split("/", limit = 2)
                        val release = plugin.gitHubClient.getLatestRelease(parts[0], parts[1])
                        if (release == null) {
                            sync { sender.sendError("GitHub repository '$sourceIdentifier' not found or has no releases.") }
                            return@Runnable
                        }
                    }
                    "geysermc" -> {
                        val build = plugin.geyserMcClient.getLatestBuild(sourceIdentifier)
                        if (build == null) {
                            sync { sender.sendError("GeyserMC project '$sourceIdentifier' not found.") }
                            return@Runnable
                        }
                    }
                }

                plugin.pluginTracker.track(tracked.copy(
                    source = source,
                    sourceIdentifier = sourceIdentifier
                ))

                sync {
                    sender.sendSuccess("Relinked ${tracked.name} to $source:$sourceIdentifier.")
                }
            } catch (e: RateLimitException) {
                sync { sender.sendError(e.message ?: "Rate limit exceeded.") }
            } catch (e: Exception) {
                sync { sender.sendError("Failed to relink plugin: ${e.message}") }
                plugin.logger.warning("Relink error: ${e.stackTraceToString()}")
            }
        })
    }

    private fun handleRollback(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendError("Usage: /wpm rollback <name> [version]")
            return
        }

        val name = args[1]
        val tracked = plugin.pluginTracker.getTracked(name)
        if (tracked == null) {
            sender.sendError("Plugin '$name' is not tracked by WPM.")
            return
        }

        val backups = plugin.installManager.getBackups(tracked.name)
        if (backups.isEmpty()) {
            sender.sendError("No backups found for ${tracked.name}.")
            return
        }

        if (args.size >= 3) {
            // Direct rollback to specified version
            plugin.installManager.rollbackPlugin(sender, name, args[2])
            return
        }

        // Show available backups with clickable rollback buttons
        sender.sendInfo("Available backups for ${tracked.name}:")
        sender.sendMessage(Component.empty())

        for (backup in backups) {
            val version = backup.nameWithoutExtension.substringAfter("${tracked.name}-")
            val sizeMb = "%.1f".format(backup.length() / 1024.0 / 1024.0)

            val line = Component.text(" ● ", NamedTextColor.DARK_GRAY)
                .append(Component.text(version, NamedTextColor.WHITE))
                .append(Component.text(" (${sizeMb}MB)", NamedTextColor.GRAY))
                .append(Component.text("  "))
                .append(
                    Component.text("[Rollback]", NamedTextColor.YELLOW, TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("Rollback to $version", NamedTextColor.YELLOW)))
                        .clickEvent(ClickEvent.suggestCommand("/wpm rollback ${tracked.name} $version"))
                )

            sender.sendMessage(line)
        }

        sender.sendMessage(Component.empty())
        sender.sendInfo("Current version: ${tracked.installedVersion}")
    }

    private fun handleReload(sender: CommandSender) {
        plugin.reloadConfig()
        sender.sendSuccess("Configuration reloaded.")
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage(Component.empty())
        sender.sendMessage(Messages.raw("<gradient:#5e4fa2:#f79459><bold>Whero Plugin Manager</bold></gradient> <gray>v${plugin.pluginMeta.version}</gray>"))
        sender.sendMessage(Component.empty())

        val commands = listOf(
            "/wpm search <query>" to "Search Hangar for plugins",
            "/wpm install <slug>" to "Install plugin from Hangar",
            "/wpm github <owner/repo>" to "Install from GitHub Releases",
            "/wpm geyser <project>" to "Install from GeyserMC Downloads",
            "/wpm remove <name>" to "Remove an installed plugin",
            "/wpm disable <name>" to "Disable a plugin on next reload",
            "/wpm enable <name>" to "Enable a disabled plugin on next reload",
            "/wpm pin <name>" to "Pin a plugin at its current version",
            "/wpm unpin <name>" to "Unpin a plugin so it updates again",
            "/wpm update [name]" to "Update one or all plugins",
            "/wpm list" to "List tracked plugins",
            "/wpm info <slug>" to "Show Hangar plugin details",
            "/wpm identify" to "Scan & link untracked plugins",
            "/wpm rollback <name>" to "Roll back to a previous version",
            "/wpm unlink <name>" to "Unlink a plugin from its source",
            "/wpm relink <name> <source>" to "Change a plugin's source",
            "/wpm reload" to "Reload configuration"
        )

        for ((cmd, desc) in commands) {
            sender.sendMessage(
                Component.text(" $cmd", NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.suggestCommand(cmd.split(" ").take(2).joinToString(" ") + " "))
                    .append(Component.text(" - $desc", NamedTextColor.GRAY))
            )
        }
        sender.sendMessage(Component.empty())
    }

    private fun sync(block: () -> Unit) {
        Bukkit.getScheduler().runTask(plugin, Runnable { block() })
    }
}
