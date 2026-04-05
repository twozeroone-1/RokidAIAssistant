package com.example.rokidphone.service

import com.example.rokidphone.data.ApiSettings
import com.example.rokidphone.data.LiveCameraMode
import com.example.rokidphone.service.ai.LiveSessionState

internal fun shouldUseManualLiveFrameCapture(
    settings: ApiSettings,
    liveSessionState: LiveSessionState,
): Boolean {
    return settings.liveModeEnabled &&
        settings.liveCameraMode == LiveCameraMode.MANUAL &&
        liveSessionState == LiveSessionState.ACTIVE
}
