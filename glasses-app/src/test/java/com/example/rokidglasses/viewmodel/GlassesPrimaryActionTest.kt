package com.example.rokidglasses.viewmodel

import com.example.rokidcommon.protocol.LiveControlInputSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlassesPrimaryActionTest {

    @Test
    fun `live session toggle action exists for phone live control`() {
        assertThat(PrimaryActionOutcome.entries.map { it.name }).contains("TOGGLE_LIVE_SESSION")
    }

    @Test
    fun `visible output without pagination requires dismiss before next action`() {
        val state = GlassesUiState(
            hasVisibleOutput = true,
            isPaginated = false,
            sleepModeEnabled = false,
        )

        assertThat(resolvePrimaryAction(state)).isEqualTo(PrimaryActionOutcome.DISMISS_OUTPUT)
    }

    @Test
    fun `paginated output before final page advances to next page`() {
        val state = GlassesUiState(
            hasVisibleOutput = true,
            isPaginated = true,
            currentPage = 0,
            totalPages = 3,
        )

        assertThat(resolvePrimaryAction(state)).isEqualTo(PrimaryActionOutcome.NEXT_PAGE)
    }

    @Test
    fun `last output page dismisses instead of starting recording`() {
        val state = GlassesUiState(
            hasVisibleOutput = true,
            isPaginated = true,
            currentPage = 1,
            totalPages = 2,
            sleepModeEnabled = false,
        )

        assertThat(resolvePrimaryAction(state)).isEqualTo(PrimaryActionOutcome.DISMISS_OUTPUT)
    }

    @Test
    fun `idle state keeps toggle recording behavior`() {
        val state = GlassesUiState(
            hasVisibleOutput = false,
            isPaginated = false,
        )

        assertThat(resolvePrimaryAction(state)).isEqualTo(PrimaryActionOutcome.TOGGLE_RECORDING)
    }

    @Test
    fun `live active toggles live session instead of dismissing output regardless of input source`() {
        val state = GlassesUiState(
            liveModeEnabled = true,
            isLiveModeActive = true,
            liveInputSource = LiveControlInputSource.GLASSES,
            hasVisibleOutput = true,
            isPaginated = false,
        )

        assertThat(resolvePrimaryAction(state).name).isEqualTo("TOGGLE_LIVE_SESSION")
    }

    @Test
    fun `live paused toggles live session instead of starting legacy recording regardless of input source`() {
        val state = GlassesUiState(
            liveModeEnabled = true,
            isLiveModeActive = false,
            liveInputSource = LiveControlInputSource.UNKNOWN,
            hasVisibleOutput = false,
            isPaginated = false,
        )

        assertThat(resolvePrimaryAction(state).name).isEqualTo("TOGGLE_LIVE_SESSION")
    }

    @Test
    fun `non live idle keeps existing recording control`() {
        val state = GlassesUiState(
            liveModeEnabled = false,
            isLiveModeActive = false,
            liveInputSource = LiveControlInputSource.GLASSES,
            hasVisibleOutput = false,
            isPaginated = false,
        )

        assertThat(resolvePrimaryAction(state)).isEqualTo(PrimaryActionOutcome.TOGGLE_RECORDING)
    }
}
