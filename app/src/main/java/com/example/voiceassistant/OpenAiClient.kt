package com.example.voiceassistant

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Minimal client for the OpenAI Chat Completions API. */
class OpenAiClient(private val apiKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Keeps the conversation so follow-up questions have context.
    private val history = JSONArray().apply {
        put(
            JSONObject()
                .put("role", "system")
                .put(
                    "content",
                    "You are a voice assistant. Answer in one to three short sentences " +
                        "that sound natural when read aloud. No markdown, no lists."
                )
        )
    }

    /** Blocking call: run it off the main thread. */
    fun ask(question: String): String {
        history.put(JSONObject().put("role", "user").put("content", question))

        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", history)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // Remove the unanswered question so history stays valid.
                history.remove(history.length() - 1)
                return when (response.code) {
                    401 -> "Your OpenAI API key was rejected."
                    429 -> "OpenAI says I'm over the rate limit or out of credit."
                    else -> "OpenAI returned error ${response.code}."
                }
            }
            val answer = JSONObject(text)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
            history.put(JSONObject().put("role", "assistant").put("content", answer))
            trimHistory()
            return answer
        }
    }

    // Keep the system prompt plus the last 10 messages to limit cost.
    private fun trimHistory() {
        while (history.length() > 11) history.remove(1)
    }

    companion object {
        const val MODEL = "gpt-4o-mini" // change to any chat model you have access to
    }
}
