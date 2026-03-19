package com.example.rokidcommon.protocol

import com.google.gson.Gson
import com.google.gson.JsonParser

data class RemoteKeySettingsPayload(
    val recordKeyCode: Int? = null,
    val cameraKeyCode: Int? = null,
)

data class RemoteKeyLearningRequestPayload(
    val target: String,
)

data class RemoteKeyLearningResultPayload(
    val target: String,
    val keyCode: Int,
)

object RemoteKeyProtocol {
    private val gson = Gson()

    fun encodeSettingsSync(recordKeyCode: Int?, cameraKeyCode: Int?): String {
        return gson.toJson(
            RemoteKeySettingsPayload(
                recordKeyCode = recordKeyCode,
                cameraKeyCode = cameraKeyCode,
            )
        )
    }

    fun decodeSettingsSync(payload: String?): RemoteKeySettingsPayload? {
        if (payload.isNullOrBlank()) {
            return null
        }
        return runCatching {
            gson.fromJson(payload, RemoteKeySettingsPayload::class.java)
        }.getOrNull()
    }

    fun encodeLearningRequest(target: String): String {
        return gson.toJson(RemoteKeyLearningRequestPayload(target = target))
    }

    fun decodeLearningRequest(payload: String?): String? {
        if (payload.isNullOrBlank()) {
            return null
        }
        return runCatching {
            gson.fromJson(payload, RemoteKeyLearningRequestPayload::class.java).target
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun encodeLearningResult(target: String, keyCode: Int): String {
        return gson.toJson(
            RemoteKeyLearningResultPayload(
                target = target,
                keyCode = keyCode,
            )
        )
    }

    fun decodeLearningResult(payload: String?): RemoteKeyLearningResultPayload? {
        if (payload.isNullOrBlank()) {
            return null
        }
        return runCatching {
            val jsonObject = JsonParser.parseString(payload).asJsonObject
            if (!jsonObject.has("target") || !jsonObject.has("keyCode")) {
                return null
            }
            gson.fromJson(payload, RemoteKeyLearningResultPayload::class.java)
        }.getOrNull()?.takeIf { it.target.isNotBlank() }
    }
}
