package com.example.rokidglasses.service.photo

enum class CameraRetryReason {
    CameraInUse,
    CameraDisabled,
    CameraError,
    Unknown,
}

class CameraCaptureRetryPolicy(
    private val baseDelayMs: Long = 750L,
    private val maxDelayMs: Long = 3000L,
) {
    fun isRecoverable(reason: CameraRetryReason): Boolean {
        return when (reason) {
            CameraRetryReason.CameraInUse,
            CameraRetryReason.CameraDisabled,
            CameraRetryReason.CameraError -> true
            CameraRetryReason.Unknown -> false
        }
    }

    fun retryDelayMs(attempt: Int): Long {
        return (baseDelayMs * attempt).coerceAtMost(maxDelayMs)
    }
}
