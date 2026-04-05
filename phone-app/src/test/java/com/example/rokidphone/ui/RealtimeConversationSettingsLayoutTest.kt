package com.example.rokidphone.ui

import com.example.rokidphone.data.ApiSettings
import com.example.rokidphone.data.LiveCameraMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RealtimeConversationSettingsLayoutTest {

    @Test
    fun defaultVisibleItems_prioritizeRoutingPlaybackAndVoiceControls() {
        val items = realtimeConversationSettingItems(ApiSettings()).map { it.key }

        assertThat(items).containsExactly(
            RealtimeConversationSettingKey.LIVE_INPUT_SOURCE,
            RealtimeConversationSettingKey.LIVE_OUTPUT_TARGET,
            RealtimeConversationSettingKey.LIVE_ANSWER_AUDIO,
            RealtimeConversationSettingKey.LIVE_BARGE_IN,
            RealtimeConversationSettingKey.LIVE_VOICE,
            RealtimeConversationSettingKey.LIVE_GOOGLE_SEARCH,
            RealtimeConversationSettingKey.LIVE_RAG,
            RealtimeConversationSettingKey.LIVE_THINKING_LEVEL,
            RealtimeConversationSettingKey.LIVE_LONG_SESSION,
            RealtimeConversationSettingKey.LIVE_MINIMAL_UI,
            RealtimeConversationSettingKey.EXPERIMENTAL_LIVE_MIC_TUNING,
            RealtimeConversationSettingKey.LIVE_THOUGHT_SUMMARIES,
            RealtimeConversationSettingKey.LIVE_CAMERA_MODE,
        ).inOrder()
    }

    @Test
    fun conditionalItems_onlyAppearWhenTheirParentSettingRequiresThem() {
        val defaultItems = realtimeConversationSettingItems(ApiSettings()).map { it.key }
        assertThat(defaultItems).doesNotContain(RealtimeConversationSettingKey.EXPERIMENTAL_LIVE_MIC_PROFILE)
        assertThat(defaultItems).doesNotContain(RealtimeConversationSettingKey.LIVE_CAMERA_INTERVAL)

        val conditionalItems = realtimeConversationSettingItems(
            ApiSettings(
                experimentalLiveMicTuningEnabled = true,
                liveCameraMode = LiveCameraMode.INTERVAL,
            )
        ).map { it.key }

        assertThat(conditionalItems).contains(RealtimeConversationSettingKey.EXPERIMENTAL_LIVE_MIC_PROFILE)
        assertThat(conditionalItems).contains(RealtimeConversationSettingKey.LIVE_CAMERA_INTERVAL)
        assertThat(
            conditionalItems.indexOf(RealtimeConversationSettingKey.EXPERIMENTAL_LIVE_MIC_PROFILE)
        ).isGreaterThan(
            conditionalItems.indexOf(RealtimeConversationSettingKey.EXPERIMENTAL_LIVE_MIC_TUNING)
        )
        assertThat(
            conditionalItems.indexOf(RealtimeConversationSettingKey.LIVE_CAMERA_INTERVAL)
        ).isGreaterThan(
            conditionalItems.indexOf(RealtimeConversationSettingKey.LIVE_CAMERA_MODE)
        )
    }
}
