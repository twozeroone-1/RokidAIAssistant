package com.example.rokidphone.input

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RemoteKeySupportTest {

    @Test
    fun `formatRemoteKeyCode returns Not set for null`() {
        assertThat(formatRemoteKeyCode(null)).isEqualTo("Not set")
    }

    @Test
    fun `formatRemoteKeyCode renders key code name`() {
        assertThat(formatRemoteKeyCode(KeyEvent.KEYCODE_CAMERA)).isEqualTo("Camera")
    }

    @Test
    fun `isBlockedRemoteKeyCode rejects back key`() {
        assertThat(isBlockedRemoteKeyCode(KeyEvent.KEYCODE_BACK)).isTrue()
    }

    @Test
    fun `isBlockedRemoteKeyCode allows volume down for remote learning`() {
        assertThat(isBlockedRemoteKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN)).isFalse()
    }
}
