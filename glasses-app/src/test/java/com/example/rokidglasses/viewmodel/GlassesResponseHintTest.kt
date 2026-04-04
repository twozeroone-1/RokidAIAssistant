package com.example.rokidglasses.viewmodel

import com.example.rokidcommon.protocol.LiveControlInputSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlassesResponseHintTest {

    @Test
    fun `live chat single page uses live active hint instead of tap continue`() {
        val hint = resolveResponseHint(
            state = GlassesUiState(
                liveModeEnabled = true,
                isLiveModeActive = true,
                liveInputSource = LiveControlInputSource.GLASSES,
            ),
            isChatMode = true,
            isPaginated = false,
            isLastPage = true,
            swipeForMoreHint = "Swipe for more",
            swipePagesHint = "Swipe left right",
            tapContinueHint = "Tap touchpad to continue conversation",
            photoSinglePageHint = "Tap touchpad to start conversation",
            liveActiveHint = "동작 중",
            liveResumeHint = "대기 중",
        )

        assertThat(hint).isEqualTo("동작 중")
    }

    @Test
    fun `live chat last page uses live resume hint instead of tap continue when paused`() {
        val hint = resolveResponseHint(
            state = GlassesUiState(
                liveModeEnabled = true,
                isLiveModeActive = false,
                liveInputSource = LiveControlInputSource.UNKNOWN,
            ),
            isChatMode = true,
            isPaginated = true,
            isLastPage = true,
            swipeForMoreHint = "Swipe for more",
            swipePagesHint = "Swipe left right",
            tapContinueHint = "Tap touchpad to continue conversation",
            photoSinglePageHint = "Tap touchpad to start conversation",
            liveActiveHint = "동작 중",
            liveResumeHint = "대기 중",
        )

        assertThat(hint).isEqualTo("대기 중")
    }

    @Test
    fun `live chat middle page keeps swipe hint`() {
        val hint = resolveResponseHint(
            state = GlassesUiState(
                liveModeEnabled = true,
                isLiveModeActive = true,
            ),
            isChatMode = true,
            isPaginated = true,
            isLastPage = false,
            swipeForMoreHint = "Swipe for more",
            swipePagesHint = "Swipe left right",
            tapContinueHint = "Tap touchpad to continue conversation",
            photoSinglePageHint = "Tap touchpad to start conversation",
            liveActiveHint = "동작 중",
            liveResumeHint = "대기 중",
        )

        assertThat(hint).isEqualTo("Swipe for more")
    }

    @Test
    fun `non live chat single page keeps tap continue`() {
        val hint = resolveResponseHint(
            state = GlassesUiState(),
            isChatMode = true,
            isPaginated = false,
            isLastPage = true,
            swipeForMoreHint = "Swipe for more",
            swipePagesHint = "Swipe left right",
            tapContinueHint = "Tap touchpad to continue conversation",
            photoSinglePageHint = "Tap touchpad to start conversation",
            liveActiveHint = "동작 중",
            liveResumeHint = "대기 중",
        )

        assertThat(hint).isEqualTo("Tap touchpad to continue conversation")
    }
}
