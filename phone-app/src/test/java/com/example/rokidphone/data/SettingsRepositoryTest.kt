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
}
