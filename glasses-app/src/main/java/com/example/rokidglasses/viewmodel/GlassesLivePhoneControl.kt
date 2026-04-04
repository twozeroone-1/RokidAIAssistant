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

fun resolveResponseHint(
    state: GlassesUiState,
    isChatMode: Boolean,
    isPaginated: Boolean,
    isLastPage: Boolean,
    swipeForMoreHint: String,
    swipePagesHint: String,
    tapContinueHint: String,
    photoSinglePageHint: String,
    liveActiveHint: String,
    liveResumeHint: String,
): String {
    if (isChatMode) {
        if (isPaginated && !isLastPage) {
            return swipeForMoreHint
        }
        if (state.canToggleLiveSession()) {
            return if (state.isLiveModeActive) {
                liveActiveHint
            } else {
                liveResumeHint
            }
        }
        return tapContinueHint
    }

    return if (isPaginated) {
        swipePagesHint
    } else {
        photoSinglePageHint
    }
}
