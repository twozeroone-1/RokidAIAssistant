package com.example.rokidphone.service.ai

/**
 * In-memory Gemini API key pool with round-robin selection and cooldown handling.
 */
class GeminiKeyPool(
    keys: List<String>,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        internal const val QUOTA_COOLDOWN_MS = 5 * 60 * 1000L
        internal const val INVALID_KEY_COOLDOWN_MS = 60 * 60 * 1000L
    }

    private val keys = keys
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    private val cooldownUntil = mutableMapOf<String, Long>()
    private var nextIndex = 0

    fun nextCandidate(): String? {
        if (keys.isEmpty()) {
            return null
        }

        val now = clock()
        repeat(keys.size) { offset ->
            val index = (nextIndex + offset) % keys.size
            val candidate = keys[index]
            val unavailableUntil = cooldownUntil[candidate] ?: 0L
            if (unavailableUntil <= now) {
                return candidate
            }
        }
        return null
    }

    fun markSuccess(key: String) {
        cooldownUntil.remove(key)
        advanceToNext(key)
    }

    fun markQuotaFailure(key: String) {
        cooldownUntil[key] = clock() + QUOTA_COOLDOWN_MS
        advanceToNext(key)
    }

    fun markInvalidKey(key: String) {
        cooldownUntil[key] = clock() + INVALID_KEY_COOLDOWN_MS
        advanceToNext(key)
    }

    private fun advanceToNext(key: String) {
        val keyIndex = keys.indexOf(key)
        if (keyIndex >= 0 && keys.isNotEmpty()) {
            nextIndex = (keyIndex + 1) % keys.size
        }
    }
}
