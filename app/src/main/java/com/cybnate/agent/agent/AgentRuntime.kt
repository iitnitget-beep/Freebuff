package com.cybnate.agent.agent

import com.cybnate.agent.provider.AiProvider
import com.cybnate.agent.provider.ChatEvent
import com.cybnate.agent.provider.ChatRequest
import com.cybnate.agent.provider.Message
import com.cybnate.agent.tools.ToolRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PermissionLevel { READ_ONLY, ASK_BEFORE_EDIT, ASK_BEFORE_EXEC, ALLOW_APPROVED }

data class PlanStep(val title: String, val status: StepStatus = StepStatus.PENDING)
enum class StepStatus { PENDING, RUNNING, DONE, FAILED }

data class AgentState(
    val running: Boolean = false,
    val plan: List<PlanStep> = emptyList(),
    val log: List<String> = emptyList(),
    val streamingText: String = "",
    val error: String? = null,
)

/**
 * Autonomous agent loop:
 * REQUEST → ANALYSIS → PLAN → TOOL SELECTION → EXECUTION → OBSERVATION → FIX → VERIFY.
 * Destructive tools always pass through [approve] unless the permission level allows them.
 */
class AgentRuntime(
    private val provider: AiProvider,
    private val tools: ToolRegistry,
    private val permissionLevel: PermissionLevel = PermissionLevel.ASK_BEFORE_EDIT,
    private val approve: suspend (toolName: String, argsJson: String) -> Boolean = { _, _ -> false },
) {
    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    @Volatile private var cancelled = false
    fun stop() { cancelled = true }

    suspend fun run(userRequest: String, model: String, projectSummary: String) {
        cancelled = false
        _state.value = AgentState(running = true, log = listOf("Analysing project…"))
        val messages = mutableListOf(
            Message("system", "You are an autonomous coding agent. Plan, use tools, verify. Project: $projectSummary"),
            Message("user", userRequest),
        )
        var turns = 0
        val maxTurns = 24
        while (!cancelled && turns < maxTurns) {
            turns++
            var sawToolCall = false
            provider.chat(ChatRequest(model = model, messages = messages, tools = tools.definitions()))
                .collect { event ->
                    when (event) {
                        is ChatEvent.TextDelta ->
                            _state.value = _state.value.copy(streamingText = _state.value.streamingText + event.text)
                        is ChatEvent.ToolCall -> {
                            sawToolCall = true
                            val allowed = when {
                                tools.isReadOnly(event.name) -> true
                                permissionLevel == PermissionLevel.ALLOW_APPROVED -> true
                                permissionLevel == PermissionLevel.READ_ONLY -> false
                                else -> approve(event.name, event.argumentsJson)
                            }
                            val result = if (allowed) tools.execute(event.name, event.argumentsJson)
                                         else "Denied by permission policy."
                            _state.value = _state.value.copy(
                                log = _state.value.log + "Tool ${event.name}: ${result.take(200)}",
                            )
                            messages += Message("tool", result)
                        }
                        is ChatEvent.Usage ->
                            _state.value = _state.value.copy(
                                log = _state.value.log + "Tokens in=${event.inputTokens} out=${event.outputTokens}",
                            )
                        is ChatEvent.Error ->
                            _state.value = _state.value.copy(error = event.message)
                        is ChatEvent.Done -> Unit
                    }
                }
            if (!sawToolCall) break
        }
        _state.value = _state.value.copy(running = false, log = _state.value.log + "Done. Verify changes in Explorer.")
    }
}
