package com.example.rokidphone.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocsAssistantSettingsTest {

    @Test
    fun `api settings expose docs assistant defaults`() {
        val settings = ApiSettings()

        assertThat(settings.answerMode).isEqualTo(AnswerMode.GENERAL_AI)
        assertThat(settings.networkProfile).isEqualTo(NetworkProfile.AUTO)
        assertThat(settings.docsProvider).isEqualTo(DocsProvider.ANYTHING_LLM)
        assertThat(settings.anythingLlmRuntimeEnabled).isTrue()
        assertThat(settings.anythingLlmLastHealthStatus).isEqualTo(DocsHealthStatus.UNKNOWN)
    }

    @Test
    fun `docs validation requires server url api key and workspace slug`() {
        val missing = ApiSettings(answerMode = AnswerMode.DOCS)
        val valid = ApiSettings(
            answerMode = AnswerMode.DOCS,
            anythingLlmServerUrl = "https://docs.example.com",
            anythingLlmApiKey = "secret",
            anythingLlmWorkspaceSlug = "team-docs",
        )

        assertThat(missing.validateForDocs()).isInstanceOf(SettingsValidationResult.InvalidConfiguration::class.java)
        assertThat(valid.validateForDocs()).isEqualTo(SettingsValidationResult.Valid)
    }
}
