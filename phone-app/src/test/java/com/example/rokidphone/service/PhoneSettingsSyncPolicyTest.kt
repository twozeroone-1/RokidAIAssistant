package com.example.rokidphone.service

import com.example.rokidphone.data.ApiSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneSettingsSyncPolicyTest {

    @Test
    fun `requiresServiceRefresh ignores response font scale changes`() {
        val previous = ApiSettings(responseFontScalePercent = 100)
        val current = previous.copy(responseFontScalePercent = 132)

        assertThat(PhoneSettingsSyncPolicy.requiresServiceRefresh(previous, current)).isFalse()
    }

    @Test
    fun `requiresServiceRefresh ignores glasses sleep mode changes`() {
        val previous = ApiSettings(glassesSleepModeEnabled = false)
        val current = previous.copy(glassesSleepModeEnabled = true)

        assertThat(PhoneSettingsSyncPolicy.requiresServiceRefresh(previous, current)).isFalse()
    }

    @Test
    fun `requiresServiceRefresh returns true for ai provider changes`() {
        val previous = ApiSettings()
        val current = previous.copy(aiModelId = "gemini-3.1-flash-lite-preview")

        assertThat(PhoneSettingsSyncPolicy.requiresServiceRefresh(previous, current)).isTrue()
    }

    @Test
    fun `shouldSyncResponseFontScale respects force and duplicate values`() {
        assertThat(PhoneSettingsSyncPolicy.shouldSyncResponseFontScale(lastSynced = null, current = 100, force = false)).isTrue()
        assertThat(PhoneSettingsSyncPolicy.shouldSyncResponseFontScale(lastSynced = 120, current = 120, force = false)).isFalse()
        assertThat(PhoneSettingsSyncPolicy.shouldSyncResponseFontScale(lastSynced = 120, current = 120, force = true)).isTrue()
    }
}
