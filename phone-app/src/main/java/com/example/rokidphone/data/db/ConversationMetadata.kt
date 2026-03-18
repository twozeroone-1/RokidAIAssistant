package com.example.rokidphone.data.db

import com.example.rokidphone.service.rag.ConversationRouteBadge
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val metadataJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
data class MessageSourcePreview(
    val title: String = "",
    val snippet: String = "",
)

@Serializable
data class ConversationMetadata(
    val routeBadge: ConversationRouteBadge? = null,
    val docsWorkspaceSlug: String? = null,
    val fallbackReason: String? = null,
)

@Serializable
data class MessageMetadata(
    val routeBadge: ConversationRouteBadge? = null,
    val docsWorkspaceSlug: String? = null,
    val fallbackReason: String? = null,
    val sourcePreviews: List<MessageSourcePreview> = emptyList(),
) {
    fun encode(): String = metadataJson.encodeToString(this)

    companion object {
        fun decode(raw: String?): MessageMetadata {
            if (raw.isNullOrBlank()) {
                return MessageMetadata()
            }
            return runCatching {
                metadataJson.decodeFromString<MessageMetadata>(raw)
            }.getOrDefault(MessageMetadata())
        }
    }
}

fun ConversationMetadata.encode(): String = metadataJson.encodeToString(this)

fun decodeConversationMetadata(raw: String?): ConversationMetadata {
    if (raw.isNullOrBlank()) {
        return ConversationMetadata()
    }
    return runCatching {
        metadataJson.decodeFromString<ConversationMetadata>(raw)
    }.getOrDefault(ConversationMetadata())
}
