package com.example.rokidphone.service.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveSessionFailurePolicyTest {

    @Test
    fun `aborted close reason is treated as resumable connection close`() {
        assertThat(isResumableConnectionTermination("The operation was aborted.")).isTrue()
    }

    @Test
    fun `quota failure is not treated as resumable connection close`() {
        assertThat(isResumableConnectionTermination("You exceeded your current quota.")).isFalse()
    }

    @Test
    fun `aborted close reason is not classified as quota or invalid key`() {
        assertThat(classifyLiveSessionFailure("The operation was aborted.")).isNull()
    }
}
