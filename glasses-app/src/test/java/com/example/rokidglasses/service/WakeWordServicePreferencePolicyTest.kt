package com.example.rokidglasses.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WakeWordServicePreferencePolicyTest {

    @Test
    fun `enabled preference starts service when not already running`() {
        assertThat(
            WakeWordServicePreferencePolicy.resolveAction(
                systemWakeWordEnabled = true,
                serviceRunning = false,
            )
        ).isEqualTo(WakeWordServiceAction.START)
    }

    @Test
    fun `disabled preference stops service when already running`() {
        assertThat(
            WakeWordServicePreferencePolicy.resolveAction(
                systemWakeWordEnabled = false,
                serviceRunning = true,
            )
        ).isEqualTo(WakeWordServiceAction.STOP)
    }

    @Test
    fun `matching preference and runtime state performs no action`() {
        assertThat(
            WakeWordServicePreferencePolicy.resolveAction(
                systemWakeWordEnabled = true,
                serviceRunning = true,
            )
        ).isEqualTo(WakeWordServiceAction.NONE)

        assertThat(
            WakeWordServicePreferencePolicy.resolveAction(
                systemWakeWordEnabled = false,
                serviceRunning = false,
            )
        ).isEqualTo(WakeWordServiceAction.NONE)
    }
}
