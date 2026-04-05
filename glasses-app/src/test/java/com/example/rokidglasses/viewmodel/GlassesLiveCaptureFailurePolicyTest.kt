package com.example.rokidglasses.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.CancellationException

class GlassesLiveCaptureFailurePolicyTest {

    @Test
    fun `treats coroutine cancellation as expected stop`() {
        val cancellation = CancellationException("cancelled")

        assertThat(GlassesLiveCaptureFailurePolicy.isExpectedStop(cancellation)).isEqualTo(true)
    }

    @Test
    fun `treats regular exceptions as real failures`() {
        assertThat(
            GlassesLiveCaptureFailurePolicy.isExpectedStop(IllegalStateException("boom"))
        ).isEqualTo(false)
    }
}
