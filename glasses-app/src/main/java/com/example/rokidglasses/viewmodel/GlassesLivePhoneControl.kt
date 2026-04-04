package com.example.rokidglasses.viewmodel

data class GlassesIdlePrompt(
    val displayText: String,
    val hintText: String,
)

fun GlassesUiState.canToggleLiveSession(): Boolean {
    return liveModeEnabled
}

fun resolveIdlePrompt(
    state: GlassesUiState,
    defaultDisplayText: String,
    defaultHintText: String,
    livePhoneActiveHint: String,
    livePhoneResumeHint: String,
): GlassesIdlePrompt {
    if (!state.canToggleLiveSession()) {
        return GlassesIdlePrompt(
            displayText = defaultDisplayText,
            hintText = defaultHintText,
        )
    }

    return GlassesIdlePrompt(
        displayText = "",
        hintText = if (state.isLiveModeActive) {
            livePhoneActiveHint
        } else {
            livePhoneResumeHint
        },
    )
}
