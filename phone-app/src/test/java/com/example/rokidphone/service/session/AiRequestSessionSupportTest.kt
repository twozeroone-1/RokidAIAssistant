package com.example.rokidphone.service.session

import com.example.rokidphone.data.ApiSettings
import com.example.rokidphone.data.AiProvider
import com.example.rokidphone.service.SpeechResult
import com.example.rokidphone.service.ai.AiServiceProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiRequestSessionSupportTest {

    @Test
    fun `decide returns current-session behavior when toggle is off`() {
        val decision = AiRequestSessionSupport.decide(ApiSettings(alwaysStartNewAiSession = false))

        assertThat(decision.createNewConversation).isFalse()
        assertThat(decision.clearProviderHistory).isFalse()
    }

    @Test
    fun `decide returns fresh-session behavior when toggle is on`() {
        val decision = AiRequestSessionSupport.decide(ApiSettings(alwaysStartNewAiSession = true))

        assertThat(decision.createNewConversation).isTrue()
        assertThat(decision.clearProviderHistory).isTrue()
    }

    @Test
    fun `clearHistoryIfNeeded clears each unique service once when toggle is on`() {
        val service = FakeAiServiceProvider()

        AiRequestSessionSupport.clearHistoryIfNeeded(
            settings = ApiSettings(alwaysStartNewAiSession = true),
            services = listOf(service, service, null)
        )

        assertThat(service.clearHistoryCalls).isEqualTo(1)
    }

    @Test
    fun `clearHistoryIfNeeded does nothing when toggle is off`() {
        val service = FakeAiServiceProvider()

        AiRequestSessionSupport.clearHistoryIfNeeded(
            settings = ApiSettings(alwaysStartNewAiSession = false),
            services = listOf(service)
        )

        assertThat(service.clearHistoryCalls).isEqualTo(0)
    }

    private class FakeAiServiceProvider : AiServiceProvider {
        override val provider: AiProvider = AiProvider.GEMINI
        var clearHistoryCalls: Int = 0

        override suspend fun transcribe(pcmAudioData: ByteArray, languageCode: String): SpeechResult {
            return SpeechResult.Success("")
        }

        override suspend fun transcribeAudioFile(
            audioData: ByteArray,
            mimeType: String,
            languageCode: String,
        ): SpeechResult {
            return SpeechResult.Success("")
        }

        override suspend fun chat(userMessage: String): String = ""

        override suspend fun analyzeImage(imageData: ByteArray, prompt: String): String = ""

        override fun clearHistory() {
            clearHistoryCalls += 1
        }
    }
}
