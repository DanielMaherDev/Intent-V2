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

    /**
     * Sum of package foreground time across apps in [startEpochMs, endEpochMs].
     * Honest screen-time proxy only — not lesson or video identity.
     * Null when permission is missing or the query fails.
     */
    fun totalForegroundMs(
        startEpochMs: Long,
        endEpochMs: Long
    ): Long?

    /**
     * Package currently in the foreground (most recent resume/move-to-foreground).
     * Null when permission is missing, query fails, or no event is available.
     */
    fun currentForegroundPackage(): String?

    /**
     * Wall-clock when [packageName] last left the foreground inside the window,
     * or null when unknown / still foreground / no events.
     */
    fun lastTimePackageLeftForeground(
        packageName: String,
        windowStartEpochMs: Long,
        endEpochMs: Long
    ): Long?
}

/** Test stub. */
class FakeUsageStatsAccess(
    var granted: Boolean = false,
    private val foregroundByPackage: Map<String, Long> = emptyMap(),
    var deviceTotalForegroundMs: Long? = null,
    var currentForeground: String? = null,
    var lastLeftForegroundByPackage: Map<String, Long> = emptyMap()
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

    override fun totalForegroundMs(
        startEpochMs: Long,
        endEpochMs: Long
    ): Long? {
        if (!granted) return null
        return deviceTotalForegroundMs
            ?: foregroundByPackage.values.sum().takeIf { foregroundByPackage.isNotEmpty() }
            ?: 0L
    }

    override fun currentForegroundPackage(): String? =
        if (!granted) null else currentForeground

    override fun lastTimePackageLeftForeground(
        packageName: String,
        windowStartEpochMs: Long,
        endEpochMs: Long
    ): Long? {
        if (!granted) return null
        return lastLeftForegroundByPackage[packageName]
    }
}
