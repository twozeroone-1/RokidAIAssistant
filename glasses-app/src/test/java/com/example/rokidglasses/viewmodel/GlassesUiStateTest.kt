package com.example.rokidglasses.viewmodel

import com.example.rokidcommon.protocol.LiveRagSplitScrollMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlassesUiStateTest {

    @Test
    fun `defaults live rag split scroll mode to manual`() {
        assertThat(GlassesUiState().liveRagSplitScrollMode)
            .isEqualTo(LiveRagSplitScrollMode.MANUAL)
    }

    @Test
    fun `defaults live rag auto scroll direction to down`() {
        assertThat(GlassesUiState().liveRagAutoScrollDirection)
            .isEqualTo(LiveRagAutoScrollDirection.DOWN)
    }
}
