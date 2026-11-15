package com.intent.launcher.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * OpenAI Chat Completions client for Discuss free-text (typed send and unmatched
 * voice/STT — same [ConversationCoordinator.submitFreeText] path).
 *
 * Network host is restricted to api.openai.com. Callers must already enforce
 * key presence + user opt-in via [LiveLlmProbe] / [LiveLlmAccess].
 * Never logs the API key.
 */
class OpenAiDiscussClient(
    private val transporter: OpenAiHttpTransporter = UrlConnectionOpenAiHttpTransporter(),
    private val model: String = DEFAULT_MODEL,
    private val endpoint: String = DEFAULT_ENDPOINT
) {

    data class Completion(
        val text: String,
        val provenanceLabel: String = LiveLlmProbe.MODEL_SUGGESTION_LABEL
    )

    sealed class Outcome {
        data class Success(val completion: Completion) : Outcome()
        data class Failure(val honestMessage: String) : Outcome()
    }

    /**
     * Posts one user message (+ short system honesty prompt) to Chat Completions.
     * When [contextPack] is non-blank, it is appended to the system message
     * (contracts v1 ContextAssembler). [apiKey] must be non-blank; never pass it to logs.
     */
    fun complete(
        apiKey: String,
        userMessage: String,
        contextPack: String? = null
    ): Outcome {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return Outcome.Failure(
                "No OpenAI API key available. Discuss stays on local heuristics."
            )
        }
        val trimmedUser = userMessage.trim()
        if (trimmedUser.isEmpty()) {
            return Outcome.Failure("Empty message — nothing to send to the model.")
        }
        if (!endpoint.startsWith(ALLOWED_ORIGIN)) {
            return Outcome.Failure(
                "Live Discuss refused an unexpected host — only api.openai.com is allowed."
            )
        }

        val pack = contextPack?.trim().orEmpty()
        val systemContent = if (pack.isNotEmpty()) {
            "$SYSTEM_PROMPT\n\n$pack"
        } else {
            SYSTEM_PROMPT
        }

        val body = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", systemContent)
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", trimmedUser)
                    )
            )
            .put("temperature", 0.4)
            .toString()

        val response = try {
            transporter.postJson(
                url = endpoint,
                authorizationBearer = trimmedKey,
                jsonBody = body
            )
        } catch (t: Throwable) {
            return Outcome.Failure(
                "Could not reach OpenAI (${t.javaClass.simpleName}). " +
                    "Discuss did not invent a reply — try again or use chips."
            )
        }

        if (response.code !in 200..299) {
            return Outcome.Failure(
                "OpenAI returned HTTP ${response.code}. " +
                    "Discuss did not invent a reply — check the key/opt-in or use chips."
            )
        }

        return try {
            val root = JSONObject(response.body)
            val content = root
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
            if (content.isEmpty()) {
                Outcome.Failure(
                    "OpenAI returned an empty reply. Discuss did not invent text."
                )
            } else {
                Outcome.Success(
                    Completion(
                        text = content,
                        provenanceLabel = LiveLlmProbe.MODEL_SUGGESTION_LABEL
                    )
                )
            }
        } catch (t: Throwable) {
            Outcome.Failure(
                "Could not parse OpenAI response (${t.javaClass.simpleName}). " +
                    "Discuss did not invent a reply."
            )
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val ALLOWED_ORIGIN = "https://api.openai.com"

        const val SYSTEM_PROMPT =
            "You are INTENT's Discuss agent. Suggest next steps for the user's intention. " +
                "Be concise. Do not claim you observed app activity or completed sessions. " +
                "Your reply is a model suggestion only — the user must confirm before any fact " +
                "or schedule change."

        /** Prefix stored on agent turns so provenance survives offline re-open. */
        fun formatAgentTurn(completion: Completion): String =
            "${completion.provenanceLabel} (OpenAI — not observed evidence)\n\n${completion.text}"
    }
}

data class OpenAiHttpResponse(
    val code: Int,
    val body: String
)

fun interface OpenAiHttpTransporter {
    fun postJson(url: String, authorizationBearer: String, jsonBody: String): OpenAiHttpResponse
}

/** Production transporter — HttpURLConnection to api.openai.com only. */
class UrlConnectionOpenAiHttpTransporter(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 45_000
) : OpenAiHttpTransporter {
    override fun postJson(
        url: String,
        authorizationBearer: String,
        jsonBody: String
    ): OpenAiHttpResponse {
        require(url.startsWith(OpenAiDiscussClient.ALLOWED_ORIGIN)) {
            "OpenAI Discuss may only call api.openai.com"
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $authorizationBearer")
        }
        try {
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            return OpenAiHttpResponse(code = code, body = body)
        } finally {
            connection.disconnect()
        }
    }
}
