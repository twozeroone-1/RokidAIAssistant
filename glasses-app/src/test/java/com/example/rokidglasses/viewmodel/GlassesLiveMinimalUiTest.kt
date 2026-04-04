package com.example.rokidglasses.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlassesLiveMinimalUiTest {

    @Test
    fun `live minimal ui is enabled only when live mode and setting are both on`() {
        assertThat(
            GlassesUiState(
                liveModeEnabled = true,
                liveMinimalUiEnabled = true,
            ).shouldUseLiveMinimalUi()
        ).isTrue()

        assertThat(
            GlassesUiState(
                liveModeEnabled = false,
                liveMinimalUiEnabled = true,
            ).shouldUseLiveMinimalUi()
        ).isFalse()

        assertThat(
            GlassesUiState(
                liveModeEnabled = true,
                liveMinimalUiEnabled = false,
            ).shouldUseLiveMinimalUi()
        ).isFalse()
    }

    @Test
    fun `live minimal ui hides connection text until ai output exists`() {
        assertThat(
            GlassesUiState(
                liveModeEnabled = true,
                liveMinimalUiEnabled = true,
                displayText = "연결됨",
                hintText = "말해주세요",
            ).resolveLiveMinimalDisplayText()
        ).isEmpty()
    }

    @Test
    fun `live minimal ui prefers assistant output over generic display text`() {
        assertThat(
            GlassesUiState(
                liveModeEnabled = true,
                liveMinimalUiEnabled = true,
                displayText = "문서 검색 중",
                liveAssistantText = "실시간 답변",
            ).resolveLiveMinimalDisplayText()
        ).isEqualTo("실시간 답변")
    }

    @Test
    fun `live minimal ui shows rag text in rag only mode`() {
        assertThat(
            GlassesUiState(
                liveModeEnabled = true,
                liveMinimalUiEnabled = true,
                liveRagEnabled = true,
                liveRagDisplayMode = GlassesLiveRagDisplayMode.RAG_RESULT_ONLY,
                liveRagText = "RAG 결과",
                displayText = "문서 검색 중",
            ).resolveLiveMinimalDisplayText()
        ).isEqualTo("RAG 결과")
    }
}
