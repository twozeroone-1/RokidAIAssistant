package com.example.rokidphone.service.rag

import com.example.rokidphone.data.AnswerMode
import com.example.rokidphone.data.ApiSettings
import com.example.rokidphone.data.DocsHealthStatus
import com.example.rokidphone.data.NetworkProfile
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RouteResolverTest {

    private val resolver = RouteResolver()

    @Test
    fun `general ai text routes to general ai`() {
        val route = resolver.resolve(
            settings = ApiSettings(answerMode = AnswerMode.GENERAL_AI),
            inputType = AssistantInputType.TEXT,
        )

        assertThat(route.target).isEqualTo(RouteTarget.GENERAL_AI)
        assertThat(route.effectiveNetworkProfile).isEqualTo(NetworkProfile.AUTO)
    }

    @Test
    fun `docs text routes to rag`() {
        val route = resolver.resolve(
            settings = ApiSettings(answerMode = AnswerMode.DOCS),
            inputType = AssistantInputType.TEXT,
        )

        assertThat(route.target).isEqualTo(RouteTarget.DOCS_RAG)
        assertThat(route.routeBadge).isEqualTo(ConversationRouteBadge.DOCS)
    }

    @Test
    fun `docs fast photo routes to photo context rag`() {
        val route = resolver.resolve(
            settings = ApiSettings(
                answerMode = AnswerMode.DOCS,
                networkProfile = NetworkProfile.FAST,
            ),
            inputType = AssistantInputType.PHOTO,
        )

        assertThat(route.target).isEqualTo(RouteTarget.DOCS_PHOTO_CONTEXT_RAG)
        assertThat(route.routeBadge).isEqualTo(ConversationRouteBadge.DOCS_VIA_PHOTO_CONTEXT)
    }

    @Test
    fun `docs slow photo falls back to general ai`() {
        val route = resolver.resolve(
            settings = ApiSettings(
                answerMode = AnswerMode.DOCS,
                networkProfile = NetworkProfile.SLOW,
            ),
            inputType = AssistantInputType.PHOTO,
        )

        assertThat(route.target).isEqualTo(RouteTarget.GENERAL_AI_FALLBACK)
        assertThat(route.routeBadge).isEqualTo(ConversationRouteBadge.GENERAL_FALLBACK)
        assertThat(route.fallbackReason).contains("Slow profile")
    }

    @Test
    fun `general fallback badge is explicit for users`() {
        assertThat(ConversationRouteBadge.GENERAL_FALLBACK.label).isEqualTo("Docs -> General")
    }

    @Test
    fun `docs auto photo stays slow when health is unknown`() {
        val route = resolver.resolve(
            settings = ApiSettings(
                answerMode = AnswerMode.DOCS,
                networkProfile = NetworkProfile.AUTO,
                anythingLlmLastHealthStatus = DocsHealthStatus.UNKNOWN,
            ),
            inputType = AssistantInputType.PHOTO,
        )

        assertThat(route.target).isEqualTo(RouteTarget.GENERAL_AI_FALLBACK)
        assertThat(route.effectiveNetworkProfile).isEqualTo(NetworkProfile.SLOW)
    }

    @Test
    fun `docs auto photo upgrades to fast when health is healthy and there are no recent failures`() {
        val route = resolver.resolve(
            settings = ApiSettings(
                answerMode = AnswerMode.DOCS,
                networkProfile = NetworkProfile.AUTO,
                anythingLlmLastHealthStatus = DocsHealthStatus.HEALTHY,
                anythingLlmRecentFailureCount = 0,
            ),
            inputType = AssistantInputType.PHOTO,
        )

        assertThat(route.target).isEqualTo(RouteTarget.DOCS_PHOTO_CONTEXT_RAG)
        assertThat(route.effectiveNetworkProfile).isEqualTo(NetworkProfile.FAST)
    }
}
