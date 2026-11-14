package com.rurnima.purnima.agent

/**
 * The entry point for natural-language instructions received from Purnima's Chat or Voice UI.
 * This contract is intentionally UI-agnostic; it must not create or expose a separate screen.
 */
enum class InstructionSource {
    CHAT,
    VOICE
}

data class AgentInstruction(
    val text: String,
    val source: InstructionSource
)

enum class AgentPermission {
    READ_REPOSITORY,
    CREATE_BRANCH,
    PROPOSE_CODE_CHANGE,
    RUN_BUILD_OR_TESTS,
    CREATE_PULL_REQUEST,
    PUBLISH_RELEASE
}

data class AgentToolRequest(
    val toolName: String,
    val arguments: Map<String, String>,
    val permission: AgentPermission
)

data class AgentToolResult(
    val toolName: String,
    val succeeded: Boolean,
    val summary: String,
    val details: String? = null
)

data class AgentRunResult(
    val summary: String,
    val toolResults: List<AgentToolResult> = emptyList(),
    val awaitingUserApproval: Boolean = false
)

/**
 * Implementations will connect the instruction to an authenticated agent service and its
 * allow-listed tools. No GitHub credentials or model secrets belong in the Android APK.
 */
interface AgentRuntime {
    suspend fun run(instruction: AgentInstruction): AgentRunResult
}

/**
 * Tool execution is kept behind a separate boundary so each tool can enforce permissions,
 * audit actions, and request approval for sensitive operations.
 */
interface AgentToolExecutor {
    suspend fun execute(request: AgentToolRequest): AgentToolResult
}

interface AgentApprovalGate {
    suspend fun requestApproval(
        instruction: AgentInstruction,
        request: AgentToolRequest
    ): Boolean
}
