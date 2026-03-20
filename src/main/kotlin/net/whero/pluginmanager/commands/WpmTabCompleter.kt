package net.whero.pluginmanager.commands

import net.whero.pluginmanager.WheroPluginManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class WpmTabCompleter(private val plugin: WheroPluginManager) : TabCompleter {

    private val subcommands = listOf("search", "install", "github", "geyser", "remove", "update", "list", "info", "identify", "rollback", "unlink", "relink", "reload")

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return subcommands.filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 2) {
            return when (args[0].lowercase()) {
                "remove", "update", "unlink", "relink", "rollback" -> {
                    plugin.pluginTracker.getTrackedNames()
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                }
                "geyser" -> {
                    net.whero.pluginmanager.api.GeyserMcClient.SUPPORTED_PROJECTS.toList()
                        .filter { it.startsWith(args[1].lowercase()) }
                }
                "identify" -> {
                    listOf("link").filter { it.startsWith(args[1].lowercase()) }
                }
                else -> emptyList()
            }
        }

        if (args[0].equals("rollback", ignoreCase = true) && args.size == 3) {
            val tracked = plugin.pluginTracker.getTracked(args[1])
            if (tracked != null) {
                return plugin.installManager.getBackups(tracked.name)
                    .map { it.nameWithoutExtension.substringAfter("${tracked.name}-") }
                    .filter { it.lowercase().startsWith(args[2].lowercase()) }
            }
        }

        if (args[0].equals("relink", ignoreCase = true) && args.size == 3) {
            return listOf("hangar:", "github:", "geysermc:")
                .filter { it.startsWith(args[2].lowercase()) }
        }

        if (args[0].equals("identify", ignoreCase = true) && args[1].equals("link", ignoreCase = true)) {
            if (args.size == 3) {
                // Suggest untracked plugin names
                return Bukkit.getPluginManager().plugins
                    .filter { it.name != plugin.pluginMeta.name && !plugin.pluginTracker.isTracked(it.name) }
                    .map { it.name }
                    .filter { it.lowercase().startsWith(args[2].lowercase()) }
            }
            if (args.size == 4) {
                return listOf("hangar:", "github:", "geysermc:")
                    .filter { it.startsWith(args[3].lowercase()) }
            }
        }

        return emptyList()
    }
}
