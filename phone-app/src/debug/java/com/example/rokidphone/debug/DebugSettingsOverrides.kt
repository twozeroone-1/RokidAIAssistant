package com.example.rokidphone.debug

import com.example.rokidphone.data.ApiSettings
import java.util.Base64

internal object DebugSettingsOverrides {
    fun resolveGeminiApiKeyOverride(
        geminiApiKey: String?,
        geminiApiKeyBase64: String?,
        geminiApiKeyPrimary: String?,
        geminiApiKeySecondary: String?
    ): String? {
        val splitKeys = listOfNotNull(
            geminiApiKeyPrimary?.takeIf { it.isNotBlank() },
            geminiApiKeySecondary?.takeIf { it.isNotBlank() }
        )
        if (splitKeys.isNotEmpty()) {
            return splitKeys.joinToString("\n")
        }
        if (!geminiApiKeyBase64.isNullOrBlank()) {
            return String(Base64.getDecoder().decode(geminiApiKeyBase64))
        }
        return geminiApiKey
    }

    fun apply(
        current: ApiSettings,
        geminiApiKey: String?,
        geminiApiKeyBase64: String?,
        geminiApiKeyPrimary: String?,
        geminiApiKeySecondary: String?,
        liveModeEnabled: Boolean?,
        liveRagEnabled: Boolean?
    ): ApiSettings {
        val geminiApiKeyOverride = resolveGeminiApiKeyOverride(
            geminiApiKey = geminiApiKey,
            geminiApiKeyBase64 = geminiApiKeyBase64,
            geminiApiKeyPrimary = geminiApiKeyPrimary,
            geminiApiKeySecondary = geminiApiKeySecondary
        )

        return current.copy(
            geminiApiKey = geminiApiKeyOverride ?: current.geminiApiKey,
            liveModeEnabled = liveModeEnabled ?: current.liveModeEnabled,
            liveRagEnabled = liveRagEnabled ?: current.liveRagEnabled
        )
    }
}
