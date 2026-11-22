package com.intent.launcher.ui

import com.intent.launcher.domain.AppConnection
import com.intent.launcher.domain.AppConnectionStatus
import com.intent.launcher.ui.screens.appsCapabilityCaption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsCapabilityCaptionTest {

    private fun conn(
        label: String,
        status: AppConnectionStatus = AppConnectionStatus.DISCONNECTED
    ) = AppConnection(
        id = label.lowercase(),
        label = label,
        packageName = "com.example",
        capabilityKey = "test",
        installed = true,
        accountStatus = status,
        limitNote = "fallback note"
    )

    @Test
    fun captionsMatchDesignSpec() {
        assertEquals(
            "Time open. Not lessons finished.",
            appsCapabilityCaption(conn("Duolingo"))
        )
        assertEquals(
            "What’s playing, if a device is active.",
            appsCapabilityCaption(conn("Spotify"))
        )
        assertEquals(
            "Activities you grant. Not connected.",
            appsCapabilityCaption(conn("Strava"))
        )
        assertEquals(
            "Activities you grant.",
            appsCapabilityCaption(conn("Strava", AppConnectionStatus.LINKED))
        )
        assertEquals(
            "Rating, if the feed is current.",
            appsCapabilityCaption(conn("Chess.com"))
        )
        assertEquals(
            "Time open. Not which videos.",
            appsCapabilityCaption(conn("Instagram"))
        )
        assertTrue(appsCapabilityCaption(conn("Other App")).contains("fallback"))
    }
}
