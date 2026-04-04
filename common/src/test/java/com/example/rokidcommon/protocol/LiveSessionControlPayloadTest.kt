package com.example.rokidcommon.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveSessionControlPayloadTest {

    @Test
    fun `round trips phone live session payload with resumable state`() {
        val payload = LiveSessionControlPayload(
            sessionActive = false,
            liveModeEnabled = true,
            effectiveInputSource = LiveControlInputSource.PHONE,
            cameraMode = "REALTIME",
            cameraIntervalSec = 1,
            liveRagEnabled = true,
            ragDisplayMode = LiveRagDisplayMode.SPLIT_LIVE_AND_RAG,
        )

        val restored = LiveSessionControlPayload.fromPayload(payload.toPayloadString())

        assertThat(restored).isEqualTo(payload)
        assertThat(restored!!.canToggleFromGlasses).isTrue()
    }

    @Test
    fun `live mode remains toggleable from glasses regardless of effective input source`() {
        val payload = LiveSessionControlPayload(
            sessionActive = true,
            liveModeEnabled = true,
            effectiveInputSource = LiveControlInputSource.GLASSES,
            cameraMode = "REALTIME",
            cameraIntervalSec = 1,
            liveRagEnabled = false,
            ragDisplayMode = LiveRagDisplayMode.RAG_RESULT_ONLY,
        )

        val restored = LiveSessionControlPayload.fromPayload(payload.toPayloadString())

        assertThat(restored).isEqualTo(payload)
        assertThat(restored!!.canToggleFromGlasses).isTrue()
    }

    @Test
    fun `older live payload still parses with safe defaults`() {
        val restored = LiveSessionControlPayload.fromPayload(
            """{"cameraMode":"REALTIME","cameraIntervalSec":1,"liveRagEnabled":false,"ragDisplayMode":"RAG_RESULT_ONLY"}"""
        )

        assertThat(restored).isNotNull()
        assertThat(restored!!.sessionActive).isFalse()
        assertThat(restored.liveModeEnabled).isFalse()
        assertThat(restored.effectiveInputSource).isEqualTo(LiveControlInputSource.UNKNOWN)
        assertThat(restored.cameraMode).isEqualTo("REALTIME")
        assertThat(restored.cameraIntervalSec).isEqualTo(1)
        assertThat(restored.canToggleFromGlasses).isFalse()
    }
}
