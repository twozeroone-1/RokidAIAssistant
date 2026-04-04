package com.example.rokidphone.service.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveUsageCostEstimatorTest {

    @Test
    fun `estimates Gemini live model cost from modality token details`() {
        val metadata = LiveUsageMetadata(
            promptTokenCount = 3000,
            cachedContentTokenCount = null,
            responseTokenCount = 3500,
            toolUsePromptTokenCount = 100,
            thoughtsTokenCount = 64,
            totalTokenCount = 6600,
            promptTokensDetails = listOf(
                LiveModalityTokenCount("TEXT", 1000),
                LiveModalityTokenCount("AUDIO", 2000),
            ),
            cacheTokensDetails = emptyList(),
            responseTokensDetails = listOf(
                LiveModalityTokenCount("AUDIO", 3000),
                LiveModalityTokenCount("TEXT", 500),
            ),
            toolUsePromptTokensDetails = listOf(
                LiveModalityTokenCount("TEXT", 100),
            ),
        )

        val estimate = GeminiLiveCostEstimator.estimate(metadata)

        assertThat(estimate).isNotNull()
        assertThat(estimate?.usd).isWithin(0.0000001).of(0.045075)
        assertThat(estimate?.usedFallbackHeuristics).isFalse()
    }

    @Test
    fun `falls back to audio live heuristics when modality details are missing`() {
        val metadata = LiveUsageMetadata(
            promptTokenCount = 435,
            cachedContentTokenCount = null,
            responseTokenCount = 12,
            toolUsePromptTokenCount = null,
            thoughtsTokenCount = null,
            totalTokenCount = 447,
        )

        val estimate = GeminiLiveCostEstimator.estimate(metadata)

        assertThat(estimate).isNotNull()
        assertThat(estimate?.usd).isWithin(0.0000001).of(0.001449)
        assertThat(estimate?.usedFallbackHeuristics).isTrue()
    }

    @Test
    fun `status summary appends estimated cost when available`() {
        val metadata = LiveUsageMetadata(
            promptTokenCount = 3000,
            cachedContentTokenCount = null,
            responseTokenCount = 3500,
            toolUsePromptTokenCount = null,
            thoughtsTokenCount = 64,
            totalTokenCount = 6500,
            promptTokensDetails = listOf(LiveModalityTokenCount("AUDIO", 3000)),
            cacheTokensDetails = emptyList(),
            responseTokensDetails = listOf(LiveModalityTokenCount("AUDIO", 3500)),
            toolUsePromptTokensDetails = emptyList(),
        )

        assertThat(
            metadata.toStatusSummary(estimatedModelCostUsd = GeminiLiveCostEstimator.estimate(metadata)?.usd)
        ).isEqualTo("입력 3,000 · 출력 3,500 · 총 6,500 · 생각 64 · 약 $0.0510")
    }

    @Test
    fun `log summary appends estimated model cost when available`() {
        val metadata = LiveUsageMetadata(
            promptTokenCount = 1000,
            cachedContentTokenCount = null,
            responseTokenCount = 2000,
            toolUsePromptTokenCount = null,
            thoughtsTokenCount = null,
            totalTokenCount = 3000,
            promptTokensDetails = listOf(LiveModalityTokenCount("TEXT", 1000)),
            cacheTokensDetails = emptyList(),
            responseTokensDetails = listOf(LiveModalityTokenCount("AUDIO", 2000)),
            toolUsePromptTokensDetails = emptyList(),
        )

        assertThat(
            metadata.toLogSummary(estimatedModelCostUsd = GeminiLiveCostEstimator.estimate(metadata)?.usd)
        ).isEqualTo("prompt=1000, response=2000, total=3000, estimatedModelCostUsd=0.024750")
    }
}
