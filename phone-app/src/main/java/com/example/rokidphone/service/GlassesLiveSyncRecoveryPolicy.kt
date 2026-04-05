package com.example.rokidphone.service

import com.example.rokidcommon.protocol.LiveControlInputSource

internal object GlassesLiveSyncRecoveryPolicy {
    const val WATCHDOG_DELAY_MS = 1_500L
    const val MAX_RETRY_COUNT = 1

    fun shouldMonitor(
        sessionActive: Boolean,
        liveModeEnabled: Boolean,
        glassesConnected: Boolean,
        effectiveInputSource: LiveControlInputSource,
    ): Boolean {
        return sessionActive &&
            liveModeEnabled &&
            glassesConnected &&
            effectiveInputSource == LiveControlInputSource.GLASSES
    }

    fun shouldRetry(
        sessionActive: Boolean,
        liveModeEnabled: Boolean,
        glassesConnected: Boolean,
        effectiveInputSource: LiveControlInputSource,
        scheduledAtMs: Long,
        lastSignalAtMs: Long?,
        nowMs: Long,
        retryCount: Int,
    ): Boolean {
        if (
            !shouldMonitor(
                sessionActive = sessionActive,
                liveModeEnabled = liveModeEnabled,
                glassesConnected = glassesConnected,
                effectiveInputSource = effectiveInputSource,
            )
        ) {
            return false
        }
        if (retryCount >= MAX_RETRY_COUNT) {
            return false
        }
        if (nowMs - scheduledAtMs < WATCHDOG_DELAY_MS) {
            return false
        }
        return lastSignalAtMs == null || lastSignalAtMs < scheduledAtMs
    }
}
