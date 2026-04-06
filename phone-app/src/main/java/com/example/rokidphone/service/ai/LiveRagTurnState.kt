package com.example.rokidphone.service.ai

import com.example.rokidcommon.protocol.LiveRagDisplayMode

data class LiveRagSplitPanels(
    val leftText: String,
    val rightText: String,
)

data class LiveRagTurnState(
    val toolInvoked: Boolean = false,
    val ragAnswerText: String? = null,
    val ragFailureText: String? = null,
) {
    fun markToolInvoked(): LiveRagTurnState = copy(toolInvoked = true)

    fun applyToolResult(result: ToolResult): LiveRagTurnState {
        val answer = if (result.success) {
            result.result.optString("answer").trim().takeIf { it.isNotBlank() }
        } else {
            null
        }
        val failure = if (result.success) {
            null
        } else {
            result.errorMessage?.trim().takeIf { !it.isNullOrBlank() }
        }
        return copy(
            toolInvoked = true,
            ragAnswerText = answer,
            ragFailureText = failure,
        )
    }

    fun resolveRagDisplayText(noResultLabel: String): String {
        return when {
            !ragAnswerText.isNullOrBlank() -> ragAnswerText
            !ragFailureText.isNullOrBlank() -> ragFailureText
            else -> noResultLabel
        }
    }

    fun resolveFinalText(
        displayMode: LiveRagDisplayMode,
        assistantText: String,
        noResultLabel: String,
    ): String? {
        return when (displayMode) {
            LiveRagDisplayMode.RAG_RESULT_ONLY -> when {
                !ragAnswerText.isNullOrBlank() -> ragAnswerText
                !ragFailureText.isNullOrBlank() -> ragFailureText
                toolInvoked -> noResultLabel
                assistantText.isNotBlank() -> assistantText
                else -> null
            }
            LiveRagDisplayMode.SPLIT_LIVE_AND_RAG -> assistantText.takeIf { it.isNotBlank() }
        }
    }

    fun resolveSplitPanels(
        assistantText: String,
        searchingLabel: String,
        noResultLabel: String,
        turnComplete: Boolean,
    ): LiveRagSplitPanels {
        val rightText = when {
            !ragAnswerText.isNullOrBlank() -> ragAnswerText
            !ragFailureText.isNullOrBlank() -> ragFailureText
            turnComplete -> noResultLabel
            else -> searchingLabel
        }
        return LiveRagSplitPanels(
            leftText = assistantText,
            rightText = rightText,
        )
    }
}
