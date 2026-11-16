package com.intent.launcher.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducedMotionTest {

    @Test
    fun systemReduced_whenAnimatorScaleZero() {
        assertTrue(ReducedMotion.systemReduced(0f))
        assertTrue(ReducedMotion.systemReduced(-0.1f))
        assertFalse(ReducedMotion.systemReduced(0.5f))
        assertFalse(ReducedMotion.systemReduced(1f))
    }

    @Test
    fun followSystem_mirrorsAnimatorScale() {
        assertTrue(
            ReducedMotion.resolvesToReduced(
                ReducedMotionPreference.FOLLOW_SYSTEM,
                animatorDurationScale = 0f
            )
        )
        assertFalse(
            ReducedMotion.resolvesToReduced(
                ReducedMotionPreference.FOLLOW_SYSTEM,
                animatorDurationScale = 1f
            )
        )
    }

    @Test
    fun preferReduced_forcesOn_evenWhenSystemAllowsMotion() {
        assertTrue(
            ReducedMotion.resolvesToReduced(
                ReducedMotionPreference.PREFER_REDUCED,
                animatorDurationScale = 1f
            )
        )
        assertTrue(
            ReducedMotion.resolvesToReduced(
                ReducedMotionPreference.PREFER_REDUCED,
                animatorDurationScale = 0f
            )
        )
    }

    @Test
    fun preferReduced_cannotDisableSystemReduced() {
        // No preference value turns reduced off when scale is 0.
        assertTrue(
            ReducedMotion.resolvesToReduced(
                ReducedMotionPreference.FOLLOW_SYSTEM,
                animatorDurationScale = 0f
            )
        )
        assertTrue(
            ReducedMotion.resolvesToReduced(
                ReducedMotionPreference.PREFER_REDUCED,
                animatorDurationScale = 0f
            )
        )
    }

    @Test
    fun motionDurationScaleFactor_collapsesWhenReduced() {
        assertEquals(
            0f,
            ReducedMotion.motionDurationScaleFactor(
                ReducedMotionPreference.FOLLOW_SYSTEM,
                animatorDurationScale = 0f
            )
        )
        assertEquals(
            0f,
            ReducedMotion.motionDurationScaleFactor(
                ReducedMotionPreference.PREFER_REDUCED,
                animatorDurationScale = 1f
            )
        )
        assertEquals(
            1f,
            ReducedMotion.motionDurationScaleFactor(
                ReducedMotionPreference.FOLLOW_SYSTEM,
                animatorDurationScale = 1f
            )
        )
        assertEquals(
            0.5f,
            ReducedMotion.motionDurationScaleFactor(
                ReducedMotionPreference.FOLLOW_SYSTEM,
                animatorDurationScale = 0.5f
            )
        )
    }

    @Test
    fun cardDurations_gateToZeroWhenReduced() {
        assertEquals(0, ReducedMotion.cardEnterMs(reduced = true))
        assertEquals(0, ReducedMotion.cardExitMs(reduced = true))
        assertEquals(ReducedMotion.CARD_ENTER_MS, ReducedMotion.cardEnterMs(reduced = false))
        assertEquals(ReducedMotion.CARD_EXIT_MS, ReducedMotion.cardExitMs(reduced = false))
        assertEquals(90, ReducedMotion.cardEnterMs(reduced = false, fullMs = 90))
    }

    @Test
    fun scrollPageInstantly_whenReduced() {
        assertTrue(ReducedMotion.scrollPageInstantly(reduced = true))
        assertFalse(ReducedMotion.scrollPageInstantly(reduced = false))
    }

    @Test
    fun fromStorage_defaultsToFollowSystem() {
        assertEquals(
            ReducedMotionPreference.FOLLOW_SYSTEM,
            ReducedMotionPreference.fromStorage(null)
        )
        assertEquals(
            ReducedMotionPreference.FOLLOW_SYSTEM,
            ReducedMotionPreference.fromStorage("nope")
        )
        assertEquals(
            ReducedMotionPreference.PREFER_REDUCED,
            ReducedMotionPreference.fromStorage("PREFER_REDUCED")
        )
    }

    @Test
    fun fakePrefs_roundTrip_doesNotTouchStore() {
        val prefs = FakeHomeChromePrefs()
        assertEquals(
            ReducedMotionPreference.FOLLOW_SYSTEM,
            prefs.reducedMotionPreference()
        )
        prefs.setReducedMotionPreference(ReducedMotionPreference.PREFER_REDUCED)
        assertEquals(
            ReducedMotionPreference.PREFER_REDUCED,
            prefs.reducedMotionPreference()
        )
        prefs.setReducedMotionPreference(ReducedMotionPreference.FOLLOW_SYSTEM)
        assertEquals(
            ReducedMotionPreference.FOLLOW_SYSTEM,
            prefs.reducedMotionPreference()
        )
    }
}
