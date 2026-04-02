package com.example.rokidphone.service.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeminiKeyPoolTest {

    @Test
    fun `nextCandidate rotates through healthy keys`() {
        val pool = GeminiKeyPool(listOf("k1", "k2", "k3"))

        assertThat(pool.nextCandidate()).isEqualTo("k1")
        pool.markSuccess("k1")

        assertThat(pool.nextCandidate()).isEqualTo("k2")
    }

    @Test
    fun `quota failure cools key down and advances`() {
        var now = 1_000L
        val pool = GeminiKeyPool(listOf("k1", "k2"), clock = { now })

        assertThat(pool.nextCandidate()).isEqualTo("k1")
        pool.markQuotaFailure("k1")

        assertThat(pool.nextCandidate()).isEqualTo("k2")
        pool.markSuccess("k2")

        now += GeminiKeyPool.QUOTA_COOLDOWN_MS
        assertThat(pool.nextCandidate()).isEqualTo("k1")
    }

    @Test
    fun `invalid key failure cools key down longer`() {
        var now = 5_000L
        val pool = GeminiKeyPool(listOf("k1", "k2"), clock = { now })

        assertThat(pool.nextCandidate()).isEqualTo("k1")
        pool.markInvalidKey("k1")

        now += GeminiKeyPool.QUOTA_COOLDOWN_MS
        assertThat(pool.nextCandidate()).isEqualTo("k2")
    }

    @Test
    fun `nextCandidate returns null when all keys are cooling down`() {
        val pool = GeminiKeyPool(listOf("k1", "k2"), clock = { 10_000L })

        pool.markQuotaFailure("k1")
        pool.markInvalidKey("k2")

        assertThat(pool.nextCandidate()).isNull()
    }
}
