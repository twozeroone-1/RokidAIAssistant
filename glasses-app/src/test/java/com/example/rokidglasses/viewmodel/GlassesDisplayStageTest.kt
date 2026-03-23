package com.example.rokidglasses.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlassesDisplayStageTest {

    @Test
    fun `default snapshot maps to idle`() {
        val snapshot = SleepModeSnapshot()

        assertThat(deriveDisplayStage(snapshot)).isEqualTo(GlassesDisplayStage.IDLE)
    }

    @Test
    fun `listening maps to capturing input`() {
        val snapshot = SleepModeSnapshot(isListening = true)

        assertThat(deriveDisplayStage(snapshot)).isEqualTo(GlassesDisplayStage.CAPTURING_INPUT)
    }

    @Test
    fun `capturing photo maps to capturing input`() {
        val snapshot = SleepModeSnapshot(isCapturingPhoto = true)

        assertThat(deriveDisplayStage(snapshot)).isEqualTo(GlassesDisplayStage.CAPTURING_INPUT)
    }

    @Test
    fun `sending state maps to sending`() {
        val snapshot = SleepModeSnapshot(isSendingInput = true)

        assertThat(deriveDisplayStage(snapshot)).isEqualTo(GlassesDisplayStage.SENDING)
    }

    @Test
    fun `analysis wait maps to analyzing`() {
        val snapshot = SleepModeSnapshot(isAwaitingAnalysis = true)

        assertThat(deriveDisplayStage(snapshot)).isEqualTo(GlassesDisplayStage.ANALYZING)
    }

    @Test
    fun `visible output maps to output`() {
        val snapshot = SleepModeSnapshot(hasVisibleOutput = true)

        assertThat(deriveDisplayStage(snapshot)).isEqualTo(GlassesDisplayStage.OUTPUT)
    }

    @Test
    fun `output wins over in progress flags`() {
        val snapshot = SleepModeSnapshot(
            isListening = true,
            isSendingInput = true,
            isAwaitingAnalysis = true,
            hasVisibleOutput = true,
        )

        assertThat(deriveDisplayStage(snapshot)).isEqualTo(GlassesDisplayStage.OUTPUT)
    }
}
