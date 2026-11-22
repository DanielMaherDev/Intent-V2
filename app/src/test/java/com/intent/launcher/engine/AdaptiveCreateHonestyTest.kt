package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
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
    fun liveProse_casualShortsMention_stillAppendsUsageHonesty() {
        val honesty = CreatePathSeedHonesty.forDescription(
            "Keep Instagram and YouTube Shorts under 45 minutes a day."
        )!!
        val casual =
            "Live assistant (OpenAI — not observed evidence)\n\n" +
                "I can help with Instagram and YouTube Shorts limits. " +
                "Want Track Your Time, Set Limits, or Content Planning?"
        assertFalse(CreatePathSeedHonesty.liveProseStatesUsageHonesty(casual))
        val merged = CreatePathSeedHonesty.mergeLiveProseWithSeedHonesty(casual, honesty)
        assertTrue(merged.contains("usage access", ignoreCase = true))
        assertTrue(
            merged.contains("≠ Shorts", ignoreCase = true) ||
                merged.contains("not Shorts", ignoreCase = true) ||
                merged.contains("will not claim exact Shorts", ignoreCase = true)
        )
        assertTrue(merged.contains(casual.substringAfter("\n\n").take(40)))
    }

    @Test
    fun oc5_liveCreate_casualShortsModel_appendsHonestyAndAppUseShape() {
        val description = "Keep Instagram and YouTube Shorts under 45 minutes a day."
        val access = FakeLiveLlmAccess(
            keyConfigured = true,
            optedIn = true,
            clientEnabled = true,
            apiKey = "sk-test-not-real"
        )
        val liveRegistry = LiveLlmProbe.liveRegistry(access)
        val payload =
            """
            {
              "choices": [{
                "message": {
                  "role": "assistant",
                  "content": "I can help with Instagram and YouTube Shorts. Track time, set limits, or plan content?"
                }
              }]
            }
            """.trimIndent()
        val client = OpenAiDiscussClient(
            transporter = OpenAiHttpTransporter { _, _, _ ->
                OpenAiHttpResponse(200, payload)
            }
        )
        CreateIntentionChat.begin(store, now + 1, liveRegistry)
        val result = CreateIntentionChat.submitUtterance(
            store = store,
            text = description,
            nowEpochMs = now + 10,
            registry = liveRegistry,
            access = access,
            client = client
        )
        assertTrue(result.ok)
        assertTrue(result.liveCallAttempted)
        assertEquals("app_use", store.snapshot().builderDraft.suggestedShapeTag)
        val agentText = store.snapshot().turns
            .filter {
                it.intentionId == CreateIntentionChat.SESSION_INTENTION_ID && it.role == "agent"
            }
            .joinToString("\n") { it.text }
        assertTrue(agentText.contains("usage access", ignoreCase = true))
        assertTrue(
            agentText.contains("will not claim exact Shorts", ignoreCase = true) ||
                agentText.contains("≠ Shorts", ignoreCase = true) ||
                agentText.contains("not Shorts", ignoreCase = true)
        )
    }

    @Test
    fun oc5_usageSeed_agentStatesShortsIdentityLimitAndUsageAccess() {
        // OC5 seed statement (same text as AdaptiveBuilderFixtures app_use).
        val description = "Keep Instagram and YouTube Shorts under 45 minutes a day."
        assertTrue(CreatePathSeedHonesty.looksLikeUsageAppTimeSeed(description))
        assertEquals("app_use", IntentionBuilder.suggestShape(description))

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

        // Measurement → permission card → intervention (not silent overlay).
        CreateIntentionChat.submitChoice(
            store, "Use both", now + 20, registry
        )
        val afterMeasure = store.snapshot().turns
            .flatMap { it.blocks }
            .mapNotNull { BuilderAnswerCard.decode(it) as? BuilderAnswerCard.Decoded.Choice }
            .lastOrNull()
        assertNotNull(afterMeasure)
        assertEquals(CreatePathSeedHonesty.PROMPT_USAGE_ACCESS, afterMeasure!!.promptId)
        assertTrue(
            afterMeasure.options.any { it.contains("Usage access", ignoreCase = true) } ||
                afterMeasure.options.any { it.contains("grant", ignoreCase = true) }
        )
        CreateIntentionChat.submitChoice(
            store, "Open Usage access when ready", now + 30, registry
        )
        val afterAccess = store.snapshot().turns
            .flatMap { it.blocks }
            .mapNotNull { BuilderAnswerCard.decode(it) as? BuilderAnswerCard.Decoded.Choice }
            .lastOrNull()
        assertNotNull(afterAccess)
        assertEquals(CreatePathSeedHonesty.PROMPT_USAGE_INTERVENTION, afterAccess!!.promptId)
        assertTrue(afterAccess.options.any { it.contains("Quiet reminder", ignoreCase = true) })
        assertTrue(afterAccess.options.any { it.contains("Ask me first", ignoreCase = true) })
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
