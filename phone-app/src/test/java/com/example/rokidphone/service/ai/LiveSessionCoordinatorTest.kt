package com.example.rokidphone.service.ai

import com.example.rokidphone.data.ApiSettings
import com.example.rokidphone.data.GeminiLiveVoice
import com.example.rokidphone.data.LiveCameraMode
import com.example.rokidphone.data.LiveInputSource
import com.example.rokidphone.data.LiveThinkingLevel
import com.example.rokidphone.data.LiveOutputTarget
import com.example.rokidcommon.protocol.LiveRagDisplayMode
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

@OptIn(ExperimentalCoroutinesApi::class)
class LiveSessionCoordinatorTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    private val createdConfigs = mutableListOf<LiveSessionConfig>()
    private val createdSessions = mutableListOf<FakeLiveSessionClient>()
    private lateinit var coordinator: LiveSessionCoordinator

    private fun liveSettings(
        geminiApiKey: String = "test-live-key",
        block: ApiSettings.() -> ApiSettings = { this },
    ): ApiSettings {
        return ApiSettings(
            liveModeEnabled = true,
            geminiApiKey = geminiApiKey,
        ).block()
    }

    @Before
    fun setUp() {
        coordinator = LiveSessionCoordinator(
            scope = scope,
            sessionFactory = { config ->
                createdConfigs += config
                FakeLiveSessionClient().also { createdSessions += it }
            }
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `sync does not create session when live mode disabled`() {
        coordinator.sync(ApiSettings(liveModeEnabled = false), glassesConnected = true)

        assertThat(createdConfigs).isEmpty()
        assertThat(coordinator.sessionState.value).isEqualTo(LiveSessionState.IDLE)
    }

    @Test
    fun `sync with auto routing prefers glasses when connected`() {
        coordinator.sync(liveSettings(), glassesConnected = true)

        assertThat(latestConfig().capturePhoneAudio).isFalse()
        assertThat(latestConfig().playbackPhoneAudio).isFalse()
        assertThat(latestConfig().liveCameraMode).isEqualTo(LiveCameraMode.OFF)
    }

    @Test
    fun `sync with auto routing falls back to phone when glasses disconnected`() {
        coordinator.sync(liveSettings(), glassesConnected = false)

        assertThat(latestConfig().capturePhoneAudio).isTrue()
        assertThat(latestConfig().playbackPhoneAudio).isTrue()
    }

    @Test
    fun `sync falls back to native live model when current model is not live compatible`() {
        coordinator.sync(
            liveSettings {
                copy(
                aiModelId = "gemini-2.5-flash"
                )
            },
            glassesConnected = false
        )

        assertThat(latestConfig().modelId)
            .isEqualTo("gemini-3.1-flash-live-preview")
    }

    @Test
    fun `sync honors explicit phone routing even when glasses connected`() {
        coordinator.sync(
            liveSettings {
                copy(
                    liveInputSource = LiveInputSource.PHONE,
                    liveOutputTarget = LiveOutputTarget.BOTH,
                )
            },
            glassesConnected = true
        )

        assertThat(latestConfig().capturePhoneAudio).isTrue()
        assertThat(latestConfig().playbackPhoneAudio).isTrue()
        assertThat(coordinator.shouldForwardAudioToGlasses).isTrue()
    }

    @Test
    fun `sync with glasses input and glasses output keeps capture on glasses and forwards audio back to glasses`() {
        coordinator.sync(
            liveSettings {
                copy(
                    liveInputSource = LiveInputSource.GLASSES,
                    liveOutputTarget = LiveOutputTarget.GLASSES,
                )
            },
            glassesConnected = true
        )

        assertThat(latestConfig().capturePhoneAudio).isFalse()
        assertThat(latestConfig().playbackPhoneAudio).isFalse()
        assertThat(coordinator.shouldForwardAudioToGlasses).isTrue()
    }

    @Test
    fun `sync with phone output keeps playback local to phone app without forwarding`() {
        coordinator.sync(
            liveSettings {
                copy(
                    liveInputSource = LiveInputSource.GLASSES,
                    liveOutputTarget = LiveOutputTarget.PHONE,
                )
            },
            glassesConnected = true
        )

        assertThat(latestConfig().capturePhoneAudio).isFalse()
        assertThat(latestConfig().playbackPhoneAudio).isTrue()
        assertThat(coordinator.shouldForwardAudioToGlasses).isFalse()
    }

    @Test
    fun `sync disables all live audio output paths when live answer audio playback is off`() {
        coordinator.sync(
            liveSettings {
                copy(
                    liveAnswerAudioEnabled = false,
                    liveInputSource = LiveInputSource.PHONE,
                    liveOutputTarget = LiveOutputTarget.BOTH,
                )
            },
            glassesConnected = true
        )

        assertThat(latestConfig().playbackPhoneAudio).isFalse()
        assertThat(coordinator.shouldForwardAudioToGlasses).isFalse()
    }

    @Test
    fun `sync passes barge in disabled as no interruption activity handling`() {
        coordinator.sync(
            liveSettings {
                copy(
                    liveBargeInEnabled = false,
                    liveInputSource = LiveInputSource.PHONE,
                    liveOutputTarget = LiveOutputTarget.PHONE,
                )
            },
            glassesConnected = false
        )

        assertThat(latestSession().lastVadConfig.activityHandling)
            .isEqualTo(GeminiLiveService.ActivityHandling.NO_INTERRUPTION)
    }

    @Test
    fun `sync passes google search toggle into live session config`() {
        coordinator.sync(
            liveSettings {
                copy(
                    liveGoogleSearchEnabled = true,
                    liveInputSource = LiveInputSource.PHONE,
                    liveOutputTarget = LiveOutputTarget.PHONE,
                )
            },
            glassesConnected = false
        )

        assertThat(latestConfig().liveGoogleSearchEnabled).isTrue()
    }

    @Test
    fun `sync forces single rag display mode when live rag is disabled`() {
        coordinator.sync(
            liveSettings {
                copy(
                    liveRagEnabled = false,
                    liveRagDisplayMode = LiveRagDisplayMode.SPLIT_LIVE_AND_RAG,
                    liveInputSource = LiveInputSource.PHONE,
                    liveOutputTarget = LiveOutputTarget.PHONE,
                )
            },
            glassesConnected = false
        )

        assertThat(latestConfig().liveRagDisplayMode)
            .isEqualTo(LiveRagDisplayMode.RAG_RESULT_ONLY)
    }

    @Test
    fun `sync passes selected live voice into live session config`() {
        coordinator.sync(
            liveSettings {
                copy(
                    liveVoiceName = GeminiLiveVoice.KORE.voiceName,
                    liveInputSource = LiveInputSource.PHONE,
                    liveOutputTarget = LiveOutputTarget.PHONE,
                )
            },
            glassesConnected = false
        )

        assertThat(latestConfig().liveVoiceName).isEqualTo(GeminiLiveVoice.KORE.voiceName)
    }

    @Test
    fun `sync passes thinking level into live session config`() {
        coordinator.sync(
            liveSettings {
                copy(
                    liveThinkingLevel = LiveThinkingLevel.HIGH,
                    liveInputSource = LiveInputSource.PHONE,
                    liveOutputTarget = LiveOutputTarget.PHONE,
                )
            },
            glassesConnected = false
        )

        assertThat(latestConfig().liveThinkingLevel).isEqualTo(LiveThinkingLevel.HIGH)
    }

    @Test
    fun `sync passes thought summaries toggle into live session config`() {
        coordinator.sync(
            liveSettings {
                copy(
                    liveThoughtSummariesEnabled = true,
                    liveInputSource = LiveInputSource.PHONE,
                    liveOutputTarget = LiveOutputTarget.PHONE,
                )
            },
            glassesConnected = false
        )

        assertThat(latestConfig().liveThoughtSummariesEnabled).isTrue()
    }

    @Test
    fun `session status exposes resolved devices and capabilities`() {
        coordinator.sync(
            liveSettings {
                copy(
                    liveInputSource = LiveInputSource.AUTO,
                    liveOutputTarget = LiveOutputTarget.AUTO,
                    liveGoogleSearchEnabled = true,
                    liveRagEnabled = true,
                    liveThinkingLevel = LiveThinkingLevel.HIGH,
                )
            },
            glassesConnected = true
        )

        assertThat(coordinator.sessionStatus.value).isEqualTo(
            LiveSessionStatusSnapshot(
                inputSource = LiveInputSource.GLASSES,
                outputTarget = LiveOutputTarget.GLASSES,
                audioOutputEnabled = true,
                liveRagEnabled = true,
                googleSearchEnabledInSettings = true,
                googleSearchAvailableForSession = true,
                thinkingLevel = LiveThinkingLevel.HIGH,
            )
        )
    }

    @Test
    fun `receiveGlassesAudioChunk forwards only when glasses is active input`() {
        coordinator.sync(
            liveSettings {
                copy(liveInputSource = LiveInputSource.GLASSES)
            },
            glassesConnected = true
        )

        coordinator.receiveGlassesAudioChunk(byteArrayOf(1, 2, 3))
        coordinator.sync(
            liveSettings {
                copy(liveInputSource = LiveInputSource.PHONE)
            },
            glassesConnected = true
        )
        coordinator.receiveGlassesAudioChunk(byteArrayOf(4, 5, 6))

        assertThat(createdSessions.first().sentAudioChunks).hasSize(1)
        assertThat(createdSessions.first().sentAudioChunks.single().contentEquals(byteArrayOf(1, 2, 3))).isTrue()
    }

    @Test
    fun `sync releases active session when live mode turns off`() {
        coordinator.sync(liveSettings(), glassesConnected = false)

        coordinator.sync(ApiSettings(liveModeEnabled = false), glassesConnected = false)

        assertThat(createdSessions.first().releaseCalls).isEqualTo(1)
        assertThat(coordinator.sessionState.value).isEqualTo(LiveSessionState.IDLE)
    }

    @Test
    fun `session state updates are mapped to coordinator state`() {
        coordinator.sync(liveSettings(), glassesConnected = false)

        latestSession().mutableState.value = GeminiLiveSession.SessionState.ACTIVE
        scope.advanceUntilIdle()

        assertThat(coordinator.sessionState.value).isEqualTo(LiveSessionState.ACTIVE)
    }

    @Test
    fun `usage metadata is forwarded from active session`() {
        coordinator.sync(liveSettings(), glassesConnected = false)
        scope.advanceUntilIdle()
        val collected = mutableListOf<LiveUsageMetadata>()
        val job = scope.launch {
            coordinator.usageMetadata.collect { collected += it }
        }
        scope.advanceUntilIdle()

        latestSession().emitUsageMetadata(
            LiveUsageMetadata(
                promptTokenCount = 1200,
                cachedContentTokenCount = null,
                responseTokenCount = 240,
                toolUsePromptTokenCount = null,
                thoughtsTokenCount = 32,
                totalTokenCount = 1472,
            )
        )
        scope.advanceUntilIdle()

        assertThat(collected).hasSize(1)
        assertThat(collected.single().totalTokenCount).isEqualTo(1472)
        job.cancel()
    }

    @Test
    fun `go away resumes long session with latest resumable handle`() {
        coordinator.sync(
            liveSettings {
                copy(
                    liveLongSessionEnabled = true,
                    liveInputSource = LiveInputSource.PHONE,
                    liveOutputTarget = LiveOutputTarget.PHONE,
                )
            },
            glassesConnected = false
        )
        scope.advanceUntilIdle()
        latestSession().mutableState.value = GeminiLiveSession.SessionState.ACTIVE
        coordinator.onSessionResumptionUpdate(
            LiveSessionResumptionUpdate(newHandle = "resume-123", resumable = true)
        )
        scope.advanceUntilIdle()

        coordinator.onGoAway("test-live-key")
        scope.advanceUntilIdle()

        assertThat(createdConfigs).hasSize(2)
        assertThat(latestConfig().sessionResumptionHandle).isEqualTo("resume-123")
        assertThat(latestConfig().liveLongSessionEnabled).isTrue()
    }

    @Test
    fun `go away does not resume when long session toggle is off`() {
        coordinator.sync(
            liveSettings {
                copy(
                    liveLongSessionEnabled = false,
                    liveInputSource = LiveInputSource.PHONE,
                    liveOutputTarget = LiveOutputTarget.PHONE,
                )
            },
            glassesConnected = false
        )
        scope.advanceUntilIdle()
        latestSession().mutableState.value = GeminiLiveSession.SessionState.ACTIVE
        coordinator.onSessionResumptionUpdate(
            LiveSessionResumptionUpdate(newHandle = "resume-123", resumable = true)
        )
        scope.advanceUntilIdle()

        coordinator.onGoAway("test-live-key")
        scope.advanceUntilIdle()

        assertThat(createdConfigs).hasSize(1)
    }

    @Test
    fun `search fallback updates session status to reflect disabled search for current session`() {
        coordinator.sync(
            liveSettings {
                copy(
                    liveGoogleSearchEnabled = true,
                    liveInputSource = LiveInputSource.PHONE,
                    liveOutputTarget = LiveOutputTarget.PHONE,
                )
            },
            glassesConnected = false
        )
        scope.advanceUntilIdle()

        coordinator.onSessionFailure(
            apiKey = "test-live-key",
            errorMessage = "You exceeded your current quota."
        )
        scope.advanceUntilIdle()

        assertThat(coordinator.sessionStatus.value?.googleSearchEnabledInSettings).isEqualTo(true)
        assertThat(coordinator.sessionStatus.value?.googleSearchAvailableForSession).isEqualTo(false)
    }

    private fun latestConfig(): LiveSessionConfig = createdConfigs.last()

    private fun latestSession(): FakeLiveSessionClient = createdSessions.last()

    private class FakeLiveSessionClient : LiveSessionClient {
        val mutableState = MutableStateFlow(GeminiLiveSession.SessionState.CONNECTING)
        private val mutableError = MutableStateFlow<String?>(null)
        private val mutableInput = MutableSharedFlow<String>()
        private val mutableOutput = MutableSharedFlow<String>()
        private val mutableThoughtSummary = MutableSharedFlow<String>()
        private val mutableAudio = MutableSharedFlow<ByteArray>()
        private val mutableUsageMetadata =
            MutableSharedFlow<LiveUsageMetadata>(replay = 1, extraBufferCapacity = 1)
        private val mutableTurnComplete = MutableSharedFlow<Unit>()
        private val mutableInterrupted = MutableSharedFlow<Unit>()
        private val mutableSessionResumptionUpdates =
            MutableSharedFlow<LiveSessionResumptionUpdate>(replay = 1, extraBufferCapacity = 1)
        private val mutableGoAwayNotices =
            MutableSharedFlow<LiveGoAwayNotice>(replay = 1, extraBufferCapacity = 1)

        val sentAudioChunks = mutableListOf<ByteArray>()
        var releaseCalls = 0
        var lastVadConfig: GeminiLiveSession.VadConfig = GeminiLiveSession.VadConfig()

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
        ): Boolean {
            lastVadConfig = vadConfig
            return true
        }

        override fun sendAudioChunk(audioData: ByteArray) {
            sentAudioChunks += audioData
        }

        override fun sendVideoFrame(jpegData: ByteArray) = Unit

        override fun endOfTurn() = Unit

        override fun release() {
            releaseCalls += 1
        }

        fun emitSessionResumptionUpdate(update: LiveSessionResumptionUpdate) {
            mutableSessionResumptionUpdates.tryEmit(update)
        }

        fun emitGoAway(notice: LiveGoAwayNotice) {
            mutableGoAwayNotices.tryEmit(notice)
        }

        fun emitUsageMetadata(metadata: LiveUsageMetadata) {
            mutableUsageMetadata.tryEmit(metadata)
        }
    }
}
