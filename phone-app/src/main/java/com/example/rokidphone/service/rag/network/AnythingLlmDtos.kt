package com.example.rokidphone.service.rag.network

import com.google.gson.annotations.SerializedName

data class AnythingLlmHealthResponse(
    @SerializedName("online")
    val online: Boolean? = null,
)

data class AnythingLlmAuthResponse(
    @SerializedName("authenticated")
    val authenticated: Boolean? = null,
)

data class AnythingLlmWorkspace(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("slug")
    val slug: String? = null,
)

data class AnythingLlmWorkspacesResponse(
    @SerializedName("workspaces")
    val workspaces: List<AnythingLlmWorkspace> = emptyList(),
    @SerializedName("workspace")
    val workspace: List<AnythingLlmWorkspace> = emptyList(),
) {
    val allWorkspaces: List<AnythingLlmWorkspace>
        get() = if (workspaces.isNotEmpty()) workspaces else workspace
}

data class AnythingLlmWorkspaceLookupResponse(
    @SerializedName("workspace")
    val workspace: List<AnythingLlmWorkspace> = emptyList(),
) {
    val resolvedWorkspace: AnythingLlmWorkspace?
        get() = workspace.firstOrNull()
}

data class AnythingLlmChatRequest(
    @SerializedName("message")
    val message: String,
    @SerializedName("mode")
    val mode: String = "query",
)

data class AnythingLlmChatSource(
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("chunk")
    val chunk: String? = null,
)

data class AnythingLlmChatResponse(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("textResponse")
    val textResponse: String? = null,
    @SerializedName("sources")
    val sources: List<AnythingLlmChatSource> = emptyList(),
    @SerializedName("close")
    val close: Boolean? = null,
    @SerializedName("error")
    val error: String? = null,
)
