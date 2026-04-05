package com.example.rokidcommon.protocol

enum class LiveControlInputSource {
    UNKNOWN,
    PHONE,
    GLASSES;

    companion object {
        fun fromRaw(raw: String?): LiveControlInputSource {
            if (raw.isNullOrBlank()) {
                return UNKNOWN
            }
            return entries.find { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

data class LiveSessionControlPayload(
    val sessionActive: Boolean,
    val liveModeEnabled: Boolean,
    val effectiveInputSource: LiveControlInputSource = LiveControlInputSource.UNKNOWN,
    val cameraMode: String? = null,
    val cameraIntervalSec: Int? = null,
    val liveRagEnabled: Boolean = false,
    val ragDisplayMode: LiveRagDisplayMode = LiveRagDisplayMode.RAG_RESULT_ONLY,
    val splitScrollMode: LiveRagSplitScrollMode = LiveRagSplitScrollMode.AUTO,
    val autoScrollSpeedLevel: Int = 2,
    val experimentalLiveMicTuningEnabled: Boolean = false,
    val experimentalLiveMicProfile: Int = 0,
) {
    val canToggleFromGlasses: Boolean
        get() = liveModeEnabled

    fun toPayloadString(): String {
        return buildString {
            append('{')
            append("\"sessionActive\":")
            append(if (sessionActive) "true" else "false")
            append(",\"liveModeEnabled\":")
            append(if (liveModeEnabled) "true" else "false")
            append(",\"effectiveInputSource\":\"")
            append(escapeLiveControlJson(effectiveInputSource.name))
            append('"')
            cameraMode?.let {
                append(",\"cameraMode\":\"")
                append(escapeLiveControlJson(it))
                append('"')
            }
            cameraIntervalSec?.let {
                append(",\"cameraIntervalSec\":")
                append(it)
            }
            append(",\"liveRagEnabled\":")
            append(if (liveRagEnabled) "true" else "false")
            append(",\"ragDisplayMode\":\"")
            append(escapeLiveControlJson(ragDisplayMode.name))
            append('"')
            append(",\"splitScrollMode\":\"")
            append(escapeLiveControlJson(splitScrollMode.name))
            append('"')
            append(",\"autoScrollSpeedLevel\":")
            append(autoScrollSpeedLevel)
            append(",\"experimentalLiveMicTuningEnabled\":")
            append(if (experimentalLiveMicTuningEnabled) "true" else "false")
            append(",\"experimentalLiveMicProfile\":")
            append(experimentalLiveMicProfile)
            append('}')
        }
    }

    companion object {
        fun fromPayload(raw: String?): LiveSessionControlPayload? {
            if (raw.isNullOrBlank()) {
                return null
            }

            return LiveSessionControlPayload(
                sessionActive = SESSION_ACTIVE_REGEX.find(raw)?.groupValues?.getOrNull(1)
                    .equals("true", ignoreCase = true),
                liveModeEnabled = LIVE_MODE_ENABLED_REGEX.find(raw)?.groupValues?.getOrNull(1)
                    .equals("true", ignoreCase = true),
                effectiveInputSource = LiveControlInputSource.fromRaw(
                    INPUT_SOURCE_REGEX.find(raw)?.groupValues?.getOrNull(1)?.let(::unescapeLiveControlJson)
                ),
                cameraMode = CAMERA_MODE_REGEX.find(raw)?.groupValues?.getOrNull(1)
                    ?.let(::unescapeLiveControlJson),
                cameraIntervalSec = CAMERA_INTERVAL_REGEX.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                liveRagEnabled = LIVE_RAG_ENABLED_REGEX.find(raw)?.groupValues?.getOrNull(1)
                    .equals("true", ignoreCase = true),
                ragDisplayMode = LiveRagDisplayMode.fromRaw(
                    RAG_DISPLAY_MODE_REGEX.find(raw)?.groupValues?.getOrNull(1)?.let(::unescapeLiveControlJson)
                ),
                splitScrollMode = LiveRagSplitScrollMode.fromRaw(
                    SPLIT_SCROLL_MODE_REGEX.find(raw)?.groupValues?.getOrNull(1)?.let(::unescapeLiveControlJson)
                ),
                autoScrollSpeedLevel = AUTO_SCROLL_SPEED_LEVEL_REGEX.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: 2,
                experimentalLiveMicTuningEnabled = EXPERIMENTAL_LIVE_MIC_TUNING_ENABLED_REGEX.find(raw)
                    ?.groupValues?.getOrNull(1)
                    .equals("true", ignoreCase = true),
                experimentalLiveMicProfile = EXPERIMENTAL_LIVE_MIC_PROFILE_REGEX.find(raw)?.groupValues?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 0,
            )
        }

        private val SESSION_ACTIVE_REGEX = Regex(""""sessionActive":(true|false)""")
        private val LIVE_MODE_ENABLED_REGEX = Regex(""""liveModeEnabled":(true|false)""")
        private val INPUT_SOURCE_REGEX = Regex(""""effectiveInputSource":"((?:\\\\.|[^"])*)"""")
        private val CAMERA_MODE_REGEX = Regex(""""cameraMode":"((?:\\\\.|[^"])*)"""")
        private val CAMERA_INTERVAL_REGEX = Regex(""""cameraIntervalSec":(-?\d+)""")
        private val LIVE_RAG_ENABLED_REGEX = Regex(""""liveRagEnabled":(true|false)""")
        private val RAG_DISPLAY_MODE_REGEX = Regex(""""ragDisplayMode":"((?:\\\\.|[^"])*)"""")
        private val SPLIT_SCROLL_MODE_REGEX = Regex(""""splitScrollMode":"((?:\\\\.|[^"])*)"""")
        private val AUTO_SCROLL_SPEED_LEVEL_REGEX = Regex(""""autoScrollSpeedLevel":(-?\d+)""")
        private val EXPERIMENTAL_LIVE_MIC_TUNING_ENABLED_REGEX =
            Regex(""""experimentalLiveMicTuningEnabled":(true|false)""")
        private val EXPERIMENTAL_LIVE_MIC_PROFILE_REGEX =
            Regex(""""experimentalLiveMicProfile":(-?\d+)""")
    }
}

private fun escapeLiveControlJson(raw: String): String {
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

private fun unescapeLiveControlJson(raw: String): String {
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
