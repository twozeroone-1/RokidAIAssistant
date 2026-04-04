package com.example.rokidglasses.viewmodel

enum class PrimaryActionOutcome {
    TOGGLE_LIVE_SESSION,
    NEXT_PAGE,
    DISMISS_OUTPUT,
    TOGGLE_RECORDING,
}

fun resolvePrimaryAction(state: GlassesUiState): PrimaryActionOutcome {
    return when {
        state.canToggleLiveSession() ->
            PrimaryActionOutcome.TOGGLE_LIVE_SESSION

        state.isPaginated && state.currentPage < state.totalPages - 1 ->
            PrimaryActionOutcome.NEXT_PAGE

        state.hasVisibleOutput ->
            PrimaryActionOutcome.DISMISS_OUTPUT

        else ->
            PrimaryActionOutcome.TOGGLE_RECORDING
    }
}
