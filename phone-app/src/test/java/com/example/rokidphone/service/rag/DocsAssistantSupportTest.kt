package com.example.rokidphone.service.rag

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocsAssistantSupportTest {

    @Test
    fun `fallback glasses response uses explicit docs to general prefix`() {
        val response = buildGlassesAssistantResponse(
            answerText = "Try restarting the service.",
            route = RouteDecision(
                target = RouteTarget.GENERAL_AI_FALLBACK,
                routeBadge = ConversationRouteBadge.GENERAL_FALLBACK,
                effectiveNetworkProfile = com.example.rokidphone.data.NetworkProfile.FAST,
                fallbackReason = "Docs could not find enough grounded information, so General AI answered instead.",
            ),
            sources = emptyList(),
        )

        assertThat(response).startsWith("[Docs -> General]")
    }
}
