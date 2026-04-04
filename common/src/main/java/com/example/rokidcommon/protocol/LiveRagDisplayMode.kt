package com.example.rokidcommon.protocol

enum class LiveRagDisplayMode {
    RAG_RESULT_ONLY,
    SPLIT_LIVE_AND_RAG;

    companion object {
        fun fromRaw(raw: String?): LiveRagDisplayMode {
            if (raw.isNullOrBlank()) {
                return RAG_RESULT_ONLY
            }
            return entries.find { it.name.equals(raw, ignoreCase = true) } ?: RAG_RESULT_ONLY
        }
    }
}

fun resolveEffectiveLiveRagDisplayMode(
    liveRagEnabled: Boolean,
    configuredMode: LiveRagDisplayMode,
): LiveRagDisplayMode {
    return if (liveRagEnabled) {
        configuredMode
    } else {
        LiveRagDisplayMode.RAG_RESULT_ONLY
    }
}
