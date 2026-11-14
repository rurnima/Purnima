package com.rurnima.purnima.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP adapter for Purnima's authenticated orchestration API.
 *
 * The service URL and token provider are injected at runtime. Credentials must come from
 * an authenticated session and must never be hard-coded into the app or logged.
 */
class HttpAgentRuntime(
    private val endpoint: URL,
    private val accessTokenProvider: suspend () -> String?
) : AgentRuntime {

    override suspend fun run(instruction: AgentInstruction): AgentRunResult {
        if (endpoint.protocol != "https") {
            return AgentRunResult("The agent service must use HTTPS.")
        }

        val accessToken = accessTokenProvider()
        if (accessToken.isNullOrBlank()) {
            return AgentRunResult("Sign in to connect Purnima's agent service.")
        }

        return withContext(Dispatchers.IO) {
            executeRequest(instruction, accessToken)
        }
    }

    private fun executeRequest(
        instruction: AgentInstruction,
        accessToken: String
    ): AgentRunResult {
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $accessToken")
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
            AgentRunResult(
                summary = json.optString("summary", "The agent completed the request."),
                toolResults = toolResults.toToolResults(),
                awaitingUserApproval = json.optBoolean("awaitingUserApproval", false)
            )
        } catch (_: Exception) {
            AgentRunResult("The agent service returned an invalid response.")
        }
    }

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
