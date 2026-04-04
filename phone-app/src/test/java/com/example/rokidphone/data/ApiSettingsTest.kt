package com.example.rokidphone.data

import com.example.rokidcommon.protocol.LiveRagDisplayMode
import com.example.rokidcommon.protocol.LiveRagSplitScrollMode
import com.example.rokidphone.service.stt.SttProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ApiSettingsTest {

    @Test
    fun `responseFontScalePercent defaults to 85`() {
        val settings = ApiSettings()

        assertThat(settings.responseFontScalePercent).isEqualTo(85)
    }

    @Test
    fun `alwaysStartNewAiSession defaults to false`() {
        val settings = ApiSettings()

        assertThat(settings.alwaysStartNewAiSession).isFalse()
    }

    @Test
    fun `live mode defaults keep general mode active`() {
        val settings = ApiSettings()

        assertThat(settings.liveModeEnabled).isFalse()
        assertThat(settings.liveRagEnabled).isFalse()
        assertThat(settings.liveAnswerAudioEnabled).isTrue()
        assertThat(settings.liveMinimalUiEnabled).isFalse()
        assertThat(settings.liveBargeInEnabled).isTrue()
        assertThat(settings.liveLongSessionEnabled).isFalse()
        assertThat(settings.liveGoogleSearchEnabled).isFalse()
        assertThat(settings.liveVoiceName).isEqualTo(GeminiLiveVoice.AOEDE.voiceName)
        assertThat(settings.liveThinkingLevel).isEqualTo(LiveThinkingLevel.DEFAULT)
        assertThat(settings.liveThoughtSummariesEnabled).isFalse()
        assertThat(settings.liveRagDisplayMode).isEqualTo(LiveRagDisplayMode.RAG_RESULT_ONLY)
        assertThat(settings.liveInputSource).isEqualTo(LiveInputSource.AUTO)
        assertThat(settings.liveOutputTarget).isEqualTo(LiveOutputTarget.AUTO)
        assertThat(settings.liveCameraMode).isEqualTo(LiveCameraMode.OFF)
        assertThat(settings.liveCameraIntervalSec).isEqualTo(5)
        assertThat(settings.liveRagSplitScrollMode).isEqualTo(LiveRagSplitScrollMode.AUTO)
        assertThat(settings.liveRagAutoScrollSpeedLevel)
            .isEqualTo(ApiSettings.DEFAULT_LIVE_RAG_AUTO_SCROLL_SPEED_LEVEL)
    }

    @Test
    fun `live rag auto scroll speed level clamps into supported range`() {
        assertThat(ApiSettings.clampLiveRagAutoScrollSpeedLevel(-3)).isEqualTo(
            ApiSettings.MIN_LIVE_RAG_AUTO_SCROLL_SPEED_LEVEL
        )
        assertThat(ApiSettings.clampLiveRagAutoScrollSpeedLevel(99)).isEqualTo(
            ApiSettings.MAX_LIVE_RAG_AUTO_SCROLL_SPEED_LEVEL
        )
    }

    @Test
    fun `live voice getter resolves configured voice option`() {
        val settings = ApiSettings(liveVoiceName = GeminiLiveVoice.KORE.voiceName)

        assertThat(settings.getLiveVoice()).isEqualTo(GeminiLiveVoice.KORE)
    }

    @Test
    fun `copy preserves responseFontScalePercent unless overridden`() {
        val original = ApiSettings(responseFontScalePercent = 132)
        val copied = original.copy(aiModelId = "gemini-3.1-flash-lite-preview")

        assertThat(copied.responseFontScalePercent).isEqualTo(132)
    }

    @Test
    fun `getGeminiApiKeys parses multiline input and removes duplicates`() {
        val settings = ApiSettings(
            geminiApiKey = " key-a \n\nkey-b\nkey-a\nkey-c "
        )

        assertThat(settings.getGeminiApiKeys()).containsExactly(
            "key-a", "key-b", "key-c"
        ).inOrder()
    }

    @Test
    fun `getGeminiApiKeys keeps single key input compatible`() {
        val settings = ApiSettings(geminiApiKey = "single-key")

        assertThat(settings.getGeminiApiKeys()).containsExactly("single-key")
    }

    @Test
    fun `toAnythingLlmSettings trims hidden whitespace from docs fields`() {
        val settings = ApiSettings(
            anythingLlmServerUrl = " https://docs.example.com/ \n",
            anythingLlmApiKey = " sk-anything\nllm-key \r\n",
            anythingLlmWorkspaceSlug = " ops-docs \n"
        )

        val docsSettings = settings.toAnythingLlmSettings()

        assertThat(docsSettings.serverUrl).isEqualTo("https://docs.example.com/")
        assertThat(docsSettings.apiKey).isEqualTo("sk-anythingllm-key")
        assertThat(docsSettings.workspaceSlug).isEqualTo("ops-docs")
    }

    @Test
    fun `getCurrentApiKey returns key of selected provider`() {
        // 測試：應回傳目前選擇 provider 的 API key
        val settings = ApiSettings(
            aiProvider = AiProvider.OPENAI,
            openaiApiKey = "openai-key",
            geminiApiKey = "gemini-key"
        )

        assertThat(settings.getCurrentApiKey()).isEqualTo("openai-key")
    }

    @Test
    fun `getCurrentApiKey for GEMINI_LIVE uses geminiApiKey`() {
        // 測試：GEMINI_LIVE 與 GEMINI 共用金鑰
        val settings = ApiSettings(
            aiProvider = AiProvider.GEMINI_LIVE,
            geminiApiKey = "gemini-live-key"
        )

        assertThat(settings.getCurrentApiKey()).isEqualTo("gemini-live-key")
    }

    @Test
    fun `live mode validation requires gemini key regardless of general provider`() {
        val missingKey = ApiSettings(
            liveModeEnabled = true,
            aiProvider = AiProvider.OPENAI,
            openaiApiKey = "openai-key"
        )
        val valid = missingKey.copy(geminiApiKey = "gemini-key")

        assertThat(missingKey.validateForChat())
            .isEqualTo(SettingsValidationResult.MissingApiKey(AiProvider.GEMINI_LIVE))
        assertThat(valid.validateForChat()).isEqualTo(SettingsValidationResult.Valid)
    }

    @Test
    fun `live mode missing keys points to gemini live and ignores docs mode`() {
        val liveMissing = ApiSettings(
            liveModeEnabled = true,
            aiProvider = AiProvider.OPENAI,
            answerMode = AnswerMode.GENERAL_AI,
            openaiApiKey = "openai-key"
        )
        val liveDocs = liveMissing.copy(answerMode = AnswerMode.DOCS)

        assertThat(liveMissing.getMissingApiKeys()).containsExactly(AiProvider.GEMINI_LIVE)
        assertThat(liveDocs.getMissingApiKeys()).containsExactly(AiProvider.GEMINI_LIVE)
    }

    @Test
    fun `live mode exposes gemini live as effective assistant provider`() {
        val settings = ApiSettings(
            liveModeEnabled = true,
            aiProvider = AiProvider.OPENAI,
            openaiApiKey = "openai-key",
            geminiApiKey = "gemini-key"
        )

        assertThat(settings.getEffectiveAssistantProvider()).isEqualTo(AiProvider.GEMINI_LIVE)
    }

    @Test
    fun `live mode resolves effective assistant model to canonical live model`() {
        val settings = ApiSettings(
            liveModeEnabled = true,
            aiModelId = "gemini-2.5-flash-lite"
        )

        assertThat(settings.getEffectiveAssistantModelId()).isEqualTo(GEMINI_LIVE_MODEL_ID)
    }

    @Test
    fun `general mode keeps selected provider and model as effective assistant`() {
        val settings = ApiSettings(
            liveModeEnabled = false,
            aiProvider = AiProvider.OPENAI,
            aiModelId = "gpt-5.1-mini"
        )

        assertThat(settings.getEffectiveAssistantProvider()).isEqualTo(AiProvider.OPENAI)
        assertThat(settings.getEffectiveAssistantModelId()).isEqualTo("gpt-5.1-mini")
    }

    @Test
    fun `custom provider base url and model use custom fields when set`() {
        // 測試：CUSTOM provider 應使用自訂 baseUrl 與 modelName
        val settings = ApiSettings(
            aiProvider = AiProvider.CUSTOM,
            aiModelId = "fallback-model",
            customBaseUrl = "http://127.0.0.1:11434/v1/",
            customModelName = "llama-custom"
        )

        assertThat(settings.getCurrentBaseUrl()).isEqualTo("http://127.0.0.1:11434/v1/")
        assertThat(settings.getCurrentModelId()).isEqualTo("llama-custom")
    }

    @Test
    fun `custom provider falls back to defaults when blank`() {
        // 測試：CUSTOM 欄位為空時應回退預設值
        val settings = ApiSettings(
            aiProvider = AiProvider.CUSTOM,
            aiModelId = "fallback-model",
            customBaseUrl = "",
            customModelName = ""
        )

        assertThat(settings.getCurrentBaseUrl()).isEqualTo(AiProvider.CUSTOM.defaultBaseUrl)
        assertThat(settings.getCurrentModelId()).isEqualTo("fallback-model")
    }

    @Test
    fun `isValid handles Baidu and custom URL validation`() {
        // 測試：Baidu 需 key+secret；Custom 需合法 URL
        val baiduInvalid = ApiSettings(aiProvider = AiProvider.BAIDU, baiduApiKey = "k", baiduSecretKey = "")
        val baiduValid = ApiSettings(aiProvider = AiProvider.BAIDU, baiduApiKey = "k", baiduSecretKey = "s")
        val customInvalid = ApiSettings(aiProvider = AiProvider.CUSTOM, customBaseUrl = "ws://invalid")
        val customValid = ApiSettings(aiProvider = AiProvider.CUSTOM, customBaseUrl = "https://example.com/v1")

        assertThat(baiduInvalid.isValid()).isFalse()
        assertThat(baiduValid.isValid()).isTrue()
        assertThat(customInvalid.isValid()).isFalse()
        assertThat(customValid.isValid()).isTrue()
    }

    @Test
    fun `speech configuration and configured stt providers reflect available keys`() {
        // 測試：語音服務可用性與 provider 清單應與設定一致
        val none = ApiSettings()
        val configured = ApiSettings(geminiApiKey = "g", openaiApiKey = "o")

        assertThat(none.hasSpeechServiceConfigured()).isFalse()
        assertThat(configured.hasSpeechServiceConfigured()).isTrue()
        assertThat(configured.getConfiguredSttProviders())
            .containsExactly(AiProvider.GEMINI, AiProvider.OPENAI)
    }

    @Test
    fun `validateForChat returns expected result types`() {
        // 測試：聊天設定驗證應回傳正確結果型別
        val customInvalid = ApiSettings(aiProvider = AiProvider.CUSTOM, customBaseUrl = "invalid-url")
        val baiduMissing = ApiSettings(aiProvider = AiProvider.BAIDU, baiduApiKey = "", baiduSecretKey = "")
        val validOpenai = ApiSettings(aiProvider = AiProvider.OPENAI, openaiApiKey = "ok")

        assertThat(customInvalid.validateForChat()).isInstanceOf(SettingsValidationResult.InvalidConfiguration::class.java)
        assertThat(baiduMissing.validateForChat()).isInstanceOf(SettingsValidationResult.MissingApiKey::class.java)
        assertThat(validOpenai.validateForChat()).isEqualTo(SettingsValidationResult.Valid)
    }

    @Test
    fun `validateForSpeech returns missing service when no stt capable providers configured`() {
        // 測試：沒有可用 STT provider 時應回傳 MissingSpeechService
        val missing = ApiSettings()
        val valid = ApiSettings(groqApiKey = "groq")

        assertThat(missing.validateForSpeech()).isInstanceOf(SettingsValidationResult.MissingSpeechService::class.java)
        assertThat(valid.validateForSpeech()).isEqualTo(SettingsValidationResult.Valid)
    }

    @Test
    fun `toSttCredentials maps selected provider and credentials`() {
        // 測試：ApiSettings 轉換後應保留 STT provider 與憑證欄位
        val settings = ApiSettings(
            sttProvider = SttProvider.DEEPGRAM,
            deepgramApiKey = "deepgram-key",
            assemblyaiApiKey = "assembly-key",
            azureSpeechKey = "azure-key"
        )

        val credentials = settings.toSttCredentials()

        assertThat(credentials.selectedProvider).isEqualTo(SttProvider.DEEPGRAM.name)
        assertThat(credentials.deepgramApiKey).isEqualTo("deepgram-key")
        assertThat(credentials.assemblyaiApiKey).isEqualTo("assembly-key")
        assertThat(credentials.azureSpeechKey).isEqualTo("azure-key")
    }

    // ==================== TTS settings ====================

    @Test
    fun `default TTS settings use EDGE_TTS with auto-detect`() {
        // 測試：預設 TTS 設定應使用 EDGE_TTS 引擎並自動偵測語音
        val settings = ApiSettings()

        assertThat(settings.autoReadResponsesAloud).isTrue()
        assertThat(settings.ttsProvider).isEqualTo(TtsProvider.EDGE_TTS)
        assertThat(settings.ttsVoiceOverride).isEmpty()
        assertThat(settings.ttsSpeechRate).isEqualTo(1.0f)
        assertThat(settings.ttsPitch).isEqualTo(0.0f)
        assertThat(settings.systemTtsSpeechRate).isEqualTo(1.0f)
        assertThat(settings.systemTtsPitch).isEqualTo(1.0f)
    }

    @Test
    fun `copy with TTS provider preserves other TTS fields`() {
        // 測試：複製時更改 provider 應保留其他 TTS 欄位
        val original = ApiSettings(
            autoReadResponsesAloud = false,
            ttsProvider = TtsProvider.EDGE_TTS,
            ttsVoiceOverride = "ko-KR-SunHiNeural",
            ttsSpeechRate = 1.5f,
            ttsPitch = -0.2f,
            systemTtsSpeechRate = 0.8f,
            systemTtsPitch = 1.2f
        )
        val copied = original.copy(ttsProvider = TtsProvider.SYSTEM_TTS)

        assertThat(copied.ttsProvider).isEqualTo(TtsProvider.SYSTEM_TTS)
        assertThat(copied.autoReadResponsesAloud).isFalse()
        assertThat(copied.ttsVoiceOverride).isEqualTo("ko-KR-SunHiNeural")
        assertThat(copied.ttsSpeechRate).isEqualTo(1.5f)
        assertThat(copied.ttsPitch).isEqualTo(-0.2f)
        assertThat(copied.systemTtsSpeechRate).isEqualTo(0.8f)
        assertThat(copied.systemTtsPitch).isEqualTo(1.2f)
    }

    @Test
    fun `copy preserves alwaysStartNewAiSession unless overridden`() {
        val original = ApiSettings(alwaysStartNewAiSession = true)
        val copied = original.copy(aiModelId = "gemini-3.1-flash-lite-preview")

        assertThat(copied.alwaysStartNewAiSession).isTrue()
    }

    @Test
    fun `TTS settings equality check`() {
        // 測試：相同 TTS 設定的 ApiSettings 應相等
        val a = ApiSettings(
            ttsProvider = TtsProvider.GOOGLE_TRANSLATE_TTS,
            ttsVoiceOverride = "en-US-JennyNeural",
            ttsSpeechRate = 1.2f
        )
        val b = ApiSettings(
            ttsProvider = TtsProvider.GOOGLE_TRANSLATE_TTS,
            ttsVoiceOverride = "en-US-JennyNeural",
            ttsSpeechRate = 1.2f
        )
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `TTS settings inequality when provider differs`() {
        // 測試：不同 TTS provider 的 ApiSettings 應不相等
        val a = ApiSettings(ttsProvider = TtsProvider.EDGE_TTS)
        val b = ApiSettings(ttsProvider = TtsProvider.SYSTEM_TTS)
        assertThat(a).isNotEqualTo(b)
    }
}
