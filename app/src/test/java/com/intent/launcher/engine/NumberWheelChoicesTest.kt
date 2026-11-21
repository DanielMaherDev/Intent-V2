package com.intent.launcher.engine

import com.intent.launcher.domain.TrackerModules
import com.intent.launcher.ui.components.PracticeMinuteChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F3 — duration NumberWheel exposes every whole integer in range. */
class NumberWheelChoicesTest {

    @Test
    fun practiceMinutes_areEveryIntegerFrom1To120() {
        assertEquals(1, PracticeMinuteChoices.MIN)
        assertEquals(120, PracticeMinuteChoices.MAX)
        assertEquals(120, PracticeMinuteChoices.VALUES.size)
        assertEquals((1..120).toList(), PracticeMinuteChoices.VALUES)
    }

    @Test
    fun languageTrackerPracticeLog_usesContiguousMinutes() {
        val module = TrackerModules.forShape("language")
            .first { it.id == "practice_log" }
        assertEquals((1..120).toList(), module.quantityChoices)
    }

    @Test
    fun builderLanguagePrompt_usesContiguousMinutes() {
        val prompt = IntentionBuilder.missingFactPrompts("language")
            .first { it.id == "practice_minutes" }
        assertEquals((1..120).toList(), prompt.quantityChoices)
        assertTrue(prompt.quantityChoices!!.contains(17))
        assertTrue(prompt.quantityChoices!!.contains(119))
    }

    @Test
    fun builderAnswerCard_quantityValues_spanInclusiveRange() {
        val spec = BuilderAnswerCard.QuantitySpec(
            promptId = "practice_minutes",
            unitLabel = "minutes per day",
            min = 1,
            max = 120,
            defaultValue = 20
        )
        assertEquals(120, spec.values.size)
        assertEquals(1, spec.values.first())
        assertEquals(120, spec.values.last())
    }
}
