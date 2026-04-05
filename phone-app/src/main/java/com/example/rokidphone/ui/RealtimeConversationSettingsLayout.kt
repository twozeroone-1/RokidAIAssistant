package com.example.rokidphone.ui

import com.example.rokidphone.data.ApiSettings
import com.example.rokidphone.data.LiveCameraMode
import com.example.rokidphone.data.LiveOutputTarget

enum class RealtimeConversationSettingKey {
    LIVE_INPUT_SOURCE,
    LIVE_OUTPUT_TARGET,
    PHONE_PLAYBACK_ROUTE,
    LIVE_ANSWER_AUDIO,
    LIVE_BARGE_IN,
    LIVE_VOICE,
    LIVE_GOOGLE_SEARCH,
    LIVE_RAG,
    LIVE_THINKING_LEVEL,
    LIVE_LONG_SESSION,
    LIVE_MINIMAL_UI,
    EXPERIMENTAL_LIVE_MIC_TUNING,
    EXPERIMENTAL_LIVE_MIC_PROFILE,
    LIVE_THOUGHT_SUMMARIES,
    LIVE_CAMERA_MODE,
    LIVE_CAMERA_INTERVAL,
}

data class RealtimeConversationSettingItem(
    val key: RealtimeConversationSettingKey,
)

fun realtimeConversationSettingItems(settings: ApiSettings): List<RealtimeConversationSettingItem> {
    val items = mutableListOf(
        RealtimeConversationSettingItem(RealtimeConversationSettingKey.LIVE_INPUT_SOURCE),
        RealtimeConversationSettingItem(RealtimeConversationSettingKey.LIVE_OUTPUT_TARGET),
        RealtimeConversationSettingItem(RealtimeConversationSettingKey.PHONE_PLAYBACK_ROUTE),
        RealtimeConversationSettingItem(RealtimeConversationSettingKey.LIVE_ANSWER_AUDIO),
        RealtimeConversationSettingItem(RealtimeConversationSettingKey.LIVE_BARGE_IN),
        RealtimeConversationSettingItem(RealtimeConversationSettingKey.LIVE_VOICE),
        RealtimeConversationSettingItem(RealtimeConversationSettingKey.LIVE_GOOGLE_SEARCH),
        RealtimeConversationSettingItem(RealtimeConversationSettingKey.LIVE_RAG),
        RealtimeConversationSettingItem(RealtimeConversationSettingKey.LIVE_THINKING_LEVEL),
        RealtimeConversationSettingItem(RealtimeConversationSettingKey.LIVE_LONG_SESSION),
        RealtimeConversationSettingItem(RealtimeConversationSettingKey.LIVE_MINIMAL_UI),
        RealtimeConversationSettingItem(RealtimeConversationSettingKey.EXPERIMENTAL_LIVE_MIC_TUNING),
    )

    if (!settings.liveAnswerAudioEnabled || settings.liveOutputTarget != LiveOutputTarget.PHONE) {
        items.remove(RealtimeConversationSettingItem(RealtimeConversationSettingKey.PHONE_PLAYBACK_ROUTE))
    }

    if (settings.experimentalLiveMicTuningEnabled) {
        items += RealtimeConversationSettingItem(RealtimeConversationSettingKey.EXPERIMENTAL_LIVE_MIC_PROFILE)
    }

    items += RealtimeConversationSettingItem(RealtimeConversationSettingKey.LIVE_THOUGHT_SUMMARIES)
    items += RealtimeConversationSettingItem(RealtimeConversationSettingKey.LIVE_CAMERA_MODE)

    if (settings.liveCameraMode == LiveCameraMode.INTERVAL) {
        items += RealtimeConversationSettingItem(RealtimeConversationSettingKey.LIVE_CAMERA_INTERVAL)
    }

    return items
}
