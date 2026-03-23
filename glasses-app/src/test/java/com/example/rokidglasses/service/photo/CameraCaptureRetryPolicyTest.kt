package com.example.rokidglasses.service.photo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CameraCaptureRetryPolicyTest {

    @Test
    fun `camera in use reason is recoverable`() {
        val policy = CameraCaptureRetryPolicy()

        assertThat(policy.isRecoverable(CameraRetryReason.CameraInUse)).isEqualTo(true)
    }

    @Test
    fun `camera error reason is recoverable`() {
        val policy = CameraCaptureRetryPolicy()

        assertThat(policy.isRecoverable(CameraRetryReason.CameraError)).isEqualTo(true)
    }

    @Test
    fun `unknown reason is not recoverable`() {
        val policy = CameraCaptureRetryPolicy()

        assertThat(policy.isRecoverable(CameraRetryReason.Unknown)).isEqualTo(false)
    }

    @Test
    fun `retry delay ramps up and caps`() {
        val policy = CameraCaptureRetryPolicy()

        assertThat(policy.retryDelayMs(attempt = 1)).isEqualTo(750L)
        assertThat(policy.retryDelayMs(attempt = 2)).isEqualTo(1500L)
        assertThat(policy.retryDelayMs(attempt = 3)).isEqualTo(2250L)
        assertThat(policy.retryDelayMs(attempt = 10)).isEqualTo(3000L)
    }
}
