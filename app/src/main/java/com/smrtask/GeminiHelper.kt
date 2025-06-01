package com.smrtask

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiHelper(
    private val context: Context,
    private val errorHandler: (String) -> Unit,
    private val uiCallback: (block: () -> Unit) -> Unit
) {
    companion object {
        private const val TAG = "GeminiHelper"
        private const val API_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = mutableListOf<JSONObject>()

    private fun getPrefs() = context.getSharedPreferences("smrtask_prefs", Context.MODE_PRIVATE)

    private fun getApiKey(): String = getPrefs().getString("api_key", "") ?: ""

    private fun getModelId(): String =
        getPrefs().getString("model_id", "gemini-2.5-flash-preview-04-17") ?: "gemini-2.5-flash-preview-04-17"

    private fun getTemperature(): Float =
        getPrefs().getFloat("temperature", 0.5f)

    private fun getMaxTokens(): Int =
        getPrefs().getInt("max_tokens", 8192)

    fun resetConversation() {
        conversationHistory.clear()
    }

    fun sendUserMessage(userText: String, expectsJson: Boolean = false, callback: (String) -> Unit) {
        if (userText.isBlank()) {
            uiCallback { errorHandler("Cannot send empty message.") }
            return
        }

        val userMessage = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userText)))
        }
        conversationHistory.add(userMessage)

        sendGeminiRequest(conversationHistory, expectsJson) { response ->
            val replyText = formatGeminiResponseText(response).trim()
            if (replyText.isNotEmpty()) {
                conversationHistory.add(
                    JSONObject().apply {
                        put("role", "model")
                        put("parts", JSONArray().put(JSONObject().put("text", replyText)))
                    }
                )
                callback(convertToStyledHtml(replyText))
            } else {
                uiCallback { errorHandler("Received empty response from Gemini.") }
            }
        }
    }

    private fun sendGeminiRequest(contents: List<JSONObject>, expectsJson: Boolean, callback: (JSONObject) -> Unit) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            uiCallback { errorHandler("Gemini API key is not set.") }
            return
        }

        val modelId = getModelId()
        val requestJson = buildRequestJson(contents, expectsJson)
        val requestBody = requestJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val url = API_URL_TEMPLATE.format(modelId, apiKey)

        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network error", e)
                uiCallback { errorHandler("Network error: ${e.localizedMessage ?: "Unknown error"}") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyString = it.body?.string()
                    if (!it.isSuccessful || bodyString.isNullOrEmpty()) {
                        handleErrorResponse(it, bodyString)
                        return
                    }

                    try {
                        val jsonResponse = JSONObject(bodyString)
                        if (isBlockedResponse(jsonResponse)) {
                            handleBlockedResponse(jsonResponse)
                        } else {
                            uiCallback { callback(jsonResponse) }
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error parsing response", ex)
                        uiCallback { errorHandler("Failed to parse response: ${ex.localizedMessage}") }
                    }
                }
            }
        })
    }

    private fun buildRequestJson(contents: List<JSONObject>, expectsJson: Boolean): JSONObject {
        return JSONObject().apply {
            put("contents", JSONArray(contents))
            put("generationConfig", JSONObject().apply {
                put("temperature", getTemperature())
                put("maxOutputTokens", getMaxTokens())
                put("topP", 1)
                put("topK", 1)
            })
            put("safetySettings", JSONArray().apply {
                put(safetyRule("HARM_CATEGORY_HARASSMENT"))
                put(safetyRule("HARM_CATEGORY_HATE_SPEECH"))
                put(safetyRule("HARM_CATEGORY_SEXUALLY_EXPLICIT"))
                put(safetyRule("HARM_CATEGORY_DANGEROUS_CONTENT"))
            })

            if (expectsJson) {
                put("response_mime_type", "application/json")
            }
        }
    }

    // ✅ Fixed: Removed duplicate safetyRule definition
    private fun safetyRule(category: String): JSONObject {
        return JSONObject().apply {
            put("category", category)
            put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
        }
    }

    private fun formatGeminiResponseText(response: JSONObject): String {
        val builder = StringBuilder()
        val candidates = response.optJSONArray("candidates") ?: return ""

        for (i in 0 until candidates.length()) {
            val content = candidates.optJSONObject(i)?.optJSONObject("content") ?: continue
            val parts = content.optJSONArray("parts") ?: continue

            for (j in 0 until parts.length()) {
                val part = parts.optJSONObject(j)
                if (part != null && part.has("text")) {
                    builder.append(part.getString("text")).append("\n")
                }
            }
        }
        return builder.toString().trim()
    }

    private fun convertToStyledHtml(text: String): String {
        val lines = text.trim().lines()
        val htmlBuilder = StringBuilder()

        var inList = false
        var inPre = false

        fun closeList() {
            if (inList) {
                htmlBuilder.append("</ul>")
                inList = false
            }
        }

        for (line in lines) {
            val trimmed = line.trim()

            when {
                trimmed.startsWith("```") -> {
                    if (inPre) {
                        htmlBuilder.append("</pre>")
                        inPre = false
                    } else {
                        closeList()
                        htmlBuilder.append("<pre>")
                        inPre = true
                    }
                }

                inPre -> htmlBuilder.append(trimmed).append("\n")

                trimmed.matches(Regex("""^#{1,6}\s+.*""")) -> {
                    closeList()
                    val level = trimmed.takeWhile { it == '#' }.length
                    val headerText = trimmed.drop(level).trim()
                    htmlBuilder.append("<h$level>").append(headerText).append("</h$level>")
                }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.matches(Regex("""\d+\.\s+.*""")) -> {
                    if (!inList) {
                        htmlBuilder.append("<ul>")
                        inList = true
                    }
                    val itemText = trimmed.replace(Regex("""^(-|\*|\d+\.)\s+"""), "")
                    htmlBuilder.append("<li>").append(itemText).append("</li>")
                }

                trimmed.isBlank() -> {
                    closeList()
                    htmlBuilder.append("<br>")
                }

                else -> {
                    closeList()
                    val processed = trimmed
                        .replace(Regex("""\*\*(.*?)\*\*"""), "<strong>$1</strong>")
                        .replace(Regex("""\*(.*?)\*"""), "<em>$1</em>")
                        .replace(Regex("""`([^`]+)`"""), "<code>$1</code>")
                    htmlBuilder.append("<p>").append(processed).append("</p>")
                }
            }
        }

        closeList()
        if (inPre) htmlBuilder.append("</pre>")

        return """
            <html>
            <head>
                <style>
                    body {
                        font-family: 'Segoe UI', sans-serif;
                        background: #ffffff;
                        padding: 20px;
                        color: #333;
                        font-size: 16px;
                    }
                    p { margin-bottom: 1em; }
                    h1, h2, h3, h4, h5, h6 { color: #1a73e8; }
                    ul { padding-left: 20px; }
                    li { margin-bottom: 8px; }
                    code {
                        background: #f4f4f4;
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-family: monospace;
                    }
                    pre {
                        background: #f4f4f4;
                        padding: 10px;
                        border-radius: 5px;
                        overflow-x: auto;
                        white-space: pre-wrap;
                        font-family: monospace;
                    }
                </style>
            </head>
            <body>
                ${htmlBuilder.toString()}
            </body>
            </html>
        """.trimIndent()
    }

    private fun isBlockedResponse(json: JSONObject): Boolean {
        return json.optJSONObject("promptFeedback")?.optString("blockReason") != null
    }

    private fun handleBlockedResponse(json: JSONObject) {
        val feedback = json.getJSONObject("promptFeedback")
        val blockReason = feedback.optString("blockReason", "Unknown")
        val safetyRatings = feedback.optJSONArray("safetyRatings")?.toString(2) ?: "N/A"

        uiCallback {
            errorHandler("Request blocked by safety rules: $blockReason\nSafety Ratings: $safetyRatings")
        }
    }

    private fun handleErrorResponse(response: Response, responseBody: String?) {
        var message = "API Error: ${response.code} ${response.message}"
        responseBody?.let {
            try {
                val errorJson = JSONObject(it)
                val detailed = errorJson.optJSONObject("error")?.optString("message")
                if (!detailed.isNullOrBlank()) {
                    message += "\nDetails: $detailed"
                }
            } catch (_: Exception) {}
        }
        uiCallback { errorHandler(message) }
    }
}