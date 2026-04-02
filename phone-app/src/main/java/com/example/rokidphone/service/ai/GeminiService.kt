package com.example.rokidphone.service.ai

import android.util.Base64
import android.util.Log
import com.example.rokidphone.data.AiProvider
import com.example.rokidphone.service.SpeechResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gemini Service Implementation (Refactored)
 * Supports Gemini 2.5 Pro, 2.5 Flash, 2.5 Flash-Lite models
 * 
 * API Docs: https://ai.google.dev/docs
 * 
 * Features:
 * - Native audio input support (speech recognition)
 * - Multimodal support (images, audio, video, PDF)
 * - Long context support
 */
class GeminiService(
    apiKey: String,
    modelId: String = "gemini-2.5-flash",
    systemPrompt: String = "",
    temperature: Float = 0.7f,
    maxTokens: Int = 2048,
    topP: Float = 1.0f,
    internal val baseUrl: String = DEFAULT_BASE_URL,
    apiKeys: List<String> = emptyList(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BaseAiService(apiKey, modelId, systemPrompt, temperature, maxTokens, topP), AiServiceProvider {
    
    companion object {
        private const val TAG = "GeminiService"
        internal const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val ERROR_API_KEY_NOT_CONFIGURED = "API key not configured. Please set up an API key in Settings."
    }
    
    override val provider = AiProvider.GEMINI

    private enum class KeyFailureType {
        QUOTA,
        INVALID
    }

    private sealed interface RequestOutcome<out T> {
        data class Success<T>(val value: T) : RequestOutcome<T>
        data class RetrySameKey(val delayMs: Long = 0L) : RequestOutcome<Nothing>
        data class RetryNextKey(val failureType: KeyFailureType, val delayMs: Long = 0L) : RequestOutcome<Nothing>
        data class Abort<T>(val value: T) : RequestOutcome<T>
    }

    private val configuredApiKeys = apiKeys
        .ifEmpty { apiKey.lineSequence().toList() }
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()

    private val keyPool = GeminiKeyPool(configuredApiKeys)
    
    private val apiUrl: String
        get() = "$baseUrl/$modelId:generateContent"

    private fun hasConfiguredApiKeys(): Boolean = configuredApiKeys.isNotEmpty()

    private fun buildJsonRequest(apiKey: String, requestJson: JSONObject): Request {
        return Request.Builder()
            .url("$apiUrl?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private suspend fun <T> executeWithKeyPool(
        noKeyValue: T,
        exhaustedValue: T,
        requestName: String,
        action: suspend (apiKey: String, attempt: Int) -> RequestOutcome<T>
    ): T {
        if (!hasConfiguredApiKeys()) {
            Log.e(TAG, "API key is not configured")
            return noKeyValue
        }

        val attemptedKeys = mutableSetOf<String>()

        keyLoop@ while (attemptedKeys.size < configuredApiKeys.size) {
            val candidate = keyPool.nextCandidate() ?: break
            if (!attemptedKeys.add(candidate)) {
                break
            }

            for (attempt in 1..MAX_RETRIES) {
                try {
                    when (val outcome = action(candidate, attempt)) {
                        is RequestOutcome.Success -> {
                            keyPool.markSuccess(candidate)
                            return outcome.value
                        }

                        is RequestOutcome.RetrySameKey -> {
                            if (outcome.delayMs > 0) {
                                delay(outcome.delayMs)
                            }
                            if (attempt < MAX_RETRIES) {
                                continue
                            }
                            Log.w(TAG, "$requestName exhausted same-key retries for current Gemini key")
                            return exhaustedValue
                        }

                        is RequestOutcome.RetryNextKey -> {
                            if (configuredApiKeys.size == 1) {
                                if (outcome.failureType == KeyFailureType.QUOTA && attempt < MAX_RETRIES) {
                                    if (outcome.delayMs > 0) {
                                        delay(outcome.delayMs)
                                    }
                                    continue
                                }
                                return exhaustedValue
                            }
                            when (outcome.failureType) {
                                KeyFailureType.QUOTA -> keyPool.markQuotaFailure(candidate)
                                KeyFailureType.INVALID -> keyPool.markInvalidKey(candidate)
                            }
                            if (outcome.delayMs > 0) {
                                delay(outcome.delayMs)
                            }
                            continue@keyLoop
                        }

                        is RequestOutcome.Abort -> return outcome.value
                    }
                } catch (e: Exception) {
                    if (isNetworkException(e) && attempt < 2) {
                        Log.w(TAG, "Network error on attempt $attempt for $requestName, retrying same key...", e)
                        delay(RETRY_DELAY_MS * attempt)
                        continue
                    }
                    if (isNetworkException(e)) {
                        Log.w(TAG, "Network error persisted for $requestName, rotating Gemini key", e)
                        keyPool.markQuotaFailure(candidate)
                        continue@keyLoop
                    }
                    Log.e(TAG, "$requestName failed with non-network exception", e)
                    return exhaustedValue
                }
            }
        }

        return exhaustedValue
    }

    private fun isQuotaFailure(code: Int, responseBody: String?): Boolean {
        val lowerBody = responseBody.orEmpty().lowercase()
        return code == 429 ||
            code == 503 ||
            lowerBody.contains("resource_exhausted") ||
            lowerBody.contains("rate limit") ||
            lowerBody.contains("quota")
    }

    private fun isInvalidKeyFailure(code: Int, responseBody: String?): Boolean {
        val lowerBody = responseBody.orEmpty().lowercase()
        return code == 401 ||
            code == 403 ||
            lowerBody.contains("api key not valid") ||
            lowerBody.contains("invalid api key") ||
            lowerBody.contains("permission denied")
    }

    private fun isNonRetryableRequestFailure(code: Int, responseBody: String?): Boolean {
        return code in 400..499 && !isQuotaFailure(code, responseBody) && !isInvalidKeyFailure(code, responseBody)
    }

    private fun keyFailureDelayMs(code: Int, attempt: Int): Long {
        return if (code == 429 || code == 503) 2000L * attempt else 0L
    }
    
    /**
     * Speech Recognition - Gemini native audio support
     */
    override suspend fun transcribe(pcmAudioData: ByteArray, languageCode: String): SpeechResult {
        return withContext(ioDispatcher) {
            Log.d(TAG, "Starting transcription, audio size: ${pcmAudioData.size} bytes, language: $languageCode")
            
            if (!hasConfiguredApiKeys()) {
                return@withContext SpeechResult.Error(ERROR_API_KEY_NOT_CONFIGURED)
            }
            
            if (pcmAudioData.size < 1000) {
                return@withContext SpeechResult.Error("Audio too short, please try again")
            }
            
            val wavData = pcmToWav(pcmAudioData)
            val audioBase64 = Base64.encodeToString(wavData, Base64.NO_WRAP)
            
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "audio/wav")
                                    put("data", audioBase64)
                                })
                            })
                            put(JSONObject().apply {
                                put("text", """Transcribe the speech in this audio to text.
The speaker is speaking ${getLanguageDisplayName(languageCode)}. Output the transcription in the original language spoken.
Rules:
1. Only output the actual spoken words, nothing else
2. If the audio contains no clear speech, only noise, silence, or unintelligible sounds, respond with exactly: Unable to recognize
3. Do not output timestamps, time codes, or numbers like "00:00"
4. Do not describe the audio or add any explanation
5. If you hear beeps, static, or mechanical sounds instead of speech, respond with: Unable to recognize""")
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 500)
                })
            }
            
            executeWithKeyPool(
                noKeyValue = SpeechResult.Error(ERROR_API_KEY_NOT_CONFIGURED),
                exhaustedValue = SpeechResult.Error("Unable to recognize speech, please try again"),
                requestName = "transcription"
            ) { currentApiKey, attempt ->
                Log.d(TAG, "Sending transcription request to Gemini (attempt $attempt)")

                val request = buildJsonRequest(currentApiKey, requestJson)

                client.newCall(request).execute().use { response ->
                    parseTranscriptionResponse(response, attempt)
                }
            }
        }
    }
    
    /**
     * Transcribe pre-encoded audio file (M4A, MP3, etc.)
     * Sends encoded audio directly to Gemini with the correct MIME type,
     * bypassing the PCM-to-WAV conversion used by transcribe().
     */
    override suspend fun transcribeAudioFile(audioData: ByteArray, mimeType: String, languageCode: String): SpeechResult {
        return withContext(ioDispatcher) {
            Log.d(TAG, "Starting audio file transcription, size: ${audioData.size} bytes, mimeType: $mimeType, language: $languageCode")
            
            if (!hasConfiguredApiKeys()) {
                return@withContext SpeechResult.Error(ERROR_API_KEY_NOT_CONFIGURED)
            }
            
            if (audioData.size < 1000) {
                return@withContext SpeechResult.Error("Audio too short, please try again")
            }
            
            // Send encoded audio directly (no PCM-to-WAV conversion)
            val audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP)
            
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", mimeType)
                                    put("data", audioBase64)
                                })
                            })
                            put(JSONObject().apply {
                                put("text", """Transcribe the speech in this audio to text.
The speaker is speaking ${getLanguageDisplayName(languageCode)}. Output the transcription in the original language spoken.
Rules:
1. Only output the actual spoken words, nothing else
2. If the audio contains no clear speech, only noise, silence, or unintelligible sounds, respond with exactly: Unable to recognize
3. Do not output timestamps, time codes, or numbers like "00:00"
4. Do not describe the audio or add any explanation
5. If you hear beeps, static, or mechanical sounds instead of speech, respond with: Unable to recognize""")
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 500)
                })
            }
            
            executeWithKeyPool(
                noKeyValue = SpeechResult.Error(ERROR_API_KEY_NOT_CONFIGURED),
                exhaustedValue = SpeechResult.Error("Unable to recognize speech, please try again"),
                requestName = "audio file transcription"
            ) { currentApiKey, attempt ->
                Log.d(TAG, "Sending audio file transcription request to Gemini (attempt $attempt)")

                val request = buildJsonRequest(currentApiKey, requestJson)

                client.newCall(request).execute().use { response ->
                    parseTranscriptionResponse(response, attempt)
                }
            }
        }
    }
    
    /**
     * Text Chat
     */
    override suspend fun chat(userMessage: String): String {
        return withContext(ioDispatcher) {
            Log.d(TAG, "Chat request: $userMessage")
            
            if (!hasConfiguredApiKeys()) {
                return@withContext ERROR_API_KEY_NOT_CONFIGURED
            }
            
            val contents = JSONArray()
            
            // Conversation history
            for ((role, content) in conversationHistory.takeLast(6)) {
                val geminiRole = if (role == "user") "user" else "model"
                contents.put(JSONObject().apply {
                    put("role", geminiRole)
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", content))
                    })
                })
            }
            
            // Current user message
            contents.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", userMessage))
                })
            })
            
            val requestJson = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", getFullSystemPrompt())))
                })
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", temperature.toDouble())
                    put("maxOutputTokens", maxTokens)
                    put("topP", topP.toDouble())
                })
            }
            
            executeWithKeyPool(
                noKeyValue = ERROR_API_KEY_NOT_CONFIGURED,
                exhaustedValue = "Sorry, AI service is temporarily unavailable. Please try again later.",
                requestName = "chat"
            ) { currentApiKey, attempt ->
                Log.d(TAG, "Sending chat request to Gemini (attempt $attempt)")

                val request = buildJsonRequest(currentApiKey, requestJson)

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    if (response.isSuccessful && responseBody != null) {
                        val json = JSONObject(responseBody)
                        val text = extractTextFromResponse(json)

                        if (!text.isNullOrEmpty()) {
                            addToHistory(userMessage, text)
                            Log.d(TAG, "Gemini response: $text")
                            RequestOutcome.Success(text)
                        } else {
                            RequestOutcome.RetrySameKey()
                        }
                    } else {
                        Log.e(TAG, "API error: ${response.code}, body: $responseBody")
                        when {
                            isQuotaFailure(response.code, responseBody) -> {
                                RequestOutcome.RetryNextKey(
                                    failureType = KeyFailureType.QUOTA,
                                    delayMs = keyFailureDelayMs(response.code, attempt)
                                )
                            }

                            isInvalidKeyFailure(response.code, responseBody) -> {
                                RequestOutcome.RetryNextKey(failureType = KeyFailureType.INVALID)
                            }

                            isNonRetryableRequestFailure(response.code, responseBody) -> {
                                RequestOutcome.Abort("Sorry, AI service is temporarily unavailable. Please try again later.")
                            }

                            else -> RequestOutcome.RetrySameKey(delayMs = 1000L * attempt)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Image Analysis
     */
    override suspend fun analyzeImage(imageData: ByteArray, prompt: String): String {
        return withContext(ioDispatcher) {
            Log.d(TAG, "Image analysis request, size: ${imageData.size} bytes")
            
            if (!hasConfiguredApiKeys()) {
                return@withContext "Sorry, unable to analyze this image. API key not configured."
            }
            
            val imageBase64 = Base64.encodeToString(imageData, Base64.NO_WRAP)
            
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", imageBase64)
                                })
                            })
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", temperature.toDouble())
                    put("maxOutputTokens", maxTokens.coerceAtMost(4096))
                })
            }
            
            executeWithKeyPool(
                noKeyValue = "Sorry, unable to analyze this image. API key not configured.",
                exhaustedValue = "Sorry, unable to analyze this image.",
                requestName = "image analysis"
            ) { currentApiKey, attempt ->
                Log.d(TAG, "Sending image analysis request to Gemini (attempt $attempt)")

                val request = buildJsonRequest(currentApiKey, requestJson)

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    if (response.isSuccessful && responseBody != null) {
                        val json = JSONObject(responseBody)
                        val text = extractTextFromResponse(json)
                        if (text.isNullOrBlank()) {
                            Log.w(TAG, "Empty response from Gemini, response: $responseBody")
                            RequestOutcome.RetrySameKey()
                        } else {
                            Log.d(TAG, "Image analysis successful, response length: ${text.length}")
                            RequestOutcome.Success(text)
                        }
                    } else {
                        Log.e(TAG, "API error: ${response.code}, body: $responseBody")
                        when {
                            isQuotaFailure(response.code, responseBody) -> {
                                RequestOutcome.RetryNextKey(
                                    failureType = KeyFailureType.QUOTA,
                                    delayMs = keyFailureDelayMs(response.code, attempt)
                                )
                            }

                            isInvalidKeyFailure(response.code, responseBody) -> {
                                RequestOutcome.RetryNextKey(failureType = KeyFailureType.INVALID)
                            }

                            isNonRetryableRequestFailure(response.code, responseBody) -> {
                                RequestOutcome.Abort("Sorry, unable to analyze this image.")
                            }

                            else -> RequestOutcome.RetrySameKey(delayMs = 1000L * attempt)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Shared helper: parse an HTTP response from a transcription request.
     * Returns the transcribed text on success, or null if the response is invalid.
     */
    private suspend fun parseTranscriptionResponse(
        response: Response,
        attempt: Int
    ): RequestOutcome<SpeechResult> {
        val responseBody = response.body.string()
        if (!response.isSuccessful) {
            Log.e(TAG, "API error: ${response.code}, body: $responseBody")
            return when {
                isQuotaFailure(response.code, responseBody) -> RequestOutcome.RetryNextKey(
                    failureType = KeyFailureType.QUOTA,
                    delayMs = keyFailureDelayMs(response.code, attempt)
                )

                isInvalidKeyFailure(response.code, responseBody) -> RequestOutcome.RetryNextKey(
                    failureType = KeyFailureType.INVALID
                )

                isNonRetryableRequestFailure(response.code, responseBody) -> {
                    RequestOutcome.Abort(SpeechResult.Error("Unable to recognize speech, please try again"))
                }

                else -> RequestOutcome.RetrySameKey(delayMs = 1000L * attempt)
            }
        }
        val json = JSONObject(responseBody)
        val text = extractTextFromResponse(json)
        if (isValidTranscription(text)) {
            Log.d(TAG, "Transcription: $text")
            return RequestOutcome.Success(SpeechResult.Success(text!!))
        }
        if (!text.isNullOrEmpty()) {
            Log.d(TAG, "Filtered invalid transcription: $text")
        }
        return RequestOutcome.RetrySameKey()
    }

    /**
     * Check whether a transcription result is valid spoken text
     */
    private fun isValidTranscription(text: String?): Boolean {
        return !text.isNullOrEmpty() &&
                !text.contains("Unable to recognize") &&
                !isGeminiErrorResponse(text) &&
                !isInvalidTranscription(text)
    }

    private fun extractTextFromResponse(json: JSONObject): String? {
        val candidates = json.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val content = candidates.getJSONObject(0).optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
                return parts.getJSONObject(0).optString("text", "").trim()
            }
        }
        return null
    }
    
    /**
     * Detect Gemini error/apology responses that indicate no valid speech was recognized
     * These are full sentences from Gemini explaining it couldn't transcribe
     */
    private fun isGeminiErrorResponse(text: String): Boolean {
        val lowerText = text.lowercase()
        
        // Common Gemini apology patterns when it can't recognize speech
        val errorPatterns = listOf(
            "i'm sorry",
            "i am sorry", 
            "cannot recognize",
            "cannot provide a transcription",
            "cannot transcribe",
            "no discernible speech",
            "no speech",
            "only noise",
            "appears to contain only noise",
            "unable to transcribe",
            "no audio content",
            "empty audio",
            "silence"
        )
        
        if (errorPatterns.any { lowerText.contains(it) }) {
            Log.d(TAG, "Detected Gemini error response")
            return true
        }
        
        return false
    }
    
    /**
     * Detect invalid transcription patterns that indicate noise/silence was misinterpreted
     * These patterns include:
     * - Repeated timestamp patterns (00:00, 00:01, etc.)
     * - Repeated zeros or colons
     * - Very repetitive short patterns
     */
    private fun isInvalidTranscription(text: String): Boolean {
        val trimmedText = text.trim()
        
        // Empty or very short text
        if (trimmedText.length < 2) return true
        
        // Pattern: mostly zeros, colons, and spaces (like "00:00:00:00..." or "00:00 00:01...")
        val timestampPattern = Regex("^[0-9: \\n]+$")
        if (timestampPattern.matches(trimmedText)) {
            Log.d(TAG, "Detected timestamp-only pattern")
            return true
        }
        
        // Pattern: repeated timestamp format (00:00, 00:01, etc.)
        val repeatedTimestampPattern = Regex("(\\d{2}:\\d{2}[:\\s]*){3,}")
        if (repeatedTimestampPattern.containsMatchIn(trimmedText)) {
            Log.d(TAG, "Detected repeated timestamp pattern")
            return true
        }
        
        // Pattern: more than 50% of text is zeros, colons, or newlines
        val invalidChars = trimmedText.count { it == '0' || it == ':' || it == '\n' || it == ' ' }
        val ratio = invalidChars.toFloat() / trimmedText.length
        if (ratio > 0.7f && trimmedText.length > 10) {
            Log.d(TAG, "Detected high ratio of invalid chars: $ratio")
            return true
        }
        
        // Pattern: very repetitive content (same short sequence repeated many times)
        if (trimmedText.length >= 20) {
            val firstFew = trimmedText.take(5)
            val occurrences = trimmedText.windowed(5, 1).count { it == firstFew }
            if (occurrences > trimmedText.length / 8) {
                Log.d(TAG, "Detected highly repetitive pattern")
                return true
            }
        }
        
        return false
    }
    
    /**
     * Convert language code to display name for transcription prompt
     */
    private fun getLanguageDisplayName(languageCode: String): String {
        return when {
            languageCode.startsWith("zh-TW") || languageCode.startsWith("zh-Hant") -> "Traditional Chinese (繁體中文)"
            languageCode.startsWith("zh-CN") || languageCode.startsWith("zh-Hans") || languageCode.startsWith("zh") -> "Simplified Chinese (简体中文)"
            languageCode.startsWith("ja") -> "Japanese (日本語)"
            languageCode.startsWith("ko") -> "Korean (한국어)"
            languageCode.startsWith("en") -> "English"
            languageCode.startsWith("fr") -> "French (Français)"
            languageCode.startsWith("es") -> "Spanish (Español)"
            languageCode.startsWith("it") -> "Italian (Italiano)"
            languageCode.startsWith("ru") -> "Russian (Русский)"
            languageCode.startsWith("uk") -> "Ukrainian (Українська)"
            languageCode.startsWith("th") -> "Thai (ไทย)"
            languageCode.startsWith("vi") -> "Vietnamese (Tiếng Việt)"
            languageCode.startsWith("ar") -> "Arabic (العربية)"
            else -> "the language with code '$languageCode'"
        }
    }
}
