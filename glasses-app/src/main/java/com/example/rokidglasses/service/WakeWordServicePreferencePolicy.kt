package com.example.rokidglasses.service

enum class WakeWordServiceAction {
    START,
    STOP,
    NONE,
}

object WakeWordServicePreferencePolicy {
    fun resolveAction(
        systemWakeWordEnabled: Boolean,
        serviceRunning: Boolean,
    ): WakeWordServiceAction {
        return when {
            systemWakeWordEnabled && !serviceRunning -> WakeWordServiceAction.START
            !systemWakeWordEnabled && serviceRunning -> WakeWordServiceAction.STOP
            else -> WakeWordServiceAction.NONE
        }
    }
}
