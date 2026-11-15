package com.intent.launcher.engine

/**
 * Platform adapter for package-level UsageStats.
 * Never identifies lessons, videos, or in-app completion — package foreground only.
 */
interface UsageStatsAccess {
    fun hasPermission(): Boolean

    /**
     * Aggregate foreground time for [packageName] in [startEpochMs, endEpochMs].
     * Null when permission is missing or the query fails.
     */
    fun foregroundMs(
        packageName: String,
        startEpochMs: Long,
        endEpochMs: Long
    ): Long?
}

/** Test stub. */
class FakeUsageStatsAccess(
    var granted: Boolean = false,
    private val foregroundByPackage: Map<String, Long> = emptyMap()
) : UsageStatsAccess {
    override fun hasPermission(): Boolean = granted

    override fun foregroundMs(
        packageName: String,
        startEpochMs: Long,
        endEpochMs: Long
    ): Long? {
        if (!granted) return null
        return foregroundByPackage[packageName] ?: 0L
    }
}
