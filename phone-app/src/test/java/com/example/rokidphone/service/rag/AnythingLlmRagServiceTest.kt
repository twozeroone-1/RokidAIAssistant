package com.example.rokidphone.service.rag

import com.example.rokidphone.data.AnythingLlmQueryMode
import com.example.rokidphone.data.AnythingLlmSettings
import com.example.rokidphone.testutil.MockWebServerRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import okhttp3.Headers.Companion.headersOf
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnythingLlmRagServiceTest {

    @get:Rule
    val serverRule = MockWebServerRule()

    private fun createSettings(
        alwaysNewSession: Boolean = true,
    ) = AnythingLlmSettings(
        serverUrl = serverRule.baseUrl.removeSuffix("/"),
        apiKey = "anythingllm-secret",
        workspaceSlug = "ops-docs",
        queryMode = AnythingLlmQueryMode.QUERY,
        alwaysNewSession = alwaysNewSession,
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

    @Test
    fun `answer includes a fresh session id by default`() = runTest {
        serverRule.server.enqueue(
            jsonResponse("""{"id":"chat-1","type":"textResponse","textResponse":"ok","sources":[],"close":true,"error":null}""")
        )

        AnythingLlmRagService().answer(createSettings(), "How do I deploy?").getOrThrow()

        val request = serverRule.server.takeRequest()
        val body = JSONObject(request.body.readUtf8())

        assertThat(request.path).isEqualTo("/api/v1/workspace/ops-docs/chat")
        assertThat(body.getString("message")).isEqualTo("How do I deploy?")
        assertThat(body.getString("mode")).isEqualTo("query")
        assertThat(body.getString("sessionId")).isNotEmpty()
    }

    @Test
    fun `answer uses a different session id for each request`() = runTest {
        repeat(2) {
            serverRule.server.enqueue(
                jsonResponse("""{"id":"chat-$it","type":"textResponse","textResponse":"ok","sources":[],"close":true,"error":null}""")
            )
        }

        val service = AnythingLlmRagService()

        service.answer(createSettings(), "First question").getOrThrow()
        service.answer(createSettings(), "Second question").getOrThrow()

        val firstBody = JSONObject(serverRule.server.takeRequest().body.readUtf8())
        val secondBody = JSONObject(serverRule.server.takeRequest().body.readUtf8())

        assertThat(firstBody.getString("sessionId")).isNotEmpty()
        assertThat(secondBody.getString("sessionId")).isNotEmpty()
        assertThat(firstBody.getString("sessionId")).isNotEqualTo(secondBody.getString("sessionId"))
    }

    @Test
    fun `answer omits session id when fresh sessions are disabled`() = runTest {
        serverRule.server.enqueue(
            jsonResponse("""{"id":"chat-1","type":"textResponse","textResponse":"ok","sources":[],"close":true,"error":null}""")
        )

        AnythingLlmRagService().answer(
            createSettings(alwaysNewSession = false),
            "How do I deploy?",
        ).getOrThrow()

        val body = JSONObject(serverRule.server.takeRequest().body.readUtf8())

        assertThat(body.has("sessionId")).isFalse()
    }
}
