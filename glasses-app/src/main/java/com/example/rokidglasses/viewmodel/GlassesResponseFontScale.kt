package com.example.rokidglasses.viewmodel

import kotlin.math.roundToInt

internal const val MIN_RESPONSE_FONT_SCALE_PERCENT = 50
internal const val MAX_RESPONSE_FONT_SCALE_PERCENT = 140
internal const val DEFAULT_RESPONSE_FONT_SCALE_PERCENT = 85
internal const val RESPONSE_FONT_SCALE_STEP_PERCENT = 5

internal data class DisplayTextRenderState(
    val text: String,
    val fontScalePercent: Int,
    val useResponseFontScale: Boolean,
    val isPaginated: Boolean,
)

internal fun clampResponseFontScalePercent(value: Int): Int {
    return value.coerceIn(MIN_RESPONSE_FONT_SCALE_PERCENT, MAX_RESPONSE_FONT_SCALE_PERCENT)
}

internal fun snapResponseFontScalePercent(value: Int): Int {
    val clampedValue = clampResponseFontScalePercent(value)
    val offset = clampedValue - MIN_RESPONSE_FONT_SCALE_PERCENT
    val snappedOffset = ((offset.toFloat() / RESPONSE_FONT_SCALE_STEP_PERCENT).roundToInt()) *
        RESPONSE_FONT_SCALE_STEP_PERCENT
    return clampResponseFontScalePercent(MIN_RESPONSE_FONT_SCALE_PERCENT + snappedOffset)
}

internal fun responseFontScaleMultiplier(value: Int): Float {
    return snapResponseFontScalePercent(value) / 100f
}

internal fun resolveDisplayTextRenderState(
    text: String,
    responseFontScalePercent: Int,
    useResponseFontScale: Boolean,
    isPaginated: Boolean,
): DisplayTextRenderState {
    return DisplayTextRenderState(
        text = text,
        fontScalePercent = if (useResponseFontScale) {
            snapResponseFontScalePercent(responseFontScalePercent)
        } else {
            DEFAULT_RESPONSE_FONT_SCALE_PERCENT
        },
        useResponseFontScale = useResponseFontScale,
        isPaginated = isPaginated,
    )
}

internal fun effectiveMaxCharsPerPage(baseMaxCharsPerPage: Int, fontScalePercent: Int): Int {
    val clampedScale = snapResponseFontScalePercent(fontScalePercent)
    return (baseMaxCharsPerPage * 100f / clampedScale).roundToInt().coerceAtLeast(1)
}

internal fun paginateResponseText(
    text: String,
    fontScalePercent: Int,
    baseMaxCharsPerPage: Int = 120,
    maxLinesPerPage: Int = 4
): List<String> {
    val maxCharsPerPage = effectiveMaxCharsPerPage(baseMaxCharsPerPage, fontScalePercent)
    if (text.length <= maxCharsPerPage) {
        return listOf(text)
    }

    val pages = mutableListOf<String>()
    val words = text.split(" ", "，", "。", "、", "！", "？")
    var currentPage = StringBuilder()
    var lineCount = 0
    var charCount = 0

    for (word in words) {
        val wordWithSpace = if (currentPage.isEmpty()) word else " $word"
        val newCharCount = charCount + wordWithSpace.length

        if (newCharCount > maxCharsPerPage || lineCount >= maxLinesPerPage) {
            if (currentPage.isNotEmpty()) {
                pages.add(currentPage.toString().trim())
                currentPage = StringBuilder()
                charCount = 0
                lineCount = 0
            }
        }

        currentPage.append(wordWithSpace)
        charCount = currentPage.length

        if (word.contains("\n")) {
            lineCount += word.count { it == '\n' }
        }
    }

    if (currentPage.isNotEmpty()) {
        pages.add(currentPage.toString().trim())
    }

    if (pages.isEmpty() || (pages.size == 1 && text.length > maxCharsPerPage)) {
        pages.clear()
        var index = 0
        while (index < text.length) {
            val end = minOf(index + maxCharsPerPage, text.length)
            var breakPoint = end
            if (end < text.length) {
                val lastSpace = text.lastIndexOf(' ', end)
                val lastPunctuation = maxOf(
                    text.lastIndexOf('。', end),
                    text.lastIndexOf('，', end),
                    text.lastIndexOf('.', end),
                    text.lastIndexOf(',', end)
                )
                val naturalBreak = maxOf(lastSpace, lastPunctuation)
                if (naturalBreak > index) {
                    breakPoint = naturalBreak + 1
                }
            }
            pages.add(text.substring(index, breakPoint).trim())
            index = breakPoint
        }
    }

    return pages
}
