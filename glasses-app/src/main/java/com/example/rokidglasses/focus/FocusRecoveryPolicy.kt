package com.example.rokidglasses.focus

data class FocusRecoveryState(
    val isResumed: Boolean,
    val hasWindowFocus: Boolean,
    val isFinishing: Boolean,
    val isChangingConfigurations: Boolean,
)

class FocusRecoveryPolicy(
    private val cooldownMs: Long = 1_000L,
) {
    fun shouldRecover(
        state: FocusRecoveryState,
        nowMs: Long,
        lastRecoveryAtMs: Long,
    ): Boolean {
        if (!state.isResumed) return false
        if (state.hasWindowFocus) return false
        if (state.isFinishing) return false
        if (state.isChangingConfigurations) return false

        return nowMs - lastRecoveryAtMs >= cooldownMs
    }
}
