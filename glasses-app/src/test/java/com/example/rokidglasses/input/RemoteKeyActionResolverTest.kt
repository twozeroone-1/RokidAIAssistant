package com.example.rokidglasses.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RemoteKeyActionResolverTest {

    @Test
    fun `mapped record key resolves to toggle recording`() {
        val resolver = RemoteKeyActionResolver(
            settings = RemoteKeySettings(
                recordKeyCode = 131,
                cameraKeyCode = 132
            )
        )

        assertThat(resolver.resolve(131)).isEqualTo(RemoteKeyAction.ToggleRecording)
    }

    @Test
    fun `mapped camera key resolves to capture photo`() {
        val resolver = RemoteKeyActionResolver(
            settings = RemoteKeySettings(
                recordKeyCode = 131,
                cameraKeyCode = 132
            )
        )

        assertThat(resolver.resolve(132)).isEqualTo(RemoteKeyAction.CapturePhoto)
    }

    @Test
    fun `unknown key resolves to none`() {
        val resolver = RemoteKeyActionResolver(
            settings = RemoteKeySettings(
                recordKeyCode = 131,
                cameraKeyCode = 132
            )
        )

        assertThat(resolver.resolve(133)).isEqualTo(RemoteKeyAction.None)
    }
}
