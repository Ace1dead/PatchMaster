package com.patchmaster.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class LlmEngine(private val apiKey: String?) {
    private val baseUrl = "https://openrouter.ai/api/v1/chat/completions"
    private var model = "nousresearch/hermes-4-405b"
    private val siteUrl = "https://github.com/Ace1dead/PatchMaster"
    private val siteName = "PatchMaster"

    companion object {
        private const val DEFAULT_TIMEOUT = 120000
        private val LIBERATED_MODELS = listOf(
            "nousresearch/hermes-4-405b",
            "meta-llama/llama-3.1-405b-instruct",
            "cognitivecomputations/dolphin-mixtral-8x22b",
            "qwen/qwen-2.5-72b-instruct",
            "mistralai/mistral-large-2411",
            "anthropic/claude-sonnet-4.6",
            "x-ai/grok-4.5",
            "google/gemini-2.5-flash"
        )
    }

    fun setModel(modelName: String) { model = modelName }
    fun getModel(): String = model
    fun getAvailableModels(): List<String> = LIBERATED_MODELS

    fun hasApiKey(): Boolean = !apiKey.isNullOrEmpty()

    data class LlmResponse(
        val content: String,
        val model: String,
        val usage: Map<String, Int> = emptyMap(),
        val isError: Boolean = false,
        val errorMessage: String = ""
    )

    suspend fun chat(messages: List<Message>, jailbreak: Boolean = true): LlmResponse {
        return withContext(Dispatchers.IO) {
            try {
                val jsonMessages = JSONArray()
                if (jailbreak && messages.isNotEmpty()) {
                    jsonMessages.put(JSONObject().apply {
                        put("role", "system")
                        put("content", JailbreakPrompts.getSystemPrompt)
                    })
                }
                for (msg in messages) {
                    jsonMessages.put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", if (jailbreak && msg.role == "user") {
                            JailbreakPrompts.wrapPrompt(msg.content)
                        } else msg.content)
                    })
                }

                val requestBody = JSONObject().apply {
                    put("model", model)
                    put("messages", jsonMessages)
                    put("temperature", 0.9)
                    put("max_tokens", 4096)
                    put("top_p", 0.95)
                }

                val conn = URL(baseUrl).openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("HTTP-Referer", siteUrl)
                    setRequestProperty("X-Title", siteName)
                    connectTimeout = DEFAULT_TIMEOUT
                    readTimeout = DEFAULT_TIMEOUT
                    doOutput = true
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(requestBody.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                val responseStream = if (responseCode in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

                val reader = BufferedReader(InputStreamReader(responseStream))
                val responseText = reader.readText()
                reader.close()

                if (responseCode !in 200..299) {
                    return@withContext LlmResponse(
                        content = "",
                        model = model,
                        isError = true,
                        errorMessage = "HTTP $responseCode: ${responseText.take(500)}"
                    )
                }

                val json = JSONObject(responseText)
                val choice = json.getJSONArray("choices")?.optJSONObject(0)
                val content = choice?.optJSONObject("message")?.optString("content", "") ?: ""

                val usageJson = json.optJSONObject("usage")
                val usage = mapOf(
                    "prompt_tokens" to (usageJson?.optInt("prompt_tokens") ?: 0),
                    "completion_tokens" to (usageJson?.optInt("completion_tokens") ?: 0),
                    "total_tokens" to (usageJson?.optInt("total_tokens") ?: 0)
                )

                LlmResponse(
                    content = content,
                    model = json.optString("model", model),
                    usage = usage
                )
            } catch (e: Exception) {
                LlmResponse(
                    content = "",
                    model = model,
                    isError = true,
                    errorMessage = e.message ?: "Unknown error"
                )
            }
        }
    }

    data class Message(
        val role: String,
        val content: String
    )

    suspend fun isApiKeyValid(): Boolean {
        if (apiKey.isNullOrEmpty()) return false
        return withContext(Dispatchers.IO) {
            try {
                val conn = URL("https://openrouter.ai/api/v1/auth/key").openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.responseCode == 200
            } catch (e: Exception) {
                false
            }
        }
    }
}
