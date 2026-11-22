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
 * OpenAI-compatible adapter: covers OpenAI, OpenRouter, custom endpoints and local servers.
 * The API key comes from [KeyVault] at call time — never hardcoded, never logged.
 */
class OpenAiCompatibleProvider(
    override val id: String,
    private val config: ProviderConfig,
    private val vault: KeyVault,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build(),
) : AiProvider {

    private fun authHeader(): String {
        val key = vault.getKey(id) ?: throw IllegalStateException("No API key set for $id")
        return "Bearer $key"
    }

    override suspend fun testConnection(): Result<String> = runCatching {
        val req = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/models")
            .header("Authorization", authHeader())
            .apply { config.customHeaders.forEach { (k, v) -> header(k, v) } }
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.body?.string()?.take(300)}")
            "Connected. Models endpoint OK."
        }
    }

    override fun chat(request: ChatRequest): Flow<ChatEvent> = flow {
        val body = JSONObject().apply {
            put("model", request.model)
            put("stream", true)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            put("messages", JSONArray().apply {
                request.messages.forEach { m ->
                    put(JSONObject().put("role", m.role).put("content", m.content))
                }
            })
            if (request.tools.isNotEmpty()) {
                put("tools", JSONArray().apply {
                    request.tools.forEach { t ->
                        put(JSONObject()
                            .put("type", "function")
                            .put("function", JSONObject()
                                .put("name", t.name)
                                .put("description", t.description)
                                .put("parameters", JSONObject(t.parametersJson))))
                    }
                })
            }
        }
        val req = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", authHeader())
            .header("Content-Type", "application/json")
            .apply { config.customHeaders.forEach { (k, v) -> header(k, v) } }
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
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") { emit(ChatEvent.Done("stop")); break }
                val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
                val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: continue
                val delta = choice.optJSONObject("delta") ?: continue
                delta.optString("content").takeIf { it.isNotEmpty() }?.let { emit(ChatEvent.TextDelta(it)) }
                delta.optJSONArray("tool_calls")?.let { calls ->
                    for (i in 0 until calls.length()) {
                        val c = calls.optJSONObject(i) ?: continue
                        val fn = c.optJSONObject("function") ?: continue
                        emit(ChatEvent.ToolCall(
                            id = c.optString("id"),
                            name = fn.optString("name"),
                            argumentsJson = fn.optString("arguments"),
                        ))
                    }
                }
                json.optJSONObject("usage")?.let { u ->
                    emit(ChatEvent.Usage(u.optInt("prompt_tokens"), u.optInt("completion_tokens")))
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
