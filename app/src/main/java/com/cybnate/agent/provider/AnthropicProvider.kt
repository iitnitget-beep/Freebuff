package com.cybnate.agent.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Anthropic Messages API adapter (user's own API key via [KeyVault]).
 * Note: an Anthropic API key gives API access — it does NOT grant Claude Code itself.
 * Gemini is served through its OpenAI-compatible endpoint by [OpenAiCompatibleProvider].
 */
class AnthropicProvider(
    private val config: ProviderConfig = ProviderDefaults.anthropic,
    private val vault: KeyVault,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build(),
) : AiProvider {

    override val id: String = "anthropic"

    private fun apiKey(): String =
        vault.getKey(id) ?: throw IllegalStateException("No Anthropic API key set")

    override suspend fun testConnection(): Result<String> = runCatching {
        val req = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/v1/models")
            .header("x-api-key", apiKey())
            .header("anthropic-version", "2023-06-01")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.body?.string()?.take(300)}")
            "Connected to Anthropic API."
        }
    }

    override fun chat(request: ChatRequest): Flow<ChatEvent> = flow {
        val system = request.messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val body = JSONObject().apply {
            put("model", request.model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            put("stream", true)
            if (system.isNotBlank()) put("system", system)
            put("messages", JSONArray().apply {
                request.messages.filter { it.role != "system" }.forEach { m ->
                    put(JSONObject().put("role", if (m.role == "tool") "user" else m.role)
                        .put("content", m.content))
                }
            })
            if (request.tools.isNotEmpty()) {
                put("tools", JSONArray().apply {
                    request.tools.forEach { t ->
                        put(JSONObject().put("name", t.name).put("description", t.description)
                            .put("input_schema", JSONObject(t.parametersJson)))
                    }
                })
            }
        }
        val req = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/v1/messages")
            .header("x-api-key", apiKey())
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                emit(ChatEvent.Error("HTTP ${resp.code}: ${resp.body?.string()?.take(500)}")); return@use
            }
            val source = resp.body?.source() ?: run { emit(ChatEvent.Error("Empty response")); return@use }
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val json = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull() ?: continue
                when (json.optString("type")) {
                    "content_block_delta" -> {
                        val d = json.optJSONObject("delta") ?: continue
                        if (d.optString("type") == "text_delta") emit(ChatEvent.TextDelta(d.optString("text")))
                    }
                    "content_block_start" -> {
                        val block = json.optJSONObject("content_block") ?: continue
                        if (block.optString("type") == "tool_use") {
                            emit(ChatEvent.ToolCall(
                                id = block.optString("id"),
                                name = block.optString("name"),
                                argumentsJson = block.optJSONObject("input")?.toString() ?: "{}",
                            ))
                        }
                    }
                    "message_delta" -> json.optJSONObject("usage")?.let { u ->
                        emit(ChatEvent.Usage(u.optInt("input_tokens"), u.optInt("output_tokens")))
                    }
                    "message_stop" -> emit(ChatEvent.Done("stop"))
                    "error" -> emit(ChatEvent.Error(json.optJSONObject("error")?.optString("message") ?: "API error"))
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
