package com.example.rokidphone.service.rag

import com.example.rokidphone.data.AnythingLlmSettings
import com.example.rokidphone.data.DocsHealthStatus

interface RagService {
    suspend fun checkHealth(settings: AnythingLlmSettings): Result<RagHealthResult>
    suspend fun answer(settings: AnythingLlmSettings, question: String): Result<RagAnswer>
}

data class RagHealthResult(
    val status: DocsHealthStatus,
    val message: String,
)

data class RagAnswer(
    val answerText: String,
    val routeLabel: String,
    val sources: List<SourcePreview>,
    val rawSourceCount: Int = 0,
)

data class SourcePreview(
    val title: String,
    val snippet: String = "",
)
