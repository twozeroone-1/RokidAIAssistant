package com.example.rokidglasses.viewmodel

import java.util.concurrent.CancellationException

internal object GlassesLiveCaptureFailurePolicy {
    fun isExpectedStop(error: Throwable): Boolean = error is CancellationException
}
