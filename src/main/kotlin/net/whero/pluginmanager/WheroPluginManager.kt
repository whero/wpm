package net.whero.pluginmanager

import net.whero.pluginmanager.api.GeyserMcClient
import net.whero.pluginmanager.api.GitHubClient
import net.whero.pluginmanager.api.HangarClient
import net.whero.pluginmanager.commands.WpmCommand
import net.whero.pluginmanager.commands.WpmTabCompleter
import net.whero.pluginmanager.managers.PluginInstallManager
import net.whero.pluginmanager.managers.PluginTracker
import org.bukkit.plugin.java.JavaPlugin

class WheroPluginManager : JavaPlugin() {

    lateinit var hangarClient: HangarClient
        private set
    lateinit var gitHubClient: GitHubClient
        private set
    lateinit var geyserMcClient: GeyserMcClient
        private set
    lateinit var pluginTracker: PluginTracker
        private set
    lateinit var installManager: PluginInstallManager
        private set

    override fun onEnable() {
        saveDefaultConfig()

        hangarClient = HangarClient(logger)
        gitHubClient = GitHubClient()
        geyserMcClient = GeyserMcClient(logger)
        pluginTracker = PluginTracker(dataFolder)
        installManager = PluginInstallManager(this)

        val command = getCommand("wpm")
        command?.setExecutor(WpmCommand(this))
        command?.tabCompleter = WpmTabCompleter(this)

        logger.info("WheroPluginManager enabled!")
    }

    override fun onDisable() {
        logger.info("WheroPluginManager disabled.")
    }
}
