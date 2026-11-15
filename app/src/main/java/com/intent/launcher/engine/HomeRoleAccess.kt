package com.intent.launcher.engine

/**
 * Probe for whether INTENT holds the Android HOME / default-launcher role.
 * Holding HOME does not grant OEM privileges — only the default home surface.
 */
interface HomeRoleAccess {
    /** True when this package is the current default HOME handler. */
    fun isDefaultHome(): Boolean

    /**
     * True when [android.app.role.RoleManager] can request ROLE_HOME (API 29+).
     * False on older APIs or when the role is unavailable — use Settings deep link.
     */
    fun canRequestHomeRole(): Boolean
}

class FakeHomeRoleAccess(
    var isDefault: Boolean = false,
    var canRequestRole: Boolean = true
) : HomeRoleAccess {
    override fun isDefaultHome(): Boolean = isDefault
    override fun canRequestHomeRole(): Boolean = canRequestRole
}
