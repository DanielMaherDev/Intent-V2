package com.intent.launcher.engine

/**
 * Platform adapter for Do Not Disturb / notification-policy access probes.
 * Reading grant status does not enable INTENT to silence apps — quiet_mode
 * stays honesty-gated in [QuietModeProbe].
 */
interface QuietModeAccess {
    /** True when the user has granted Notification policy access to this package. */
    fun hasNotificationPolicyAccess(): Boolean
}

/** Test stub. */
class FakeQuietModeAccess(
    var policyAccessGranted: Boolean = false
) : QuietModeAccess {
    override fun hasNotificationPolicyAccess(): Boolean = policyAccessGranted
}
