package com.example.rokidphone.service.ai

internal object LiveTranscriptAccumulator {

    fun merge(current: String, incoming: String): String {
        val previous = current.trim()
        val next = incoming.trim()
        if (next.isBlank()) {
            return previous
        }

        if (previous.isBlank()) {
            return next
        }

        if (next == previous || previous.endsWith(next)) {
            return previous
        }

        if (next.startsWith(previous)) {
            return next
        }

        val overlapLength = longestOverlapSuffixPrefix(previous, next)
        if (overlapLength > 0) {
            return normalizeSpacing(previous + next.drop(overlapLength))
        }

        return normalizeSpacing(previous + separator(previous, incoming, next) + next)
    }

    private fun longestOverlapSuffixPrefix(previous: String, next: String): Int {
        val maxOverlap = minOf(previous.length, next.length)
        for (length in maxOverlap downTo 2) {
            if (previous.takeLast(length) == next.take(length)) {
                return length
            }
        }
        return 0
    }

    private fun separator(previous: String, incoming: String, next: String): String {
        val previousLast = previous.last()
        val nextFirst = next.first()

        if (incoming.firstOrNull()?.isWhitespace() == true) {
            return " "
        }

        if (previousLast.isWhitespace() || nextFirst.isWhitespace()) {
            return ""
        }

        if (previousLast.isDigit() && nextFirst.isDigit()) {
            return " "
        }

        if (previousLast.isLetter() && nextFirst.isDigit()) {
            return " "
        }

        if (isSentenceTerminal(previousLast) && isWordLike(nextFirst)) {
            return " "
        }

        return ""
    }

    private fun normalizeSpacing(text: String): String {
        return text
            .replace(Regex(" {2,}"), " ")
            .replace(Regex(" ([,.;:!?])"), "$1")
            .trim()
    }

    private fun isSentenceTerminal(char: Char): Boolean {
        return char == '.' || char == '!' || char == '?' || char == '…'
    }

    private fun isWordLike(char: Char): Boolean {
        return char.isLetterOrDigit()
    }
}
