package com.example.rokidphone.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.rokidphone.data.SettingsRepository

class DebugSettingsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = SettingsRepository.getInstance(context)
        val rawGeminiApiKey = intent.getStringExtra(EXTRA_GEMINI_API_KEY)
        val rawGeminiApiKeyBase64 = intent.getStringExtra(EXTRA_GEMINI_API_KEY_BASE64)
        val rawGeminiApiKeyPrimary = intent.getStringExtra(EXTRA_GEMINI_API_KEY_PRIMARY)
        val rawGeminiApiKeySecondary = intent.getStringExtra(EXTRA_GEMINI_API_KEY_SECONDARY)
        val resolvedGeminiApiKey = DebugSettingsOverrides.resolveGeminiApiKeyOverride(
            geminiApiKey = rawGeminiApiKey,
            geminiApiKeyBase64 = rawGeminiApiKeyBase64,
            geminiApiKeyPrimary = rawGeminiApiKeyPrimary,
            geminiApiKeySecondary = rawGeminiApiKeySecondary
        )

        Log.d(
            TAG,
            "Received debug settings override: primaryLen=${rawGeminiApiKeyPrimary?.length ?: 0}, " +
                "secondaryLen=${rawGeminiApiKeySecondary?.length ?: 0}, " +
                "base64Provided=${!rawGeminiApiKeyBase64.isNullOrBlank()}, " +
                "resolvedLineCount=${resolvedGeminiApiKey?.lineSequence()?.count { it.isNotBlank() } ?: 0}"
        )

        val updatedSettings = DebugSettingsOverrides.apply(
            current = repository.getSettings(),
            geminiApiKey = resolvedGeminiApiKey,
            geminiApiKeyBase64 = null,
            geminiApiKeyPrimary = null,
            geminiApiKeySecondary = null,
            liveModeEnabled = intent.getOptionalBooleanExtra(EXTRA_LIVE_MODE_ENABLED),
            liveRagEnabled = intent.getOptionalBooleanExtra(EXTRA_LIVE_RAG_ENABLED)
        )

        repository.saveSettings(updatedSettings)

        Log.d(
            TAG,
            "Applied debug settings override: liveMode=${updatedSettings.liveModeEnabled}, " +
                "liveRag=${updatedSettings.liveRagEnabled}, " +
                "geminiKeyCount=${updatedSettings.getGeminiApiKeys().size}"
        )
    }

    private fun Intent.getOptionalBooleanExtra(name: String): Boolean? {
        return if (hasExtra(name)) {
            getBooleanExtra(name, false)
        } else {
            null
        }
    }

    companion object {
        private const val TAG = "DebugSettingsReceiver"

        const val EXTRA_GEMINI_API_KEY = "gemini_api_key"
        const val EXTRA_GEMINI_API_KEY_BASE64 = "gemini_api_key_base64"
        const val EXTRA_GEMINI_API_KEY_PRIMARY = "gemini_api_key_primary"
        const val EXTRA_GEMINI_API_KEY_SECONDARY = "gemini_api_key_secondary"
        const val EXTRA_LIVE_MODE_ENABLED = "live_mode_enabled"
        const val EXTRA_LIVE_RAG_ENABLED = "live_rag_enabled"
    }
}
