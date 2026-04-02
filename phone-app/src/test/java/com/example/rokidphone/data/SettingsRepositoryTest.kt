package com.example.rokidphone.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

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
}
