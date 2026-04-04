package com.example.rokidphone.service.ai

import java.util.Locale

data class GeminiLiveCostEstimate(
    val usd: Double,
    val usedFallbackHeuristics: Boolean,
)

object GeminiLiveCostEstimator {
    private const val TOKENS_PER_MILLION = 1_000_000.0

    private const val TEXT_INPUT_RATE = 0.75
    private const val AUDIO_INPUT_RATE = 3.00
    private const val VIDEO_INPUT_RATE = 1.00
    private const val AUDIO_OUTPUT_RATE = 12.00
    private const val TEXT_OUTPUT_RATE = 4.50

    fun estimate(metadata: LiveUsageMetadata): GeminiLiveCostEstimate? {
        var usedFallbackHeuristics = false

        val promptCost = if (metadata.promptTokensDetails.isNotEmpty()) {
            metadata.promptTokensDetails.sumPromptCost()
        } else {
            metadata.promptTokenCount?.let {
                usedFallbackHeuristics = true
                it.toCost(AUDIO_INPUT_RATE)
            } ?: 0.0
        }

        val toolPromptCost = if (metadata.toolUsePromptTokensDetails.isNotEmpty()) {
            metadata.toolUsePromptTokensDetails.sumPromptCost()
        } else {
            metadata.toolUsePromptTokenCount?.let {
                usedFallbackHeuristics = true
                it.toCost(TEXT_INPUT_RATE)
            } ?: 0.0
        }

        val responseCost = if (metadata.responseTokensDetails.isNotEmpty()) {
            metadata.responseTokensDetails.sumResponseCost()
        } else {
            metadata.responseTokenCount?.let {
                usedFallbackHeuristics = true
                it.toCost(AUDIO_OUTPUT_RATE)
            } ?: 0.0
        }

        val total = promptCost + toolPromptCost + responseCost
        return if (total > 0.0) {
            GeminiLiveCostEstimate(
                usd = total,
                usedFallbackHeuristics = usedFallbackHeuristics,
            )
        } else {
            null
        }
    }

    private fun List<LiveModalityTokenCount>.sumPromptCost(): Double {
        return sumOf { detail ->
            when (detail.modality.uppercase(Locale.US)) {
                "TEXT", "DOCUMENT" -> detail.tokenCount.toCost(TEXT_INPUT_RATE)
                "AUDIO" -> detail.tokenCount.toCost(AUDIO_INPUT_RATE)
                "IMAGE", "VIDEO" -> detail.tokenCount.toCost(VIDEO_INPUT_RATE)
                else -> 0.0
            }
        }
    }

    private fun List<LiveModalityTokenCount>.sumResponseCost(): Double {
        return sumOf { detail ->
            when (detail.modality.uppercase(Locale.US)) {
                "TEXT" -> detail.tokenCount.toCost(TEXT_OUTPUT_RATE)
                "AUDIO" -> detail.tokenCount.toCost(AUDIO_OUTPUT_RATE)
                else -> 0.0
            }
        }
    }

    private fun Int.toCost(ratePerMillion: Double): Double {
        return (this * ratePerMillion) / TOKENS_PER_MILLION
    }
}
