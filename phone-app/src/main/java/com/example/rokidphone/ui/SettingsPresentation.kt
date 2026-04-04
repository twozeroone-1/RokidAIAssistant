package com.example.rokidphone.ui

import com.example.rokidphone.data.ApiSettings

internal data class AiServicePresentation(
    val showProviderRow: Boolean,
    val showModelRow: Boolean,
    val showLiveLockCard: Boolean,
)

internal fun resolveAiServicePresentation(
    settings: ApiSettings,
    currentModelLabel: String,
): AiServicePresentation {
    return if (settings.liveModeEnabled) {
        AiServicePresentation(
            showProviderRow = false,
            showModelRow = false,
            showLiveLockCard = true,
        )
    } else {
        AiServicePresentation(
            showProviderRow = true,
            showModelRow = currentModelLabel.isNotBlank(),
            showLiveLockCard = false,
        )
    }
}
