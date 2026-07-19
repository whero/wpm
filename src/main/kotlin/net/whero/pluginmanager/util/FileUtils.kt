package net.whero.pluginmanager.util

import java.io.File
import java.util.zip.ZipFile

/**
 * Helpers for safely turning untrusted names (from remote APIs or persisted
 * tracking data) into paths inside a known directory.
 */
object FileUtils {

    /**
     * True if [name] is a plain file name: non-blank, not a dot segment, and
     * containing no path separators on any platform.
     */
    fun isPlainFileName(name: String): Boolean =
        name.isNotBlank() &&
            name != "." &&
            name != ".." &&
            name.indexOf('/') == -1 &&
            name.indexOf('\\') == -1

    /**
     * Resolves [fileName] directly inside [dir]. Returns null when the name is not
     * a plain file name or the resolved path would escape [dir] (path traversal or
     * symlinks).
     */
    fun resolveInDir(dir: File, fileName: String): File? {
        if (!isPlainFileName(fileName)) return null
        val base = dir.canonicalFile
        val resolved = File(base, fileName).canonicalFile
        return if (resolved.parentFile == base) resolved else null
    }

    /**
     * True if [file] is a readable ZIP archive containing a Bukkit/Paper plugin
     * descriptor. Used as a minimum integrity check for downloads that carry no
     * publisher hash (e.g. GitHub release assets).
     */
    fun isValidPluginJar(file: File): Boolean =
        try {
            ZipFile(file).use { zip ->
                zip.getEntry("plugin.yml") != null || zip.getEntry("paper-plugin.yml") != null
            }
        } catch (_: Exception) {
            false
        }
}
