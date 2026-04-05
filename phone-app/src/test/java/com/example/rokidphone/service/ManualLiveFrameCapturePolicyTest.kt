package com.example.rokidphone.service

import com.example.rokidphone.data.ApiSettings
import com.example.rokidphone.data.LiveCameraMode
import com.example.rokidphone.service.ai.LiveSessionState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ManualLiveFrameCapturePolicyTest {

    @Test
    fun `active manual live session uses single frame capture path`() {
        val shouldUseManualLiveFrame = shouldUseManualLiveFrameCapture(
            settings = ApiSettings(
                liveModeEnabled = true,
                liveCameraMode = LiveCameraMode.MANUAL,
            ),
            liveSessionState = LiveSessionState.ACTIVE,
        )

        assertThat(shouldUseManualLiveFrame).isTrue()
    }

    @Test
    fun `inactive or non manual camera modes keep default photo capture path`() {
        val liveOff = shouldUseManualLiveFrameCapture(
            settings = ApiSettings(
                liveModeEnabled = false,
                liveCameraMode = LiveCameraMode.MANUAL,
            ),
            liveSessionState = LiveSessionState.ACTIVE,
        )
        val cameraOff = shouldUseManualLiveFrameCapture(
            settings = ApiSettings(
                liveModeEnabled = true,
                liveCameraMode = LiveCameraMode.OFF,
            ),
            liveSessionState = LiveSessionState.ACTIVE,
        )
        val sessionConnecting = shouldUseManualLiveFrameCapture(
            settings = ApiSettings(
                liveModeEnabled = true,
                liveCameraMode = LiveCameraMode.MANUAL,
            ),
            liveSessionState = LiveSessionState.CONNECTING,
        )

        assertThat(liveOff).isFalse()
        assertThat(cameraOff).isFalse()
        assertThat(sessionConnecting).isFalse()
    }
}
