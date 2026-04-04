package com.example.rokidphone.service.ai

internal data class LiveErrorPresentation(
    val userMessage: String,
    val shouldNotifyApiKeyMissing: Boolean,
)

private const val GENERIC_LIVE_SESSION_ERROR = "Live session error"
private const val INVALID_LIVE_API_KEY_MESSAGE =
    "Gemini Live API 키가 유효하지 않습니다. 설정에서 Gemini API 키를 다시 입력하세요."
private const val LIVE_QUOTA_MESSAGE =
    "Gemini Live 쿼터를 초과했거나 현재 프로젝트 플랜 제한에 걸렸습니다. 잠시 후 다시 시도하거나 Google Search 설정을 확인하세요."

internal fun resolveLiveErrorPresentation(rawError: String?): LiveErrorPresentation {
    if (rawError.isNullOrBlank()) {
        return LiveErrorPresentation(
            userMessage = GENERIC_LIVE_SESSION_ERROR,
            shouldNotifyApiKeyMissing = false,
        )
    }

    val normalizedError = rawError.trim()
    val isInvalidApiKey = normalizedError.contains("api key not valid", ignoreCase = true) ||
        normalizedError.contains("invalid api key", ignoreCase = true)
    val isQuotaFailure = normalizedError.contains("resource_exhausted", ignoreCase = true) ||
        normalizedError.contains("rate limit", ignoreCase = true) ||
        normalizedError.contains("quota", ignoreCase = true) ||
        normalizedError.contains("too many requests", ignoreCase = true) ||
        normalizedError.contains("429", ignoreCase = true) ||
        normalizedError.contains("503", ignoreCase = true)

    return if (isInvalidApiKey) {
        LiveErrorPresentation(
            userMessage = INVALID_LIVE_API_KEY_MESSAGE,
            shouldNotifyApiKeyMissing = true,
        )
    } else if (isQuotaFailure) {
        LiveErrorPresentation(
            userMessage = LIVE_QUOTA_MESSAGE,
            shouldNotifyApiKeyMissing = false,
        )
    } else {
        LiveErrorPresentation(
            userMessage = normalizedError,
            shouldNotifyApiKeyMissing = false,
        )
    }
}
