package com.example.rokidglasses.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlassesPrimaryActionTest {

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

}
