package com.example.rokidphone.ui

import com.example.rokidphone.data.ApiSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsPresentationTest {

    @Test
    fun `live mode replaces provider and model rows with lock card`() {
        val presentation = resolveAiServicePresentation(
            settings = ApiSettings().copy(liveModeEnabled = true),
            currentModelLabel = "Gemini 2.5 Flash"
        )

        assertThat(presentation.showProviderRow).isFalse()
        assertThat(presentation.showModelRow).isFalse()
        assertThat(presentation.showLiveLockCard).isTrue()
    }

    @Test
    fun `standard mode keeps provider and model rows visible`() {
        val presentation = resolveAiServicePresentation(
            settings = ApiSettings().copy(liveModeEnabled = false),
            currentModelLabel = "Gemini 2.5 Flash"
        )

        assertThat(presentation.showProviderRow).isTrue()
        assertThat(presentation.showModelRow).isTrue()
        assertThat(presentation.showLiveLockCard).isFalse()
    }
}
