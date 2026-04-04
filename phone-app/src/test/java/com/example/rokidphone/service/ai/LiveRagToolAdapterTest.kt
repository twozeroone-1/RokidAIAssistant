package com.example.rokidphone.service.ai

import com.example.rokidphone.data.AnythingLlmQueryMode
import com.example.rokidphone.data.AnythingLlmSettings
import com.example.rokidphone.service.rag.RagAnswer
import com.example.rokidphone.service.rag.RagHealthResult
import com.example.rokidphone.service.rag.RagService
import com.example.rokidphone.service.rag.SourcePreview
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LiveRagToolAdapterTest {

    private val settings = AnythingLlmSettings(
        serverUrl = "https://docs.local",
        apiKey = "docs-key",
        workspaceSlug = "manuals",
        runtimeEnabled = true,
        queryMode = AnythingLlmQueryMode.QUERY,
    )

    @Test
    fun `declaration exposes search_docs function`() {
        val declaration = LiveRagToolAdapter.declaration()
        val function = declaration.getJSONArray("function_declarations").getJSONObject(0)

        assertThat(function.getString("name")).isEqualTo(LiveRagToolAdapter.FUNCTION_NAME)
    }

    @Test
    fun `execute returns rag answer and sources`() = runTest {
        val adapter = LiveRagToolAdapter(
            ragService = FakeRagService(
                result = Result.success(
                    RagAnswer(
                        answerText = "Hold the power button for 3 seconds.",
                        routeLabel = "AnythingLLM query",
                        sources = listOf(
                            SourcePreview(
                                title = "Quick Start",
                                snippet = "Press and hold the power button."
                            )
                        ),
                        rawSourceCount = 1
                    )
                )
            )
        )

        val result = adapter.execute(
            call = GeminiFunctionCall(
                id = "tool-1",
                name = LiveRagToolAdapter.FUNCTION_NAME,
                args = JSONObject().put("query", "How do I power on the glasses?")
            ),
            settings = settings
        )

        assertThat(result.success).isTrue()
        assertThat(result.result.getString("answer"))
            .isEqualTo("Hold the power button for 3 seconds.")
        assertThat(result.result.getJSONArray("sources").length()).isEqualTo(1)
    }

    @Test
    fun `execute fails when query is blank`() = runTest {
        val adapter = LiveRagToolAdapter(ragService = FakeRagService(Result.failure(IllegalStateException())))

        val result = adapter.execute(
            call = GeminiFunctionCall(
                id = "tool-2",
                name = LiveRagToolAdapter.FUNCTION_NAME,
                args = JSONObject()
            ),
            settings = settings
        )

        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("query")
    }

    private class FakeRagService(
        private val result: Result<RagAnswer>
    ) : RagService {
        override suspend fun checkHealth(settings: AnythingLlmSettings): Result<RagHealthResult> {
            error("Not needed in test")
        }

        override suspend fun answer(settings: AnythingLlmSettings, question: String): Result<RagAnswer> {
            return result
        }
    }
}
