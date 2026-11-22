package com.intent.launcher.engine

import com.intent.launcher.domain.AnswerSchema
import com.intent.launcher.domain.BuilderDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuilderAnswerCardSchemasTest {

    @Test
    fun blockForAsk_coversAllAnswerSchemas() {
        AnswerSchema.entries.forEach { schema ->
            val block = BuilderAnswerCard.blockForAsk(
                BuilderDecision.Ask(
                    intentionId = "i1",
                    expectedRevision = 0L,
                    questionId = "q-$schema",
                    whyItMatters = "why",
                    answerSchema = schema,
                    purpose = "purpose",
                    options = listOf("A", "B")
                )
            )
            assertTrue(BuilderAnswerCard.isBuilderCard(block))
            val decoded = BuilderAnswerCard.decode(block)
            assertNotNull("decode failed for $schema", decoded)
            assertEquals("q-$schema", decoded!!.promptId)
        }
    }

    @Test
    fun choiceOptions_includeOtherNotSureSkip() {
        val opts = ChoiceOptions.withDeferralOptions(listOf("Spanish", "French"))
        assertTrue(opts.any { ChoiceOptions.isOther(it) })
        assertTrue(opts.any { ChoiceOptions.isNotSure(it) })
        assertTrue(opts.any { ChoiceOptions.isSkip(it) })
        assertEquals(
            ChoiceOptions.Selection.Skip,
            ChoiceOptions.resolveSelection("Skip for now", opts)
        )
        assertEquals(
            ChoiceOptions.Selection.NotSure,
            ChoiceOptions.resolveSelection("Not sure", opts)
        )
        assertEquals(
            ChoiceOptions.Selection.NeedsFreeText,
            ChoiceOptions.resolveSelection("Other", opts)
        )
    }

    @Test
    fun inactiveBlock_doesNotDecode() {
        val block = BuilderAnswerCard.choiceBlock(
            blockId = "b1",
            title = "t",
            body = "b",
            spec = BuilderAnswerCard.ChoiceSpec("q1", listOf("A"))
        ).copy(active = false)
        assertFalse(BuilderAnswerCard.isBuilderCard(block) && BuilderAnswerCard.decode(block) != null)
        assertEquals(null, BuilderAnswerCard.decode(block))
    }

    @Test
    fun multiChoiceAsk_projectsMultiKind_notSingleRadio() {
        val block = BuilderAnswerCard.blockForAsk(
            BuilderDecision.Ask(
                intentionId = "i1",
                expectedRevision = 0L,
                questionId = "q-practice",
                whyItMatters =
                    "Practice stays distinct from the rating goal. Pick one or more approaches.",
                answerSchema = AnswerSchema.MULTI_CHOICE,
                purpose = AdaptiveBuilderEngine.KEY_PRACTICE_PREFERENCE,
                options = CreatePathSeedHonesty.PRACTICE_PREFERENCE_OPTIONS
            )
        )
        val decoded = BuilderAnswerCard.decode(block)
        assertTrue(decoded is BuilderAnswerCard.Decoded.MultiChoice)
        assertEquals(AnswerSchema.MULTI_CHOICE, BuilderAnswerCard.schemaOf(decoded!!))
        assertEquals("What practice approaches do you want?", block.title)
        assertFalse(block.body.contains("Pick one below", ignoreCase = true))
        assertTrue(block.actions.contains("Playing games"))
        assertTrue(block.actions.contains("Reviewing games"))
        assertTrue(block.actions.contains("Studying"))
        assertFalse(block.actions.any { it.equals("A combination", ignoreCase = true) })
    }

    @Test
    fun multiChoiceBlock_createPathOmitsDeferralKeepsOther() {
        val block = BuilderAnswerCard.multiChoiceBlock(
            blockId = "b-multi",
            title = "Practice",
            body = "Pick all that apply",
            promptId = AdaptiveBuilderEngine.KEY_PRACTICE_PREFERENCE,
            options = CreatePathSeedHonesty.PRACTICE_PREFERENCE_OPTIONS,
            includeDeferral = false
        )
        val decoded = BuilderAnswerCard.decode(block) as BuilderAnswerCard.Decoded.MultiChoice
        assertTrue(decoded.options.contains("Playing games"))
        assertTrue(decoded.options.any { ChoiceOptions.isOther(it) })
        assertFalse(decoded.options.any { ChoiceOptions.isNotSure(it) })
        assertFalse(decoded.options.any { ChoiceOptions.isSkip(it) })
    }
}
