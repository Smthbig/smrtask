package com.smrtask

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Maintains conversation history for Gemini requests.
 */
class GeminiConversation {
    private val messages = mutableListOf<JSONObject>()

    fun addUserMessage(content: String) {
        messages.add(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", content) })
            })
        })
    }

    fun addModelMessage(content: String) {
        if (content.isNotBlank()) {
            messages.add(JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", content) })
                })
            })
        } else {
            Log.w("GeminiConversation", "Skipped empty model message.")
        }
    }

    fun getContents(): List<JSONObject> = messages.toList()

    fun clear() = messages.clear()
}