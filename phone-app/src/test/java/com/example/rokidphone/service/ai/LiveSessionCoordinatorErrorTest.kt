package com.example.rokidphone.service.ai

import com.example.rokidphone.data.ApiSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveSessionCoordinatorErrorTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    private lateinit var fakeSession: FakeLiveSessionClient
    private lateinit var coordinator: LiveSessionCoordinator

    @Before
    fun setUp() {
        fakeSession = FakeLiveSessionClient()
        coordinator = LiveSessionCoordinator(
            scope = scope,
            sessionFactory = { fakeSession }
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `session error message is forwarded`() {
        coordinator.sync(
            ApiSettings(
                liveModeEnabled = true,
                geminiApiKey = "test-live-key",
            ),
            glassesConnected = false
        )
        scope.advanceUntilIdle()

        coordinator.onSessionFailure(
            apiKey = "test-live-key",
            errorMessage = "API key not valid. Please pass a valid API key."
        )
        scope.advanceUntilIdle()

        assertThat(coordinator.errorMessage.value)
            .isEqualTo("API key not valid. Please pass a valid API key.")
    }

    private class FakeLiveSessionClient : LiveSessionClient {
        val mutableState = MutableStateFlow(GeminiLiveSession.SessionState.CONNECTING)
        val mutableError = MutableStateFlow<String?>(null)
        private val mutableInput = MutableSharedFlow<String>()
        private val mutableOutput = MutableSharedFlow<String>()
        private val mutableThoughtSummary = MutableSharedFlow<String>()
        private val mutableAudio = MutableSharedFlow<ByteArray>()
        private val mutableUsageMetadata =
            MutableSharedFlow<LiveUsageMetadata>(replay = 1, extraBufferCapacity = 1)
        private val mutableTurnComplete = MutableSharedFlow<Unit>()
        private val mutableInterrupted = MutableSharedFlow<Unit>()
        private val mutableSessionResumptionUpdates = MutableSharedFlow<LiveSessionResumptionUpdate>()
        private val mutableGoAwayNotices = MutableSharedFlow<LiveGoAwayNotice>()

        override val sessionState: StateFlow<GeminiLiveSession.SessionState> = mutableState.asStateFlow()
        override val errorMessage: StateFlow<String?> = mutableError.asStateFlow()
        override val inputTranscription: SharedFlow<String> = mutableInput.asSharedFlow()
        override val outputTranscription: SharedFlow<String> = mutableOutput.asSharedFlow()
        override val thoughtSummary: SharedFlow<String> = mutableThoughtSummary.asSharedFlow()
        override val outputAudio: SharedFlow<ByteArray> = mutableAudio.asSharedFlow()
        override val usageMetadata: SharedFlow<LiveUsageMetadata> = mutableUsageMetadata.asSharedFlow()
        override val turnComplete: SharedFlow<Unit> = mutableTurnComplete.asSharedFlow()
        override val interrupted: SharedFlow<Unit> = mutableInterrupted.asSharedFlow()
        override val sessionResumptionUpdates: SharedFlow<LiveSessionResumptionUpdate> =
            mutableSessionResumptionUpdates.asSharedFlow()
        override val goAwayNotices: SharedFlow<LiveGoAwayNotice> = mutableGoAwayNotices.asSharedFlow()

        override fun start(
            vadConfig: GeminiLiveSession.VadConfig,
            tools: List<JSONObject>?
        ): Boolean = true

        override fun sendAudioChunk(audioData: ByteArray) = Unit

        override fun sendVideoFrame(jpegData: ByteArray) = Unit

        override fun endOfTurn() = Unit

        override fun release() = Unit
    }
}
