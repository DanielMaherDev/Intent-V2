package com.intent.launcher.engine

/**
 * FE-E3 fps / jank / chart honesty gate.
 *
 * Encodes the measured animation budget that ships today and **refuses**
 * invented fps SLOs, jank %, chart UX, blur, or heavy motion unless a
 * frame-timing measurement is attached. JVM-testable; no Android framework types.
 *
 * Baseline numbers come from the 0.0.137 KVM cold-start / nav pass
 * (`docs/frontend/performance-notes.md`). They accept keeping the cheap
 * motion set — they do **not** authorize new effect classes.
 */
object AnimationBudgetGate {

    /** Settings section title under Motion — fps/jank/charts honesty. */
    const val SETTINGS_SECTION_TITLE = "Animation budget"

    /** Artifact that recorded the cold-start / nav baseline. */
    const val BASELINE_ARTIFACT = "0.0.137"

    /** AVD / method label for the baseline (software GPU — not a phone SLO). */
    const val BASELINE_DEVICE =
        "INTENT_Visual_Pixel · API 34 · -gpu software · Project cloud KVM"

    enum class EffectClass {
        /** HorizontalPager / default NavHost / 1 Hz data tick / LinearProgress meter / press+scroll. */
        CHEAP_FUNCTIONAL,

        /** Backdrop blur / Modifier.blur / RenderEffect. */
        BLUR,

        /** Time-series / distribution / streak / sparkline chart chrome. */
        CHART,

        /** Infinite loops, Lottie, shared-element spectacle, continuous ornament. */
        HEAVY_MOTION
    }

    enum class ClaimKind {
        /** "Ships at 60/90/120 fps" (or any fps SLO). */
        FPS_SLO,

        /** Invented jank % / frame-time % without a measurement. */
        JANK_PERCENT,

        /** Worksheet chart UX is implemented. */
        CHART_UX,

        /** Macrobenchmark / FrameTiming CI gate is green. */
        FRAME_TIMING_GATE,

        /** Current cheap motion set stays accepted on the KVM baseline. */
        CHEAP_MOTION_SET
    }

    data class Baseline(
        val artifact: String = BASELINE_ARTIFACT,
        val device: String = BASELINE_DEVICE,
        /** Cold Activity TotalTime working range (ms). */
        val coldTotalTimeMsMin: Int = 1888,
        val coldTotalTimeMsMax: Int = 3182,
        /** Primary Compose nav hop working range (ms, dump-poll upper bound). */
        val navHopMsMin: Int = 2489,
        val navHopMsMax: Int = 3247,
        /**
         * Post-Displayed Choreographer skipped-frame counts observed on software GPU.
         * High skips are expected on `-gpu software` — not a phone jank %.
         */
        val choreographerSkippedFramesMin: Int = 122,
        val choreographerSkippedFramesMax: Int = 148,
        /** True only when Macrobenchmark / FrameTiming / systrace was recorded. */
        val frameTimingMeasured: Boolean = false,
        /** True only when a real chart surface ships (not text Reports). */
        val chartsShipped: Boolean = false
    )

    data class Decision(
        val accepted: Boolean,
        val reason: String
    )

    data class SettingsCopy(
        val sectionTitle: String = SETTINGS_SECTION_TITLE,
        /** Short status chip — e.g. "Frame gate unmet · no charts". */
        val statusLabel: String,
        /** Full honesty body for Settings. */
        val body: String
    )

    /** Tip baseline — no frame timing, no charts. */
    val tipBaseline: Baseline = Baseline()

    fun acceptsEffect(
        effect: EffectClass,
        baseline: Baseline = tipBaseline
    ): Decision =
        when (effect) {
            EffectClass.CHEAP_FUNCTIONAL -> Decision(
                accepted = true,
                reason = "KVM cold-start / nav baseline on $BASELINE_ARTIFACT " +
                    "accepts keeping the cheap functional motion set " +
                    "(pager, default NavHost, 1 Hz tick, duration meter, press/scroll)."
            )
            EffectClass.BLUR -> Decision(
                accepted = false,
                reason = denyUntilFrameTiming("blur / RenderEffect")
            )
            EffectClass.CHART -> {
                if (baseline.chartsShipped && baseline.frameTimingMeasured) {
                    Decision(
                        accepted = true,
                        reason = "Charts are shipped and a measured frame budget is attached."
                    )
                } else {
                    Decision(
                        accepted = false,
                        reason = "Charts intentionally absent — text Reports / PathSummary only. " +
                            "Do not ship chart libraries or decorative sparklines until " +
                            "provenance-labeled series data and a measured frame budget exist."
                    )
                }
            }
            EffectClass.HEAVY_MOTION -> Decision(
                accepted = false,
                reason = denyUntilFrameTiming(
                    "heavy / continuous / looping / shared-element motion"
                )
            )
        }

