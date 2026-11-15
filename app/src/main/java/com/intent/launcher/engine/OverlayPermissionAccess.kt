package com.intent.launcher.engine

/**
 * Platform adapter for SYSTEM_ALERT_WINDOW / draw-over-apps.
 * Granting permission does not ship a floating agent in this build.
 */
interface OverlayPermissionAccess {
    fun canDrawOverlays(): Boolean
}

class FakeOverlayPermissionAccess(
    var granted: Boolean = false
) : OverlayPermissionAccess {
    override fun canDrawOverlays(): Boolean = granted
}
