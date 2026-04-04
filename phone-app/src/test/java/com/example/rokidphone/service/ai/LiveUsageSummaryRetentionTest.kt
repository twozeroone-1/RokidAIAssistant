package com.example.rokidphone.service.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveUsageSummaryRetentionTest {

    @Test
    fun `reconnect states clear persisted usage summary`() {
        assertThat(
            LiveUsageSummaryRetention.shouldClearForSessionState(LiveSessionState.CONNECTING)
        ).isTrue()
        assertThat(
            LiveUsageSummaryRetention.shouldClearForSessionState(LiveSessionState.RECONNECTING)
        ).isTrue()
    }

    @Test
    fun `idle and error states keep last usage summary visible`() {
        assertThat(
            LiveUsageSummaryRetention.shouldClearForSessionState(LiveSessionState.ACTIVE)
        ).isFalse()
        assertThat(
            LiveUsageSummaryRetention.shouldClearForSessionState(LiveSessionState.ERROR)
        ).isFalse()
        assertThat(
            LiveUsageSummaryRetention.shouldClearForSessionState(LiveSessionState.IDLE)
        ).isFalse()
        assertThat(
            LiveUsageSummaryRetention.shouldClearForSessionState(LiveSessionState.STOPPING)
        ).isFalse()
    }

    @Test
    fun `first user transcript of a new turn clears previous summary`() {
        assertThat(
            LiveUsageSummaryRetention.shouldClearForIncomingUserTranscript(
                hasPersistedUsageSummary = true,
                currentUserTranscript = "",
                incomingTranscript = "새 질문 시작"
            )
        ).isTrue()
    }

    @Test
    fun `continued transcript keeps current summary until next turn actually starts`() {
        assertThat(
            LiveUsageSummaryRetention.shouldClearForIncomingUserTranscript(
                hasPersistedUsageSummary = true,
                currentUserTranscript = "이전 조각",
                incomingTranscript = "이어서 말함"
            )
        ).isFalse()
        assertThat(
            LiveUsageSummaryRetention.shouldClearForIncomingUserTranscript(
                hasPersistedUsageSummary = false,
                currentUserTranscript = "",
                incomingTranscript = "새 질문 시작"
            )
        ).isFalse()
        assertThat(
            LiveUsageSummaryRetention.shouldClearForIncomingUserTranscript(
                hasPersistedUsageSummary = true,
                currentUserTranscript = "",
                incomingTranscript = ""
            )
        ).isFalse()
    }
}
