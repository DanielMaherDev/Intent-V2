package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry

/** Chains live platform probes into one honest [CapabilityRegistry]. */
object PlatformCapabilityLive {
    fun registry(
        usage: UsageStatsAccess,
        overlay: OverlayPermissionAccess,
        voicePermission: VoicePermissionAccess = FakeVoicePermissionAccess(granted = false),
        speech: SpeechToTextAccess = FakeSpeechToTextAccess(available = false)
    ): CapabilityRegistry {
        val withUsage = UsageStatsProbe.liveRegistry(usage)
        val withOverlay = OverlayPermissionProbe.liveRegistry(overlay, base = withUsage.all())
        return VoiceInputProbe.liveRegistry(
            permission = voicePermission,
            speech = speech,
            base = withOverlay.all()
        )
    }
}
