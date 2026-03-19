package com.example.rokidphone.service.rag

import com.example.rokidphone.data.AnswerMode
import com.example.rokidphone.data.ApiSettings
import com.example.rokidphone.data.DocsHealthStatus
import com.example.rokidphone.data.NetworkProfile

enum class AssistantInputType {
    TEXT,
    VOICE,
    PHOTO
}

enum class RouteTarget {
    GENERAL_AI,
    DOCS_RAG,
    DOCS_PHOTO_CONTEXT_RAG,
    GENERAL_AI_FALLBACK
}

enum class ConversationRouteBadge(val label: String) {
    GENERAL("General"),
    DOCS("Docs"),
    DOCS_VIA_PHOTO_CONTEXT("Docs + Photo"),
    GENERAL_FALLBACK("Docs -> General")
}

data class RouteDecision(
    val target: RouteTarget,
    val routeBadge: ConversationRouteBadge,
    val effectiveNetworkProfile: NetworkProfile,
    val fallbackReason: String? = null,
)

class RouteResolver {

    fun resolve(
        settings: ApiSettings,
        inputType: AssistantInputType,
    ): RouteDecision {
        val effectiveNetworkProfile = resolveEffectiveNetworkProfile(settings)

        if (settings.answerMode == AnswerMode.GENERAL_AI) {
            return RouteDecision(
                target = RouteTarget.GENERAL_AI,
                routeBadge = ConversationRouteBadge.GENERAL,
                effectiveNetworkProfile = settings.networkProfile,
            )
        }

        return when (inputType) {
            AssistantInputType.TEXT,
            AssistantInputType.VOICE -> RouteDecision(
                target = RouteTarget.DOCS_RAG,
                routeBadge = ConversationRouteBadge.DOCS,
                effectiveNetworkProfile = effectiveNetworkProfile,
            )

            AssistantInputType.PHOTO -> {
                if (effectiveNetworkProfile == NetworkProfile.FAST) {
                    RouteDecision(
                        target = RouteTarget.DOCS_PHOTO_CONTEXT_RAG,
                        routeBadge = ConversationRouteBadge.DOCS_VIA_PHOTO_CONTEXT,
                        effectiveNetworkProfile = effectiveNetworkProfile,
                    )
                } else {
                    RouteDecision(
                        target = RouteTarget.GENERAL_AI_FALLBACK,
                        routeBadge = ConversationRouteBadge.GENERAL_FALLBACK,
                        effectiveNetworkProfile = effectiveNetworkProfile,
                        fallbackReason = "Slow profile forced a general AI fallback for photo input.",
                    )
                }
            }
        }
    }

    private fun resolveEffectiveNetworkProfile(settings: ApiSettings): NetworkProfile {
        if (settings.answerMode != AnswerMode.DOCS) {
            return settings.networkProfile
        }

        return when (settings.networkProfile) {
            NetworkProfile.AUTO -> {
                if (settings.anythingLlmLastHealthStatus == DocsHealthStatus.HEALTHY &&
                    settings.anythingLlmRecentFailureCount <= 0
                ) {
                    NetworkProfile.FAST
                } else {
                    NetworkProfile.SLOW
                }
            }
            else -> settings.networkProfile
        }
    }
}
