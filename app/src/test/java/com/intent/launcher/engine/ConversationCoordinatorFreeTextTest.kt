package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConversationCoordinatorFreeTextTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_100_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow-free.json"))
        SpanishScenario.seed(store, now)
    }

    @Test
    fun noKey_notSupported_noLiveCall() {
        val access = FakeLiveLlmAccess()
        val registry = LiveLlmProbe.liveRegistry(access)
        var httpCalls = 0
        val client = OpenAiDiscussClient(
            transporter = OpenAiHttpTransporter { _, _, _ ->
                httpCalls++
                OpenAiHttpResponse(200, "{}")
            }
        )
        val intention = store.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val result = ConversationCoordinator.submitFreeText(
            store = store,
            intention = intention,
            text = "Help me plan Thursday",
            nowEpochMs = now + 50,
            registry = registry,
            access = access,
            client = client
        )
        assertEquals(
            com.intent.launcher.capability.CapabilityStatus.NOT_SUPPORTED,
            registry.get(CapabilityRegistry.ID_LIVE_LLM)!!.status
        )
        assertFalse(registry.canUseLiveLlm())
        assertFalse(result.liveCallAttempted)
        assertEquals(0, httpCalls)
        assertEquals(LiveLlmProbe.LOCAL_HEURISTIC_LABEL, result.sourceLabel)
        val agent = store.snapshot().turns.last { it.role == "agent" }
        assertTrue(agent.text.contains("local heuristics", ignoreCase = true))
        assertFalse(agent.text.contains("Live assistant"))
    }

    @Test
    fun optInOff_denied_noLiveCall() {
        val access = FakeLiveLlmAccess(
            keyConfigured = true,
            optedIn = false,
            clientEnabled = true,
            apiKey = "sk-test-not-real"
        )
        val registry = LiveLlmProbe.liveRegistry(access)
        var httpCalls = 0
        val client = OpenAiDiscussClient(
            transporter = OpenAiHttpTransporter { _, _, _ ->
                httpCalls++
                OpenAiHttpResponse(200, "{}")
            }
        )
        val intention = store.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val result = ConversationCoordinator.submitFreeText(
            store = store,
            intention = intention,
            text = "What should I practice?",
            nowEpochMs = now + 60,
            registry = registry,
            access = access,
            client = client
        )
        assertEquals(
            com.intent.launcher.capability.CapabilityStatus.DENIED,
            registry.get(CapabilityRegistry.ID_LIVE_LLM)!!.status
        )
        assertFalse(result.liveCallAttempted)
        assertEquals(0, httpCalls)
        assertTrue(result.honestyNote!!.contains("opt in", ignoreCase = true))
        assertEquals(LiveLlmProbe.LOCAL_HEURISTIC_LABEL, result.sourceLabel)
    }

    @Test
    fun liveConnected_mockedHttpSuccess_modelProvenance() {
        val access = FakeLiveLlmAccess(
            keyConfigured = true,
            optedIn = true,
            clientEnabled = true,
            apiKey = "sk-test-not-real"
        )
        val registry = LiveLlmProbe.liveRegistry(access)
        assertTrue(registry.canUseLiveLlm())
        var httpCalls = 0
        val client = OpenAiDiscussClient(
            transporter = OpenAiHttpTransporter { _, _, body ->
                httpCalls++
                assertTrue(body.contains("What should I practice?"))
                assertTrue(body.contains("Learn Spanish") || body.contains("Intention context"))
                OpenAiHttpResponse(
                    200,
                    """
                    {"choices":[{"message":{"role":"assistant","content":"Keep Thursday at six."}}]}
                    """.trimIndent()
                )
            }
        )
        val intention = store.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val result = ConversationCoordinator.submitFreeText(
            store = store,
            intention = intention,
            text = "What should I practice?",
            nowEpochMs = now + 70,
            registry = registry,
            access = access,
            client = client
        )
        assertTrue(result.liveCallAttempted)
        assertEquals(1, httpCalls)
        assertEquals(LiveLlmProbe.MODEL_SUGGESTION_LABEL, result.sourceLabel)
        val agent = store.snapshot().turns.last { it.role == "agent" }
        assertTrue(agent.text.contains("Live assistant"))
        assertTrue(agent.text.contains("not observed evidence"))
        assertTrue(agent.text.contains("Keep Thursday at six."))
        assertTrue(LiveLlmDiscussGuard.assertSafeModelSideEffect(false, false))
    }
}
