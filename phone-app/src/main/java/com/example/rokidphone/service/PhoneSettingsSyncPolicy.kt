package com.example.rokidphone.service

import com.example.rokidphone.data.ApiSettings

internal object PhoneSettingsSyncPolicy {

    fun requiresServiceRefresh(previous: ApiSettings, current: ApiSettings): Boolean {
        return previous.copy(
            responseFontScalePercent = current.responseFontScalePercent,
            glassesSleepModeEnabled = current.glassesSleepModeEnabled,
            liveMinimalUiEnabled = current.liveMinimalUiEnabled,
        ) != current
    }

    fun shouldSyncResponseFontScale(
        lastSynced: Int?,
        current: Int,
        force: Boolean
    ): Boolean {
        val normalizedCurrent = ApiSettings.clampResponseFontScalePercent(current)
        return force || lastSynced != normalizedCurrent
    }
}
