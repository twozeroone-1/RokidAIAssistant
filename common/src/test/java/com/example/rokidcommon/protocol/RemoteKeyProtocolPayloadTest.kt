package com.example.rokidcommon.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RemoteKeyProtocolPayloadTest {

    @Test
    fun `settings sync payload roundtrips`() {
        val payload = RemoteKeyProtocol.encodeSettingsSync(
            recordKeyCode = 131,
            cameraKeyCode = 132,
        )

        assertThat(RemoteKeyProtocol.decodeSettingsSync(payload)).isEqualTo(
            RemoteKeySettingsPayload(
                recordKeyCode = 131,
                cameraKeyCode = 132,
            )
        )
    }

    @Test
    fun `learning request payload roundtrips`() {
        val payload = RemoteKeyProtocol.encodeLearningRequest("RECORD")

        assertThat(RemoteKeyProtocol.decodeLearningRequest(payload)).isEqualTo("RECORD")
    }

    @Test
    fun `learning result payload roundtrips`() {
        val payload = RemoteKeyProtocol.encodeLearningResult(
            target = "CAMERA",
            keyCode = 260,
        )

        assertThat(RemoteKeyProtocol.decodeLearningResult(payload)).isEqualTo(
            RemoteKeyLearningResultPayload(
                target = "CAMERA",
                keyCode = 260,
            )
        )
    }

    @Test
    fun `invalid payloads decode to null`() {
        assertThat(RemoteKeyProtocol.decodeSettingsSync("nope")).isNull()
        assertThat(RemoteKeyProtocol.decodeLearningRequest("")).isNull()
        assertThat(RemoteKeyProtocol.decodeLearningResult("{\"target\":\"RECORD\"}")).isNull()
    }
}
