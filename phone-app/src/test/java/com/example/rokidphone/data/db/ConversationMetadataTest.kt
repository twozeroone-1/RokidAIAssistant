package com.example.rokidphone.data.db

import com.example.rokidphone.service.rag.ConversationRouteBadge
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConversationMetadataTest {

    @Test
    fun `message metadata round trips route badge fallback reason and sources`() {
        val metadata = MessageMetadata(
            routeBadge = ConversationRouteBadge.GENERAL_FALLBACK,
            fallbackReason = "Slow profile forced a general AI fallback.",
            docsWorkspaceSlug = "ops-docs",
            sourcePreviews = listOf(
                MessageSourcePreview(
                    title = "deploy.md",
                    snippet = "Run the deployment check before releasing.",
                )
            ),
        )

        val encoded = metadata.encode()
        val decoded = MessageMetadata.decode(encoded)

        assertThat(decoded.routeBadge).isEqualTo(ConversationRouteBadge.GENERAL_FALLBACK)
        assertThat(decoded.fallbackReason).contains("Slow profile")
        assertThat(decoded.docsWorkspaceSlug).isEqualTo("ops-docs")
        assertThat(decoded.sourcePreviews).hasSize(1)
    }
}
