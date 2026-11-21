package com.intent.launcher.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FE-E3 — fps / jank / chart honesty gate on tip.
 * Never invent fps SLOs, jank %, or chart UX without measured evidence.
 */
class AnimationBudgetGateTest {

    @Test
    fun tipBaseline_hasNoFrameTimingOrCharts() {
        val b = AnimationBudgetGate.tipBaseline
        assertFalse(b.frameTimingMeasured)
        assertFalse(b.chartsShipped)
        assertEquals(AnimationBudgetGate.BASELINE_ARTIFACT, b.artifact)
        assertTrue(b.coldTotalTimeMsMin in 1000..5000)
        assertTrue(b.navHopMsMin in 1000..5000)
        assertTrue(b.choreographerSkippedFramesMin >= 100)
    }

    @Test
    fun cheapFunctionalMotion_acceptedOnBaseline() {
        val d = AnimationBudgetGate.acceptsEffect(
            AnimationBudgetGate.EffectClass.CHEAP_FUNCTIONAL
        )
        assertTrue(d.accepted)
        assertTrue(d.reason.contains("cheap", ignoreCase = true))
        assertTrue(
            AnimationBudgetGate.evaluateClaim(
                AnimationBudgetGate.ClaimKind.CHEAP_MOTION_SET
            ).accepted
        )
    }

    @Test
    fun blurAndHeavyMotion_deniedWithoutFrameTiming() {
        val blur = AnimationBudgetGate.acceptsEffect(AnimationBudgetGate.EffectClass.BLUR)
        val heavy = AnimationBudgetGate.acceptsEffect(
            AnimationBudgetGate.EffectClass.HEAVY_MOTION
        )
        assertFalse(blur.accepted)
        assertFalse(heavy.accepted)
        assertTrue(blur.reason.contains("frame", ignoreCase = true))
        assertFalse(
            AnimationBudgetGate.mayAcceptNewEffect(AnimationBudgetGate.EffectClass.BLUR)
        )
        assertFalse(
            AnimationBudgetGate.mayAcceptNewEffect(
                AnimationBudgetGate.EffectClass.HEAVY_MOTION
            )
        )
    }

    @Test
    fun charts_intentionallyAbsent_denied() {
        val chart = AnimationBudgetGate.acceptsEffect(AnimationBudgetGate.EffectClass.CHART)
        assertFalse(chart.accepted)
        assertTrue(chart.reason.contains("absent", ignoreCase = true))
        assertFalse(
            AnimationBudgetGate.evaluateClaim(
                AnimationBudgetGate.ClaimKind.CHART_UX
            ).accepted
        )
        assertFalse(
            AnimationBudgetGate.mayAcceptNewEffect(AnimationBudgetGate.EffectClass.CHART)
        )
    }

    @Test
    fun fpsSloAndJankPercent_cannotBeClaimed() {
        val fps = AnimationBudgetGate.evaluateClaim(AnimationBudgetGate.ClaimKind.FPS_SLO)
        assertFalse(fps.accepted)
        assertTrue(fps.reason.contains("fps", ignoreCase = true))

        val jank = AnimationBudgetGate.evaluateClaim(
            AnimationBudgetGate.ClaimKind.JANK_PERCENT
        )
        assertFalse(jank.accepted)

        val invented = AnimationBudgetGate.evaluateClaim(
            kind = AnimationBudgetGate.ClaimKind.JANK_PERCENT,
            inventedJankPercent = 2.5
        )
        assertFalse(invented.accepted)
        assertTrue(invented.reason.contains("Invented", ignoreCase = true))
    }

    @Test
    fun frameTimingGate_unmetOnTip() {
        val gate = AnimationBudgetGate.evaluateClaim(
            AnimationBudgetGate.ClaimKind.FRAME_TIMING_GATE
        )
        assertFalse(gate.accepted)
        assertTrue(gate.reason.contains("UNMET", ignoreCase = true))
    }

    @Test
    fun withMeasuredFrameTiming_heavyAndBlurMayBeConsidered() {
        val measured = AnimationBudgetGate.Baseline(frameTimingMeasured = true)
        assertTrue(
            AnimationBudgetGate.mayAcceptNewEffect(
                AnimationBudgetGate.EffectClass.BLUR,
                measured
            )
        )
        assertTrue(
            AnimationBudgetGate.mayAcceptNewEffect(
                AnimationBudgetGate.EffectClass.HEAVY_MOTION,
                measured
            )
        )
        // Charts still need chartsShipped.
        assertFalse(
            AnimationBudgetGate.mayAcceptNewEffect(
                AnimationBudgetGate.EffectClass.CHART,
                measured
            )
        )
        val chartsReady = measured.copy(chartsShipped = true)
        assertTrue(
            AnimationBudgetGate.acceptsEffect(
                AnimationBudgetGate.EffectClass.CHART,
                chartsReady
            ).accepted
        )
        assertTrue(
            AnimationBudgetGate.evaluateClaim(
                AnimationBudgetGate.ClaimKind.JANK_PERCENT,
                baseline = measured
            ).accepted
        )
    }

    @Test
    fun settingsCopy_honestStatusAndBody() {
        val copy = AnimationBudgetGate.settingsCopy()
        assertEquals(AnimationBudgetGate.SETTINGS_SECTION_TITLE, copy.sectionTitle)
        assertTrue(copy.statusLabel.contains("unmet", ignoreCase = true))
        assertTrue(copy.statusLabel.contains("no charts", ignoreCase = true))
        assertTrue(copy.body.contains("0.0.137"))
        assertTrue(copy.body.contains("no fabricated sparklines", ignoreCase = true))
        assertTrue(copy.body.contains("fps", ignoreCase = true))
        assertFalse(copy.body.contains("60 fps ship", ignoreCase = true) &&
            !copy.body.contains("No 60", ignoreCase = true))
        assertTrue(copy.body.contains("No 60/90/120 fps SLO", ignoreCase = true))
    }
}
