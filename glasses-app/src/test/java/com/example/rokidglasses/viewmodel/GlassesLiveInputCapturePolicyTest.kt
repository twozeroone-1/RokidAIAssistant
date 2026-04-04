package com.example.rokidglasses.viewmodel

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
}
