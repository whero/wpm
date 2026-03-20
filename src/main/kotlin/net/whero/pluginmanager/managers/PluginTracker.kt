package net.whero.pluginmanager.managers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.whero.pluginmanager.api.TrackedPlugin
import java.io.File

class PluginTracker(private val dataFolder: File) {

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
        val list: List<TrackedPlugin> = gson.fromJson(json, type) ?: return
        tracked.clear()
        list.forEach { tracked[it.name.lowercase()] = it }
    }

    private fun save() {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        trackingFile.writeText(gson.toJson(tracked.values.toList()))
    }
}
