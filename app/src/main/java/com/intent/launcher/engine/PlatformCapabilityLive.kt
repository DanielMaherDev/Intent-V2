package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry

/** Chains live platform probes into one honest [CapabilityRegistry]. */
object PlatformCapabilityLive {
    fun registry(
        usage: UsageStatsAccess,
        overlay: OverlayPermissionAccess,
        voicePermission: VoicePermissionAccess = FakeVoicePermissionAccess(granted = false),
        speech: SpeechToTextAccess = FakeSpeechToTextAccess(available = false),
        notifications: NotificationListenerAccess = FakeNotificationListenerAccess(granted = false)
    ): CapabilityRegistry {
        val withUsage = UsageStatsProbe.liveRegistry(usage)
        val withOverlay = OverlayPermissionProbe.liveRegistry(overlay, base = withUsage.all())
        val withVoice = VoiceInputProbe.liveRegistry(
            permission = voicePermission,
            speech = speech,
            base = withOverlay.all()
        )
        return NotificationListenerProbe.liveRegistry(
            access = notifications,
            base = withVoice.all()
        )
    }
}
