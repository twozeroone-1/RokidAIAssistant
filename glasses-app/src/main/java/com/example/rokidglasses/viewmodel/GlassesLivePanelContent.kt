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

fun resolveLiveRagAutoScrollDurationMillis(
    maxScrollPx: Int,
    speedLevel: Int,
): Int? {
    if (maxScrollPx <= 0) {
        return null
    }

    val pixelsPerSecond = when (speedLevel.coerceIn(0, 4)) {
        0 -> 6
        1 -> 9
        2 -> 12
        3 -> 18
        else -> 24
    }

    return ((maxScrollPx * 1000.0) / pixelsPerSecond).toInt()
        .coerceIn(4500, 60000)
}

fun resolveLiveRagManualScrollTarget(
    currentScrollPx: Int,
    maxScrollPx: Int,
    viewportHeightPx: Int,
    command: LiveRagManualScrollCommand,
): Int? {
    if (maxScrollPx <= 0 || viewportHeightPx <= 0) {
        return null
    }

    val pageStep = viewportHeightPx.coerceAtLeast(1)
    val delta = when (command) {
        LiveRagManualScrollCommand.UP -> -pageStep
        LiveRagManualScrollCommand.DOWN -> pageStep
    }
    val target = (currentScrollPx + delta).coerceIn(0, maxScrollPx)
    return target.takeIf { it != currentScrollPx }
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
