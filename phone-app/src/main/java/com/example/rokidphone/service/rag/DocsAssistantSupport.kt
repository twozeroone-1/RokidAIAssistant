package com.example.rokidphone.service.rag

import com.example.rokidphone.data.AnswerMode
import com.example.rokidphone.data.ApiSettings
import com.example.rokidphone.data.DocsHealthStatus
import com.example.rokidphone.data.SettingsRepository
import com.example.rokidphone.data.db.ConversationMetadata
import com.example.rokidphone.data.db.MessageMetadata
import com.example.rokidphone.data.db.MessageSourcePreview
import com.example.rokidphone.service.ServiceBridge

fun buildConversationProviderId(settings: ApiSettings): String {
    return if (settings.answerMode == AnswerMode.DOCS) {
        settings.docsProvider.name
    } else {
        settings.aiProvider.name
    }
}

fun buildConversationModelId(settings: ApiSettings): String {
    return if (settings.answerMode == AnswerMode.DOCS) {
        settings.anythingLlmWorkspaceSlug.ifBlank { "anythingllm-query" }
    } else {
        settings.aiModelId
    }
}

fun buildConversationMetadata(
    route: RouteDecision,
    settings: ApiSettings,
): ConversationMetadata {
    return ConversationMetadata(
        routeBadge = route.routeBadge,
        docsWorkspaceSlug = settings.anythingLlmWorkspaceSlug.takeIf { route.routeBadge != ConversationRouteBadge.GENERAL },
        fallbackReason = route.fallbackReason,
    )
}

fun buildAssistantMessageMetadata(
    route: RouteDecision,
    settings: ApiSettings,
    sources: List<SourcePreview> = emptyList(),
): MessageMetadata {
    return MessageMetadata(
        routeBadge = route.routeBadge,
        docsWorkspaceSlug = settings.anythingLlmWorkspaceSlug.takeIf { route.routeBadge != ConversationRouteBadge.GENERAL },
        fallbackReason = route.fallbackReason,
        sourcePreviews = sources.map {
            MessageSourcePreview(
                title = it.title,
                snippet = it.snippet,
            )
        },
    )
}

fun markDocsAssistantHealthy(
    settingsRepository: SettingsRepository,
    message: String,
) {
    val current = settingsRepository.getSettings()
    settingsRepository.saveSettings(
        current.copy(
            anythingLlmLastHealthStatus = DocsHealthStatus.HEALTHY,
            anythingLlmLastHealthMessage = message,
            anythingLlmRecentFailureCount = 0,
        )
    )
}

fun markDocsAssistantFailure(
    settingsRepository: SettingsRepository,
    message: String,
) {
    val current = settingsRepository.getSettings()
    settingsRepository.saveSettings(
        current.copy(
            anythingLlmLastHealthStatus = DocsHealthStatus.UNHEALTHY,
            anythingLlmLastHealthMessage = message,
            anythingLlmRecentFailureCount = current.anythingLlmRecentFailureCount + 1,
        )
    )
}

fun buildGlassesAssistantResponse(
    answerText: String,
    route: RouteDecision,
    sources: List<SourcePreview>,
    normalizer: InputNormalizer = InputNormalizer(),
): String {
    val cleanedAnswer = ServiceBridge.cleanMarkdown(answerText)
    if (route.routeBadge == ConversationRouteBadge.GENERAL) {
        return cleanedAnswer
    }

    val sourceSummary = normalizer.buildSourceSummary(sources)
    return buildString {
        append("[Docs]")
        append('\n')
        append(cleanedAnswer)
        if (!sourceSummary.isNullOrBlank()) {
            append("\n\n")
            append(sourceSummary)
        }
        if (!route.fallbackReason.isNullOrBlank()) {
            append("\n\n")
            append(route.fallbackReason)
        }
    }
}
