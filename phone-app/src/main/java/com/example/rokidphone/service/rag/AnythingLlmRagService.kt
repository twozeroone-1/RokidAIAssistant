package com.example.rokidphone.service.rag

import com.example.rokidphone.data.AnythingLlmSettings
import com.example.rokidphone.data.DocsHealthStatus
import com.example.rokidphone.service.rag.network.AnythingLlmApi
import com.example.rokidphone.service.rag.network.AnythingLlmApiFactory
import com.example.rokidphone.service.rag.network.AnythingLlmChatRequest
import retrofit2.HttpException
import java.util.UUID

class AnythingLlmRagService(
    private val apiFactory: (String, String) -> AnythingLlmApi = { serverUrl, apiKey ->
        AnythingLlmApiFactory.create(serverUrl, apiKey)
    },
) : RagService {

    override suspend fun checkHealth(settings: AnythingLlmSettings): Result<RagHealthResult> = runCatching {
        validateSettings(settings)
        val api = apiFactory(AnythingLlmApiFactory.normalizeServerUrl(settings.serverUrl), settings.apiKey)

        val ping = api.ping()
        if (ping.online != true) {
            error("AnythingLLM server is offline.")
        }

        val auth = api.auth()
        if (auth.authenticated != true) {
            error("AnythingLLM authentication failed.")
        }

        val workspace = api.workspaces().allWorkspaces.firstOrNull { it.slug == settings.workspaceSlug }
            ?: api.workspace(settings.workspaceSlug).resolvedWorkspace
            ?: error("Workspace '${settings.workspaceSlug}' was not found.")

        RagHealthResult(
            status = DocsHealthStatus.HEALTHY,
            message = "AnythingLLM is online and workspace ${workspace.name ?: workspace.slug ?: settings.workspaceSlug} is available.",
        )
    }.mapFailure("AnythingLLM health check failed")

    override suspend fun answer(settings: AnythingLlmSettings, question: String): Result<RagAnswer> = runCatching {
        validateSettings(settings)
        val normalizedQuestion = question.trim()
        require(normalizedQuestion.isNotBlank()) { "Question must not be blank." }

        val api = apiFactory(AnythingLlmApiFactory.normalizeServerUrl(settings.serverUrl), settings.apiKey)
        val requestSessionId = if (settings.alwaysNewSession) UUID.randomUUID().toString() else null
        val response = api.chat(
            slug = settings.workspaceSlug,
            request = AnythingLlmChatRequest(
                message = normalizedQuestion,
                mode = settings.queryMode.wireValue,
                sessionId = requestSessionId,
            ),
        )

        val answerText = response.textResponse?.trim().takeUnless { it.isNullOrBlank() }
            ?: response.error?.trim().takeUnless { it.isNullOrBlank() }
            ?: error("AnythingLLM returned an empty response.")

        RagAnswer(
            answerText = answerText,
            routeLabel = "AnythingLLM ${settings.queryMode.name.lowercase()}",
            sources = response.sources.mapIndexed { index, source ->
                SourcePreview(
                    title = source.title?.trim().takeUnless { it.isNullOrBlank() } ?: "Source ${index + 1}",
                    snippet = source.chunk?.trim().orEmpty(),
                )
            },
            rawSourceCount = response.sources.size,
        )
    }.mapFailure("AnythingLLM answer failed")

    private fun validateSettings(settings: AnythingLlmSettings) {
        require(settings.runtimeEnabled) { "AnythingLLM runtime is disabled." }
        require(settings.serverUrl.isNotBlank()) { "AnythingLLM server URL is required." }
        require(settings.serverUrl.startsWith("http://") || settings.serverUrl.startsWith("https://")) {
            "AnythingLLM server URL must start with http:// or https://."
        }
        require(settings.apiKey.isNotBlank()) { "AnythingLLM API key is required." }
        require(settings.workspaceSlug.isNotBlank()) { "AnythingLLM workspace slug is required." }
    }

    private fun <T> Result<T>.mapFailure(prefix: String): Result<T> {
        return fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                val message = when (error) {
                    is HttpException -> "$prefix: HTTP ${error.code()} ${error.message()}"
                    else -> "$prefix: ${error.message ?: "Unknown error"}"
                }
                Result.failure(IllegalStateException(message, error))
            },
        )
    }
}
