package com.example.rokidcommon.protocol

enum class LiveRagSplitScrollMode {
    AUTO,
    MANUAL;

    companion object {
        fun fromRaw(raw: String?): LiveRagSplitScrollMode {
            if (raw.isNullOrBlank()) {
                return MANUAL
            }
            return entries.find { it.name.equals(raw, ignoreCase = true) } ?: MANUAL
        }
    }
}
