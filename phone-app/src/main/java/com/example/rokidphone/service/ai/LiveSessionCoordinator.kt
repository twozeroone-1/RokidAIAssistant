package com.example.rokidphone.service.ai

import com.example.rokidphone.data.ApiSettings
import com.example.rokidphone.data.LiveCameraMode
import com.example.rokidphone.data.LiveInputSource
import com.example.rokidphone.data.LiveOutputTarget
import com.example.rokidphone.data.LiveThinkingLevel
import com.example.rokidphone.data.resolveGeminiLiveModelId
import com.example.rokidcommon.protocol.LiveRagDisplayMode
import com.example.rokidcommon.protocol.resolveEffectiveLiveRagDisplayMode
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class LiveSessionState {
    IDLE,
    CONNECTING,
    ACTIVE,
    RECONNECTING,
    INTERRUPTED,
    STOPPING,
    ERROR
}

data class LiveSessionConfig(
    val apiKey: String,
    val modelId: String,
    val systemPrompt: String,
    val liveVoiceName: String,
    val capturePhoneAudio: Boolean,
    val playbackPhoneAudio: Boolean,
    val liveCameraMode: LiveCameraMode,
    val liveCameraIntervalSec: Int,
    val liveRagEnabled: Boolean,
    val liveRagDisplayMode: LiveRagDisplayMode = LiveRagDisplayMode.RAG_RESULT_ONLY,
    val liveBargeInEnabled: Boolean,
    val liveLongSessionEnabled: Boolean,
    val liveGoogleSearchEnabled: Boolean,
    val googleSearchAvailableForAttempt: Boolean = liveGoogleSearchEnabled,
    val liveThinkingLevel: LiveThinkingLevel,
    val liveThoughtSummariesEnabled: Boolean,
    val sessionResumptionHandle: String? = null,
)

private data class LiveSessionBaseConfig(
    val apiKeys: List<String>,
    val modelId: String,
    val systemPrompt: String,
    val liveVoiceName: String,
    val capturePhoneAudio: Boolean,
    val playbackPhoneAudio: Boolean,
    val liveCameraMode: LiveCameraMode,
    val liveCameraIntervalSec: Int,
    val liveRagEnabled: Boolean,
    val liveRagDisplayMode: LiveRagDisplayMode,
    val liveBargeInEnabled: Boolean,
    val liveLongSessionEnabled: Boolean,
    val liveGoogleSearchEnabled: Boolean,
    val liveThinkingLevel: LiveThinkingLevel,
    val liveThoughtSummariesEnabled: Boolean,
) {
    fun withApiKey(
        apiKey: String,
        sessionResumptionHandle: String? = null,
        googleSearchAvailableForAttempt: Boolean = liveGoogleSearchEnabled,
    ): LiveSessionConfig {
        return LiveSessionConfig(
            apiKey = apiKey,
            modelId = modelId,
            systemPrompt = systemPrompt,
            liveVoiceName = liveVoiceName,
            capturePhoneAudio = capturePhoneAudio,
            playbackPhoneAudio = playbackPhoneAudio,
            liveCameraMode = liveCameraMode,
            liveCameraIntervalSec = liveCameraIntervalSec,
            liveRagEnabled = liveRagEnabled,
            liveRagDisplayMode = liveRagDisplayMode,
            liveBargeInEnabled = liveBargeInEnabled,
            liveLongSessionEnabled = liveLongSessionEnabled,
            liveGoogleSearchEnabled = liveGoogleSearchEnabled,
            googleSearchAvailableForAttempt = googleSearchAvailableForAttempt,
            liveThinkingLevel = liveThinkingLevel,
            liveThoughtSummariesEnabled = liveThoughtSummariesEnabled,
            sessionResumptionHandle = sessionResumptionHandle,
        )
    }
}

interface LiveSessionClient {
    val sessionState: StateFlow<GeminiLiveSession.SessionState>
    val errorMessage: StateFlow<String?>
    val inputTranscription: SharedFlow<String>
    val outputTranscription: SharedFlow<String>
    val thoughtSummary: SharedFlow<String>
    val outputAudio: SharedFlow<ByteArray>
    val usageMetadata: SharedFlow<LiveUsageMetadata>
    val turnComplete: SharedFlow<Unit>
    val interrupted: SharedFlow<Unit>
    val sessionResumptionUpdates: SharedFlow<LiveSessionResumptionUpdate>
    val goAwayNotices: SharedFlow<LiveGoAwayNotice>

