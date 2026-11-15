package com.intent.launcher.engine

/** Platform adapter for RECORD_AUDIO (mic) permission. */
interface VoicePermissionAccess {
    fun hasRecordAudioPermission(): Boolean
}

class FakeVoicePermissionAccess(
    var granted: Boolean = false
) : VoicePermissionAccess {
    override fun hasRecordAudioPermission(): Boolean = granted
}
