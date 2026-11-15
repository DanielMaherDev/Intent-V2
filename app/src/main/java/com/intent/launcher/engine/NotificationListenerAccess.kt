package com.intent.launcher.engine

/**
 * Platform adapter for NotificationListenerService access.
 * Grant enables permitted notification summaries only — never universal silence
 * or a guarantee of every notification from every app.
 */
interface NotificationListenerAccess {
    fun hasPermission(): Boolean
}

/** Test stub. */
class FakeNotificationListenerAccess(
    var granted: Boolean = false
) : NotificationListenerAccess {
    override fun hasPermission(): Boolean = granted
}
