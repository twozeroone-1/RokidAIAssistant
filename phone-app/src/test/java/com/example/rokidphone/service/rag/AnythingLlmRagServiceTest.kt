package com.example.rokidphone.service.rag

import com.example.rokidphone.data.AnythingLlmQueryMode
import com.example.rokidphone.data.AnythingLlmSettings
import com.example.rokidphone.testutil.MockWebServerRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okhttp3.Headers.Companion.headersOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnythingLlmRagServiceTest {

    @get:Rule
    val serverRule = MockWebServerRule()

    private fun createSettings() = AnythingLlmSettings(
        serverUrl = serverRule.baseUrl.removeSuffix("/"),
        apiKey = "anythingllm-secret",
        workspaceSlug = "ops-docs",
        queryMode = AnythingLlmQueryMode.QUERY,
    )

    private fun jsonResponse(body: String, code: Int = 200) = MockResponse(
        code = code,
        body = body,
        headers = headersOf("Content-Type", "application/json"),
    )

    @Test
    fun `check health validates ping auth and current workspaces response`() = runTest {
        serverRule.server.enqueue(jsonResponse("""{"online":true}"""))
        serverRule.server.enqueue(jsonResponse("""{"authenticated":true}"""))
        serverRule.server.enqueue(
            jsonResponse("""{"workspaces":[{"name":"Ops Docs","slug":"ops-docs"}]}""")
        )

        val result = AnythingLlmRagService().checkHealth(createSettings()).getOrThrow()

        assertThat(result.message).contains("Ops Docs")
        assertThat(result.message).contains("online")
    }

    @Test
    fun `check health falls back to current workspace lookup response`() = runTest {
        serverRule.server.enqueue(jsonResponse("""{"online":true}"""))
        serverRule.server.enqueue(jsonResponse("""{"authenticated":true}"""))
        serverRule.server.enqueue(jsonResponse("""{"workspaces":[]}"""))
        serverRule.server.enqueue(
            jsonResponse("""{"workspace":[{"name":"Ops Docs","slug":"ops-docs"}]}""")
        )

        val result = AnythingLlmRagService().checkHealth(createSettings()).getOrThrow()

        assertThat(result.message).contains("Ops Docs")
        assertThat(result.message).contains("available")
    }

    @Test
    fun `answer maps text response and sources`() = runTest {
        serverRule.server.enqueue(
            jsonResponse(
                """
                {
                  "id":"chat-1",
                  "type":"textResponse",
                  "textResponse":"Found the answer in docs.",
                  "sources":[
                    {
                      "title":"deploy.md",
                      "chunk":"Run the deployment check before releasing."
                    }
                  ],
                  "close":true,
                  "error":null
                }
                """.trimIndent()
            )
        )

        val result = AnythingLlmRagService().answer(createSettings(), "How do I deploy?").getOrThrow()

        assertThat(result.answerText).isEqualTo("Found the answer in docs.")
        assertThat(result.sources).hasSize(1)
        assertThat(result.sources.first().title).isEqualTo("deploy.md")
        assertThat(result.sources.first().snippet).contains("deployment check")
    }
}
