package com.intent.launcher.ui

import com.intent.launcher.ui.screens.scrubAgentProse
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrubAgentProseTest {

    @Test
    fun stripsCasePrefix() {
        assertEquals(
            "I can help you track the steps that apply to you.",
            scrubAgentProse("CASE-03 — I can help you track the steps that apply to you.")
        )
        assertEquals(
            "Work legally in Spain.",
            scrubAgentProse("CASE-03: Work legally in Spain.")
        )
        assertEquals(
            "Plain reply",
            scrubAgentProse("Plain reply")
        )
    }
}
