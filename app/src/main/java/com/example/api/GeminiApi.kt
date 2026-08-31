package com.example.api

import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object GeminiApiClient {
    private const val TAG = "GeminiApiClient"
    private val requestIdCounter = AtomicInteger(0)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val SUPPORTED_MODELS = listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.0-flash-lite")

    fun generateContent(prompt: String, systemInstruction: String): String {
        val requestId = requestIdCounter.incrementAndGet()
        val apiKey = BuildConfig.GEMINI_API_KEY
        val maskedKey = if (apiKey.length > 8) "${apiKey.take(6)}...${apiKey.takeLast(4)}" else if (apiKey.isNotEmpty()) "PRESENT(${apiKey.length} chars)" else "EMPTY"

        Log.d(TAG, "==================================================")
        Log.d(TAG, "[GeminiRequest #$requestId] New AI Request Initiated")
        Log.d(TAG, "[GeminiRequest #$requestId] API Key Status: $maskedKey (Valid prefix: ${apiKey.startsWith("AIza")})")
        Log.d(TAG, "[GeminiRequest #$requestId] Prompt Length: ${prompt.length} chars")
        Log.d(TAG, "==================================================")

        if (apiKey.isBlank()) {
            Log.e(TAG, "[GeminiRequest #$requestId] Aborting: Gemini API Key is missing in BuildConfig.")
            return "ERROR_NO_API_KEY"
        }

        // Build valid JSON request payload using JSONObject natively
        val requestJson = JSONObject().apply {
            val contentsArr = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArr = JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    }
                    put("parts", partsArr)
                }
                put(contentObj)
            }
            put("contents", contentsArr)

            if (systemInstruction.isNotBlank()) {
                val sysObj = JSONObject().apply {
                    val partsArr = JSONArray().apply {
                        put(JSONObject().put("text", systemInstruction))
                    }
                    put("parts", partsArr)
                }
                put("systemInstruction", sysObj)
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        // Try model endpoints
        for (modelName in SUPPORTED_MODELS) {
            val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
            val url = "$baseUrl?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val backoffDelaysMs = listOf(1000L, 2000L, 4000L)
            val maxAttempts = 1 + backoffDelaysMs.size

            for (attempt in 1..maxAttempts) {
                Log.d(TAG, "[GeminiRequest #$requestId] Sending HTTP POST to $modelName (Attempt $attempt/$maxAttempts)...")
                var responseCode = -1
                var is429 = false
                var isQuotaExhausted = false

                try {
                    client.newCall(request).execute().use { response ->
                        responseCode = response.code
                        val bodyString = response.body?.string() ?: ""

                        Log.d(TAG, "[GeminiRequest #$requestId] Model $modelName -> HTTP $responseCode")
                        if (responseCode != 200) {
                            Log.e(TAG, "[GeminiRequest #$requestId] Error Body: $bodyString")
                        }

                        if (responseCode == 200 && bodyString.isNotEmpty()) {
                            val rootJson = JSONObject(bodyString)
                            val candidates = rootJson.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val firstCandidate = candidates.getJSONObject(0)
                                val content = firstCandidate.optJSONObject("content")
                                if (content != null) {
                                    val parts = content.optJSONArray("parts")
                                    if (parts != null && parts.length() > 0) {
                                        val textResult = parts.getJSONObject(0).optString("text", "")
                                        if (textResult.isNotBlank()) {
                                            Log.d(TAG, "[GeminiRequest #$requestId] Success on $modelName! Text length: ${textResult.length} chars")
                                            return textResult
                                        }
                                    }
                                }
                            }
                        } else if (responseCode == 429) {
                            is429 = true
                            if (bodyString.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                                bodyString.contains("quota", ignoreCase = true)) {
                                isQuotaExhausted = true
                            }
                        } else if (responseCode == 400 || responseCode == 404) {
                            Log.e(TAG, "[GeminiRequest #$requestId] HTTP $responseCode for $modelName. Trying fallback model if available.")
                            break // Break inner attempt loop to try next model
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[GeminiRequest #$requestId] Exception calling $modelName: ${e.message}", e)
                }

                if (is429) {
                    if (isQuotaExhausted) {
                        Log.e(TAG, "[GeminiRequest #$requestId] Rate limit/Quota exhausted for $modelName. Trying next model...")
                        break // Try next model in SUPPORTED_MODELS
                    }
                    if (attempt <= backoffDelaysMs.size) {
                        val delayMs = backoffDelaysMs[attempt - 1]
                        Log.d(TAG, "[GeminiRequest #$requestId] Retrying in ${delayMs}ms due to 429...")
                        try {
                            Thread.sleep(delayMs)
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return "ERROR_INTERRUPTED"
                        }
                    }
                }
            }
        }

        Log.e(TAG, "[GeminiRequest #$requestId] All model endpoints failed.")
        return "ERROR_SERVICE_UNAVAILABLE"
    }
}


