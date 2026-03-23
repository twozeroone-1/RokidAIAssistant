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
    fun `saveSettings persists auto read responses toggle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)

        repository.saveSettings(ApiSettings())
        repository.saveSettings(ApiSettings(autoReadResponsesAloud = false))

        val reloadedRepository = SettingsRepository(context)

        assertThat(reloadedRepository.getSettings().autoReadResponsesAloud).isFalse()
    }

    @Test
    fun `saveSettings persists remote key mapping values`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository(context)

        repository.saveSettings(
            ApiSettings(
                remoteRecordKeyCode = 131,
                remoteCameraKeyCode = 132,
                remoteKeyLearningTarget = RemoteKeyLearningTarget.CAMERA,
                remoteKeyLearningStatusMessage = "Waiting for key on glasses"
            )
        )

        val reloadedRepository = SettingsRepository(context)
        val settings = reloadedRepository.getSettings()

        assertThat(settings.remoteRecordKeyCode).isEqualTo(131)
        assertThat(settings.remoteCameraKeyCode).isEqualTo(132)
        assertThat(settings.remoteKeyLearningTarget).isEqualTo(RemoteKeyLearningTarget.CAMERA)
        assertThat(settings.remoteKeyLearningStatusMessage).isEqualTo("Waiting for key on glasses")
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
