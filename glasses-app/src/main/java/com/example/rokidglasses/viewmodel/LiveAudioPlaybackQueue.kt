package com.example.rokidglasses.viewmodel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class LiveAudioPlaybackQueue(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val onWriteChunk: suspend (ByteArray) -> Unit,
) {
    private var playbackChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var workerJob: Job? = null

    fun enqueue(audioData: ByteArray) {
        val queued = playbackChannel.trySend(audioData.copyOf())
        if (!queued.isSuccess) {
            return
        }
        ensureWorker()
    }

    fun stop() {
        val oldChannel = playbackChannel
        playbackChannel = Channel(Channel.UNLIMITED)
        workerJob?.cancel()
        workerJob = null
        oldChannel.cancel()
    }

    private fun ensureWorker() {
        if (workerJob?.isActive == true) {
            return
        }

        val activeChannel = playbackChannel
        workerJob = scope.launch(dispatcher) {
            for (chunk in activeChannel) {
                onWriteChunk(chunk)
            }
        }
    }
}
