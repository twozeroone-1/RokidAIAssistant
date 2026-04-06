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
            splitScrollMode = GlassesLiveRagSplitScrollMode.AUTO,
            assistantText = "왼쪽 응답",
            ragText = "오른쪽 문서 결과",
            ragTextFinalized = false,
        )

        assertThat(content.showSplitPanels).isTrue()
        assertThat(content.leftText).isEqualTo("왼쪽 응답")
        assertThat(content.rightText).isEqualTo("오른쪽 문서 결과")
        assertThat(content.autoScrollPanels).isTrue()
        assertThat(content.manualScrollPanels).isFalse()
    }

    @Test
    fun `single live rag mode keeps single body display`() {
        val content = resolveLivePanelContent(
            isLiveModeActive = true,
            liveRagEnabled = true,
            ragDisplayMode = GlassesLiveRagDisplayMode.RAG_RESULT_ONLY,
            splitScrollMode = GlassesLiveRagSplitScrollMode.AUTO,
            assistantText = "assistant",
            ragText = "rag",
            ragTextFinalized = true,
        )

        assertThat(content.showSplitPanels).isFalse()
        assertThat(content.autoScrollPanels).isFalse()
        assertThat(content.manualScrollPanels).isFalse()
    }

    @Test
    fun `split mode does not stay active when live rag is disabled`() {
        val content = resolveLivePanelContent(
            isLiveModeActive = true,
            liveRagEnabled = false,
            ragDisplayMode = GlassesLiveRagDisplayMode.SPLIT_LIVE_AND_RAG,
            splitScrollMode = GlassesLiveRagSplitScrollMode.AUTO,
            assistantText = "왼쪽 응답",
            ragText = "오른쪽 문서 결과",
            ragTextFinalized = true,
        )

        assertThat(content.showSplitPanels).isFalse()
        assertThat(content.autoScrollPanels).isFalse()
        assertThat(content.manualScrollPanels).isFalse()
    }

    @Test
    fun `split live rag mode enables synchronized auto scrolling when auto mode is selected`() {
        val content = resolveLivePanelContent(
            isLiveModeActive = true,
            liveRagEnabled = true,
            ragDisplayMode = GlassesLiveRagDisplayMode.SPLIT_LIVE_AND_RAG,
            splitScrollMode = GlassesLiveRagSplitScrollMode.AUTO,
            assistantText = "왼쪽 응답",
            ragText = "오른쪽 문서 결과",
            ragTextFinalized = false,
        )

        assertThat(content.showSplitPanels).isTrue()
        assertThat(content.autoScrollPanels).isTrue()
        assertThat(content.manualScrollPanels).isFalse()
    }

    @Test
    fun `split live rag mode enables synchronized manual scrolling when manual mode is selected`() {
        val content = resolveLivePanelContent(
            isLiveModeActive = true,
            liveRagEnabled = true,
            ragDisplayMode = GlassesLiveRagDisplayMode.SPLIT_LIVE_AND_RAG,
            splitScrollMode = GlassesLiveRagSplitScrollMode.MANUAL,
            assistantText = "왼쪽 응답",
            ragText = "오른쪽 문서 결과",
            ragTextFinalized = true,
        )

        assertThat(content.showSplitPanels).isTrue()
        assertThat(content.autoScrollPanels).isFalse()
        assertThat(content.manualScrollPanels).isTrue()
    }

    @Test
    fun `manual directional input becomes synchronized manual scroll action`() {
        val action = resolveLivePanelScrollAction(
            livePanelContent = GlassesLivePanelContent(
                showSplitPanels = true,
                leftText = "left",
                rightText = "right",
                autoScrollPanels = false,
                manualScrollPanels = true,
            ),
            command = LiveRagManualScrollCommand.UP,
        )

        assertThat(action).isEqualTo(
            LivePanelScrollAction.Manual(LiveRagManualScrollCommand.UP)
        )
    }

    @Test
    fun `auto directional input becomes synchronized auto direction action`() {
        val action = resolveLivePanelScrollAction(
            livePanelContent = GlassesLivePanelContent(
                showSplitPanels = true,
                leftText = "left",
                rightText = "right",
                autoScrollPanels = true,
                manualScrollPanels = false,
            ),
            command = LiveRagManualScrollCommand.DOWN,
        )

        assertThat(action).isEqualTo(
            LivePanelScrollAction.Auto(LiveRagAutoScrollDirection.DOWN)
        )
    }

    @Test
    fun `auto scroll duration gets longer as speed is slowed down`() {
        val normalDuration = resolveLiveRagAutoScrollDurationMillis(
            maxScrollPx = 600,
            speedLevel = 2,
        )
        val slowDuration = resolveLiveRagAutoScrollDurationMillis(
            maxScrollPx = 600,
            speedLevel = 0,
        )
        val fastDuration = resolveLiveRagAutoScrollDurationMillis(
            maxScrollPx = 600,
            speedLevel = 4,
        )

        assertThat(slowDuration).isGreaterThan(normalDuration)
        assertThat(normalDuration).isGreaterThan(fastDuration)
    }

    @Test
    fun `normal auto scroll uses 12 pixels per second pacing`() {
        val duration = resolveLiveRagAutoScrollDurationMillis(
            maxScrollPx = 600,
            speedLevel = 2,
        )

        assertThat(duration).isEqualTo(50000)
    }

    @Test
    fun `very slow auto scroll stays slow enough for a short panel`() {
        val duration = resolveLiveRagAutoScrollDurationMillis(
            maxScrollPx = 220,
            speedLevel = 0,
        )

        assertThat(duration).isAtLeast(30000)
    }

    @Test
    fun `manual scroll advances by one visible page`() {
        val target = resolveLiveRagManualScrollTarget(
            currentScrollPx = 0,
            maxScrollPx = 800,
            viewportHeightPx = 220,
            command = LiveRagManualScrollCommand.DOWN,
        )

        assertThat(target).isEqualTo(220)
    }

    @Test
    fun `manual scroll clamps to max scroll when near the end`() {
        val target = resolveLiveRagManualScrollTarget(
            currentScrollPx = 700,
            maxScrollPx = 800,
            viewportHeightPx = 220,
            command = LiveRagManualScrollCommand.DOWN,
        )

        assertThat(target).isEqualTo(800)
    }
}
