package com.example.rokidcommon.protocol

enum class LiveTranscriptRole {
    USER,
    ASSISTANT,
    THINKING,
    RAG;

    companion object {
        fun fromRaw(raw: String?): LiveTranscriptRole? {
            if (raw.isNullOrBlank()) {
                return null
            }
            return entries.find { it.name.equals(raw, ignoreCase = true) }
        }
    }
}

data class LiveTranscriptPayload(
    val role: LiveTranscriptRole,
    val text: String,
    val isFinal: Boolean = false,
) {
    fun toPayloadString(): String {
        return buildString {
            append('{')
            append("\"role\":\"")
            append(escapeJson(role.name))
            append("\",\"text\":\"")
            append(escapeJson(text))
            append("\",\"isFinal\":")
            append(if (isFinal) "true" else "false")
            append('}')
        }
    }

    companion object {
        fun fromPayload(raw: String?): LiveTranscriptPayload? {
            if (raw.isNullOrBlank()) {
                return null
            }

            val roleMatch = ROLE_REGEX.find(raw)?.groupValues?.getOrNull(1)
            val textMatch = TEXT_REGEX.find(raw)?.groupValues?.getOrNull(1)
            val isFinalMatch = FINAL_REGEX.find(raw)?.groupValues?.getOrNull(1)

            val role = LiveTranscriptRole.fromRaw(roleMatch) ?: return null
            val text = textMatch?.let(::unescapeJson) ?: return null

            return LiveTranscriptPayload(
                role = role,
                text = text,
                isFinal = isFinalMatch.equals("true", ignoreCase = true),
            )
        }

        private val ROLE_REGEX = Regex(""""role":"([^"]+)"""")
        private val TEXT_REGEX = Regex(""""text":"((?:\\\\.|[^"])*)"""")
        private val FINAL_REGEX = Regex(""""isFinal":(true|false)""")
    }
}

private fun escapeJson(raw: String): String {
    return buildString {
        raw.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}

private fun unescapeJson(raw: String): String {
    return buildString {
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            if (char == '\\' && index + 1 < raw.length) {
                when (val escaped = raw[index + 1]) {
                    '\\' -> append('\\')
                    '"' -> append('"')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> append(escaped)
                }
                index += 2
            } else {
                append(char)
                index += 1
            }
        }
    }
}
