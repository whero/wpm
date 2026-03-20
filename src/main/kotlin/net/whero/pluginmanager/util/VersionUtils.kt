package net.whero.pluginmanager.util

object VersionUtils {

    /**
     * Normalizes a version string for comparison by stripping:
     * - Leading "v" prefix (e.g. "v1.2.0" → "1.2.0")
     * - Build metadata after "+" (e.g. "7.4.0+7381-3decaf0" → "7.4.0")
     * - Surrounding whitespace
     */
    fun normalize(version: String): String {
        return version
            .trim()
            .removePrefix("v")
            .substringBefore("+")
    }

    /**
     * Returns true if [installed] and [latest] represent the same version
     * after normalization.
     */
    fun isSameVersion(installed: String, latest: String): Boolean {
        return normalize(installed) == normalize(latest)
    }
}
