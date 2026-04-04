package com.example.rokidglasses.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlassesLivePanelContentTest {

    @Test
    fun `split live rag mode shows dual panels when either side has content`() {
        val content = resolveLivePanelContent(
            isLiveModeActive = true,
            liveRagEnabled = true,
            ragDisplayMode = GlassesLiveRagDisplayMode.SPLIT_LIVE_AND_RAG,
            assistantText = "왼쪽 응답",
            ragText = "오른쪽 문서 결과",
        )

        assertThat(content.showSplitPanels).isTrue()
        assertThat(content.leftText).isEqualTo("왼쪽 응답")
        assertThat(content.rightText).isEqualTo("오른쪽 문서 결과")
    }

    @Test
    fun `single live rag mode keeps single body display`() {
        val content = resolveLivePanelContent(
            isLiveModeActive = true,
            liveRagEnabled = true,
            ragDisplayMode = GlassesLiveRagDisplayMode.RAG_RESULT_ONLY,
            assistantText = "assistant",
            ragText = "rag",
        )

        assertThat(content.showSplitPanels).isFalse()
    }

    @Test
    fun `split mode does not stay active when live rag is disabled`() {
        val content = resolveLivePanelContent(
            isLiveModeActive = true,
            liveRagEnabled = false,
            ragDisplayMode = GlassesLiveRagDisplayMode.SPLIT_LIVE_AND_RAG,
            assistantText = "왼쪽 응답",
            ragText = "오른쪽 문서 결과",
        )

        assertThat(content.showSplitPanels).isFalse()
    }
}
