package com.example.rokidcommon.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveRagDisplayModeTest {

    @Test
    fun `effective display mode falls back to single rag result when live rag disabled`() {
        val effectiveMode = resolveEffectiveLiveRagDisplayMode(
            liveRagEnabled = false,
            configuredMode = LiveRagDisplayMode.SPLIT_LIVE_AND_RAG,
        )

        assertThat(effectiveMode).isEqualTo(LiveRagDisplayMode.RAG_RESULT_ONLY)
    }

    @Test
    fun `effective display mode preserves configured split mode when live rag enabled`() {
        val effectiveMode = resolveEffectiveLiveRagDisplayMode(
            liveRagEnabled = true,
            configuredMode = LiveRagDisplayMode.SPLIT_LIVE_AND_RAG,
        )

        assertThat(effectiveMode).isEqualTo(LiveRagDisplayMode.SPLIT_LIVE_AND_RAG)
    }
}
