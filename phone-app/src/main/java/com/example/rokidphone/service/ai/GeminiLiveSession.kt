package com.example.rokidphone.service.ai

import android.content.Context
import android.util.Log
import com.example.rokidphone.data.LiveThinkingLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Gemini Live Session Coordinator
 *
 * Functionality:
 * - Coordinates GeminiLiveService (WebSocket) and LiveAudioManager (Audio).
 * - Manages the full lifecycle of a Live session (start/stop).
 * - Handles bidirectional audio data transmission.
 * - Integrates ToolCallRouter for tool call routing and execution.
 * - Provides unified session state and events to the upper layer (PhoneAIService / ViewModel).
 *
 * Integration with existing architecture:
 * - Created and managed within PhoneAIService.
 * - Communicates with the glasses via MessageType.
 * - Does not affect the existing REST API mode (GeminiService).
 *
 * Android specific handling:
 * - Ensures Context is available to initialize LiveAudioManager.
 * - Managed within the Service lifecycle to avoid memory leaks.
 * - Handles Activity/Service state changes.
 */
class GeminiLiveSession(
    private val context: Context,
    private val apiKey: String,
    private val modelId: String = "gemini-3.1-flash-live-preview",
    private val systemPrompt: String = "",
    private val liveVoiceName: String,
    private val capturePhoneAudio: Boolean = true,
    private val playbackPhoneAudio: Boolean = true,
    private val enableLongSession: Boolean = false,
    private val sessionResumptionHandle: String? = null,
    private val thinkingLevel: LiveThinkingLevel = LiveThinkingLevel.DEFAULT,
    private val includeThoughtSummaries: Boolean = false,
    private val extraToolDeclarations: List<JSONObject> = emptyList(),
    private val routerConfigurator: (ToolCallRouter) -> Unit = {},
) : LiveSessionClient {
    companion object {
        private const val TAG = "GeminiLiveSession"
    }

    // ========== Session State ==========

    /**
     * Session State
     */
    enum class SessionState {
        IDLE,           // Idle, not started
        CONNECTING,     // Connecting to WebSocket
        ACTIVE,         // Session active (can send/receive audio)
        PAUSED,         // Session paused
        DISCONNECTING,  // Disconnecting
        ERROR           // Error
    }

    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    override val sessionState = _sessionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage = _errorMessage.asStateFlow()

    // ========== Session Components ==========

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var liveService: GeminiLiveService? = null
    private var liveServiceStateJob: Job? = null
    private var audioManager: LiveAudioManager? = null
    private var toolCallRouter: ToolCallRouter? = null
    private var lastHandledConnectionState: GeminiLiveService.ConnectionState? = null

    // ========== Event Streams ==========

    /**
     * User speech transcription event
     */
    private val _inputTranscription = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val inputTranscription = _inputTranscription.asSharedFlow()

    /**
     * AI response transcription event
     */
    private val _outputTranscription = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val outputTranscription = _outputTranscription.asSharedFlow()

    private val _thoughtSummary = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val thoughtSummary = _thoughtSummary.asSharedFlow()

    private val _outputAudio = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
    override val outputAudio = _outputAudio.asSharedFlow()

    private val _usageMetadata = MutableSharedFlow<LiveUsageMetadata>(extraBufferCapacity = 8)
    override val usageMetadata = _usageMetadata.asSharedFlow()

    /**
     * AI turn complete event
     */
    private val _turnComplete = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val turnComplete = _turnComplete.asSharedFlow()

    /**
     * Session interrupted event (User interrupted AI)
     */
    private val _interrupted = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val interrupted = _interrupted.asSharedFlow()

    private val _sessionResumptionUpdates =
        MutableSharedFlow<LiveSessionResumptionUpdate>(extraBufferCapacity = 4)
    override val sessionResumptionUpdates = _sessionResumptionUpdates.asSharedFlow()

    private val _goAwayNotices = MutableSharedFlow<LiveGoAwayNotice>(extraBufferCapacity = 4)
    override val goAwayNotices = _goAwayNotices.asSharedFlow()

    /**
     * Tool call event
     */
    private val _toolCalls = MutableSharedFlow<List<GeminiLiveService.ToolCall>>()
    val toolCalls = _toolCalls.asSharedFlow()

    // ========== Configuration ==========

    /**
     * VAD Configuration
     */
    data class VadConfig(
        val startSensitivity: GeminiLiveService.StartOfSpeechSensitivity =
            GeminiLiveService.StartOfSpeechSensitivity.START_SENSITIVITY_HIGH,
        val endSensitivity: GeminiLiveService.EndOfSpeechSensitivity =
            GeminiLiveService.EndOfSpeechSensitivity.END_SENSITIVITY_LOW,
        val silenceDurationMs: Int = 500,
        val activityHandling: GeminiLiveService.ActivityHandling =
            GeminiLiveService.ActivityHandling.START_OF_ACTIVITY_INTERRUPTS
    )

    private var vadConfig = VadConfig()

    // ========== Tool Declarations ==========

    /**
     * Tool declaration list
     * Uses ToolDeclarations.allDeclarations() to provide default declarations
     */
    private var toolDeclarations: List<JSONObject>? = null

    internal fun shouldPauseRecordingDuringPlayback(vadConfig: VadConfig): Boolean {
        return vadConfig.activityHandling == GeminiLiveService.ActivityHandling.NO_INTERRUPTION
    }

    // ========== Session Control ==========

    /**
     * Start Live Session
     *
     * @param vadConfig VAD configuration (optional)
     * @param tools Tool declarations (optional)
     * @return Whether the startup was successful
     */
    override fun start(
        vadConfig: VadConfig,
        tools: List<JSONObject>?
    ): Boolean {
        if (_sessionState.value != SessionState.IDLE && _sessionState.value != SessionState.ERROR) {
            Log.w(TAG, "Session is already in progress, state: ${_sessionState.value}")
            return false
        }

        Log.d(TAG, "Starting Live session")

        this.vadConfig = vadConfig
        this.toolDeclarations = tools

        _errorMessage.value = null
        _sessionState.value = SessionState.CONNECTING
        lastHandledConnectionState = null

        try {
            // Initialize ToolCallRouter
            toolCallRouter = ToolCallRouter(scope).also { router ->
                val systemToolsHandler = SystemToolsHandler(context)

                router.registerHandler("check_schedule") { call ->
                    systemToolsHandler.handleCheckSchedule(call)
                }

                router.registerHandler("make_call") { call ->
                    systemToolsHandler.handleMakeCall(call)
                }

                routerConfigurator(router)

                // Collect tool execution results and return to WebSocket
                scope.launch {
                    router.toolResults.collect { result ->
                        Log.d(TAG, "Tool result received: ${result.toolCallId}, success=${result.success}")
                        sendToolResponse(result.toolCallId, result.toResponseJson())
                    }
                }
            }

            if (capturePhoneAudio || playbackPhoneAudio) {
                audioManager = LiveAudioManager(context, scope).apply {
                    setPauseRecordingDuringPlayback(
                        shouldPauseRecordingDuringPlayback(vadConfig)
                    )
                    if (capturePhoneAudio) {
                        onAudioChunk = { chunk ->
                            liveService?.sendAudio(chunk)
                        }
                    }

                    onPlaybackComplete = {
                        Log.d(TAG, "AI response playback complete")
                    }

                    onError = { error ->
                        Log.e(TAG, "Audio error: $error")
                        _errorMessage.value = error
                    }
                }
            }

            // Initialize WebSocket Service
            liveService = GeminiLiveService(
                apiKey = apiKey,
                modelId = modelId,
                systemPrompt = systemPrompt,
                liveVoiceName = liveVoiceName,
                enableLongSession = enableLongSession,
                sessionResumptionHandle = sessionResumptionHandle,
                thinkingLevel = thinkingLevel,
                includeThoughtSummaries = includeThoughtSummaries,
                scope = scope
            ).apply {
                // Update VAD settings
                updateVadSettings(
                    startSensitivity = vadConfig.startSensitivity,
                    endSensitivity = vadConfig.endSensitivity,
                    silenceDurationMs = vadConfig.silenceDurationMs,
                    activityHandling = vadConfig.activityHandling
                )

                // Set callbacks
                onConnectionStateChanged = { state ->
                    handleConnectionStateChangeIfNew(state)
                }

                onAudioReceived = { audioData ->
                    handleAudioReceived(audioData)
                }

                onTurnComplete = {
                    handleTurnComplete()
                }

                onInterrupted = {
                    handleInterrupted()
                }

                onToolCall = { calls ->
                    handleToolCalls(calls)
                }

                onInputTranscription = { text ->
                    handleInputTranscription(text)
                }

                onOutputTranscription = { text ->
                    handleOutputTranscription(text)
                }

                onThoughtSummary = { text ->
                    handleThoughtSummary(text)
                }

                onUsageMetadata = { metadata ->
                    scope.launch {
                        _usageMetadata.emit(metadata)
                    }
                }

                onSessionResumptionUpdate = { update ->
                    scope.launch {
                        _sessionResumptionUpdates.emit(update)
                    }
                }

                onGoAway = { notice ->
                    scope.launch {
                        _goAwayNotices.emit(notice)
                    }
                }
            }

            lastHandledConnectionState = liveService?.connectionState?.value

            liveServiceStateJob = scope.launch {
                liveService?.connectionState?.collect { state ->
                    handleConnectionStateChangeIfNew(state)
                }
            }

            // Request audio focus
            audioManager?.requestAudioFocus()

            // Start connection
            val mergedTools = when {
                tools.isNullOrEmpty() -> extraToolDeclarations.ifEmpty { null }
                extraToolDeclarations.isEmpty() -> tools
                else -> tools + extraToolDeclarations
            }
            liveService?.connect(mergedTools)

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session", e)
            _errorMessage.value = e.message ?: "Startup failed"
            _sessionState.value = SessionState.ERROR
            return false
        }
    }

    /**
     * Stop Live Session
     */
    fun stop() {
        if (_sessionState.value == SessionState.IDLE) {
            Log.w(TAG, "Session not started")
            return
        }

        Log.d(TAG, "Stopping Live session")
        _sessionState.value = SessionState.DISCONNECTING

        try {
            liveServiceStateJob?.cancel()
            liveServiceStateJob = null

            // Stop recording and playback
            audioManager?.release()
            audioManager = null

            // Disconnect WebSocket
            liveService?.disconnect()
            liveService = null

            _sessionState.value = SessionState.IDLE
            Log.d(TAG, "Session ended")

        } catch (e: Exception) {
            Log.e(TAG, "Error occurred while stopping session", e)
            _sessionState.value = SessionState.IDLE
        }
    }

    /**
     * Pause session (stop recording but keep connection)
     */
    fun pause() {
        if (_sessionState.value != SessionState.ACTIVE) {
            Log.w(TAG, "Session not active, cannot pause")
            return
        }

        Log.d(TAG, "Pausing session")
        audioManager?.stopRecording()
        _sessionState.value = SessionState.PAUSED
    }

    /**
     * Resume session
     */
    fun resume() {
        if (_sessionState.value != SessionState.PAUSED) {
            Log.w(TAG, "Session is not paused")
            return
        }

        Log.d(TAG, "Resuming session")
        audioManager?.startRecording()
        _sessionState.value = SessionState.ACTIVE
    }

    // ========== Data Transmission ==========

    /**
     * Send video frame
     *
     * @param jpegData Image data in JPEG format
     */
    override fun sendVideoFrame(jpegData: ByteArray) {
        if (_sessionState.value != SessionState.ACTIVE) {
            Log.w(TAG, "Session not active, cannot send video")
            return
        }

        liveService?.sendVideoFrame(jpegData)
    }

    override fun sendAudioChunk(audioData: ByteArray) {
        if (_sessionState.value != SessionState.ACTIVE) {
            Log.w(TAG, "Session not active, cannot send audio chunk")
            return
        }

        liveService?.sendAudio(audioData)
    }

    /**
     * Send tool call response
     *
     * @param toolCallId Tool Call ID
     * @param result Execution result
     */
    fun sendToolResponse(toolCallId: String, result: JSONObject) {
        liveService?.sendToolResponse(toolCallId, result)
    }

    /**
     * Manually end input turn
     * Used when not using automatic VAD
     */
    override fun endOfTurn() {
        liveService?.endOfTurn()
    }

    // ========== Event Handling ==========

    /**
     * Handle connection state changes
     */
    private fun handleConnectionStateChangeIfNew(state: GeminiLiveService.ConnectionState) {
        if (lastHandledConnectionState == state) {
            return
        }
        lastHandledConnectionState = state
        handleConnectionStateChange(state)
    }

    private fun handleConnectionStateChange(state: GeminiLiveService.ConnectionState) {
        Log.d(TAG, "Connection state changed: $state")

        when (state) {
            GeminiLiveService.ConnectionState.DISCONNECTED -> {
                if (_sessionState.value != SessionState.DISCONNECTING) {
                    // Unexpected disconnection
                    _sessionState.value = SessionState.ERROR
                    _errorMessage.value = "Connection lost"
                }
            }

            GeminiLiveService.ConnectionState.CONNECTING,
            GeminiLiveService.ConnectionState.SETTING_UP -> {
                _sessionState.value = SessionState.CONNECTING
            }

            GeminiLiveService.ConnectionState.READY -> {
                _sessionState.value = SessionState.ACTIVE

                if (capturePhoneAudio) {
                    scope.launch {
                        delay(100)
                        audioManager?.startRecording()
                    }
                }

                Log.d(TAG, "Session ready")
            }

            GeminiLiveService.ConnectionState.ERROR -> {
                _sessionState.value = SessionState.ERROR
                _errorMessage.value = liveService?.errorMessage?.value ?: "Connection error"
            }
        }
    }

    /**
     * Handle received AI audio
     */
    private fun handleAudioReceived(audioData: ByteArray) {
        scope.launch {
            _outputAudio.emit(audioData)
        }

        if (playbackPhoneAudio) {
            audioManager?.playAudio(audioData)
        }
    }

    /**
     * Handle turn completion
     */
    private fun handleTurnComplete() {
        Log.d(TAG, "AI turn complete")

        if (playbackPhoneAudio) {
            audioManager?.finishPlayback()
        }

        // Emit event
        scope.launch {
            _turnComplete.emit(Unit)
        }
    }

    /**
     * Handle interruption
     */
    private fun handleInterrupted() {
        Log.d(TAG, "AI interrupted by user")

        if (playbackPhoneAudio) {
            audioManager?.stopPlayback()
        }

        // Emit event
        scope.launch {
            _interrupted.emit(Unit)
        }
    }

    /**
     * Handle tool calls - route to ToolCallRouter for execution
     */
    private fun handleToolCalls(calls: List<GeminiLiveService.ToolCall>) {
        Log.d(TAG, "Received ${calls.size} tool calls")

        // Send event to upper layer (PhoneAIService can listen)
        scope.launch {
            _toolCalls.emit(calls)
        }

        // Route execution through ToolCallRouter
        // Router will automatically return results via toolResults flow
        val router = toolCallRouter
        if (router != null) {
            router.handleToolCalls(calls)
        } else {
            // Fallback: If router is not initialized, send default responses directly
            Log.w(TAG, "ToolCallRouter not initialized, sending default responses")
            for (call in calls) {
                val result = JSONObject().apply {
                    put("status", "success")
                    put("message", "Tool ${call.name} acknowledged (router not ready)")
                }
                sendToolResponse(call.id, result)
            }
        }
    }

    /**
     * Handle user speech transcription
     */
    private fun handleInputTranscription(text: String) {
        Log.d(TAG, "User said: $text")
        scope.launch {
            _inputTranscription.emit(text)
        }
    }

    /**
     * Handle AI response transcription
     */
    private fun handleOutputTranscription(text: String) {
        Log.d(TAG, "AI said: $text")
        scope.launch {
            _outputTranscription.emit(text)
        }
    }

    private fun handleThoughtSummary(text: String) {
        Log.d(TAG, "AI thought summary: $text")
        scope.launch {
            _thoughtSummary.emit(text)
        }
    }

    // ========== Resource Release ==========

    /**
     * Release all resources
     */
    override fun release() {
        Log.d(TAG, "Releasing GeminiLiveSession resources")

        // Cancel all in-flight tool calls
        toolCallRouter?.cancelAll()
        toolCallRouter?.release()
        toolCallRouter = null
        liveServiceStateJob?.cancel()
        liveServiceStateJob = null

        stop()

        audioManager?.abandonAudioFocus()

        scope.cancel()
    }

    /**
     * Get ToolCallRouter (for external custom handler registration)
     */
    fun getToolCallRouter(): ToolCallRouter? = toolCallRouter

    // ========== Tool Declarations ==========

    /**
     * Get default tool declarations (delegated to ToolDeclarations)
     */
    fun getDefaultToolDeclarations(): List<JSONObject> {
        return ToolDeclarations.allDeclarations()
    }
}
