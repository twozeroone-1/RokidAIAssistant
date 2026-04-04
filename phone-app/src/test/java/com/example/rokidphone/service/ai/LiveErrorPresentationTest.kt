package com.example.rokidphone.service.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveErrorPresentationTest {

    @Test
    fun `invalid api key error maps to settings guidance`() {
        val presentation = resolveLiveErrorPresentation("API key not valid. Please pass a valid API key.")

        assertThat(presentation.userMessage)
            .isEqualTo("Gemini Live API 키가 유효하지 않습니다. 설정에서 Gemini API 키를 다시 입력하세요.")
        assertThat(presentation.shouldNotifyApiKeyMissing).isTrue()
    }

    @Test
    fun `blank live error falls back to generic message`() {
        val presentation = resolveLiveErrorPresentation(null)

        assertThat(presentation.userMessage).isEqualTo("Live session error")
        assertThat(presentation.shouldNotifyApiKeyMissing).isFalse()
    }

    @Test
    fun `quota error maps to quota guidance`() {
        val presentation = resolveLiveErrorPresentation("You exceeded your current quota.")

        assertThat(presentation.userMessage)
            .isEqualTo("Gemini Live 쿼터를 초과했거나 현재 프로젝트 플랜 제한에 걸렸습니다. 잠시 후 다시 시도하거나 Google Search 설정을 확인하세요.")
        assertThat(presentation.shouldNotifyApiKeyMissing).isFalse()
    }
}
