package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry

/** Chains live platform probes into one honest [CapabilityRegistry]. */
object PlatformCapabilityLive {
    fun registry(
        usage: UsageStatsAccess,
        overlay: OverlayPermissionAccess
    ): CapabilityRegistry {
        val withUsage = UsageStatsProbe.liveRegistry(usage)
        return OverlayPermissionProbe.liveRegistry(overlay, base = withUsage.all())
    }
}
