package com.intent.launcher.ui

import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.ui.screens.drawerStatusLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerStatusLabelTest {

    @Test
    fun mapsPhasesToDesignSpecVocabulary() {
        assertEquals("Exploring", drawerStatusLabel(IntentionPhase.EXPLORING))
        assertEquals("Planning", drawerStatusLabel(IntentionPhase.CLARIFYING))
        assertEquals("Deciding", drawerStatusLabel(IntentionPhase.PROPOSAL_PENDING))
        assertEquals("Active", drawerStatusLabel(IntentionPhase.ACTIVE))
        assertEquals("Paused", drawerStatusLabel(IntentionPhase.PAUSED))
    }
}
