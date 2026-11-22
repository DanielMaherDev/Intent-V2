package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * OC5 / OC9 — on-device create path must surface honesty from the **agent**,
 * not rely on the user typing it via Other.
 */
class AdaptiveCreateHonestyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val registry = CapabilityRegistry.default
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow-create-honesty.json"))
    }

    @Test
    fun oc5_usageSeed_agentStatesShortsIdentityLimitAndUsageAccess() {
        // OC5 seed statement (same text as AdaptiveBuilderFixtures app_use).
        val description = "Keep Instagram and YouTube Shorts under 45 minutes a day."
        assertTrue(CreatePathSeedHonesty.looksLikeUsageAppTimeSeed(description))

        CreateIntentionChat.begin(store, now + 1, registry)
        val result = CreateIntentionChat.submitUtterance(
            store = store,
            text = description,
            nowEpochMs = now + 10,
            registry = registry
        )
        assertTrue(result.ok)

        val agentTurns = store.snapshot().turns.filter {
            it.intentionId == CreateIntentionChat.SESSION_INTENTION_ID && it.role == "agent"
        }
        val honestyTurn = agentTurns.firstOrNull { turn ->
            turn.text.contains("Shorts", ignoreCase = true) &&
                (turn.text.contains("usage access", ignoreCase = true) ||
                    turn.text.contains("UsageStats", ignoreCase = true) ||
                    turn.text.contains("package", ignoreCase = true))
        }
        assertNotNull(
            "Agent must state Shorts identity / usage-access honesty on create, got: " +
                agentTurns.map { it.text.take(120) },
            honestyTurn
        )
        val text = honestyTurn!!.text
        assertTrue(text.contains("Shorts", ignoreCase = true))
        assertTrue(
            text.contains("usage access", ignoreCase = true) ||
                text.contains("UsageStats", ignoreCase = true)
        )
        assertTrue(
            text.contains("package", ignoreCase = true) ||
                text.contains("identity", ignoreCase = true) ||
                text.contains("not tell", ignoreCase = true)
        )
        assertTrue(
            "Must refuse exact Shorts-minute claims",
            text.contains("will not claim exact Shorts", ignoreCase = true) ||
                text.contains("not claim exact Shorts", ignoreCase = true) ||
                text.contains("≠ Shorts", ignoreCase = true) ||
                text.contains("not Shorts", ignoreCase = true)
        )
        assertTrue(
            text.contains("track package", ignoreCase = true) ||
                text.contains("log Shorts", ignoreCase = true) ||
                text.contains("use both", ignoreCase = true)
        )

        // First ask offers measurement choices (agent-driven, not user Other).
        val choice = store.snapshot().turns
            .flatMap { it.blocks }
            .mapNotNull { BuilderAnswerCard.decode(it) as? BuilderAnswerCard.Decoded.Choice }
            .lastOrNull()
        assertNotNull(choice)
        assertTrue(
            choice!!.options.any { it.contains("package", ignoreCase = true) } ||
                choice.options.any { it.contains("Shorts", ignoreCase = true) }
        )
    }

    @Test
    fun oc9_jobOfferSeed_agentFramesDecideByDate_notAutoAccept() {
        val description = "Help me decide whether to accept a job offer. I have ten days."
        assertTrue(CreatePathSeedHonesty.looksLikeJobOfferDecideSeed(description))

        CreateIntentionChat.begin(store, now + 1, registry)
        val result = CreateIntentionChat.submitUtterance(
            store = store,
            text = description,
            nowEpochMs = now + 10,
            registry = registry
        )
        assertTrue(result.ok)

        val agentTurns = store.snapshot().turns.filter {
            it.intentionId == CreateIntentionChat.SESSION_INTENTION_ID && it.role == "agent"
        }
        val honestyTurn = agentTurns.firstOrNull { turn ->
            val t = turn.text.lowercase()
            (t.contains("decide") || t.contains("decision")) &&
                (t.contains("deadline") || t.contains("due") || t.contains("date")) &&
                (t.contains("not assume") || t.contains("auto-accept") ||
                    t.contains("not auto") || t.contains("will not accept"))
        }
        assertNotNull(
            "Agent must frame decide-by-date (help decide, not auto-accept), got: " +
                agentTurns.map { it.text.take(140) },
            honestyTurn
        )
        val text = honestyTurn!!.text
        assertTrue(
            text.contains("deadline", ignoreCase = true) ||
                text.contains("due", ignoreCase = true) ||
                text.contains("date", ignoreCase = true)
        )
        assertTrue(
            text.contains("not assume", ignoreCase = true) ||
                text.contains("auto-accept", ignoreCase = true) ||
                text.contains("will not", ignoreCase = true)
        )
    }

    @Test
    fun spanishSeed_doesNotInjectUsageOrJobOfferHonesty() {
        CreateIntentionChat.begin(store, now + 1, registry)
        CreateIntentionChat.submitUtterance(
            store, "I want to learn Spanish", now + 10, registry
        )
        val agentText = store.snapshot().turns
            .filter {
                it.intentionId == CreateIntentionChat.SESSION_INTENTION_ID && it.role == "agent"
            }
            .joinToString("\n") { it.text }
        assertFalse(agentText.contains("Shorts", ignoreCase = true))
        assertFalse(agentText.contains("auto-accept", ignoreCase = true))
        assertFalse(agentText.contains("usage access", ignoreCase = true))
    }
}
