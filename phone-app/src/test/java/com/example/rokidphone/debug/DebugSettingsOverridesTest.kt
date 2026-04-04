package com.example.rokidphone.debug

import com.example.rokidphone.data.ApiSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

class DebugSettingsOverridesTest {

    @Test
    fun apply_keeps_existing_values_when_overrides_missing() {
        val current = ApiSettings(
            geminiApiKey = "existing-key",
            liveModeEnabled = false,
            liveRagEnabled = false
        )

        val updated = DebugSettingsOverrides.apply(
            current = current,
            geminiApiKey = null,
            geminiApiKeyBase64 = null,
            geminiApiKeyPrimary = null,
            geminiApiKeySecondary = null,
            liveModeEnabled = null,
            liveRagEnabled = null
        )

        assertThat(updated).isEqualTo(current)
    }

    @Test
    fun apply_overrides_requested_values() {
        val current = ApiSettings(
            geminiApiKey = "old-key",
            liveModeEnabled = false,
            liveRagEnabled = false
        )

        val updated = DebugSettingsOverrides.apply(
            current = current,
            geminiApiKey = "new-key-1\nnew-key-2",
            geminiApiKeyBase64 = null,
            geminiApiKeyPrimary = null,
            geminiApiKeySecondary = null,
            liveModeEnabled = true,
            liveRagEnabled = true
        )

        assertThat(updated.geminiApiKey).isEqualTo("new-key-1\nnew-key-2")
        assertThat(updated.liveModeEnabled).isTrue()
        assertThat(updated.liveRagEnabled).isTrue()
    }

    @Test
    fun resolveGeminiApiKeyOverride_decodes_base64_payload() {
        val encoded = Base64.getEncoder().encodeToString("key-a\nkey-b".toByteArray())

        val decoded = DebugSettingsOverrides.resolveGeminiApiKeyOverride(
            geminiApiKey = null,
            geminiApiKeyBase64 = encoded,
            geminiApiKeyPrimary = null,
            geminiApiKeySecondary = null
        )

        assertThat(decoded).isEqualTo("key-a\nkey-b")
    }

    @Test
    fun resolveGeminiApiKeyOverride_prefers_split_key_extras() {
        val decoded = DebugSettingsOverrides.resolveGeminiApiKeyOverride(
            geminiApiKey = null,
            geminiApiKeyBase64 = null,
            geminiApiKeyPrimary = "key-a",
            geminiApiKeySecondary = "key-b"
        )

        assertThat(decoded).isEqualTo("key-a\nkey-b")
    }
}
