package com.example.rokidphone.service.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveTranscriptAccumulatorTest {

    @Test
    fun `merge appends partial assistant chunks`() {
        val merged = LiveTranscriptAccumulator.merge("3시", "40분이에요.")

        assertThat(merged).isEqualTo("3시 40분이에요.")
    }

    @Test
    fun `merge keeps cumulative transcript when new chunk already contains previous text`() {
        val merged = LiveTranscriptAccumulator.merge("지금은", "지금은 3시 40분이에요.")

        assertThat(merged).isEqualTo("지금은 3시 40분이에요.")
    }

    @Test
    fun `merge avoids duplicating exact repeated chunk`() {
        val merged = LiveTranscriptAccumulator.merge("안녕하세요", "안녕하세요")

        assertThat(merged).isEqualTo("안녕하세요")
    }

    @Test
    fun `merge preserves word boundaries from model chunks`() {
        val chunks = listOf(
            "지금은 2026년",
            " 4월",
            " 4일",
            " 토요일",
            " 오전",
            " 3시",
            "40분이에요.",
            " 더",
            " 필요한",
            " 정보가",
            " 있으신가요?",
        )

        val merged = chunks.fold("") { current, chunk ->
            LiveTranscriptAccumulator.merge(current, chunk)
        }

        assertThat(merged).isEqualTo("지금은 2026년 4월 4일 토요일 오전 3시 40분이에요. 더 필요한 정보가 있으신가요?")
    }

    @Test
    fun `merge keeps spaced korean chunks natural`() {
        val chunks = listOf(
            "그런 말씀을",
            " 해 주시니",
            " 정말",
            " 기뻐요!",
            " 저도",
            " 사용자님과",
            " 대화하는",
            " 것을",
            " 정말",
            " 좋아해요.",
            " 혹시",
            " 다른",
            " 대화하고",
            " 싶은",
            " 주제가",
            " 있으신가요?",
        )

        val merged = chunks.fold("") { current, chunk ->
            LiveTranscriptAccumulator.merge(current, chunk)
        }

        assertThat(merged).isEqualTo("그런 말씀을 해 주시니 정말 기뻐요! 저도 사용자님과 대화하는 것을 정말 좋아해요. 혹시 다른 대화하고 싶은 주제가 있으신가요?")
    }
}
