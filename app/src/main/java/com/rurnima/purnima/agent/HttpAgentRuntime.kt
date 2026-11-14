package com.rurnima.purnima.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTPS-only client for Purnima's agent gateway.
 *
 * No user-entered token, API key, or sign-in flow is required. Provider credentials and
 * model access stay on the server; the public gateway must be protected with edge rate limits.
 */
class HttpAgentRuntime(
    private val endpoint: URL
) : AgentRuntime {

    override suspend fun run(instruction: AgentInstruction): AgentRunResult {
        if (endpoint.protocol != "https") {
            return AgentRunResult("The agent service must use HTTPS.")
        }

        return withContext(Dispatchers.IO) {
            executeRequest(instruction)
        }
    }

    private fun executeRequest(instruction: AgentInstruction): AgentRunResult {
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        try {
            val requestBody = JSONObject()
                .put("instruction", instruction.text)
                .put("source", instruction.source.name)
                .toString()

            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                return AgentRunResult(
                    summary = "Agent service request failed (HTTP $statusCode)."
                )
            }

            return parseResponse(responseText)
        } catch (_: IOException) {
            return AgentRunResult("Could not reach Purnima's agent service.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(responseText: String): AgentRunResult {
        return try {
            val json = JSONObject(responseText)
            val toolResults = json.optJSONArray("toolResults") ?: JSONArray()
            val plan = json.optJSONArray("plan") ?: JSONArray()
            AgentRunResult(
                summary = json.optString("summary", "The agent received the request."),
                toolResults = toolResults.toToolResults(),
                awaitingUserApproval = json.optBoolean("awaitingUserApproval", false),
                plan = plan.toPlanSteps(),
                executionEnabled = json.optBoolean("executionEnabled", false),
                executionStatus = json.optString("executionStatus", "unknown")
            )
        } catch (_: Exception) {
            AgentRunResult("The agent service returned an invalid response.")
        }
    }

    private fun JSONArray.toPlanSteps(): List<AgentPlanStep> =
        (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let { item ->
                AgentPlanStep(
                    tool = item.optString("tool", "unknown"),
                    permission = item.optString("permission").toAgentPermissionOrNull(),
                    description = item.optString("description", "No description provided."),
                    requiresApproval = item.optBoolean("requiresApproval", true),
                    executionStatus = item.optString("executionStatus", "unknown")
                )
            }
        }

    private fun String.toAgentPermissionOrNull(): AgentPermission? =
        runCatching { AgentPermission.valueOf(this) }.getOrNull()

    private fun JSONArray.toToolResults(): List<AgentToolResult> =
        (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let { item ->
                AgentToolResult(
                    toolName = item.optString("toolName", "unknown"),
                    succeeded = item.optBoolean("succeeded", false),
                    summary = item.optString("summary", "No details provided."),
                    details = item.optString("details").takeIf { it.isNotBlank() }
                )
            }
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
