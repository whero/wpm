package net.whero.pluginmanager.managers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.whero.pluginmanager.api.TrackedPlugin
import net.whero.pluginmanager.util.FileUtils
import java.io.File
import java.util.logging.Logger

class PluginTracker(private val dataFolder: File, private val logger: Logger) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val trackingFile = File(dataFolder, "installed-plugins.json")
    private val tracked = mutableMapOf<String, TrackedPlugin>()

    init {
        load()
    }

    fun track(plugin: TrackedPlugin) {
        tracked[plugin.name.lowercase()] = plugin
        save()
    }

    fun untrack(name: String): Boolean {
        val removed = tracked.remove(name.lowercase()) != null
        if (removed) save()
        return removed
    }

    fun getTracked(name: String): TrackedPlugin? =
        tracked[name.lowercase()]

    fun getAllTracked(): List<TrackedPlugin> =
        tracked.values.toList()

    fun isTracked(name: String): Boolean =
        tracked.containsKey(name.lowercase())

    fun getTrackedNames(): List<String> =
        tracked.values.map { it.name }

    private fun load() {
        if (!trackingFile.exists()) return
        val json = trackingFile.readText()
        if (json.isBlank()) return

        val type = object : TypeToken<List<TrackedPlugin>>() {}.type
        val list: List<TrackedPlugin> = try {
            gson.fromJson(json, type) ?: return
        } catch (e: Exception) {
            logger.warning("Could not parse installed-plugins.json (${e.message}); starting with empty tracking data.")
            return
        }
        tracked.clear()
        for (entry in list) {
            // Tracking data drives file operations; drop entries with unsafe file names
            if (!FileUtils.isPlainFileName(entry.fileName)) {
                logger.warning("Ignoring tracked plugin '${entry.name}': unsafe fileName '${entry.fileName}' in installed-plugins.json")
                continue
            }
            tracked[entry.name.lowercase()] = entry
        }
    }

    private fun save() {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        trackingFile.writeText(gson.toJson(tracked.values.toList()))
    }
}
