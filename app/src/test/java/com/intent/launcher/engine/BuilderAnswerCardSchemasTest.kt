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
}
