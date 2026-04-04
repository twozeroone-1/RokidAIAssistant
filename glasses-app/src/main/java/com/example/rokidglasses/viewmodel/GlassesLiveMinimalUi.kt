package com.example.rokidglasses.viewmodel

import com.example.rokidcommon.protocol.LiveRagDisplayMode
import com.example.rokidcommon.protocol.resolveEffectiveLiveRagDisplayMode

fun GlassesUiState.shouldUseLiveMinimalUi(): Boolean {
    return liveMinimalUiEnabled && liveModeEnabled
}

fun GlassesUiState.resolveLiveMinimalDisplayText(): String {
    if (!shouldUseLiveMinimalUi()) {
        return displayText
    }

    val effectiveRagMode = resolveEffectiveLiveRagDisplayMode(
        liveRagEnabled = liveRagEnabled,
        configuredMode = liveRagDisplayMode,
    )

    return when {
        effectiveRagMode == LiveRagDisplayMode.RAG_RESULT_ONLY && liveRagText.isNotBlank() -> {
            liveRagText
        }
        liveAssistantText.isNotBlank() -> {
            liveAssistantText
        }
        aiResponse.isNotBlank() -> {
            aiResponse
        }
        else -> ""
    }
}
