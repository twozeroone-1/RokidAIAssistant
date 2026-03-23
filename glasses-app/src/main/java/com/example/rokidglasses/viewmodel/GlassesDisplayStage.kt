package com.example.rokidglasses.viewmodel

enum class GlassesDisplayStage {
    IDLE,
    CAPTURING_INPUT,
    SENDING,
    ANALYZING,
    OUTPUT,
}

data class SleepModeSnapshot(
    val isListening: Boolean = false,
    val isCapturingPhoto: Boolean = false,
    val isSendingInput: Boolean = false,
    val isAwaitingAnalysis: Boolean = false,
    val hasVisibleOutput: Boolean = false,
)

fun deriveDisplayStage(snapshot: SleepModeSnapshot): GlassesDisplayStage {
    return when {
        snapshot.hasVisibleOutput -> GlassesDisplayStage.OUTPUT
        snapshot.isAwaitingAnalysis -> GlassesDisplayStage.ANALYZING
        snapshot.isSendingInput -> GlassesDisplayStage.SENDING
        snapshot.isListening || snapshot.isCapturingPhoto -> GlassesDisplayStage.CAPTURING_INPUT
        else -> GlassesDisplayStage.IDLE
    }
}

fun GlassesUiState.toSleepModeSnapshot(): SleepModeSnapshot {
    return SleepModeSnapshot(
        isListening = isListening,
        isCapturingPhoto = isCapturingPhoto,
        isSendingInput = isSendingInput,
        isAwaitingAnalysis = isAwaitingAnalysis,
        hasVisibleOutput = hasVisibleOutput,
    )
}
