package com.example.rokidphone.service.ai

import com.example.rokidphone.data.ApiSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
import java.util.ArrayDeque

@OptIn(ExperimentalCoroutinesApi::class)
class LiveSessionCoordinatorKeyPoolTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    private val createdConfigs = mutableListOf<LiveSessionConfig>()
    private val queuedSessions = ArrayDeque<FakeLiveSessionClient>()
    private lateinit var coordinator: LiveSessionCoordinator

    @Before
    fun setUp() {
        coordinator = LiveSessionCoordinator(
            scope = scope,
            sessionFactory = { config ->
                createdConfigs += config
                queuedSessions.removeFirst()
            }
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `live session starts with first parsed Gemini key`() {
        queuedSessions += FakeLiveSessionClient()

        coordinator.sync(
            ApiSettings(
                liveModeEnabled = true,
                geminiApiKey = "key-1\n\nkey-2\nkey-1"
            ),
            glassesConnected = false
        )

        assertThat(createdConfigs).hasSize(1)
        assertThat(createdConfigs.single().apiKey).isEqualTo("key-1")
    }

    @Test
    fun `invalid Gemini live key falls back to next configured key`() {
        val firstSession = FakeLiveSessionClient()
        val secondSession = FakeLiveSessionClient()
        queuedSessions += firstSession
        queuedSessions += secondSession

        coordinator.sync(
            ApiSettings(
                liveModeEnabled = true,
                geminiApiKey = "key-1\nkey-2"
            ),
            glassesConnected = false
        )
        scope.advanceUntilIdle()

        coordinator.onSessionFailure(
            apiKey = "key-1",
            errorMessage = "API key not valid. Please pass a valid API key."
        )
        scope.advanceUntilIdle()

        assertThat(createdConfigs).hasSize(2)
        assertThat(createdConfigs[0].apiKey).isEqualTo("key-1")
        assertThat(createdConfigs[1].apiKey).isEqualTo("key-2")
        assertThat(firstSession.releaseCalls).isEqualTo(1)
        assertThat(coordinator.errorMessage.value).isNull()
    }

    @Test
    fun `google search quota failure retries same key without search before rotating`() {
        val firstSession = FakeLiveSessionClient()
        val secondSession = FakeLiveSessionClient()
        queuedSessions += firstSession
        queuedSessions += secondSession
        val notices = mutableListOf<String>()
        val job = scope.launch {
            coordinator.sessionNotices.collect { notices += it }
        }

        coordinator.sync(
            ApiSettings(
                liveModeEnabled = true,
                geminiApiKey = "key-1\nkey-2",
                liveGoogleSearchEnabled = true,
            ),
            glassesConnected = false
        )
        scope.advanceUntilIdle()

        coordinator.onSessionFailure(
            apiKey = "key-1",
            errorMessage = "You exceeded your current quota."
        )
        scope.advanceUntilIdle()

        assertThat(createdConfigs).hasSize(2)
        assertThat(createdConfigs[0].apiKey).isEqualTo("key-1")
        assertThat(createdConfigs[0].googleSearchAvailableForAttempt).isTrue()
        assertThat(createdConfigs[1].apiKey).isEqualTo("key-1")
        assertThat(createdConfigs[1].googleSearchAvailableForAttempt).isFalse()
        assertThat(firstSession.releaseCalls).isEqualTo(1)
        assertThat(coordinator.errorMessage.value).isNull()
        assertThat(notices.single())
            .contains("Google Search")
        job.cancel()
    }

    @Test
    fun `after search fallback exhausts same key next key also starts without search`() {
        val firstSession = FakeLiveSessionClient()
        val secondSession = FakeLiveSessionClient()
        val thirdSession = FakeLiveSessionClient()
        queuedSessions += firstSession
        queuedSessions += secondSession
        queuedSessions += thirdSession

        coordinator.sync(
            ApiSettings(
                liveModeEnabled = true,
                geminiApiKey = "key-1\nkey-2",
                liveGoogleSearchEnabled = true,
            ),
            glassesConnected = false
        )
        scope.advanceUntilIdle()

        coordinator.onSessionFailure(
            apiKey = "key-1",
            errorMessage = "You exceeded your current quota."
        )
        scope.advanceUntilIdle()

        coordinator.onSessionFailure(
            apiKey = "key-1",
            errorMessage = "You exceeded your current quota."
        )
        scope.advanceUntilIdle()

        assertThat(createdConfigs).hasSize(3)
        assertThat(createdConfigs[1].apiKey).isEqualTo("key-1")
        assertThat(createdConfigs[1].googleSearchAvailableForAttempt).isFalse()
        assertThat(createdConfigs[2].apiKey).isEqualTo("key-2")
        assertThat(createdConfigs[2].googleSearchAvailableForAttempt).isFalse()
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

        var releaseCalls = 0

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

        override fun release() {
            releaseCalls += 1
        }
    }
}
