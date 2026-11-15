package com.intent.launcher.platform

import android.content.Context
import android.provider.Settings
import com.intent.launcher.engine.OverlayPermissionAccess

class AndroidOverlayPermissionAccess(
    private val context: Context
) : OverlayPermissionAccess {
    override fun canDrawOverlays(): Boolean =
        Settings.canDrawOverlays(context)
}
