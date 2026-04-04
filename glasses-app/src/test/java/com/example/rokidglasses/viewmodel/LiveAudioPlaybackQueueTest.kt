package com.example.rokidglasses.viewmodel

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveAudioPlaybackQueueTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `queue writes chunks in order through a single active worker`() = scope.runTest {
        val started = mutableListOf<String>()
        val completed = mutableListOf<String>()
        val firstWriteGate = CompletableDeferred<Unit>()
        val queue = LiveAudioPlaybackQueue(
            scope = scope,
            dispatcher = dispatcher,
        ) { chunk: ByteArray ->
            val text = chunk.decodeToString()
            started += text
            if (text == "A") {
                firstWriteGate.await()
            }
            completed += text
        }

        queue.enqueue("A".encodeToByteArray())
        queue.enqueue("B".encodeToByteArray())
        advanceUntilIdle()

        assertThat(started).containsExactly("A")
        assertThat(completed).isEmpty()

        firstWriteGate.complete(Unit)
        advanceUntilIdle()

        assertThat(started).containsExactly("A", "B").inOrder()
        assertThat(completed).containsExactly("A", "B").inOrder()
        queue.stop()
    }

    @Test
    fun `stop clears queued chunks and allows fresh playback afterward`() = scope.runTest {
        val completed = mutableListOf<String>()
        val firstWriteGate = CompletableDeferred<Unit>()
        val queue = LiveAudioPlaybackQueue(
            scope = scope,
            dispatcher = dispatcher,
        ) { chunk: ByteArray ->
            val text = chunk.decodeToString()
            if (text == "A") {
                firstWriteGate.await()
            }
            completed += text
        }

        queue.enqueue("A".encodeToByteArray())
        queue.enqueue("B".encodeToByteArray())
        advanceUntilIdle()

        queue.stop()
        firstWriteGate.complete(Unit)
        advanceUntilIdle()

        queue.enqueue("C".encodeToByteArray())
        advanceUntilIdle()

        assertThat(completed).containsExactly("C")
        queue.stop()
    }
}
