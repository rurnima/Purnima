package com.rurnima.purnima.knowledge

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private const val KNOWLEDGE_API = "https://purnima-knowledge-hunting.purnima.workers.dev"
private val knowledgeExecutor = Executors.newSingleThreadExecutor()
private val mainHandler = Handler(Looper.getMainLooper())

private data class KnowledgeResult(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String,
    val stars: Int? = null,
    val language: String? = null,
    val license: String? = null
)

/** Isolated feature screen; it is not wired into MainActivity until the integration step. */
@Composable
fun KnowledgeHuntingScreen(
    modifier: Modifier = Modifier,
    onOpenSource: (String) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("Research") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var provider by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<KnowledgeResult>()) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Knowledge Hunting", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Explore the web, research papers, and open-source tools.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Research", "GitHub Tools").forEach { option ->
                    val selected = mode == option
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.clickable(enabled = !loading) { mode = option }
                    ) {
                        Text(
                            option,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (mode == "GitHub Tools") "Search public GitHub tools" else "What do you want to discover?") },
                placeholder = { Text("e.g. open-source deep research agents") },
                singleLine = true,
                enabled = !loading
            )

            Button(
                onClick = {
                    val submitted = query.trim()
                    if (submitted.length < 2) {
                        error = "Enter at least 2 characters to search."
                        return@Button
                    }
                    loading = true
                    error = null
                    results = emptyList()
                    val endpoint = if (mode == "GitHub Tools") "/api/knowledge/github-tools" else "/api/knowledge/search"
                    knowledgeExecutor.execute {
                        try {
                            val connection = (URL(KNOWLEDGE_API + endpoint).openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"
                                connectTimeout = 15000
                                readTimeout = 20000
                                doOutput = true
                                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                setRequestProperty("Accept", "application/json")
                            }
                            connection.outputStream.use {
                                it.write(JSONObject().put("query", submitted).put("limit", 8).toString().toByteArray(Charsets.UTF_8))
                            }
                            val status = connection.responseCode
                            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                            val payload = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                            connection.disconnect()
                            if (status !in 200..299) throw IllegalStateException("Search request failed (HTTP $status).")
                            val json = JSONObject(payload)
                            val array = json.optJSONArray("results") ?: JSONArray()
                            val parsed = buildList {
                                for (i in 0 until array.length()) {
                                    val item = array.optJSONObject(i) ?: continue
                                    val title = item.optString("title").ifBlank { item.optString("name") }
                                    val link = item.optString("url")
                                    if (title.isNotBlank() && link.startsWith("https://")) {
                                        add(KnowledgeResult(
                                            title = title,
                                            url = link,
                                            snippet = item.optString("snippet", item.optString("description", "No description available.")),
                                            source = item.optString("source", "Source"),
                                            stars = if (item.has("stars")) item.optInt("stars") else null,
                                            language = item.optString("language").takeIf { it.isNotBlank() && it != "Unknown" },
                                            license = item.optString("license").takeIf { it.isNotBlank() && it != "Not specified" }
                                        ))
                                    }
                                }
                            }
                            mainHandler.post {
                                provider = json.optString("provider", "Knowledge API")
                                results = parsed
                                loading = false
                                if (parsed.isEmpty()) error = "No results found. Try a different query."
                            }
                        } catch (exception: Exception) {
                            Log.w("KnowledgeHunting", "Search failed", exception)
                            mainHandler.post {
                                loading = false
                                error = "Could not reach Knowledge Hunting. Check your connection and try again."
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.size(8.dp))
                    Text("Searching…")
                } else Text("Search")
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (provider.isNotBlank() && results.isNotEmpty()) {
                Text("${results.size} results · $provider", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results, key = { it.url }) { result ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenSource(result.url) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(result.snippet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val metadata = listOfNotNull(
                                result.source,
                                result.stars?.let { "★ $it" },
                                result.language,
                                result.license
                            ).joinToString(" · ")
                            Text(metadata, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { onOpenSource(result.url) }, modifier = Modifier.align(Alignment.End)) {
                                Text("Open source ↗")
                            }
                        }
                    }
                }
            }
        }
    }
}
