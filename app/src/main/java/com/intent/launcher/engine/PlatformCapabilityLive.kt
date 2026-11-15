package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry

/** Chains live platform probes into one honest [CapabilityRegistry]. */
object PlatformCapabilityLive {
    fun registry(
        usage: UsageStatsAccess,
        overlay: OverlayPermissionAccess,
        voicePermission: VoicePermissionAccess = FakeVoicePermissionAccess(granted = false),
        speech: SpeechToTextAccess = FakeSpeechToTextAccess(available = false),
        notifications: NotificationListenerAccess = FakeNotificationListenerAccess(granted = false),
        quietMode: QuietModeAccess = FakeQuietModeAccess(policyAccessGranted = false),
        screenCapture: ScreenCaptureAccess = FakeScreenCaptureAccess(),
        homeRole: HomeRoleAccess = FakeHomeRoleAccess(isDefault = false),
        liveLlm: LiveLlmAccess = FakeLiveLlmAccess()
    ): CapabilityRegistry {
        val withUsage = UsageStatsProbe.liveRegistry(usage)
        val withOverlay = OverlayPermissionProbe.liveRegistry(overlay, base = withUsage.all())
        val withHome = HomeRoleProbe.liveRegistry(homeRole, base = withOverlay.all())
        val withVoice = VoiceInputProbe.liveRegistry(
            permission = voicePermission,
            speech = speech,
            base = withHome.all()
        )
        val withReview = SessionNotificationReviewProbe.liveRegistry(
            access = notifications,
            base = withVoice.all()
        )
        val withQuiet = QuietModeProbe.liveRegistry(
            access = quietMode,
            base = withReview.all()
        )
        val withCapture = ScreenCaptureProbe.liveRegistry(
            access = screenCapture,
            base = withQuiet.all()
        )
        return LiveLlmProbe.liveRegistry(liveLlm, base = withCapture.all())
    }
}
