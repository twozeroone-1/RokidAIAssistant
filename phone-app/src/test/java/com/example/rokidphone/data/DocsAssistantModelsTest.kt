package com.example.rokidphone.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocsAssistantModelsTest {

    @Test
    fun `toAnythingLlmSettings keeps docs requests on fresh-session policy`() {
        val settings = ApiSettings(
            answerMode = AnswerMode.DOCS,
            anythingLlmServerUrl = "https://docs.local",
            anythingLlmApiKey = "secret",
            anythingLlmWorkspaceSlug = "manuals",
        )

        val result = settings.toAnythingLlmSettings()

        assertThat(result.alwaysNewSession).isTrue()
    }
}