    fun start(
        vadConfig: GeminiLiveSession.VadConfig = GeminiLiveSession.VadConfig(),
        tools: List<JSONObject>? = null,
    ): Boolean

    fun sendAudioChunk(audioData: ByteArray)

    fun sendVideoFrame(jpegData: ByteArray)

    fun endOfTurn()

    fun release()
}

class LiveSessionCoordinator(
    private val scope: CoroutineScope,
    private val sessionFactory: (LiveSessionConfig) -> LiveSessionClient,
) {
    companion object {
        private const val TAG = "LiveSessionCoordinator"
        private const val GOOGLE_SEARCH_FALLBACK_NOTICE =
            "Google Search를 사용할 수 없어 검색 없이 계속합니다. 원인: 쿼터 초과 또는 현재 프로젝트 플랜 제한."
    }

    private val _sessionState = MutableStateFlow(LiveSessionState.IDLE)
    val sessionState: StateFlow<LiveSessionState> = _sessionState.asStateFlow()

    private val _userTranscription = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val userTranscription: SharedFlow<String> = _userTranscription.asSharedFlow()

    private val _assistantTranscription = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val assistantTranscription: SharedFlow<String> = _assistantTranscription.asSharedFlow()

    private val _assistantThoughtSummary = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val assistantThoughtSummary: SharedFlow<String> = _assistantThoughtSummary.asSharedFlow()

    private val _assistantAudio = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
    val assistantAudio: SharedFlow<ByteArray> = _assistantAudio.asSharedFlow()

    private val _usageMetadata = MutableSharedFlow<LiveUsageMetadata>(extraBufferCapacity = 8)
    val usageMetadata: SharedFlow<LiveUsageMetadata> = _usageMetadata.asSharedFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _turnComplete = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val turnComplete: SharedFlow<Unit> = _turnComplete.asSharedFlow()

    private val _interrupted = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val interrupted: SharedFlow<Unit> = _interrupted.asSharedFlow()

    private val _sessionNotices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val sessionNotices: SharedFlow<String> = _sessionNotices.asSharedFlow()

    private val _sessionStatus = MutableStateFlow<LiveSessionStatusSnapshot?>(null)
    val sessionStatus: StateFlow<LiveSessionStatusSnapshot?> = _sessionStatus.asStateFlow()

    private val recoveryScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    var shouldForwardAudioToGlasses: Boolean = false
        private set

    private var activeSession: LiveSessionClient? = null
    private var activeConfig: LiveSessionConfig? = null
    private var activeBaseConfig: LiveSessionBaseConfig? = null
    private var effectiveInputSource: LiveInputSource = LiveInputSource.AUTO
    private var effectiveOutputTarget: LiveOutputTarget = LiveOutputTarget.AUTO
    private var sessionCollectorJobs: List<Job> = emptyList()
    private var keyPool: GeminiKeyPool? = null
    private var latestSessionError: String? = null
    private var sessionReachedActive = false
    private var terminalFailureHandled = false
    private var latestResumptionHandle: String? = null
    private var lastAttemptedResumptionHandle: String? = null
    private var googleSearchFallbackUsedForSession = false

    fun sync(settings: ApiSettings, glassesConnected: Boolean) {
        if (!settings.liveModeEnabled) {
            stopSession()
            return
        }

        val resolvedInput = resolveInputSource(settings.liveInputSource, glassesConnected)
        val resolvedOutput = resolveOutputTarget(settings.liveOutputTarget, glassesConnected)
        val baseConfig = LiveSessionBaseConfig(
            apiKeys = settings.getGeminiApiKeys(),
            modelId = resolveLiveModelId(settings.aiModelId),
            systemPrompt = settings.systemPrompt,
            liveVoiceName = settings.getLiveVoice().voiceName,
            capturePhoneAudio = resolvedInput == LiveInputSource.PHONE,
            playbackPhoneAudio = resolvedOutput == LiveOutputTarget.PHONE ||
                resolvedOutput == LiveOutputTarget.BOTH,
            liveCameraMode = settings.liveCameraMode,
            liveCameraIntervalSec = settings.liveCameraIntervalSec,
            liveRagEnabled = settings.liveRagEnabled,
            liveRagDisplayMode = resolveEffectiveLiveRagDisplayMode(
                liveRagEnabled = settings.liveRagEnabled,
                configuredMode = settings.liveRagDisplayMode,
            ),
            liveBargeInEnabled = settings.liveBargeInEnabled,
            liveLongSessionEnabled = settings.liveLongSessionEnabled,
            liveGoogleSearchEnabled = settings.liveGoogleSearchEnabled,
            liveThinkingLevel = settings.liveThinkingLevel,
            liveThoughtSummariesEnabled = settings.liveThoughtSummariesEnabled,
        )

        effectiveInputSource = resolvedInput
        effectiveOutputTarget = resolvedOutput
        shouldForwardAudioToGlasses = resolvedOutput == LiveOutputTarget.GLASSES ||
            resolvedOutput == LiveOutputTarget.BOTH

        if (baseConfig == activeBaseConfig && activeSession != null) {
            updateSessionStatus(activeConfig)
            return
        }

        restartSession(baseConfig)
    }

    fun receiveGlassesAudioChunk(audioData: ByteArray) {
        if (effectiveInputSource != LiveInputSource.GLASSES) {
            return
        }
        activeSession?.sendAudioChunk(audioData)
    }

    fun receiveVideoFrame(frameData: ByteArray) {
        if (activeConfig?.liveCameraMode == LiveCameraMode.OFF) {
            return
        }
        activeSession?.sendVideoFrame(frameData)
    }

    fun endOfTurn() {
        activeSession?.endOfTurn()
    }

    fun release() {
        stopSession()
    }

    private fun restartSession(baseConfig: LiveSessionBaseConfig) {
        activeBaseConfig = baseConfig
        keyPool = GeminiKeyPool(baseConfig.apiKeys)
        latestResumptionHandle = null
        lastAttemptedResumptionHandle = null
        googleSearchFallbackUsedForSession = false
        startSessionWithNextKey(isRecovery = false)
    }

    private fun startSessionWithNextKey(
        isRecovery: Boolean,
        sessionResumptionHandle: String? = null,
    ): Boolean {
        val baseConfig = activeBaseConfig ?: return false
        val nextApiKey = keyPool?.nextCandidate()

        if (nextApiKey.isNullOrBlank()) {
            _errorMessage.value = latestSessionError ?: "사용 가능한 Gemini Live API 키가 없습니다."
            _sessionState.value = LiveSessionState.ERROR
            return false
        }

        val config = baseConfig.withApiKey(
            apiKey = nextApiKey,
            sessionResumptionHandle = sessionResumptionHandle,
            googleSearchAvailableForAttempt = baseConfig.liveGoogleSearchEnabled &&
                !googleSearchFallbackUsedForSession,
        )
        startSession(config, isRecovery)
        return true
    }

    private fun startSession(config: LiveSessionConfig, isRecovery: Boolean) {
        stopSession(emitIdle = false, clearBaseConfig = false)

        val session = sessionFactory(config)
        activeSession = session
        activeConfig = config
        latestSessionError = null
        sessionReachedActive = false
        terminalFailureHandled = false
        _errorMessage.value = null
        _sessionState.value = if (isRecovery) LiveSessionState.RECONNECTING else LiveSessionState.CONNECTING
        updateSessionStatus(config)

        sessionCollectorJobs = listOf(
            scope.launch {
                session.sessionState.collect { state ->
                    if (session !== activeSession) {
                        return@collect
                    }

                    when (state) {
                        GeminiLiveSession.SessionState.ACTIVE -> {
                            if (!sessionReachedActive) {
                                sessionReachedActive = true
                                keyPool?.markSuccess(config.apiKey)
                            }
                            _sessionState.value = LiveSessionState.ACTIVE
                        }

                        GeminiLiveSession.SessionState.ERROR -> {
                            _sessionState.value = LiveSessionState.ERROR
                            scope.launch {
                                if (session !== activeSession) {
                                    return@launch
                                }
                                val resolvedErrorMessage = latestSessionError ?: session.errorMessage.value
                                if (!resolvedErrorMessage.isNullOrBlank()) {
                                    onSessionFailure(config.apiKey, resolvedErrorMessage)
                                }
                            }
                        }

                        else -> {
                            _sessionState.value = state.toCoordinatorState()
                        }
                    }
                }
            },
            scope.launch {
                session.errorMessage.collect { message ->
                    if (session !== activeSession || message.isNullOrBlank()) {
                        return@collect
                    }
                    latestSessionError = message
                    if (session.sessionState.value == GeminiLiveSession.SessionState.ERROR) {
                        onSessionFailure(config.apiKey, message)
                    }
                }
            },
            scope.launch {
                session.inputTranscription.collect { _userTranscription.emit(it) }
            },
            scope.launch {
                session.outputTranscription.collect { _assistantTranscription.emit(it) }
            },
            scope.launch {
                session.thoughtSummary.collect { _assistantThoughtSummary.emit(it) }
            },
            scope.launch {
                session.outputAudio.collect { _assistantAudio.emit(it) }
            },
            scope.launch {
                session.usageMetadata.collect { _usageMetadata.emit(it) }
            },
            scope.launch {
                session.turnComplete.collect { _turnComplete.emit(Unit) }
            },
            scope.launch {
                session.interrupted.collect { _interrupted.emit(Unit) }
            },
            scope.launch {
                session.sessionResumptionUpdates.collect { update ->
                    if (session !== activeSession) {
                        return@collect
                    }
                    onSessionResumptionUpdate(update)
                }
            },
            scope.launch {
                session.goAwayNotices.collect {
                    if (session !== activeSession) {
                        return@collect
                    }
                    onGoAway(config.apiKey)
                }
            },
        )

        val activityHandling = if (config.liveBargeInEnabled) {
            GeminiLiveService.ActivityHandling.START_OF_ACTIVITY_INTERRUPTS
        } else {
            GeminiLiveService.ActivityHandling.NO_INTERRUPTION
        }

        val started = session.start(
            vadConfig = GeminiLiveSession.VadConfig(
                activityHandling = activityHandling
            )
        )
        if (!started) {
            latestSessionError = session.errorMessage.value ?: "Live session failed to start"
            onSessionFailure(config.apiKey, latestSessionError)
        }
    }

    internal fun onSessionFailure(apiKey: String, errorMessage: String?) {
        if (activeConfig?.apiKey != apiKey || terminalFailureHandled) {
            return
        }

        terminalFailureHandled = true
        latestSessionError = errorMessage

        val failureType = classifyLiveSessionFailure(errorMessage)
        if (maybeFallbackWithoutGoogleSearch(apiKey, failureType)) {
            return
        }
        if (failureType == null && resumeSessionWithSavedHandle(apiKey, "connection_failure")) {
            return
        }
        if (failureType != null && rotateToNextKey(apiKey, failureType)) {
            return
        }

        _errorMessage.value = errorMessage ?: "Live session failed"
        _sessionState.value = LiveSessionState.ERROR
    }

    internal fun onSessionResumptionUpdate(update: LiveSessionResumptionUpdate) {
        if (update.newHandle.isNotBlank()) {
            latestResumptionHandle = update.newHandle
            if (update.newHandle != lastAttemptedResumptionHandle) {
                lastAttemptedResumptionHandle = null
            }
        }
    }

    internal fun onGoAway(apiKey: String) {
        resumeSessionWithSavedHandle(
            apiKey = apiKey,
            trigger = "goAway",
        )
    }

    private fun rotateToNextKey(
        failedApiKey: String,
        failureType: LiveSessionFailureType,
    ): Boolean {
        val pool = keyPool ?: activeBaseConfig?.let { baseConfig ->
            GeminiKeyPool(baseConfig.apiKeys).also { keyPool = it }
        } ?: return false

        when (failureType) {
            LiveSessionFailureType.INVALID_KEY -> pool.markInvalidKey(failedApiKey)
            LiveSessionFailureType.QUOTA -> pool.markQuotaFailure(failedApiKey)
        }

        logDebug("Rotating Gemini Live API key due to $failureType")
        return runRecovery { startSessionWithNextKey(isRecovery = true) }
    }

    private fun maybeFallbackWithoutGoogleSearch(
        apiKey: String,
        failureType: LiveSessionFailureType?,
    ): Boolean {
        val config = activeConfig ?: return false
        if (failureType != LiveSessionFailureType.QUOTA) {
            return false
        }
        if (sessionReachedActive) {
            return false
        }
        if (!config.liveGoogleSearchEnabled || !config.googleSearchAvailableForAttempt) {
            return false
        }
        if (googleSearchFallbackUsedForSession) {
            return false
        }

        googleSearchFallbackUsedForSession = true
        _sessionNotices.tryEmit(GOOGLE_SEARCH_FALLBACK_NOTICE)
        logDebug("Google Search unavailable for current session, restarting without Search")
        return runRecovery {
            startSession(
                config = config.copy(googleSearchAvailableForAttempt = false),
                isRecovery = true,
            )
            true
        }
    }

    private fun stopSession(
        emitIdle: Boolean = true,
        clearBaseConfig: Boolean = true,
    ) {
        if (activeSession == null && emitIdle) {
            _sessionState.value = LiveSessionState.IDLE
            return
        }

        _sessionState.value = LiveSessionState.STOPPING
        sessionCollectorJobs.forEach { it.cancel() }
        sessionCollectorJobs = emptyList()
        activeSession?.release()
        activeSession = null
        activeConfig = null
        latestSessionError = null
        sessionReachedActive = false
        terminalFailureHandled = false
        _errorMessage.value = null
        if (clearBaseConfig) {
            effectiveInputSource = LiveInputSource.AUTO
            effectiveOutputTarget = LiveOutputTarget.AUTO
            shouldForwardAudioToGlasses = false
            _sessionStatus.value = null
            activeBaseConfig = null
            keyPool = null
            latestResumptionHandle = null
            lastAttemptedResumptionHandle = null
            googleSearchFallbackUsedForSession = false
        }
        if (emitIdle) {
            _sessionState.value = LiveSessionState.IDLE
        }
    }

    private fun resumeSessionWithSavedHandle(
        apiKey: String,
        trigger: String,
    ): Boolean {
        val baseConfig = activeBaseConfig ?: return false
        if (!baseConfig.liveLongSessionEnabled) {
            return false
        }

        val handle = latestResumptionHandle?.takeIf { it.isNotBlank() } ?: return false
        if (lastAttemptedResumptionHandle == handle) {
            return false
        }

        logDebug("Resuming Gemini Live session after $trigger using saved handle")
        lastAttemptedResumptionHandle = handle
        return runRecovery {
            startSession(
                config = baseConfig.withApiKey(apiKey, handle),
                isRecovery = true,
            )
            true
        }
    }

    private fun runRecovery(block: () -> Boolean): Boolean {
        var started = false
        recoveryScope.launch(start = CoroutineStart.UNDISPATCHED) {
            started = block()
        }
        return started
    }

    private fun logDebug(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: RuntimeException) {
            // Local JVM unit tests do not mock android.util.Log.
        }
    }

    private fun resolveInputSource(
        configured: LiveInputSource,
        glassesConnected: Boolean,
    ): LiveInputSource {
        return when (configured) {
            LiveInputSource.AUTO -> if (glassesConnected) LiveInputSource.GLASSES else LiveInputSource.PHONE
            else -> configured
        }
    }

    private fun resolveOutputTarget(
        configured: LiveOutputTarget,
        glassesConnected: Boolean,
    ): LiveOutputTarget {
        return when (configured) {
            LiveOutputTarget.AUTO -> if (glassesConnected) LiveOutputTarget.GLASSES else LiveOutputTarget.PHONE
            else -> configured
        }
    }

    private fun GeminiLiveSession.SessionState.toCoordinatorState(): LiveSessionState {
        return when (this) {
            GeminiLiveSession.SessionState.IDLE -> LiveSessionState.IDLE
            GeminiLiveSession.SessionState.CONNECTING -> LiveSessionState.CONNECTING
            GeminiLiveSession.SessionState.ACTIVE -> LiveSessionState.ACTIVE
            GeminiLiveSession.SessionState.PAUSED -> LiveSessionState.INTERRUPTED
            GeminiLiveSession.SessionState.DISCONNECTING -> LiveSessionState.STOPPING
            GeminiLiveSession.SessionState.ERROR -> LiveSessionState.ERROR
        }
    }

    private fun resolveLiveModelId(selectedModelId: String): String {
        return resolveGeminiLiveModelId(selectedModelId)
    }

    private fun updateSessionStatus(config: LiveSessionConfig?) {
        _sessionStatus.value = config?.let {
            LiveSessionStatusSnapshot(
                inputSource = effectiveInputSource,
                outputTarget = effectiveOutputTarget,
                liveRagEnabled = it.liveRagEnabled,
                googleSearchEnabledInSettings = it.liveGoogleSearchEnabled,
                googleSearchAvailableForSession = it.googleSearchAvailableForAttempt,
                thinkingLevel = it.liveThinkingLevel,
            )
        }
    }
}
