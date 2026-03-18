package com.example.rokidphone.service.rag

class InputNormalizer {

    fun normalizeText(text: String): String = text.trim()

    fun combinePhotoContext(
        sceneDescription: String,
        userQuestion: String? = null,
    ): String {
        val trimmedQuestion = userQuestion?.trim().orEmpty()
        val trimmedSceneDescription = sceneDescription.trim()
        return buildString {
            if (trimmedQuestion.isNotBlank()) {
                append(trimmedQuestion)
                append("\n\n")
            }
            append("Use the following scene description to answer with documentation-grounded guidance:\n")
            append(trimmedSceneDescription)
        }.trim()
    }

    fun buildSourceSummary(
        sources: List<SourcePreview>,
        limit: Int = 2,
    ): String? {
        val summary = sources
            .mapNotNull { it.title.takeIf(String::isNotBlank) }
            .distinct()
            .take(limit)
        if (summary.isEmpty()) {
            return null
        }
        return "Sources: ${summary.joinToString(", ")}"
    }
}
