package com.example.rokidphone.service.ai

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiveToolDeclarationsTest {

    @Test
    fun `buildLiveToolDeclarations includes google search tool when enabled`() {
        val declarations = buildLiveToolDeclarations(
            googleSearchEnabled = true,
            liveRagDeclaration = null,
        )

        assertThat(declarations).hasSize(1)
        assertThat(declarations.single().has("googleSearch")).isTrue()
    }

    @Test
    fun `buildLiveToolDeclarations combines google search and live rag`() {
        val ragDeclaration = JSONObject("""{"function_declarations":[{"name":"search_docs"}]}""")

        val declarations = buildLiveToolDeclarations(
            googleSearchEnabled = true,
            liveRagDeclaration = ragDeclaration,
        )

        assertThat(declarations).hasSize(2)
        assertThat(declarations[0].has("googleSearch")).isTrue()
        assertThat(declarations[1].toString()).isEqualTo(ragDeclaration.toString())
    }

    @Test
    fun `buildLiveToolDeclarations returns empty list when all tools disabled`() {
        val declarations = buildLiveToolDeclarations(
            googleSearchEnabled = false,
            liveRagDeclaration = null,
        )

        assertThat(declarations).isEmpty()
    }
}