    fun evaluateClaim(
        kind: ClaimKind,
        baseline: Baseline = tipBaseline,
        inventedJankPercent: Double? = null
    ): Decision =
        when (kind) {
            ClaimKind.FPS_SLO -> Decision(
                accepted = false,
                reason = "No fps SLO. Frame timing not measured " +
                    "(frameTimingMeasured=${baseline.frameTimingMeasured}). " +
                    "Do not claim 60/90/120 fps."
            )
            ClaimKind.JANK_PERCENT -> {
                if (inventedJankPercent != null && !baseline.frameTimingMeasured) {
                    Decision(
                        accepted = false,
                        reason = "Invented jank % ($inventedJankPercent) rejected — " +
                            "no FrameTiming / Macrobenchmark / systrace on tip."
                    )
                } else if (!baseline.frameTimingMeasured) {
                    Decision(
                        accepted = false,
                        reason = "Jank % not measured. Software-GPU Choreographer skips " +
                            "(${baseline.choreographerSkippedFramesMin}–" +
                            "${baseline.choreographerSkippedFramesMax} after Displayed on " +
                            "$BASELINE_ARTIFACT) are context only — not a phone jank gate."
                    )
                } else {
                    Decision(
                        accepted = true,
                        reason = "Frame timing measured — jank % may be reported from that pass."
                    )
                }
            }
            ClaimKind.CHART_UX -> Decision(
                accepted = baseline.chartsShipped,
                reason = if (baseline.chartsShipped) {
                    "Chart UX is marked shipped on this baseline."
                } else {
                    "Chart UX intentionally absent — Reports stay labeled text projections."
                }
            )
            ClaimKind.FRAME_TIMING_GATE -> Decision(
                accepted = baseline.frameTimingMeasured,
                reason = if (baseline.frameTimingMeasured) {
                    "Frame-timing gate present on this baseline."
                } else {
                    "Frame-timing gate UNMET — cold-start/nav ms are not an effects gate."
                }
            )
            ClaimKind.CHEAP_MOTION_SET -> acceptsEffect(EffectClass.CHEAP_FUNCTIONAL, baseline)
        }

    /**
     * Whether a future PR may green-light a new effect class.
     * Requires an attached frame-timing measurement (and charts need chartsShipped).
     */
    fun mayAcceptNewEffect(
        effect: EffectClass,
        baseline: Baseline = tipBaseline
    ): Boolean =
        when (effect) {
            EffectClass.CHEAP_FUNCTIONAL -> true
            EffectClass.CHART -> baseline.chartsShipped && baseline.frameTimingMeasured
            EffectClass.BLUR, EffectClass.HEAVY_MOTION -> baseline.frameTimingMeasured
        }

    fun settingsCopy(baseline: Baseline = tipBaseline): SettingsCopy {
        val frameLabel = if (baseline.frameTimingMeasured) {
            "Frame gate measured"
        } else {
            "Frame / jank gate unmet"
        }
        val chartLabel = if (baseline.chartsShipped) {
            "charts shipped"
        } else {
            "no charts"
        }
        return SettingsCopy(
            statusLabel = "$frameLabel · $chartLabel",
            body = buildString {
                append("Measured budget (artifact $BASELINE_ARTIFACT, $BASELINE_DEVICE): ")
                append("cold Activity TotalTime ≈${baseline.coldTotalTimeMsMin}–")
                append("${baseline.coldTotalTimeMsMax} ms; primary nav hops ≈")
                append("${baseline.navHopMsMin}–${baseline.navHopMsMax} ms (dump-poll upper bound). ")
                append("Post-Displayed Choreographer skipped ")
                append("${baseline.choreographerSkippedFramesMin}–")
                append("${baseline.choreographerSkippedFramesMax} frames on software GPU — ")
                append("context only, not a phone jank %. ")
                append("Accepted today: cheap functional motion (pager, default Nav, ")
                append("1 Hz session tick, duration meter). ")
                append("Denied without a new frame-timing pass: blur, heavy/continuous motion, ")
                append("and chart chrome. Reports stay text PathSummary / ProgressProof — ")
                append("no fabricated sparklines. No 60/90/120 fps SLO on this build.")
            }
        )
    }

    private fun denyUntilFrameTiming(what: String): String =
        "Denied: $what requires a measured frame-timing pass " +
            "(Macrobenchmark / FrameTiming / systrace) before acceptance. " +
            "Cold-start / nav ms on $BASELINE_ARTIFACT do not authorize it."
}
