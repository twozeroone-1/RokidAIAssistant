package com.example.rokidglasses.viewmodel

import com.example.rokidcommon.protocol.LiveRagDisplayMode
import com.example.rokidcommon.protocol.LiveRagSplitScrollMode
import com.example.rokidcommon.protocol.resolveEffectiveLiveRagDisplayMode

data class GlassesLivePanelContent(
    val showSplitPanels: Boolean,
    val leftText: String,
    val rightText: String,
    val autoScrollRightPanel: Boolean,
    val manualScrollRightPanel: Boolean,
)

typealias GlassesLiveRagDisplayMode = LiveRagDisplayMode
typealias GlassesLiveRagSplitScrollMode = LiveRagSplitScrollMode

enum class LiveRagManualScrollCommand {
    UP,
    DOWN,
}

internal const val LIVE_RAG_MANUAL_SCROLL_STEP_PX = 96

fun resolveLiveRagAutoScrollDurationMillis(
    maxScrollPx: Int,
    speedLevel: Int,
): Int? {
    if (maxScrollPx <= 0) {
        return null
    }

    return when (speedLevel.coerceIn(0, 4)) {
        0 -> (maxScrollPx * 38).coerceIn(8000, 28000)
        1 -> (maxScrollPx * 30).coerceIn(6000, 22000)
        2 -> (maxScrollPx * 22).coerceIn(4000, 16000)
        3 -> (maxScrollPx * 16).coerceIn(3000, 12000)
        else -> (maxScrollPx * 12).coerceIn(2500, 9000)
    }
}

fun resolveLivePanelContent(
    isLiveModeActive: Boolean,
    liveRagEnabled: Boolean,
    ragDisplayMode: GlassesLiveRagDisplayMode,
    splitScrollMode: GlassesLiveRagSplitScrollMode,
    assistantText: String,
    ragText: String,
    ragTextFinalized: Boolean,
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
        autoScrollRightPanel = showSplitPanels &&
            ragText.isNotBlank() &&
            ragTextFinalized &&
            splitScrollMode == GlassesLiveRagSplitScrollMode.AUTO,
        manualScrollRightPanel = showSplitPanels &&
            ragText.isNotBlank() &&
            splitScrollMode == GlassesLiveRagSplitScrollMode.MANUAL,
    )
}
