package com.example.rokidcommon.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveTranscriptPayloadTest {

    @Test
    fun `live transcript payload round trips through json string`() {
        val payload = LiveTranscriptPayload(
            role = LiveTranscriptRole.ASSISTANT,
            text = "3시 40분이에요.",
            isFinal = true,
        )

        val restored = LiveTranscriptPayload.fromPayload(payload.toPayloadString())

        assertThat(restored).isEqualTo(payload)
    }

    @Test
    fun `live transcript payload parse returns null for plain text payload`() {
        val restored = LiveTranscriptPayload.fromPayload("plain text")

        assertThat(restored).isNull()
    }

    @Test
    fun `live transcript payload supports thinking role`() {
        val payload = LiveTranscriptPayload(
            role = LiveTranscriptRole.THINKING,
            text = "입력 경로와 출력 경로를 따로 보겠습니다.",
            isFinal = false,
        )

        val restored = LiveTranscriptPayload.fromPayload(payload.toPayloadString())

        assertThat(restored).isEqualTo(payload)
    }

    @Test
    fun `live transcript payload supports rag role`() {
        val payload = LiveTranscriptPayload(
            role = LiveTranscriptRole.RAG,
            text = "전원 버튼을 3초간 누르세요.",
            isFinal = true,
        )

        val restored = LiveTranscriptPayload.fromPayload(payload.toPayloadString())

        assertThat(restored).isEqualTo(payload)
    }
}
