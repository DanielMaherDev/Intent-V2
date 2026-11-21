package com.intent.launcher.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiDiscussClientTest {

    @Test
    fun emptyKey_noNetwork_failure() {
        var called = false
        val client = OpenAiDiscussClient(
            transporter = OpenAiHttpTransporter { _, _, _ ->
                called = true
                OpenAiHttpResponse(200, "{}")
            }
        )
        val outcome = client.complete(apiKey = "  ", userMessage = "hello")
        assertTrue(outcome is OpenAiDiscussClient.Outcome.Failure)
        assertFalse(called)
    }

    @Test
    fun mockedHttpSuccess_parsesAssistantContent() {
        val payload =
            """
            {
              "id": "chatcmpl-test",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "Try a short practice block tonight."
                  },
                  "finish_reason": "stop"
                }
              ]
            }
            """.trimIndent()
        var sawUrl: String? = null
        var sawAuthPrefix: String? = null
        val client = OpenAiDiscussClient(
            transporter = OpenAiHttpTransporter { url, authorizationBearer, _ ->
                sawUrl = url
                sawAuthPrefix = authorizationBearer.take(7)
                OpenAiHttpResponse(200, payload)
            }
        )
        val outcome = client.complete(apiKey = "sk-test-not-real", userMessage = "What next?")
        assertTrue(outcome is OpenAiDiscussClient.Outcome.Success)
        val success = outcome as OpenAiDiscussClient.Outcome.Success
        assertEquals("Try a short practice block tonight.", success.completion.text)
        assertEquals(LiveLlmProbe.MODEL_SUGGESTION_LABEL, success.completion.provenanceLabel)
        assertEquals(OpenAiDiscussClient.DEFAULT_ENDPOINT, sawUrl)
        assertEquals("sk-test", sawAuthPrefix)
        val labeled = OpenAiDiscussClient.formatAgentTurn(success.completion)
        assertTrue(labeled.contains("Live assistant"))
        assertTrue(labeled.contains("not observed evidence"))
        assertTrue(labeled.contains("Try a short practice block tonight."))
    }

    @Test
    fun mockedHttpError_honestFailure_noInvent() {
        val client = OpenAiDiscussClient(
            transporter = OpenAiHttpTransporter { _, _, _ ->
                OpenAiHttpResponse(401, """{"error":{"message":"unauthorized"}}""")
            }
        )
        val outcome = client.complete(apiKey = "sk-test-not-real", userMessage = "hi")
        assertTrue(outcome is OpenAiDiscussClient.Outcome.Failure)
        val failure = outcome as OpenAiDiscussClient.Outcome.Failure
        assertTrue(failure.honestMessage.contains("401"))
        assertTrue(failure.honestMessage.contains("did not invent", ignoreCase = true))
    }

    @Test
    fun contextPack_appendedToSystemPrompt() {
        var sawBody: String? = null
        val client = OpenAiDiscussClient(
            transporter = OpenAiHttpTransporter { _, _, body ->
                sawBody = body
                OpenAiHttpResponse(
                    200,
                    """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""
                )
            }
        )
        val pack =
            "## Intention context (local; not model memory)\n" +
                "title=Learn Spanish\n" +
                "Rules: Suggest only; do not claim OBSERVED evidence."
        val outcome = client.complete(
            apiKey = "sk-test-not-real",
            userMessage = "What next?",
            contextPack = pack
        )
        assertTrue(outcome is OpenAiDiscussClient.Outcome.Success)
        assertTrue(sawBody!!.contains("Learn Spanish"))
        assertTrue(sawBody!!.contains("Suggest only"))
        assertTrue(sawBody!!.contains("What next?"))
    }
}
