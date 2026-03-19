package com.example.rokidphone.service.rag

import com.example.rokidphone.data.ApiSettings
import com.example.rokidphone.data.toAnythingLlmSettings

data class DocsFallbackDecision(
    val shouldFallback: Boolean,
    val reason: String? = null,
)

data class DocsTextResolution(
    val answerText: String,
    val route: RouteDecision,
    val sources: List<SourcePreview> = emptyList(),
)

object DocsFallbackEvaluator {
    private const val SHORT_ANSWER_WORD_THRESHOLD = 6
    private const val MISS_REASON =
        "Docs could not find enough grounded information, so General AI answered instead."
    private const val FAILURE_REASON =
        "Docs service was unavailable, so General AI answered instead."

    private val missPatterns = listOf(
        Regex("""\bi don't know\b"""),
        Regex("""\bdo not know\b"""),
        Regex("""\bnot found\b"""),
        Regex("""\bno relevant information\b"""),
        Regex("""\bno information\b"""),
        Regex("""\bunable to find\b"""),
        Regex("""\bnot available in (the )?(documents|docs)\b"""),
        Regex("""\bdon't have enough information\b"""),
    )

    fun evaluateAnswer(answer: RagAnswer): DocsFallbackDecision {
        val text = answer.answerText.trim()
        if (text.isBlank()) {
            return DocsFallbackDecision(
                shouldFallback = true,
                reason = FAILURE_REASON,
            )
        }
        if (answer.sources.isNotEmpty()) {
            return DocsFallbackDecision(shouldFallback = false)
        }

        val normalized = text.lowercase()
        if (missPatterns.any { it.containsMatchIn(normalized) }) {
            return DocsFallbackDecision(
                shouldFallback = true,
                reason = MISS_REASON,
            )
        }

        val wordCount = Regex("""\S+""").findAll(text).count()
        if (wordCount <= SHORT_ANSWER_WORD_THRESHOLD) {
            return DocsFallbackDecision(
                shouldFallback = true,
                reason = MISS_REASON,
            )
        }

        return DocsFallbackDecision(shouldFallback = false)
    }

    fun fallbackForFailure(message: String?): DocsFallbackDecision {
        return DocsFallbackDecision(
            shouldFallback = true,
            reason = FAILURE_REASON,
        )
    }
}

fun RouteDecision.asGeneralFallback(reason: String): RouteDecision {
    return copy(
        target = RouteTarget.GENERAL_AI_FALLBACK,
        routeBadge = ConversationRouteBadge.GENERAL_FALLBACK,
        fallbackReason = reason,
    )
}

suspend fun resolveDocsTextQuery(
    settings: ApiSettings,
    route: RouteDecision,
    normalizedQuestion: String,
    ragService: RagService,
    generalAnswer: suspend (String) -> String,
    onDocsHealthy: (String) -> Unit = {},
    onDocsFailure: (String) -> Unit = {},
): DocsTextResolution {
    require(route.target == RouteTarget.DOCS_RAG) { "resolveDocsTextQuery requires a DOCS_RAG route." }

    val docsResult = ragService.answer(
        settings = settings.toAnythingLlmSettings(),
        question = normalizedQuestion,
    )

    return docsResult.fold(
        onSuccess = { ragAnswer ->
            onDocsHealthy("AnythingLLM responded from ${settings.anythingLlmWorkspaceSlug.ifBlank { "workspace" }}.")
            val decision = DocsFallbackEvaluator.evaluateAnswer(ragAnswer)
            if (!decision.shouldFallback) {
                DocsTextResolution(
                    answerText = ragAnswer.answerText,
                    route = route,
                    sources = ragAnswer.sources,
                )
            } else {
                runCatching { generalAnswer(normalizedQuestion) }
                    .map { fallbackText ->
                        DocsTextResolution(
                            answerText = fallbackText,
                            route = route.asGeneralFallback(decision.reason ?: "General AI answered instead."),
                        )
                    }
                    .getOrElse {
                        DocsTextResolution(
                            answerText = ragAnswer.answerText,
                            route = route,
                            sources = ragAnswer.sources,
                        )
                    }
            }
        },
        onFailure = { error ->
            onDocsFailure(error.message ?: "Docs Assistant request failed.")
            val decision = DocsFallbackEvaluator.fallbackForFailure(error.message)
            val fallbackText = generalAnswer(normalizedQuestion)
            DocsTextResolution(
                answerText = fallbackText,
                route = route.asGeneralFallback(decision.reason ?: "General AI answered instead."),
            )
        },
    )
}
