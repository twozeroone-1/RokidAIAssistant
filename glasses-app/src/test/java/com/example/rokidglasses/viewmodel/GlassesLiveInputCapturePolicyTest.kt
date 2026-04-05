package com.example.rokidglasses.viewmodel

import android.media.MediaRecorder
import com.example.rokidcommon.protocol.LiveControlInputSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlassesLiveInputCapturePolicyTest {

    @Test
    fun `active connected live session auto captures glasses input`() {
        val shouldCapture = shouldCaptureLiveInputFromGlasses(
            isBluetoothConnected = true,
            liveModeEnabled = true,
            sessionActive = true,
            inputSource = LiveControlInputSource.GLASSES,
        )

        assertThat(shouldCapture).isTrue()
    }

    @Test
    fun `phone input paused session or disconnected glasses do not auto capture`() {
        val phoneInput = shouldCaptureLiveInputFromGlasses(
            isBluetoothConnected = true,
            liveModeEnabled = true,
            sessionActive = true,
            inputSource = LiveControlInputSource.PHONE,
        )
        val pausedSession = shouldCaptureLiveInputFromGlasses(
            isBluetoothConnected = true,
            liveModeEnabled = true,
            sessionActive = false,
            inputSource = LiveControlInputSource.GLASSES,
        )
        val disconnected = shouldCaptureLiveInputFromGlasses(
            isBluetoothConnected = false,
            liveModeEnabled = true,
            sessionActive = true,
            inputSource = LiveControlInputSource.GLASSES,
        )

        assertThat(phoneInput).isFalse()
        assertThat(pausedSession).isFalse()
        assertThat(disconnected).isFalse()
    }

    @Test
    fun `disabled experiment keeps legacy MIC path`() {
        val config = resolveLiveMicCaptureConfig(
            experimentEnabled = false,
            selectedProfile = 2,
        )

        assertThat(config.sourceCandidates).containsExactly(MediaRecorder.AudioSource.MIC)
        assertThat(config.enableNoiseSuppressor).isFalse()
        assertThat(config.enableAutomaticGainControl).isFalse()
        assertThat(config.enableAcousticEchoCanceler).isFalse()
    }

    @Test
    fun `near field experiment prioritizes communication source with full effects`() {
        val config = resolveLiveMicCaptureConfig(
            experimentEnabled = true,
            selectedProfile = 0,
        )

        assertThat(config.sourceCandidates.first()).isEqualTo(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        assertThat(config.sourceCandidates.last()).isEqualTo(MediaRecorder.AudioSource.MIC)
        assertThat(config.enableNoiseSuppressor).isTrue()
        assertThat(config.enableAutomaticGainControl).isTrue()
        assertThat(config.enableAcousticEchoCanceler).isTrue()
    }

    @Test
    fun `far field experiment prioritizes recognition source with limited effects`() {
        val config = resolveLiveMicCaptureConfig(
            experimentEnabled = true,
            selectedProfile = 1,
        )

        assertThat(config.sourceCandidates.first()).isEqualTo(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        assertThat(config.sourceCandidates.last()).isEqualTo(MediaRecorder.AudioSource.MIC)
        assertThat(config.enableNoiseSuppressor).isTrue()
        assertThat(config.enableAutomaticGainControl).isTrue()
        assertThat(config.enableAcousticEchoCanceler).isFalse()
    }

    @Test
    fun `panorama experiment falls back to MIC without extra effects`() {
        val config = resolveLiveMicCaptureConfig(
            experimentEnabled = true,
            selectedProfile = 2,
        )

        assertThat(config.sourceCandidates).containsExactly(MediaRecorder.AudioSource.MIC)
        assertThat(config.enableNoiseSuppressor).isFalse()
        assertThat(config.enableAutomaticGainControl).isFalse()
        assertThat(config.enableAcousticEchoCanceler).isFalse()
    }
}
