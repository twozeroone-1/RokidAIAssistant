package com.example.rokidphone.service.ai

import com.example.rokidphone.data.LiveInputSource
import com.example.rokidphone.data.LiveOutputTarget
import com.example.rokidphone.data.LiveThinkingLevel

data class LiveSessionStatusSnapshot(
    val inputSource: LiveInputSource,
    val outputTarget: LiveOutputTarget,
    val audioOutputEnabled: Boolean,
    val liveRagEnabled: Boolean,
    val googleSearchEnabledInSettings: Boolean,
    val googleSearchAvailableForSession: Boolean,
    val thinkingLevel: LiveThinkingLevel,
)
