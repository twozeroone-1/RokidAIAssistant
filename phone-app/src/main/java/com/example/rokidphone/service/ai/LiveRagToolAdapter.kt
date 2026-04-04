package com.example.rokidphone.service.ai

import com.example.rokidphone.data.AnythingLlmSettings
import com.example.rokidphone.service.rag.AnythingLlmRagService
import com.example.rokidphone.service.rag.RagService
import org.json.JSONArray
import org.json.JSONObject

class LiveRagToolAdapter(
    private val ragService: RagService = AnythingLlmRagService()
) {
    companion object {
        const val FUNCTION_NAME = "search_docs"

        fun declaration(): JSONObject {
            return JSONObject().apply {
                put("function_declarations", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", FUNCTION_NAME)
                        put(
                            "description",
                            "Search the user's indexed manuals and docs. Use this for product usage, troubleshooting, and setup questions."
                        )
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("query", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "The documentation question or search query.")
                                })
                            })
                            put("required", JSONArray().put("query"))
                        })
                    })
                })
            }
        }
    }

    suspend fun execute(
        call: GeminiFunctionCall,
        settings: AnythingLlmSettings
    ): ToolResult {
        val query = call.args.optString("query").trim()
        if (query.isBlank()) {
            return ToolResult.failure(call.id, "search_docs requires a non-empty query.")
        }

        val answer = ragService.answer(settings, query).getOrElse { error ->
            return ToolResult.failure(call.id, error.message ?: "Docs search failed.")
        }

        return ToolResult.success(
            call.id,
            JSONObject().apply {
                put("answer", answer.answerText)
                put("route", answer.routeLabel)
                put("source_count", answer.rawSourceCount)
                put("sources", JSONArray().apply {
                    answer.sources.forEach { source ->
                        put(JSONObject().apply {
                            put("title", source.title)
                            put("snippet", source.snippet)
                        })
                    }
                })
            }
        )
    }
}
