package com.example.rokidphone.service.ai

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiveUsageMetadataTest {

    @Test
    fun `from server message parses top level usage metadata`() {
        val metadata = LiveUsageMetadata.fromServerMessage(
            JSONObject(
                """
                {
                  "usageMetadata": {
                    "promptTokenCount": 1200,
                    "responseTokenCount": 240,
                    "thoughtsTokenCount": 32,
                    "totalTokenCount": 1472,
                    "promptTokensDetails": [
                      { "modality": "TEXT", "tokenCount": 200 },
                      { "modality": "AUDIO", "tokenCount": 1000 }
                    ],
                    "responseTokensDetails": [
                      { "modality": "AUDIO", "tokenCount": 240 }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertThat(metadata).isNotNull()
        assertThat(metadata?.promptTokenCount).isEqualTo(1200)
        assertThat(metadata?.responseTokenCount).isEqualTo(240)
        assertThat(metadata?.thoughtsTokenCount).isEqualTo(32)
        assertThat(metadata?.totalTokenCount).isEqualTo(1472)
        assertThat(metadata?.promptTokensDetails)
            .containsExactly(
                LiveModalityTokenCount("TEXT", 200),
                LiveModalityTokenCount("AUDIO", 1000)
            )
            .inOrder()
        assertThat(metadata?.responseTokensDetails)
            .containsExactly(LiveModalityTokenCount("AUDIO", 240))
    }

    @Test
    fun `status summary includes thoughts when present`() {
        val metadata = LiveUsageMetadata(
            promptTokenCount = 1200,
            cachedContentTokenCount = null,
            responseTokenCount = 240,
            toolUsePromptTokenCount = null,
            thoughtsTokenCount = 32,
            totalTokenCount = 1472,
        )

        assertThat(metadata.toStatusSummary())
            .isEqualTo("입력 1,200 · 출력 240 · 총 1,472 · 생각 32")
    }

    @Test
    fun `log summary includes all available usage fields`() {
        val metadata = LiveUsageMetadata(
            promptTokenCount = 1200,
            cachedContentTokenCount = 400,
            responseTokenCount = 240,
            toolUsePromptTokenCount = 48,
            thoughtsTokenCount = 32,
            totalTokenCount = 1472,
        )

        assertThat(metadata.toLogSummary())
            .isEqualTo(
                "prompt=1200, cached=400, response=240, toolPrompt=48, thoughts=32, total=1472"
            )
    }
}
