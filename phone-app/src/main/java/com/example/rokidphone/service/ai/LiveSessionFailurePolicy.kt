package com.example.rokidphone.service.ai

internal enum class LiveSessionFailureType {
    INVALID_KEY,
    QUOTA,
}

internal fun isResumableConnectionTermination(rawError: String?): Boolean {
    if (rawError.isNullOrBlank()) {
        return false
    }

    val normalizedError = rawError.trim().lowercase()

    return normalizedError.contains("the operation was aborted") ||
        normalizedError.contains("operation was aborted")
}

internal fun classifyLiveSessionFailure(rawError: String?): LiveSessionFailureType? {
    if (rawError.isNullOrBlank()) {
        return null
    }

    val normalizedError = rawError.trim().lowercase()

    return when {
        normalizedError.contains("api key not valid") ||
            normalizedError.contains("invalid api key") ||
            normalizedError.contains("permission denied") -> LiveSessionFailureType.INVALID_KEY

        normalizedError.contains("resource_exhausted") ||
            normalizedError.contains("rate limit") ||
            normalizedError.contains("quota") ||
            normalizedError.contains("too many requests") ||
            normalizedError.contains("429") ||
            normalizedError.contains("503") -> LiveSessionFailureType.QUOTA

        else -> null
    }
}
