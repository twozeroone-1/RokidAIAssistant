package com.example.rokidphone.service.rag

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocsFallbackEvaluatorTest {

    @Test
    fun `answer with sources does not fallback`() {
        val answer = RagAnswer(
            answerText = "Run the deployment check before releasing.",
            routeLabel = "AnythingLLM query",
            sources = listOf(
                SourcePreview(
                    title = "deploy.md",
                    snippet = "Run the deployment check before releasing.",
                )
            ),
        )

        val decision = DocsFallbackEvaluator.evaluateAnswer(answer)

        assertThat(decision.shouldFallback).isFalse()
        assertThat(decision.reason).isNull()
    }

    @Test
    fun `short answer with no sources and miss phrase falls back`() {
        val answer = RagAnswer(
            answerText = "I don't know from the available documents.",
            routeLabel = "AnythingLLM query",
            sources = emptyList(),
        )

        val decision = DocsFallbackEvaluator.evaluateAnswer(answer)

        assertThat(decision.shouldFallback).isTrue()
        assertThat(decision.reason).contains("Docs")
    }

    @Test
    fun `substantive answer with no sources stays on docs`() {
        val answer = RagAnswer(
            answerText = "Restart the device, confirm the workspace service is online, then retry the sync flow from the settings screen.",
            routeLabel = "AnythingLLM query",
            sources = emptyList(),
        )

        val decision = DocsFallbackEvaluator.evaluateAnswer(answer)

        assertThat(decision.shouldFallback).isFalse()
        assertThat(decision.reason).isNull()
    }

    @Test
    fun `failure always triggers fallback`() {
        val decision = DocsFallbackEvaluator.fallbackForFailure("AnythingLLM answer failed: HTTP 504 Gateway Timeout")

        assertThat(decision.shouldFallback).isTrue()
        assertThat(decision.reason).contains("General AI")
    }
}
