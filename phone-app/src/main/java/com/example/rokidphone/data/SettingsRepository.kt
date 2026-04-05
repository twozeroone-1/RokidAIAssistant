package com.example.rokidphone.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.rokidcommon.protocol.LiveRagDisplayMode
import com.example.rokidcommon.protocol.LiveRagSplitScrollMode
import com.example.rokidphone.R
import com.example.rokidphone.service.stt.SttProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Settings Repository
 * Uses EncryptedSharedPreferences for secure API Key storage
 */
class SettingsRepository(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "rokid_api_settings"
        
        // Keys for general settings
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_ANSWER_MODE = "answer_mode"
        private const val KEY_NETWORK_PROFILE = "network_profile"
        private const val KEY_DOCS_PROVIDER = "docs_provider"
        private const val KEY_LIVE_MODE_ENABLED = "live_mode_enabled"
        private const val KEY_LIVE_RAG_ENABLED = "live_rag_enabled"
        private const val KEY_LIVE_ANSWER_AUDIO_ENABLED = "live_answer_audio_enabled"
        private const val KEY_LIVE_MINIMAL_UI_ENABLED = "live_minimal_ui_enabled"
        private const val KEY_LIVE_BARGE_IN_ENABLED = "live_barge_in_enabled"
        private const val KEY_LIVE_LONG_SESSION_ENABLED = "live_long_session_enabled"
        private const val KEY_LIVE_GOOGLE_SEARCH_ENABLED = "live_google_search_enabled"
        private const val KEY_LIVE_VOICE_NAME = "live_voice_name"
        private const val KEY_LIVE_THINKING_LEVEL = "live_thinking_level"
        private const val KEY_LIVE_THOUGHT_SUMMARIES_ENABLED = "live_thought_summaries_enabled"
        private const val KEY_LIVE_RAG_DISPLAY_MODE = "live_rag_display_mode"
        private const val KEY_LIVE_RAG_SPLIT_SCROLL_MODE = "live_rag_split_scroll_mode"
        private const val KEY_LIVE_RAG_AUTO_SCROLL_SPEED_LEVEL = "live_rag_auto_scroll_speed_level"
        private const val KEY_EXPERIMENTAL_LIVE_MIC_TUNING_ENABLED = "experimental_live_mic_tuning_enabled"
        private const val KEY_EXPERIMENTAL_LIVE_MIC_PROFILE = "experimental_live_mic_profile"
        private const val KEY_LIVE_INPUT_SOURCE = "live_input_source"
        private const val KEY_LIVE_OUTPUT_TARGET = "live_output_target"
        private const val KEY_LIVE_CAMERA_MODE = "live_camera_mode"
        private const val KEY_LIVE_CAMERA_INTERVAL_SEC = "live_camera_interval_sec"
        private const val KEY_STT_PROVIDER = "stt_provider"
        private const val KEY_SPEECH_LANGUAGE = "speech_language"
        private const val KEY_RESPONSE_LANGUAGE = "response_language"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        
        // Keys for API keys (stored encrypted)
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
        private const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_XAI_API_KEY = "xai_api_key"
        private const val KEY_ALIBABA_API_KEY = "alibaba_api_key"
        private const val KEY_ZHIPU_API_KEY = "zhipu_api_key"
        private const val KEY_BAIDU_API_KEY = "baidu_api_key"
        private const val KEY_BAIDU_SECRET_KEY = "baidu_secret_key"
        private const val KEY_PERPLEXITY_API_KEY = "perplexity_api_key"
        private const val KEY_MOONSHOT_API_KEY = "moonshot_api_key"
        private const val KEY_CUSTOM_API_KEY = "custom_api_key"
        
        // Keys for custom provider settings
        private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        private const val KEY_CUSTOM_MODEL_NAME = "custom_model_name"

        // Docs assistant keys
        private const val KEY_ANYTHING_LLM_SERVER_URL = "anythingllm_server_url"
        private const val KEY_ANYTHING_LLM_API_KEY = "anythingllm_api_key"
        private const val KEY_ANYTHING_LLM_WORKSPACE_SLUG = "anythingllm_workspace_slug"
        private const val KEY_ANYTHING_LLM_RUNTIME_ENABLED = "anythingllm_runtime_enabled"
        private const val KEY_ANYTHING_LLM_QUERY_MODE = "anythingllm_query_mode"
        private const val KEY_ANYTHING_LLM_LAST_HEALTH_STATUS = "anythingllm_last_health_status"
        private const val KEY_ANYTHING_LLM_LAST_HEALTH_MESSAGE = "anythingllm_last_health_message"
        private const val KEY_ANYTHING_LLM_RECENT_FAILURE_COUNT = "anythingllm_recent_failure_count"
        
        // Keys for recording settings
        private const val KEY_AUTO_ANALYZE_RECORDINGS = "auto_analyze_recordings"
        private const val KEY_PUSH_CHAT_TO_GLASSES = "push_chat_to_glasses"
        private const val KEY_PUSH_RECORDING_TO_GLASSES = "push_recording_to_glasses"
        private const val KEY_ALWAYS_START_NEW_AI_SESSION = "always_start_new_ai_session"
        private const val KEY_GLASSES_SLEEP_MODE_ENABLED = "glasses_sleep_mode_enabled"
        private const val KEY_RESPONSE_FONT_SCALE_PERCENT = "response_font_scale_percent"
        
        // Keys for TTS settings
        private const val KEY_AUTO_READ_RESPONSES_ALOUD = "auto_read_responses_aloud"
        private const val KEY_TTS_PROVIDER = "tts_provider"
        private const val KEY_TTS_VOICE_OVERRIDE = "tts_voice_override"
        private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_SYSTEM_TTS_SPEECH_RATE = "system_tts_speech_rate"
        private const val KEY_SYSTEM_TTS_PITCH = "system_tts_pitch"

        // Keys for LLM parameters
        private const val KEY_TEMPERATURE = "llm_temperature"
        private const val KEY_MAX_TOKENS = "llm_max_tokens"
        private const val KEY_TOP_P = "llm_top_p"
        private const val KEY_FREQUENCY_PENALTY = "llm_frequency_penalty"
        private const val KEY_PRESENCE_PENALTY = "llm_presence_penalty"
        
        @Volatile
        private var instance: SettingsRepository? = null
        
        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to regular SharedPreferences if encryption fails
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<ApiSettings> = _settingsFlow.asStateFlow()
    
    /**
     * Get current settings
     */
    fun getSettings(): ApiSettings = _settingsFlow.value
    
    /**
     * Load settings
     */
    private fun loadSettings(): ApiSettings {
        // Get saved system prompt or use current locale's default
        val savedSystemPrompt = prefs.getString(KEY_SYSTEM_PROMPT, null)
        val currentLocaleDefault = context.getString(R.string.default_system_prompt)
        
        // Sync system prompt with current app language if it's a default prompt from another language
        val systemPrompt = if (savedSystemPrompt == null) {
            currentLocaleDefault
        } else if (isDefaultPromptFromDifferentLocale(savedSystemPrompt)) {
            // User is using a default prompt but from a different language - update to current locale
            android.util.Log.d("SettingsRepository", "Syncing system prompt to current locale")
            prefs.edit().putString(KEY_SYSTEM_PROMPT, currentLocaleDefault).apply()
            currentLocaleDefault
        } else {
            savedSystemPrompt
        }
        
        return ApiSettings(
            responseFontScalePercent = ApiSettings.snapResponseFontScalePercent(
                prefs.getInt(
                    KEY_RESPONSE_FONT_SCALE_PERCENT,
                    ApiSettings.DEFAULT_RESPONSE_FONT_SCALE_PERCENT
                )
            ),
            aiProvider = AiProvider.fromName(
                prefs.getString(KEY_AI_PROVIDER, AiProvider.GEMINI.name) ?: AiProvider.GEMINI.name
            ),
            aiModelId = prefs.getString(KEY_AI_MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash",
            answerMode = AnswerMode.valueOf(
                prefs.getString(KEY_ANSWER_MODE, AnswerMode.GENERAL_AI.name) ?: AnswerMode.GENERAL_AI.name
            ),
            networkProfile = NetworkProfile.valueOf(
                prefs.getString(KEY_NETWORK_PROFILE, NetworkProfile.AUTO.name) ?: NetworkProfile.AUTO.name
            ),
            docsProvider = DocsProvider.valueOf(
                prefs.getString(KEY_DOCS_PROVIDER, DocsProvider.ANYTHING_LLM.name) ?: DocsProvider.ANYTHING_LLM.name
            ),
            liveModeEnabled = prefs.getBoolean(KEY_LIVE_MODE_ENABLED, false),
            liveRagEnabled = prefs.getBoolean(KEY_LIVE_RAG_ENABLED, false),
            liveAnswerAudioEnabled = prefs.getBoolean(KEY_LIVE_ANSWER_AUDIO_ENABLED, true),
            liveMinimalUiEnabled = prefs.getBoolean(KEY_LIVE_MINIMAL_UI_ENABLED, false),
            liveBargeInEnabled = prefs.getBoolean(KEY_LIVE_BARGE_IN_ENABLED, true),
            liveLongSessionEnabled = prefs.getBoolean(KEY_LIVE_LONG_SESSION_ENABLED, false),
            liveGoogleSearchEnabled = prefs.getBoolean(KEY_LIVE_GOOGLE_SEARCH_ENABLED, false),
            liveVoiceName = prefs.getString(KEY_LIVE_VOICE_NAME, GeminiLiveVoice.AOEDE.voiceName)
                ?: GeminiLiveVoice.AOEDE.voiceName,
            liveThinkingLevel = runCatching {
                LiveThinkingLevel.valueOf(
                    prefs.getString(KEY_LIVE_THINKING_LEVEL, LiveThinkingLevel.DEFAULT.name)
                        ?: LiveThinkingLevel.DEFAULT.name
                )
            }.getOrDefault(LiveThinkingLevel.DEFAULT),
            liveThoughtSummariesEnabled = prefs.getBoolean(KEY_LIVE_THOUGHT_SUMMARIES_ENABLED, false),
            liveRagDisplayMode = LiveRagDisplayMode.fromRaw(
                prefs.getString(KEY_LIVE_RAG_DISPLAY_MODE, LiveRagDisplayMode.RAG_RESULT_ONLY.name)
            ),
            liveRagSplitScrollMode = LiveRagSplitScrollMode.fromRaw(
                prefs.getString(KEY_LIVE_RAG_SPLIT_SCROLL_MODE, LiveRagSplitScrollMode.AUTO.name)
            ),
            liveRagAutoScrollSpeedLevel = ApiSettings.clampLiveRagAutoScrollSpeedLevel(
                prefs.getInt(
                    KEY_LIVE_RAG_AUTO_SCROLL_SPEED_LEVEL,
                    ApiSettings.DEFAULT_LIVE_RAG_AUTO_SCROLL_SPEED_LEVEL
                )
            ),
            experimentalLiveMicTuningEnabled = prefs.getBoolean(
                KEY_EXPERIMENTAL_LIVE_MIC_TUNING_ENABLED,
                false
            ),
            experimentalLiveMicProfile = ApiSettings.clampExperimentalLiveMicProfile(
                prefs.getInt(
                    KEY_EXPERIMENTAL_LIVE_MIC_PROFILE,
                    ApiSettings.DEFAULT_EXPERIMENTAL_LIVE_MIC_PROFILE
                )
            ),
            liveInputSource = LiveInputSource.valueOf(
                prefs.getString(KEY_LIVE_INPUT_SOURCE, LiveInputSource.AUTO.name) ?: LiveInputSource.AUTO.name
            ),
            liveOutputTarget = LiveOutputTarget.valueOf(
                prefs.getString(KEY_LIVE_OUTPUT_TARGET, LiveOutputTarget.AUTO.name) ?: LiveOutputTarget.AUTO.name
            ),
            liveCameraMode = LiveCameraMode.valueOf(
                prefs.getString(KEY_LIVE_CAMERA_MODE, LiveCameraMode.OFF.name) ?: LiveCameraMode.OFF.name
            ),
            liveCameraIntervalSec = prefs.getInt(KEY_LIVE_CAMERA_INTERVAL_SEC, 5),
            geminiApiKey = prefs.getString(KEY_GEMINI_API_KEY, "") ?: "",
            openaiApiKey = prefs.getString(KEY_OPENAI_API_KEY, "") ?: "",
            anthropicApiKey = prefs.getString(KEY_ANTHROPIC_API_KEY, "") ?: "",
            deepseekApiKey = prefs.getString(KEY_DEEPSEEK_API_KEY, "") ?: "",
            groqApiKey = prefs.getString(KEY_GROQ_API_KEY, "") ?: "",
            xaiApiKey = prefs.getString(KEY_XAI_API_KEY, "") ?: "",
            alibabaApiKey = prefs.getString(KEY_ALIBABA_API_KEY, "") ?: "",
            zhipuApiKey = prefs.getString(KEY_ZHIPU_API_KEY, "") ?: "",
            baiduApiKey = prefs.getString(KEY_BAIDU_API_KEY, "") ?: "",
            baiduSecretKey = prefs.getString(KEY_BAIDU_SECRET_KEY, "") ?: "",
            perplexityApiKey = prefs.getString(KEY_PERPLEXITY_API_KEY, "") ?: "",
            moonshotApiKey = prefs.getString(KEY_MOONSHOT_API_KEY, "") ?: "",
            customApiKey = prefs.getString(KEY_CUSTOM_API_KEY, "") ?: "",
            customBaseUrl = prefs.getString(KEY_CUSTOM_BASE_URL, "http://localhost:11434/v1/") 
                ?: "http://localhost:11434/v1/",
            customModelName = prefs.getString(KEY_CUSTOM_MODEL_NAME, "llama4") ?: "llama4",
            anythingLlmServerUrl = normalizeAnythingLlmServerUrl(prefs.getString(KEY_ANYTHING_LLM_SERVER_URL, "") ?: ""),
            anythingLlmApiKey = normalizeAnythingLlmApiKey(prefs.getString(KEY_ANYTHING_LLM_API_KEY, "") ?: ""),
            anythingLlmWorkspaceSlug = normalizeAnythingLlmWorkspaceSlug(prefs.getString(KEY_ANYTHING_LLM_WORKSPACE_SLUG, "") ?: ""),
            anythingLlmRuntimeEnabled = prefs.getBoolean(KEY_ANYTHING_LLM_RUNTIME_ENABLED, true),
            anythingLlmQueryMode = AnythingLlmQueryMode.valueOf(
                prefs.getString(KEY_ANYTHING_LLM_QUERY_MODE, AnythingLlmQueryMode.QUERY.name)
                    ?: AnythingLlmQueryMode.QUERY.name
            ),
            anythingLlmLastHealthStatus = DocsHealthStatus.valueOf(
                prefs.getString(KEY_ANYTHING_LLM_LAST_HEALTH_STATUS, DocsHealthStatus.UNKNOWN.name)
                    ?: DocsHealthStatus.UNKNOWN.name
            ),
            anythingLlmLastHealthMessage = prefs.getString(KEY_ANYTHING_LLM_LAST_HEALTH_MESSAGE, "") ?: "",
            anythingLlmRecentFailureCount = prefs.getInt(KEY_ANYTHING_LLM_RECENT_FAILURE_COUNT, 0),
            sttProvider = SttProvider.fromName(
                prefs.getString(KEY_STT_PROVIDER, SttProvider.GEMINI.name) ?: SttProvider.GEMINI.name
            ),
            // Use device locale (e.g. "ko-KR") as the first-run default so new users get
            // the correct TTS and response language automatically.
            // Existing users who already have a saved value keep their preference unchanged.
            speechLanguage = prefs.getString(
                KEY_SPEECH_LANGUAGE,
                Locale.getDefault().toLanguageTag()
            ) ?: Locale.getDefault().toLanguageTag(),
            responseLanguage = prefs.getString(
                KEY_RESPONSE_LANGUAGE,
                Locale.getDefault().toLanguageTag()
            ) ?: Locale.getDefault().toLanguageTag(),
            systemPrompt = systemPrompt,
            autoReadResponsesAloud = prefs.getBoolean(KEY_AUTO_READ_RESPONSES_ALOUD, true),
            ttsProvider = TtsProvider.fromName(
                prefs.getString(KEY_TTS_PROVIDER, TtsProvider.EDGE_TTS.name) ?: TtsProvider.EDGE_TTS.name
            ),
            ttsVoiceOverride = prefs.getString(KEY_TTS_VOICE_OVERRIDE, "") ?: "",
            ttsSpeechRate = prefs.getFloat(KEY_TTS_SPEECH_RATE, 1.0f),
            ttsPitch = prefs.getFloat(KEY_TTS_PITCH, 0.0f),
            systemTtsSpeechRate = prefs.getFloat(KEY_SYSTEM_TTS_SPEECH_RATE, 1.0f),
            systemTtsPitch = prefs.getFloat(KEY_SYSTEM_TTS_PITCH, 1.0f),
            autoAnalyzeRecordings = prefs.getBoolean(KEY_AUTO_ANALYZE_RECORDINGS, true),
            pushChatToGlasses = prefs.getBoolean(KEY_PUSH_CHAT_TO_GLASSES, true),
            pushRecordingToGlasses = prefs.getBoolean(KEY_PUSH_RECORDING_TO_GLASSES, true),
            alwaysStartNewAiSession = prefs.getBoolean(KEY_ALWAYS_START_NEW_AI_SESSION, false),
            glassesSleepModeEnabled = prefs.getBoolean(KEY_GLASSES_SLEEP_MODE_ENABLED, false),
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
            maxTokens = prefs.getInt(KEY_MAX_TOKENS, 2048),
            topP = prefs.getFloat(KEY_TOP_P, 1.0f),
            frequencyPenalty = prefs.getFloat(KEY_FREQUENCY_PENALTY, 0.0f),
            presencePenalty = prefs.getFloat(KEY_PRESENCE_PENALTY, 0.0f)
        )
    }
    
    /**
     * Check if a system prompt is a default prompt from a different locale than the current app locale
     */
    private fun isDefaultPromptFromDifferentLocale(prompt: String): Boolean {
        val currentDefault = context.getString(R.string.default_system_prompt)
        
        // If it matches current locale's default, it's fine
        if (prompt == currentDefault) return false
        
        // Check if it's a default prompt from any other language
        for (lang in AppLanguage.entries) {
            try {
                val langDefault = getDefaultSystemPromptForLanguage(lang)
                if (prompt == langDefault) {
                    // It's a default prompt from a different language - needs sync
                    return true
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
        
        // It's a custom prompt, don't touch it
        return false
    }

    private fun normalizeGeminiKeyInput(raw: String): String {
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
    }

    /**
     * Save settings
     */
    fun saveSettings(settings: ApiSettings) {
        val normalizedSettings = settings.copy(
            responseFontScalePercent = ApiSettings.snapResponseFontScalePercent(settings.responseFontScalePercent),
            liveRagAutoScrollSpeedLevel = ApiSettings.clampLiveRagAutoScrollSpeedLevel(
                settings.liveRagAutoScrollSpeedLevel
            ),
            experimentalLiveMicProfile = ApiSettings.clampExperimentalLiveMicProfile(
                settings.experimentalLiveMicProfile
            ),
            geminiApiKey = normalizeGeminiKeyInput(settings.geminiApiKey),
            anythingLlmServerUrl = normalizeAnythingLlmServerUrl(settings.anythingLlmServerUrl),
            anythingLlmApiKey = normalizeAnythingLlmApiKey(settings.anythingLlmApiKey),
            anythingLlmWorkspaceSlug = normalizeAnythingLlmWorkspaceSlug(settings.anythingLlmWorkspaceSlug)
        )

        prefs.edit().apply {
            putString(KEY_AI_PROVIDER, normalizedSettings.aiProvider.name)
            putString(KEY_AI_MODEL, normalizedSettings.aiModelId)
            putString(KEY_ANSWER_MODE, normalizedSettings.answerMode.name)
            putString(KEY_NETWORK_PROFILE, normalizedSettings.networkProfile.name)
            putString(KEY_DOCS_PROVIDER, normalizedSettings.docsProvider.name)
            putBoolean(KEY_LIVE_MODE_ENABLED, normalizedSettings.liveModeEnabled)
            putBoolean(KEY_LIVE_RAG_ENABLED, normalizedSettings.liveRagEnabled)
            putBoolean(KEY_LIVE_ANSWER_AUDIO_ENABLED, normalizedSettings.liveAnswerAudioEnabled)
            putBoolean(KEY_LIVE_MINIMAL_UI_ENABLED, normalizedSettings.liveMinimalUiEnabled)
            putBoolean(KEY_LIVE_BARGE_IN_ENABLED, normalizedSettings.liveBargeInEnabled)
            putBoolean(KEY_LIVE_LONG_SESSION_ENABLED, normalizedSettings.liveLongSessionEnabled)
            putBoolean(KEY_LIVE_GOOGLE_SEARCH_ENABLED, normalizedSettings.liveGoogleSearchEnabled)
            putString(KEY_LIVE_VOICE_NAME, normalizedSettings.liveVoiceName)
            putString(KEY_LIVE_THINKING_LEVEL, normalizedSettings.liveThinkingLevel.name)
            putBoolean(KEY_LIVE_THOUGHT_SUMMARIES_ENABLED, normalizedSettings.liveThoughtSummariesEnabled)
            putString(KEY_LIVE_RAG_DISPLAY_MODE, normalizedSettings.liveRagDisplayMode.name)
            putString(KEY_LIVE_RAG_SPLIT_SCROLL_MODE, normalizedSettings.liveRagSplitScrollMode.name)
            putInt(KEY_LIVE_RAG_AUTO_SCROLL_SPEED_LEVEL, normalizedSettings.liveRagAutoScrollSpeedLevel)
            putBoolean(
                KEY_EXPERIMENTAL_LIVE_MIC_TUNING_ENABLED,
                normalizedSettings.experimentalLiveMicTuningEnabled
            )
            putInt(
                KEY_EXPERIMENTAL_LIVE_MIC_PROFILE,
                normalizedSettings.experimentalLiveMicProfile
            )
            putString(KEY_LIVE_INPUT_SOURCE, normalizedSettings.liveInputSource.name)
            putString(KEY_LIVE_OUTPUT_TARGET, normalizedSettings.liveOutputTarget.name)
            putString(KEY_LIVE_CAMERA_MODE, normalizedSettings.liveCameraMode.name)
            putInt(KEY_LIVE_CAMERA_INTERVAL_SEC, normalizedSettings.liveCameraIntervalSec)
            putString(KEY_GEMINI_API_KEY, normalizedSettings.geminiApiKey)
            putString(KEY_OPENAI_API_KEY, normalizedSettings.openaiApiKey)
            putString(KEY_ANTHROPIC_API_KEY, normalizedSettings.anthropicApiKey)
            putString(KEY_DEEPSEEK_API_KEY, normalizedSettings.deepseekApiKey)
            putString(KEY_GROQ_API_KEY, normalizedSettings.groqApiKey)
            putString(KEY_XAI_API_KEY, normalizedSettings.xaiApiKey)
            putString(KEY_ALIBABA_API_KEY, normalizedSettings.alibabaApiKey)
            putString(KEY_ZHIPU_API_KEY, normalizedSettings.zhipuApiKey)
            putString(KEY_BAIDU_API_KEY, normalizedSettings.baiduApiKey)
            putString(KEY_BAIDU_SECRET_KEY, normalizedSettings.baiduSecretKey)
            putString(KEY_PERPLEXITY_API_KEY, normalizedSettings.perplexityApiKey)
            putString(KEY_MOONSHOT_API_KEY, normalizedSettings.moonshotApiKey)
            putString(KEY_CUSTOM_API_KEY, normalizedSettings.customApiKey)
            putString(KEY_CUSTOM_BASE_URL, normalizedSettings.customBaseUrl)
            putString(KEY_CUSTOM_MODEL_NAME, normalizedSettings.customModelName)
            putString(KEY_ANYTHING_LLM_SERVER_URL, normalizedSettings.anythingLlmServerUrl)
            putString(KEY_ANYTHING_LLM_API_KEY, normalizedSettings.anythingLlmApiKey)
            putString(KEY_ANYTHING_LLM_WORKSPACE_SLUG, normalizedSettings.anythingLlmWorkspaceSlug)
            putBoolean(KEY_ANYTHING_LLM_RUNTIME_ENABLED, normalizedSettings.anythingLlmRuntimeEnabled)
            putString(KEY_ANYTHING_LLM_QUERY_MODE, normalizedSettings.anythingLlmQueryMode.name)
            putString(KEY_ANYTHING_LLM_LAST_HEALTH_STATUS, normalizedSettings.anythingLlmLastHealthStatus.name)
            putString(KEY_ANYTHING_LLM_LAST_HEALTH_MESSAGE, normalizedSettings.anythingLlmLastHealthMessage)
            putInt(KEY_ANYTHING_LLM_RECENT_FAILURE_COUNT, normalizedSettings.anythingLlmRecentFailureCount)
            putString(KEY_STT_PROVIDER, normalizedSettings.sttProvider.name)
            putString(KEY_SPEECH_LANGUAGE, normalizedSettings.speechLanguage)
            putString(KEY_RESPONSE_LANGUAGE, normalizedSettings.responseLanguage)
            putString(KEY_SYSTEM_PROMPT, normalizedSettings.systemPrompt)
            putBoolean(KEY_AUTO_READ_RESPONSES_ALOUD, normalizedSettings.autoReadResponsesAloud)
            putString(KEY_TTS_PROVIDER, normalizedSettings.ttsProvider.name)
            putString(KEY_TTS_VOICE_OVERRIDE, normalizedSettings.ttsVoiceOverride)
            putFloat(KEY_TTS_SPEECH_RATE, normalizedSettings.ttsSpeechRate)
            putFloat(KEY_TTS_PITCH, normalizedSettings.ttsPitch)
            putFloat(KEY_SYSTEM_TTS_SPEECH_RATE, normalizedSettings.systemTtsSpeechRate)
            putFloat(KEY_SYSTEM_TTS_PITCH, normalizedSettings.systemTtsPitch)
            putBoolean(KEY_AUTO_ANALYZE_RECORDINGS, normalizedSettings.autoAnalyzeRecordings)
            putBoolean(KEY_PUSH_CHAT_TO_GLASSES, normalizedSettings.pushChatToGlasses)
            putBoolean(KEY_PUSH_RECORDING_TO_GLASSES, normalizedSettings.pushRecordingToGlasses)
            putBoolean(KEY_ALWAYS_START_NEW_AI_SESSION, normalizedSettings.alwaysStartNewAiSession)
            putBoolean(KEY_GLASSES_SLEEP_MODE_ENABLED, normalizedSettings.glassesSleepModeEnabled)
            putInt(KEY_RESPONSE_FONT_SCALE_PERCENT, normalizedSettings.responseFontScalePercent)
            putFloat(KEY_TEMPERATURE, normalizedSettings.temperature)
            putInt(KEY_MAX_TOKENS, normalizedSettings.maxTokens)
            putFloat(KEY_TOP_P, normalizedSettings.topP)
            putFloat(KEY_FREQUENCY_PENALTY, normalizedSettings.frequencyPenalty)
            putFloat(KEY_PRESENCE_PENALTY, normalizedSettings.presencePenalty)
            apply()
        }
        _settingsFlow.value = normalizedSettings
    }
    
    /**
     * Update single setting
     */
    fun updateAiProvider(provider: AiProvider) {
        val current = getSettings()
        // When switching provider, auto-select the first model of that provider
        val defaultModel = AvailableModels.getModelsForProvider(provider).firstOrNull()?.id 
            ?: current.aiModelId
        saveSettings(current.copy(aiProvider = provider, aiModelId = defaultModel))
    }
    
    fun updateAiModel(modelId: String) {
        saveSettings(getSettings().copy(aiModelId = modelId))
    }

    fun updateAnswerMode(answerMode: AnswerMode) {
        saveSettings(getSettings().copy(answerMode = answerMode))
    }

    fun updateNetworkProfile(networkProfile: NetworkProfile) {
        saveSettings(getSettings().copy(networkProfile = networkProfile))
    }

    fun updateAnythingLlmServerUrl(serverUrl: String) {
        saveSettings(getSettings().copy(anythingLlmServerUrl = serverUrl))
    }

    fun updateAnythingLlmApiKey(apiKey: String) {
        saveSettings(getSettings().copy(anythingLlmApiKey = apiKey))
    }

    fun updateAnythingLlmWorkspaceSlug(workspaceSlug: String) {
        saveSettings(getSettings().copy(anythingLlmWorkspaceSlug = workspaceSlug))
    }

    fun updateAnythingLlmRuntimeEnabled(enabled: Boolean) {
        saveSettings(getSettings().copy(anythingLlmRuntimeEnabled = enabled))
    }
    
    fun updateGeminiApiKey(apiKey: String) {
        saveSettings(getSettings().copy(geminiApiKey = apiKey))
    }
    
    fun updateOpenaiApiKey(apiKey: String) {
        saveSettings(getSettings().copy(openaiApiKey = apiKey))
    }
    
    fun updateAnthropicApiKey(apiKey: String) {
        saveSettings(getSettings().copy(anthropicApiKey = apiKey))
    }
    
    fun updateDeepseekApiKey(apiKey: String) {
        saveSettings(getSettings().copy(deepseekApiKey = apiKey))
    }
    
    fun updateGroqApiKey(apiKey: String) {
        saveSettings(getSettings().copy(groqApiKey = apiKey))
    }
    
    fun updateXaiApiKey(apiKey: String) {
        saveSettings(getSettings().copy(xaiApiKey = apiKey))
    }
    
    fun updateAlibabaApiKey(apiKey: String) {
        saveSettings(getSettings().copy(alibabaApiKey = apiKey))
    }
    
    fun updateZhipuApiKey(apiKey: String) {
        saveSettings(getSettings().copy(zhipuApiKey = apiKey))
    }
    
    fun updateBaiduApiKey(apiKey: String) {
        saveSettings(getSettings().copy(baiduApiKey = apiKey))
    }
    
    fun updateBaiduSecretKey(secretKey: String) {
        saveSettings(getSettings().copy(baiduSecretKey = secretKey))
    }
    
    fun updatePerplexityApiKey(apiKey: String) {
        saveSettings(getSettings().copy(perplexityApiKey = apiKey))
    }
    
    fun updateMoonshotApiKey(apiKey: String) {
        saveSettings(getSettings().copy(moonshotApiKey = apiKey))
    }
    
    fun updateCustomApiKey(apiKey: String) {
        saveSettings(getSettings().copy(customApiKey = apiKey))
    }
    
    fun updateCustomBaseUrl(baseUrl: String) {
        saveSettings(getSettings().copy(customBaseUrl = baseUrl))
    }
    
    fun updateCustomModelName(modelName: String) {
        saveSettings(getSettings().copy(customModelName = modelName))
    }
    
    fun updateSttProvider(provider: SttProvider) {
        saveSettings(getSettings().copy(sttProvider = provider))
    }
    
    fun updateSystemPrompt(prompt: String) {
        saveSettings(getSettings().copy(systemPrompt = prompt))
    }

    fun updateAlwaysStartNewAiSession(enabled: Boolean) {
        saveSettings(getSettings().copy(alwaysStartNewAiSession = enabled))
    }

    fun updateAutoReadResponsesAloud(enabled: Boolean) {
        saveSettings(getSettings().copy(autoReadResponsesAloud = enabled))
    }
    
    /**
     * Get the default system prompt in the current locale
     */
    fun getDefaultSystemPrompt(): String {
        return context.getString(R.string.default_system_prompt)
    }
    
    /**
     * Get the default system prompt for a specific language
     */
    fun getDefaultSystemPromptForLanguage(language: AppLanguage): String {
        val locale = LanguageManager.getLocale(language)
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        val localizedContext = context.createConfigurationContext(configuration)
        return localizedContext.getString(R.string.default_system_prompt)
    }
    
    /**
     * Check if the current system prompt is a default prompt (any language)
     */
    fun isUsingDefaultSystemPrompt(): Boolean {
        val currentPrompt = getSettings().systemPrompt
        if (currentPrompt.isEmpty()) return true
        
        // Check against all language defaults
        return AppLanguage.entries.any { lang ->
            try {
                getDefaultSystemPromptForLanguage(lang) == currentPrompt
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Reset system prompt to default (localized)
     */
    fun resetSystemPromptToDefault() {
        updateSystemPrompt(getDefaultSystemPrompt())
    }
}
