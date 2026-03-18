package com.example.rokidphone.service.rag.network

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Path

interface AnythingLlmApi {
    @GET("api/ping")
    suspend fun ping(): AnythingLlmHealthResponse

    @GET("api/v1/auth")
    suspend fun auth(): AnythingLlmAuthResponse

    @GET("api/v1/workspaces")
    suspend fun workspaces(): AnythingLlmWorkspacesResponse

    @GET("api/v1/workspace/{slug}")
    suspend fun workspace(@Path("slug") slug: String): AnythingLlmWorkspaceLookupResponse

    @POST("api/v1/workspace/{slug}/chat")
    suspend fun chat(
        @Path("slug") slug: String,
        @Body request: AnythingLlmChatRequest,
    ): AnythingLlmChatResponse
}
