package com.example.rokidphone.service.ai

import com.google.common.truth.Truth.assertThat
import com.example.rokidphone.data.GeminiLiveVoice
import com.example.rokidphone.data.LiveMediaResolution
import com.example.rokidphone.data.LiveThinkingLevel
import okio.ByteString.Companion.encodeUtf8
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeminiLiveServiceTest {

    @Test
    fun `setup message puts audio transcription configs at setup root`() {
        val service = GeminiLiveService(apiKey = "test-key")

        val setupMessage = service.buildSetupMessage(tools = null)
        val setup = setupMessage.getJSONObject("setup")
        val realtimeInputConfig = setup.getJSONObject("realtimeInputConfig")

        assertThat(setup.has("inputAudioTranscription")).isTrue()
        assertThat(setup.has("outputAudioTranscription")).isTrue()
        assertThat(realtimeInputConfig.has("inputAudioTranscription")).isFalse()
        assertThat(realtimeInputConfig.has("outputAudioTranscription")).isFalse()
    }

    @Test
    fun `setup message uses websocket camel case schema`() {
        val service = GeminiLiveService(apiKey = "test-key", systemPrompt = "be concise")

        val setup = service.buildSetupMessage(tools = null).getJSONObject("setup")
        val generationConfig = setup.getJSONObject("generationConfig")
        val speechConfig = generationConfig.getJSONObject("speechConfig")
        val realtimeInputConfig = setup.getJSONObject("realtimeInputConfig")
        val aad = realtimeInputConfig.getJSONObject("automaticActivityDetection")

        assertThat(setup.has("generationConfig")).isTrue()
        assertThat(setup.has("systemInstruction")).isTrue()
        assertThat(setup.has("realtimeInputConfig")).isTrue()
        assertThat(generationConfig.has("responseModalities")).isTrue()
        assertThat(speechConfig.has("voiceConfig")).isTrue()
        assertThat(aad.has("startOfSpeechSensitivity")).isTrue()
        assertThat(aad.has("endOfSpeechSensitivity")).isTrue()
        assertThat(aad.has("prefixPaddingMs")).isTrue()
        assertThat(aad.has("silenceDurationMs")).isTrue()
        assertThat(realtimeInputConfig.has("activityHandling")).isTrue()

        assertThat(setup.has("generation_config")).isFalse()
        assertThat(setup.has("system_instruction")).isFalse()
        assertThat(setup.has("realtime_input_config")).isFalse()
    }

    @Test
    fun `setup message includes configured live voice name`() {
        val service = GeminiLiveService(
            apiKey = "test-key",
            liveVoiceName = GeminiLiveVoice.KORE.voiceName,
        )

        val voiceName = service
            .buildSetupMessage()
            .getJSONObject("setup")
            .getJSONObject("generationConfig")
            .getJSONObject("speechConfig")
            .getJSONObject("voiceConfig")
            .getJSONObject("prebuiltVoiceConfig")
            .getString("voiceName")

        assertThat(voiceName).isEqualTo(GeminiLiveVoice.KORE.voiceName)
    }

    @Test
    fun `setup message uses no interruption when barge in is disabled`() {
        val service = GeminiLiveService(apiKey = "test-key")
        service.updateVadSettings(
            activityHandling = GeminiLiveService.ActivityHandling.NO_INTERRUPTION
        )

        val activityHandling = service
            .buildSetupMessage()
            .getJSONObject("setup")
            .getJSONObject("realtimeInputConfig")
            .getString("activityHandling")

        assertThat(activityHandling).isEqualTo("NO_INTERRUPTION")
    }

    @Test
    fun `setup message keeps tool declarations under setup root`() {
        val service = GeminiLiveService(apiKey = "test-key")
        val tools = listOf(JSONObject("""{"function_declarations":[{"name":"search_docs"}]}"""))

        val setupMessage = service.buildSetupMessage(tools)
        val setup = setupMessage.getJSONObject("setup")

        assertThat(setup.getJSONArray("tools").length()).isEqualTo(1)
    }

    @Test
    fun `setup message enables session resumption and context compression for long sessions`() {
        val service = GeminiLiveService(
            apiKey = "test-key",
            enableLongSession = true,
            sessionResumptionHandle = "resume-123"
        )

        val setup = service.buildSetupMessage().getJSONObject("setup")
        val sessionResumption = setup.getJSONObject("sessionResumption")
        val contextWindowCompression = setup.getJSONObject("contextWindowCompression")

        assertThat(sessionResumption.getString("handle")).isEqualTo("resume-123")
        assertThat(contextWindowCompression.has("slidingWindow")).isTrue()
    }

    @Test
    fun `setup message enables session resumption even when long sessions are off`() {
        val service = GeminiLiveService(
            apiKey = "test-key",
            enableLongSession = false,
            sessionResumptionHandle = "resume-123"
        )

        val setup = service.buildSetupMessage().getJSONObject("setup")

        assertThat(setup.getJSONObject("sessionResumption").getString("handle"))
            .isEqualTo("resume-123")
        assertThat(setup.has("contextWindowCompression")).isFalse()
    }

    @Test
    fun `setup message omits thinking config by default`() {
        val service = GeminiLiveService(apiKey = "test-key")

        val generationConfig = service
            .buildSetupMessage()
            .getJSONObject("setup")
            .getJSONObject("generationConfig")

        assertThat(generationConfig.has("thinkingConfig")).isFalse()
    }

    @Test
    fun `setup message includes configured thinking level`() {
        val service = GeminiLiveService(
            apiKey = "test-key",
            thinkingLevel = LiveThinkingLevel.MEDIUM,
        )

        val generationConfig = service
            .buildSetupMessage()
            .getJSONObject("setup")
            .getJSONObject("generationConfig")

        assertThat(generationConfig.getJSONObject("thinkingConfig").getString("thinkingLevel"))
            .isEqualTo("medium")
    }

    @Test
    fun `setup message includes configured media resolution`() {
        val service = GeminiLiveService(
            apiKey = "test-key",
            mediaResolution = LiveMediaResolution.HIGH,
        )

        val generationConfig = service
            .buildSetupMessage()
            .getJSONObject("setup")
            .getJSONObject("generationConfig")

        assertThat(generationConfig.getString("mediaResolution"))
            .isEqualTo("MEDIA_RESOLUTION_HIGH")
    }

    @Test
    fun `setup message includes thought summaries when enabled`() {
        val service = GeminiLiveService(
            apiKey = "test-key",
            includeThoughtSummaries = true,
        )

        val generationConfig = service
            .buildSetupMessage()
            .getJSONObject("setup")
            .getJSONObject("generationConfig")

        assertThat(generationConfig.getJSONObject("thinkingConfig").getBoolean("includeThoughts"))
            .isTrue()
    }

    @Test
    fun `server content routes thought parts to dedicated callback`() {
        val service = GeminiLiveService(apiKey = "test-key")
        var thoughtSummary: String? = null
        var assistantText: String? = null

        service.onThoughtSummary = { thoughtSummary = it }
        service.onOutputTranscription = { assistantText = it }

        service.handleServerContentForTest(
            JSONObject(
                """
                {
                  "modelTurn": {
                    "parts": [
                      { "text": "문제를 단계별로 나누겠습니다.", "thought": true },
                      { "text": "먼저 입력 경로를 확인해볼게요." }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertThat(thoughtSummary).isEqualTo("문제를 단계별로 나누겠습니다.")
        assertThat(assistantText).isEqualTo("먼저 입력 경로를 확인해볼게요.")
    }

    @Test
    fun `server message publishes usage metadata while continuing setup handling`() {
        val service = GeminiLiveService(apiKey = "test-key")
        var usageMetadata: LiveUsageMetadata? = null

        service.onUsageMetadata = { usageMetadata = it }

        service.handleServerMessageForTest(
            """
            {
              "usageMetadata": {
                "promptTokenCount": 1200,
                "responseTokenCount": 240,
                "totalTokenCount": 1440
              },
              "setupComplete": {}
            }
            """.trimIndent()
        )

        assertThat(usageMetadata).isNotNull()
        assertThat(usageMetadata?.promptTokenCount).isEqualTo(1200)
        assertThat(usageMetadata?.responseTokenCount).isEqualTo(240)
        assertThat(usageMetadata?.totalTokenCount).isEqualTo(1440)
        assertThat(service.connectionState.value).isEqualTo(GeminiLiveService.ConnectionState.READY)
    }

    @Test
    fun `abnormal close message keeps websocket close reason`() {
        val service = GeminiLiveService(apiKey = "test-key")

        val closeMessage = service.abnormalCloseMessage(
            code = 1007,
            reason = "API key not valid. Please pass a valid API key."
        )

        assertThat(closeMessage).isEqualTo("API key not valid. Please pass a valid API key.")
    }

    @Test
    fun `abnormal closing immediately promotes live connection to error`() {
        val service = GeminiLiveService(apiKey = "test-key")

        val promoted = service.promoteAbnormalCloseToError(
            code = 1007,
            reason = "API key not valid. Please pass a valid API key."
        )

        assertThat(promoted).isTrue()
        assertThat(service.connectionState.value).isEqualTo(GeminiLiveService.ConnectionState.ERROR)
        assertThat(service.errorMessage.value)
            .isEqualTo("API key not valid. Please pass a valid API key.")
    }

    @Test
    fun `normal close does not produce websocket error message`() {
        val service = GeminiLiveService(apiKey = "test-key")

        val closeMessage = service.abnormalCloseMessage(code = 1000, reason = "Client disconnect")

        assertThat(closeMessage).isNull()
    }

    @Test
    fun `normal closing does not promote live connection to error`() {
        val service = GeminiLiveService(apiKey = "test-key")

        val promoted = service.promoteAbnormalCloseToError(code = 1000, reason = "Client disconnect")

        assertThat(promoted).isFalse()
        assertThat(service.connectionState.value)
            .isEqualTo(GeminiLiveService.ConnectionState.DISCONNECTED)
        assertThat(service.errorMessage.value).isNull()
    }

    @Test
    fun `transport failure keeps previously captured websocket close reason`() {
        val service = GeminiLiveService(apiKey = "test-key")
        service.promoteAbnormalCloseToError(
            code = 1007,
            reason = "API key not valid. Please pass a valid API key."
        )

        val resolvedMessage = service.resolveTransportFailureMessage(
            "sent ping but didn't receive pong within 20000ms"
        )

        assertThat(resolvedMessage)
            .isEqualTo("API key not valid. Please pass a valid API key.")
    }

    @Test
    fun `summarize json for log redacts base64 media payloads`() {
        val service = GeminiLiveService(apiKey = "test-key")
        val payload = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", "abcdefghijklmnopqrstuvwxyz")
                    })
                })
            })
        }

        val summary = service.summarizeJsonForLog(payload)

        assertThat(summary).contains("<redacted:26 chars>")
        assertThat(summary).doesNotContain("abcdefghijklmnopqrstuvwxyz")
    }

    @Test
    fun `summarize raw message for log falls back for non json text`() {
        val service = GeminiLiveService(apiKey = "test-key")

        val summary = service.summarizeRawMessageForLog("not-json")

        assertThat(summary).isEqualTo("not-json")
    }

    @Test
    fun `decode binary message returns utf8 json text`() {
        val service = GeminiLiveService(apiKey = "test-key")

        val decoded = service.decodeBinaryMessage("""{"setupComplete":{}}""".encodeUtf8())

        assertThat(decoded).isEqualTo("""{"setupComplete":{}}""")
    }

    @Test
    fun `realtime audio message uses audio field instead of deprecated mediaChunks`() {
        val service = GeminiLiveService(apiKey = "test-key")

        val message = service.buildRealtimeAudioMessage(byteArrayOf(1, 2, 3))
        val realtimeInput = message.getJSONObject("realtimeInput")

        assertThat(realtimeInput.has("audio")).isTrue()
        assertThat(realtimeInput.has("mediaChunks")).isFalse()
    }

    @Test
    fun `realtime video message uses video field instead of deprecated mediaChunks`() {
        val service = GeminiLiveService(apiKey = "test-key")

        val message = service.buildRealtimeVideoMessage(byteArrayOf(1, 2, 3))
        val realtimeInput = message.getJSONObject("realtimeInput")

        assertThat(realtimeInput.has("video")).isTrue()
        assertThat(realtimeInput.has("mediaChunks")).isFalse()
    }

    @Test
    fun `end of turn message uses audioStreamEnd for realtime audio`() {
        val service = GeminiLiveService(apiKey = "test-key")

        val message = service.buildAudioStreamEndMessage()
        val realtimeInput = message.getJSONObject("realtimeInput")

        assertThat(realtimeInput.optBoolean("audioStreamEnd", false)).isTrue()
        assertThat(message.has("clientContent")).isFalse()
    }
}
