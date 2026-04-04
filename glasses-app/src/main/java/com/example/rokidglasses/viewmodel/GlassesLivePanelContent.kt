package com.example.rokidglasses.viewmodel

import com.example.rokidcommon.protocol.LiveRagDisplayMode
import com.example.rokidcommon.protocol.resolveEffectiveLiveRagDisplayMode

data class GlassesLivePanelContent(
    val showSplitPanels: Boolean,
    val leftText: String,
    val rightText: String,
)

typealias GlassesLiveRagDisplayMode = LiveRagDisplayMode

fun resolveLivePanelContent(
    isLiveModeActive: Boolean,
    liveRagEnabled: Boolean,
    ragDisplayMode: GlassesLiveRagDisplayMode,
    assistantText: String,
    ragText: String,
): GlassesLivePanelContent {
    val effectiveMode = resolveEffectiveLiveRagDisplayMode(
        liveRagEnabled = liveRagEnabled,
        configuredMode = ragDisplayMode,
    )
    val showSplitPanels = isLiveModeActive &&
        effectiveMode == GlassesLiveRagDisplayMode.SPLIT_LIVE_AND_RAG &&
        (assistantText.isNotBlank() || ragText.isNotBlank())

    return GlassesLivePanelContent(
        showSplitPanels = showSplitPanels,
        leftText = assistantText,
        rightText = ragText,
    )
}
