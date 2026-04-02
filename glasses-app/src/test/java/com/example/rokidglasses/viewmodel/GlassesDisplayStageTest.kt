package com.example.rokidglasses.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlassesDisplayStageTest {

    @Test
    fun `deriveDisplayStage returns idle by default`() {
        val snapshot = SleepModeSnapshot()

        assertThat(deriveDisplayStage(snapshot)).isEqualTo(GlassesDisplayStage.IDLE)
    }

    @Test
    fun `deriveDisplayStage maps listening to capturing input`() {
        val snapshot = SleepModeSnapshot(isListening = true)

        assertThat(deriveDisplayStage(snapshot)).isEqualTo(GlassesDisplayStage.CAPTURING_INPUT)
    }

    @Test
    fun `deriveDisplayStage maps sending to sending`() {
        val snapshot = SleepModeSnapshot(isSendingInput = true)

        assertThat(deriveDisplayStage(snapshot)).isEqualTo(GlassesDisplayStage.SENDING)
    }

    @Test
    fun `deriveDisplayStage maps awaiting analysis to analyzing`() {
        val snapshot = SleepModeSnapshot(isAwaitingAnalysis = true)

        assertThat(deriveDisplayStage(snapshot)).isEqualTo(GlassesDisplayStage.ANALYZING)
    }

    @Test
    fun `deriveDisplayStage prioritizes visible output`() {
        val snapshot = SleepModeSnapshot(
            isListening = true,
            isSendingInput = true,
            isAwaitingAnalysis = true,
            hasVisibleOutput = true,
        )

        assertThat(deriveDisplayStage(snapshot)).isEqualTo(GlassesDisplayStage.OUTPUT)
    }
}
