package com.example.rokidphone.service.ai

import com.example.rokidcommon.protocol.LiveRagDisplayMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveRagTurnStateTest {

    @Test
    fun `single display prefers rag answer when tool succeeds`() {
        val state = LiveRagTurnState(
            toolInvoked = true,
            ragAnswerText = "전원 버튼을 3초간 누르세요."
        )

        val finalText = state.resolveFinalText(
            displayMode = LiveRagDisplayMode.RAG_RESULT_ONLY,
            assistantText = "버튼을 눌러보세요.",
            noResultLabel = "문서 검색 없음"
        )

        assertThat(finalText).isEqualTo("전원 버튼을 3초간 누르세요.")
    }

    @Test
    fun `single display falls back to assistant answer when tool is never called`() {
        val finalText = LiveRagTurnState().resolveFinalText(
            displayMode = LiveRagDisplayMode.RAG_RESULT_ONLY,
            assistantText = "지금은 오후 3시입니다.",
            noResultLabel = "문서 검색 없음"
        )

        assertThat(finalText).isEqualTo("지금은 오후 3시입니다.")
    }

    @Test
    fun `single display shows no result label when tool is called without usable answer`() {
        val state = LiveRagTurnState()
            .markToolInvoked()
            .applyToolResult(ToolResult.failure("tool-2", "Docs search failed."))

        val finalText = state.resolveFinalText(
            displayMode = LiveRagDisplayMode.RAG_RESULT_ONLY,
            assistantText = "문서를 다시 찾아보세요.",
            noResultLabel = "문서 검색 없음"
        )

        assertThat(finalText).isEqualTo("문서 검색 없음")
    }

    @Test
    fun `split display keeps assistant answer on left and no result label on right when rag is absent`() {
        val state = LiveRagTurnState()

        val panels = state.resolveSplitPanels(
            assistantText = "지금은 오후 3시입니다.",
            searchingLabel = "문서 검색 중...",
            noResultLabel = "문서 검색 없음",
            turnComplete = true,
        )

        assertThat(panels.leftText).isEqualTo("지금은 오후 3시입니다.")
        assertThat(panels.rightText).isEqualTo("문서 검색 없음")
    }
}
