package com.example.rokidglasses.focus

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FocusRecoveryPolicyTest {

    private val policy = FocusRecoveryPolicy(cooldownMs = 1_000L)

    @Test
    fun `recovery allowed when resumed without focus after cooldown`() {
        val state = FocusRecoveryState(
            isResumed = true,
            hasWindowFocus = false,
            isFinishing = false,
            isChangingConfigurations = false,
        )

        assertThat(
            policy.shouldRecover(
                state = state,
                nowMs = 5_000L,
                lastRecoveryAtMs = 3_000L,
            )
        ).isEqualTo(true)
    }

    @Test
    fun `recovery blocked when app still has focus`() {
        val state = FocusRecoveryState(
            isResumed = true,
            hasWindowFocus = true,
            isFinishing = false,
            isChangingConfigurations = false,
        )

        assertThat(
            policy.shouldRecover(
                state = state,
                nowMs = 5_000L,
                lastRecoveryAtMs = 0L,
            )
        ).isEqualTo(false)
    }

    @Test
    fun `recovery blocked when activity is not resumed`() {
        val state = FocusRecoveryState(
            isResumed = false,
            hasWindowFocus = false,
            isFinishing = false,
            isChangingConfigurations = false,
        )

        assertThat(
            policy.shouldRecover(
                state = state,
                nowMs = 5_000L,
                lastRecoveryAtMs = 0L,
            )
        ).isEqualTo(false)
    }

    @Test
    fun `recovery blocked during cooldown`() {
        val state = FocusRecoveryState(
            isResumed = true,
            hasWindowFocus = false,
            isFinishing = false,
            isChangingConfigurations = false,
        )

        assertThat(
            policy.shouldRecover(
                state = state,
                nowMs = 5_000L,
                lastRecoveryAtMs = 4_500L,
            )
        ).isEqualTo(false)
    }

    @Test
    fun `recovery blocked while finishing or reconfiguring`() {
        val finishingState = FocusRecoveryState(
            isResumed = true,
            hasWindowFocus = false,
            isFinishing = true,
            isChangingConfigurations = false,
        )
        val reconfiguringState = FocusRecoveryState(
            isResumed = true,
            hasWindowFocus = false,
            isFinishing = false,
            isChangingConfigurations = true,
        )

        assertThat(
            policy.shouldRecover(
                state = finishingState,
                nowMs = 5_000L,
                lastRecoveryAtMs = 0L,
            )
        ).isEqualTo(false)
        assertThat(
            policy.shouldRecover(
                state = reconfiguringState,
                nowMs = 5_000L,
                lastRecoveryAtMs = 0L,
            )
        ).isEqualTo(false)
    }
}
