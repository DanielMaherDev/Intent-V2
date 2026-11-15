package com.intent.launcher.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.intent.launcher.engine.VoicePermissionAccess

class AndroidVoicePermissionAccess(
    private val context: Context
) : VoicePermissionAccess {
    override fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
