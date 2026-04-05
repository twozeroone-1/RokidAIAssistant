package com.example.rokidphone.service

import com.example.rokidcommon.protocol.LiveControlInputSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlassesLiveSyncRecoveryPolicyTest {

    @Test
    fun `should monitor only for active glasses live sessions on a connected device`() {
        assertThat(
            GlassesLiveSyncRecoveryPolicy.shouldMonitor(
                sessionActive = true,
                liveModeEnabled = true,
                glassesConnected = true,
                effectiveInputSource = LiveControlInputSource.GLASSES,
            )
        ).isTrue()

        assertThat(
            GlassesLiveSyncRecoveryPolicy.shouldMonitor(
                sessionActive = true,
                liveModeEnabled = true,
                glassesConnected = true,
                effectiveInputSource = LiveControlInputSource.PHONE,
            )
        ).isFalse()

        assertThat(
            GlassesLiveSyncRecoveryPolicy.shouldMonitor(
                sessionActive = false,
                liveModeEnabled = true,
                glassesConnected = true,
                effectiveInputSource = LiveControlInputSource.GLASSES,
            )
        ).isFalse()
    }

    @Test
    fun `should retry when glasses input signal never arrives after timeout`() {
        assertThat(
            GlassesLiveSyncRecoveryPolicy.shouldRetry(
                sessionActive = true,
                liveModeEnabled = true,
                glassesConnected = true,
                effectiveInputSource = LiveControlInputSource.GLASSES,
                scheduledAtMs = 10_000L,
                lastSignalAtMs = null,
                nowMs = 11_600L,
                retryCount = 0,
            )
        ).isTrue()
    }

    @Test
    fun `should not retry before timeout or after a fresh glasses signal`() {
        assertThat(
            GlassesLiveSyncRecoveryPolicy.shouldRetry(
                sessionActive = true,
                liveModeEnabled = true,
                glassesConnected = true,
                effectiveInputSource = LiveControlInputSource.GLASSES,
                scheduledAtMs = 10_000L,
                lastSignalAtMs = null,
                nowMs = 11_000L,
                retryCount = 0,
            )
        ).isFalse()

        assertThat(
            GlassesLiveSyncRecoveryPolicy.shouldRetry(
                sessionActive = true,
                liveModeEnabled = true,
                glassesConnected = true,
                effectiveInputSource = LiveControlInputSource.GLASSES,
                scheduledAtMs = 10_000L,
                lastSignalAtMs = 10_200L,
                nowMs = 11_600L,
                retryCount = 0,
            )
        ).isFalse()
    }

    @Test
    fun `should stop retrying after max attempts`() {
        assertThat(
            GlassesLiveSyncRecoveryPolicy.shouldRetry(
                sessionActive = true,
                liveModeEnabled = true,
                glassesConnected = true,
                effectiveInputSource = LiveControlInputSource.GLASSES,
                scheduledAtMs = 10_000L,
                lastSignalAtMs = null,
                nowMs = 11_600L,
                retryCount = GlassesLiveSyncRecoveryPolicy.MAX_RETRY_COUNT,
            )
        ).isFalse()
    }
}
