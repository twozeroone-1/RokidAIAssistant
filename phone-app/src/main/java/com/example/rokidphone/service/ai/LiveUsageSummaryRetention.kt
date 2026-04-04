package com.example.rokidphone.service.ai

object LiveUsageSummaryRetention {

    fun shouldClearForSessionState(state: LiveSessionState): Boolean {
        return when (state) {
            LiveSessionState.CONNECTING,
            LiveSessionState.RECONNECTING -> true

            else -> false
        }
    }

    fun shouldClearForIncomingUserTranscript(
        hasPersistedUsageSummary: Boolean,
        currentUserTranscript: String,
        incomingTranscript: String,
    ): Boolean {
        return hasPersistedUsageSummary &&
            currentUserTranscript.isBlank() &&
            incomingTranscript.isNotBlank()
    }
}
