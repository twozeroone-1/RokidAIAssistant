package com.example.rokidglasses.viewmodel

import com.example.rokidcommon.protocol.LiveControlInputSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GlassesLivePhoneControlTest {

    @Test
    fun `active phone live uses speak hint and clears legacy display text`() {
        val prompt = resolveIdlePrompt(
            state = GlassesUiState(
                liveModeEnabled = true,
                isLiveModeActive = true,
                liveInputSource = LiveControlInputSource.GLASSES,
            ),
            defaultDisplayText = "Tap touchpad to start conversation",
            defaultHintText = "Tap touchpad to start conversation",
            livePhoneActiveHint = "동작 중",
            livePhoneResumeHint = "대기 중",
        )

        assertThat(prompt.displayText).isEmpty()
        assertThat(prompt.hintText).isEqualTo("동작 중")
    }

    @Test
    fun `paused phone live uses resume hint and clears legacy display text`() {
        val prompt = resolveIdlePrompt(
            state = GlassesUiState(
                liveModeEnabled = true,
                isLiveModeActive = false,
                liveInputSource = LiveControlInputSource.UNKNOWN,
            ),
            defaultDisplayText = "Tap touchpad to start conversation",
            defaultHintText = "Tap touchpad to start conversation",
            livePhoneActiveHint = "동작 중",
            livePhoneResumeHint = "대기 중",
        )

        assertThat(prompt.displayText).isEmpty()
        assertThat(prompt.hintText).isEqualTo("대기 중")
    }

    @Test
    fun `legacy idle prompt remains outside phone live mode`() {
        val prompt = resolveIdlePrompt(
            state = GlassesUiState(
                liveModeEnabled = false,
                isLiveModeActive = false,
                liveInputSource = LiveControlInputSource.UNKNOWN,
            ),
            defaultDisplayText = "Tap touchpad to start conversation",
            defaultHintText = "Tap touchpad to start recording",
            livePhoneActiveHint = "동작 중",
            livePhoneResumeHint = "대기 중",
        )

        assertThat(prompt.displayText).isEqualTo("Tap touchpad to start conversation")
        assertThat(prompt.hintText).isEqualTo("Tap touchpad to start recording")
    }
}
