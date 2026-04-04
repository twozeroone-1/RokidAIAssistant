package com.example.rokidphone.service.ai

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class LiveModalityTokenCount(
    val modality: String,
    val tokenCount: Int,
)

data class LiveUsageMetadata(
    val promptTokenCount: Int?,
    val cachedContentTokenCount: Int?,
    val responseTokenCount: Int?,
    val toolUsePromptTokenCount: Int?,
    val thoughtsTokenCount: Int?,
    val totalTokenCount: Int?,
    val promptTokensDetails: List<LiveModalityTokenCount> = emptyList(),
    val cacheTokensDetails: List<LiveModalityTokenCount> = emptyList(),
    val responseTokensDetails: List<LiveModalityTokenCount> = emptyList(),
    val toolUsePromptTokensDetails: List<LiveModalityTokenCount> = emptyList(),
) {
    fun hasAnyValue(): Boolean {
        return listOf(
            promptTokenCount,
            cachedContentTokenCount,
            responseTokenCount,
            toolUsePromptTokenCount,
            thoughtsTokenCount,
            totalTokenCount,
        ).any { it != null } ||
            promptTokensDetails.isNotEmpty() ||
            cacheTokensDetails.isNotEmpty() ||
            responseTokensDetails.isNotEmpty() ||
            toolUsePromptTokensDetails.isNotEmpty()
    }

    fun toStatusSummary(estimatedModelCostUsd: Double? = null): String? {
        if (!hasAnyValue()) {
            return null
        }

        val parts = buildList {
            promptTokenCount?.let { add("입력 ${it.formatCount()}") }
            responseTokenCount?.let { add("출력 ${it.formatCount()}") }
            totalTokenCount?.let { add("총 ${it.formatCount()}") }
            thoughtsTokenCount?.takeIf { it > 0 }?.let { add("생각 ${it.formatCount()}") }
            estimatedModelCostUsd?.let { add("약 ${it.formatUsdShort()}") }
        }

        return parts.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")
    }

    fun toLogSummary(estimatedModelCostUsd: Double? = null): String {
        return buildList {
            promptTokenCount?.let { add("prompt=$it") }
            cachedContentTokenCount?.let { add("cached=$it") }
            responseTokenCount?.let { add("response=$it") }
            toolUsePromptTokenCount?.let { add("toolPrompt=$it") }
            thoughtsTokenCount?.let { add("thoughts=$it") }
            totalTokenCount?.let { add("total=$it") }
            estimatedModelCostUsd?.let { add("estimatedModelCostUsd=${it.formatUsdLog()}") }
        }.joinToString(separator = ", ")
    }

    companion object {
        fun fromServerMessage(message: JSONObject): LiveUsageMetadata? {
            return fromJson(message.optJSONObject("usageMetadata"))
        }

        fun fromJson(json: JSONObject?): LiveUsageMetadata? {
            if (json == null) {
                return null
            }

            return LiveUsageMetadata(
                promptTokenCount = json.optIntOrNull("promptTokenCount"),
                cachedContentTokenCount = json.optIntOrNull("cachedContentTokenCount"),
                responseTokenCount = json.optIntOrNull("responseTokenCount"),
                toolUsePromptTokenCount = json.optIntOrNull("toolUsePromptTokenCount"),
                thoughtsTokenCount = json.optIntOrNull("thoughtsTokenCount"),
                totalTokenCount = json.optIntOrNull("totalTokenCount"),
                promptTokensDetails = json.optJSONArray("promptTokensDetails").toModalityTokenCounts(),
                cacheTokensDetails = json.optJSONArray("cacheTokensDetails").toModalityTokenCounts(),
                responseTokensDetails = json.optJSONArray("responseTokensDetails").toModalityTokenCounts(),
                toolUsePromptTokensDetails = json.optJSONArray("toolUsePromptTokensDetails").toModalityTokenCounts(),
            ).takeIf { it.hasAnyValue() }
        }
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    return if (has(key) && !isNull(key)) getInt(key) else null
}

private fun JSONArray?.toModalityTokenCounts(): List<LiveModalityTokenCount> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val modality = item.optString("modality").trim()
            val tokenCount = item.optIntOrNull("tokenCount") ?: continue
            if (modality.isNotBlank()) {
                add(
                    LiveModalityTokenCount(
                        modality = modality.uppercase(Locale.US),
                        tokenCount = tokenCount,
                    )
                )
            }
        }
    }
}

private fun Int.formatCount(): String = String.format(Locale.US, "%,d", this)

private fun Double.formatUsdShort(): String {
    return if (this < 0.0001) {
        "<$0.0001"
    } else {
        "$" + String.format(Locale.US, "%.4f", this)
    }
}

private fun Double.formatUsdLog(): String = String.format(Locale.US, "%.6f", this)
