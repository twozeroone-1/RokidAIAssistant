package com.example.rokidphone.service.ai

import org.json.JSONObject

internal fun buildLiveToolDeclarations(
    googleSearchEnabled: Boolean,
    liveRagDeclaration: JSONObject?,
): List<JSONObject> {
    val declarations = mutableListOf<JSONObject>()
    if (googleSearchEnabled) {
        declarations += googleSearchDeclaration()
    }
    if (liveRagDeclaration != null) {
        declarations += liveRagDeclaration
    }
    return declarations
}

internal fun googleSearchDeclaration(): JSONObject {
    return JSONObject().apply {
        put("googleSearch", JSONObject())
    }
}
