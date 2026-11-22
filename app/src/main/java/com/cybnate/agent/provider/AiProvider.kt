package com.cybnate.agent.provider

import kotlinx.coroutines.flow.Flow

/** Provider-agnostic AI interface. Adding a provider = new adapter, no engine rewrite. */
interface AiProvider {
    val id: String
    suspend fun testConnection(): Result<String>
    fun chat(request: ChatRequest): Flow<ChatEvent>
}

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val tools: List<ToolDef> = emptyList(),
    val temperature: Double = 0.2,
    val maxTokens: Int = 4096,
)

data class Message(val role: String, val content: String)

data class ToolDef(val name: String, val description: String, val parametersJson: String)

sealed interface ChatEvent {
    data class TextDelta(val text: String) : ChatEvent
    data class ToolCall(val id: String, val name: String, val argumentsJson: String) : ChatEvent
    data class Usage(val inputTokens: Int, val outputTokens: Int) : ChatEvent
    data class Done(val finishReason: String) : ChatEvent
    data class Error(val message: String) : ChatEvent
}

data class ProviderConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val model: String,
    val temperature: Double = 0.2,
    val maxTokens: Int = 4096,
    val customHeaders: Map<String, String> = emptyMap(),
)

/** Well-known provider defaults. Keys are NEVER stored here — see KeyVault. */
object ProviderDefaults {
    val anthropic = ProviderConfig("anthropic", "Anthropic", "https://api.anthropic.com", "claude-sonnet-4-5")
    val openai = ProviderConfig("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o")
    val gemini = ProviderConfig("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash")
    val openrouter = ProviderConfig("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openrouter/free")
}
