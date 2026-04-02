package com.example.rokidphone.data

enum class AnswerMode {
    GENERAL_AI,
    DOCS
}

enum class NetworkProfile {
    FAST,
    SLOW,
    AUTO
}

enum class DocsProvider {
    ANYTHING_LLM
}

enum class DocsHealthStatus {
    UNKNOWN,
    HEALTHY,
    UNHEALTHY
}

enum class AnythingLlmQueryMode(val wireValue: String) {
    QUERY("query"),
    CHAT("chat")
}

data class AnythingLlmSettings(
    val serverUrl: String,
    val apiKey: String,
    val workspaceSlug: String,
    val runtimeEnabled: Boolean = true,
    val queryMode: AnythingLlmQueryMode = AnythingLlmQueryMode.QUERY,
    val alwaysNewSession: Boolean = true,
    val lastHealthStatus: DocsHealthStatus = DocsHealthStatus.UNKNOWN,
    val lastHealthMessage: String = "",
    val recentFailureCount: Int = 0,
)

fun normalizeAnythingLlmServerUrl(raw: String): String = raw.trim()

fun normalizeAnythingLlmWorkspaceSlug(raw: String): String = raw.trim()

fun normalizeAnythingLlmApiKey(raw: String): String = raw.filterNot { it.isWhitespace() }

fun ApiSettings.toAnythingLlmSettings(): AnythingLlmSettings {
    return AnythingLlmSettings(
        serverUrl = normalizeAnythingLlmServerUrl(anythingLlmServerUrl),
        apiKey = normalizeAnythingLlmApiKey(anythingLlmApiKey),
        workspaceSlug = normalizeAnythingLlmWorkspaceSlug(anythingLlmWorkspaceSlug),
        runtimeEnabled = anythingLlmRuntimeEnabled,
        queryMode = anythingLlmQueryMode,
        lastHealthStatus = anythingLlmLastHealthStatus,
        lastHealthMessage = anythingLlmLastHealthMessage,
        recentFailureCount = anythingLlmRecentFailureCount,
    )
}
