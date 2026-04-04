package com.example.rokidphone.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.rokidcommon.protocol.LiveRagDisplayMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    @Test
    fun `getSettings uses updated default glasses response font scale when unset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteSharedPreferences("rokid_api_settings")

        val repository = SettingsRepository(context)

        assertThat(repository.getSettings().responseFontScalePercent)
            .isEqualTo(ApiSettings.DEFAULT_RESPONSE_FONT_SCALE_PERCENT)
    }

    @Test
    fun `saveSettings persists glasses response font scale percent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)

        repository.saveSettings(ApiSettings(responseFontScalePercent = 125))

        val reloadedRepository = SettingsRepository(context)

        assertThat(reloadedRepository.getSettings().responseFontScalePercent).isEqualTo(125)
    }

    @Test
    fun `saveSettings clamps glasses response font scale percent into supported range`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)

        repository.saveSettings(ApiSettings(responseFontScalePercent = 20))
        assertThat(SettingsRepository(context).getSettings().responseFontScalePercent).isEqualTo(50)

        repository.saveSettings(ApiSettings(responseFontScalePercent = 400))
        assertThat(SettingsRepository(context).getSettings().responseFontScalePercent).isEqualTo(140)
    }

    @Test
    fun `saveSettings snaps glasses response font scale percent to 5 percent steps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)

        repository.saveSettings(ApiSettings(responseFontScalePercent = 87))

        assertThat(SettingsRepository(context).getSettings().responseFontScalePercent).isEqualTo(85)
    }

    @Test
    fun `saveSettings normalizes multiline Gemini key pool`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)

        repository.saveSettings(
            ApiSettings(
                geminiApiKey = " key-a \n\nkey-b\nkey-a\nkey-c "
            )
        )

        val reloadedRepository = SettingsRepository(context)

        assertThat(reloadedRepository.getSettings().geminiApiKey).isEqualTo("key-a\nkey-b\nkey-c")
    }

    @Test
    fun `saveSettings trims AnythingLLM fields`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)

        repository.saveSettings(
            ApiSettings(
                anythingLlmServerUrl = " https://docs.example.com/ \n",
                anythingLlmApiKey = " sk-anything\nllm-key \r\n",
                anythingLlmWorkspaceSlug = " ops-docs \n"
            )
        )

        val reloadedRepository = SettingsRepository(context)

        assertThat(reloadedRepository.getSettings().anythingLlmServerUrl).isEqualTo("https://docs.example.com/")
        assertThat(reloadedRepository.getSettings().anythingLlmApiKey).isEqualTo("sk-anythingllm-key")
        assertThat(reloadedRepository.getSettings().anythingLlmWorkspaceSlug).isEqualTo("ops-docs")
    }

    @Test
    fun `saveSettings persists auto read responses toggle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)

        repository.saveSettings(ApiSettings())
        repository.saveSettings(ApiSettings(autoReadResponsesAloud = false))

        val reloadedRepository = SettingsRepository(context)

        assertThat(reloadedRepository.getSettings().autoReadResponsesAloud).isFalse()
    }

    @Test
    fun `saveSettings persists glasses sleep mode toggle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)

        repository.saveSettings(ApiSettings(glassesSleepModeEnabled = true))

        val reloadedRepository = SettingsRepository(context)

        assertThat(reloadedRepository.getSettings().glassesSleepModeEnabled).isTrue()
    }

    @Test
    fun `saveSettings persists always start new ai session toggle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)

        repository.saveSettings(ApiSettings(alwaysStartNewAiSession = true))

        val reloadedRepository = SettingsRepository(context)

        assertThat(reloadedRepository.getSettings().alwaysStartNewAiSession).isTrue()
    }

    @Test
    fun `saveSettings persists live mode configuration`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)

        repository.saveSettings(
            ApiSettings(
                liveModeEnabled = true,
                liveRagEnabled = true,
                liveBargeInEnabled = false,
                liveLongSessionEnabled = true,
                liveGoogleSearchEnabled = true,
                liveVoiceName = GeminiLiveVoice.KORE.voiceName,
                liveThinkingLevel = LiveThinkingLevel.MEDIUM,
                liveThoughtSummariesEnabled = true,
                liveRagDisplayMode = LiveRagDisplayMode.SPLIT_LIVE_AND_RAG,
                liveInputSource = LiveInputSource.GLASSES,
                liveOutputTarget = LiveOutputTarget.BOTH,
                liveCameraMode = LiveCameraMode.INTERVAL,
                liveCameraIntervalSec = 10
            )
        )

        val reloadedSettings = SettingsRepository(context).getSettings()

        assertThat(reloadedSettings.liveModeEnabled).isTrue()
        assertThat(reloadedSettings.liveRagEnabled).isTrue()
        assertThat(reloadedSettings.liveBargeInEnabled).isFalse()
        assertThat(reloadedSettings.liveLongSessionEnabled).isTrue()
        assertThat(reloadedSettings.liveGoogleSearchEnabled).isTrue()
        assertThat(reloadedSettings.liveVoiceName).isEqualTo(GeminiLiveVoice.KORE.voiceName)
        assertThat(reloadedSettings.liveThinkingLevel).isEqualTo(LiveThinkingLevel.MEDIUM)
        assertThat(reloadedSettings.liveThoughtSummariesEnabled).isTrue()
        assertThat(reloadedSettings.liveRagDisplayMode).isEqualTo(LiveRagDisplayMode.SPLIT_LIVE_AND_RAG)
        assertThat(reloadedSettings.liveInputSource).isEqualTo(LiveInputSource.GLASSES)
        assertThat(reloadedSettings.liveOutputTarget).isEqualTo(LiveOutputTarget.BOTH)
        assertThat(reloadedSettings.liveCameraMode).isEqualTo(LiveCameraMode.INTERVAL)
        assertThat(reloadedSettings.liveCameraIntervalSec).isEqualTo(10)
    }
}
