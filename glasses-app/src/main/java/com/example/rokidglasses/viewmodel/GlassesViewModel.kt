package com.example.rokidglasses.viewmodel

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rokidcommon.Constants
import com.example.rokidcommon.protocol.ConnectionState
import com.example.rokidcommon.protocol.LiveControlInputSource
import com.example.rokidcommon.protocol.LiveRagDisplayMode
import com.example.rokidcommon.protocol.LiveRagSplitScrollMode
import com.example.rokidcommon.protocol.LiveSessionControlPayload
import com.example.rokidcommon.protocol.Message
import com.example.rokidcommon.protocol.MessageType
import com.example.rokidcommon.protocol.LiveTranscriptPayload
import com.example.rokidcommon.protocol.LiveTranscriptRole
import com.example.rokidcommon.protocol.resolveEffectiveLiveRagDisplayMode
import com.example.rokidglasses.R
import com.example.rokidglasses.sdk.CameraMode
import com.example.rokidglasses.sdk.CxrServiceManager
import com.example.rokidglasses.sdk.UnifiedCameraManager
import com.example.rokidglasses.service.BluetoothClientState
import com.example.rokidglasses.service.BluetoothSppClient
import com.example.rokidglasses.service.photo.GlassesCameraManager
import com.example.rokidglasses.service.photo.ImageCompressor
import com.example.rokidglasses.service.photo.PhotoTransferProtocol
import com.example.rokidglasses.service.photo.createPhotoTransferProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class GlassesUiState(
    val isConnected: Boolean = false,
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val isSendingInput: Boolean = false,
    val isAwaitingAnalysis: Boolean = false,
    val hasVisibleOutput: Boolean = false,
    val sleepModeEnabled: Boolean = false,
    val responseFontScalePercent: Int = DEFAULT_RESPONSE_FONT_SCALE_PERCENT,
    val displayUsesResponseFontScale: Boolean = false,
    val displayText: String = "",
    val hintText: String = "",
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val userTranscript: String = "",
    val aiResponse: String = "",
    // Pagination for long text
    val currentPage: Int = 0,
    val totalPages: Int = 1,
    val isPaginated: Boolean = false,
    // Bluetooth related
    val bluetoothState: BluetoothClientState = BluetoothClientState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val availableDevices: List<BluetoothDevice> = emptyList(),
    // CXR connected phone name (to help identify correct SPP device)
    val cxrConnectedPhoneName: String? = null,
    // Photo capture state
    val isCapturingPhoto: Boolean = false,
    val photoTransferProgress: Float = 0f,
    // Gemini Live mode state
    val liveModeEnabled: Boolean = false,
    val liveMinimalUiEnabled: Boolean = false,
    val isLiveModeActive: Boolean = false,
    val liveInputSource: LiveControlInputSource = LiveControlInputSource.UNKNOWN,
    val experimentalLiveMicTuningEnabled: Boolean = false,
    val experimentalLiveMicProfile: Int = 0,
    val liveTranscription: String = "",
    val liveRagEnabled: Boolean = false,
    val liveRagDisplayMode: LiveRagDisplayMode = LiveRagDisplayMode.RAG_RESULT_ONLY,
    val liveRagSplitScrollMode: LiveRagSplitScrollMode = LiveRagSplitScrollMode.AUTO,
    val liveRagAutoScrollSpeedLevel: Int = 2,
    val liveAssistantText: String = "",
    val liveRagText: String = "",
    val liveRagIsFinal: Boolean = false,
)

private fun shouldKeepLiveDisplayText(
    isLiveModeActive: Boolean,
    liveRagEnabled: Boolean,
    liveRagDisplayMode: LiveRagDisplayMode,
    liveRagText: String,
    liveAssistantText: String,
): Boolean {
    if (!isLiveModeActive) {
        return false
    }
    return when (
        resolveEffectiveLiveRagDisplayMode(
            liveRagEnabled = liveRagEnabled,
            configuredMode = liveRagDisplayMode,
        )
    ) {
        LiveRagDisplayMode.RAG_RESULT_ONLY -> liveRagText.isNotBlank()
        LiveRagDisplayMode.SPLIT_LIVE_AND_RAG ->
            liveAssistantText.isNotBlank() || liveRagText.isNotBlank()
    }
}

internal data class LiveMicCaptureConfig(
    val sourceCandidates: List<Int>,
    val enableNoiseSuppressor: Boolean,
    val enableAutomaticGainControl: Boolean,
    val enableAcousticEchoCanceler: Boolean,
)

internal fun resolveLiveMicCaptureConfig(
    experimentEnabled: Boolean,
    selectedProfile: Int,
): LiveMicCaptureConfig {
    if (!experimentEnabled) {
        return LiveMicCaptureConfig(
            sourceCandidates = listOf(MediaRecorder.AudioSource.MIC),
            enableNoiseSuppressor = false,
            enableAutomaticGainControl = false,
            enableAcousticEchoCanceler = false,
        )
    }

    return when (selectedProfile.coerceIn(0, 2)) {
        0 -> LiveMicCaptureConfig(
            sourceCandidates = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
            ),
            enableNoiseSuppressor = true,
            enableAutomaticGainControl = true,
            enableAcousticEchoCanceler = true,
        )

        1 -> LiveMicCaptureConfig(
            sourceCandidates = listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
            ),
            enableNoiseSuppressor = true,
            enableAutomaticGainControl = true,
            enableAcousticEchoCanceler = false,
        )

        else -> LiveMicCaptureConfig(
            sourceCandidates = listOf(MediaRecorder.AudioSource.MIC),
            enableNoiseSuppressor = false,
            enableAutomaticGainControl = false,
            enableAcousticEchoCanceler = false,
        )
    }
}

/**
 * Glasses ViewModel
 * 
 * New Architecture:
 * 1. Glasses record -> Collect PCM data
 * 2. Recording ends -> Send via Bluetooth SPP to phone
 * 3. Phone processes AI -> Returns result
 * 4. Glasses display result
 */
class GlassesViewModel(
    private val context: Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "GlassesViewModel"
        // Max characters per page for glasses display
        private const val MAX_CHARS_PER_PAGE = 120
        private const val MAX_LINES_PER_PAGE = 4
    }

    private enum class ResponseDisplayMode {
        CHAT,
        PHOTO_ANALYSIS
    }

    private enum class LiveCameraStreamingMode {
        OFF,
        MANUAL,
        INTERVAL,
        REALTIME
    }
    
    private val _uiState = MutableStateFlow(GlassesUiState(
        displayText = context.getString(R.string.say_hey_rokid),
        hintText = context.getString(R.string.tap_touchpad_record)
    ))
    val uiState: StateFlow<GlassesUiState> = _uiState.asStateFlow()
    private val _liveRagManualScrollCommands =
        MutableSharedFlow<LiveRagManualScrollCommand>(extraBufferCapacity = 1)
    val liveRagManualScrollCommands = _liveRagManualScrollCommands.asSharedFlow()
    
    // Store full AI response for pagination
    private var fullAiResponse: String = ""
    private var responsePages: List<String> = emptyList()
    private var currentResponseDisplayMode = ResponseDisplayMode.CHAT
    
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    
    // Bluetooth SPP client - connects to phone
    private val bluetoothClient = BluetoothSppClient(context, viewModelScope)
    
    // Camera manager for photo capture
    // Glasses use Camera2 API to directly access local camera (no longer using CXR-M SDK)
    // CXR-M SDK is for phone side to remotely control glasses camera
    private var cameraManager: UnifiedCameraManager? = null
    
    // CXR-S SDK service manager (for communication with phone)
    private var cxrServiceManager: CxrServiceManager? = null
    
    // Photo transfer protocol
    private var photoTransferProtocol: PhotoTransferProtocol? = null
    
    // Audio buffer - collects recording data
    private val audioBuffer = ByteArrayOutputStream()
    private var liveAudioTrack: AudioTrack? = null
    private var liveInputAudioRecord: AudioRecord? = null
    private var liveInputNoiseSuppressor: NoiseSuppressor? = null
    private var liveInputAutomaticGainControl: AutomaticGainControl? = null
    private var liveInputAcousticEchoCanceler: AcousticEchoCanceler? = null
    private var liveInputCaptureJob: Job? = null
    private var liveInputCaptureActive = false
    private val liveAudioPlaybackQueue = LiveAudioPlaybackQueue(
        scope = viewModelScope,
        dispatcher = Dispatchers.IO,
    ) { audioData ->
        writeLiveAudioChunk(audioData)
    }
    
    // ========== Gemini Live Mode Related ==========
    
    // Live mode activation status (notified by phone)
    private var isLiveModeActive = false
    
    // Real-time video streaming job (~1fps camera frame capture and send to phone)
    private var videoStreamingJob: Job? = null
    
    // Video streaming frame rate control (milliseconds)
    private var videoFrameIntervalMs = 1000L
    private var liveCameraMode = LiveCameraStreamingMode.OFF
    
    // Video streaming JPEG compression quality (0-100)
    private val videoFrameQuality = 50
    
    init {
        initializeBluetooth()
        initializeCamera()
        initializeCxrService()
    }
    
    /**
     * Initialize CXR-S SDK service (Glasses Side)
     * Used to receive messages and commands from phone side
     */
    private fun initializeCxrService() {
        if (CxrServiceManager.isSdkAvailable()) {
            cxrServiceManager = CxrServiceManager.getInstance()
            val initialized = cxrServiceManager?.initialize() == true
            Log.d(TAG, "CXR-S Service initialized: $initialized")
            
            if (initialized) {
                // Listen for connection state
                viewModelScope.launch {
                    cxrServiceManager?.connectionState?.collect { state ->
                        when (state) {
                            is CxrServiceManager.ConnectionState.Connected -> {
                                Log.d(TAG, "CXR connected to: ${state.deviceName}")
                                // Store CXR-connected phone name for SPP device selection
                                _uiState.update { it.copy(cxrConnectedPhoneName = state.deviceName) }
                            }
                            is CxrServiceManager.ConnectionState.Disconnected -> {
                                Log.d(TAG, "CXR disconnected")
                                _uiState.update { it.copy(cxrConnectedPhoneName = null) }
                            }
                        }
                    }
                }
            }
        } else {
            Log.w(TAG, "CXR-S SDK not available")
        }
    }
    
    private fun initializeCamera() {
        viewModelScope.launch {
            // Glasses directly use Camera2 API (no longer depends on CXR-M SDK)
            // CXR-M SDK camera functionality is for phone side to remotely control glasses camera
            cameraManager = UnifiedCameraManager(
                context = context,
                preferredMode = CameraMode.CAMERA2  // Use local Camera2 API directly
            )
            
            val result = cameraManager?.initialize()
            
            if (result?.isSuccess == true) {
                val cameraType = cameraManager?.getCameraTypeName() ?: "Unknown"
                Log.d(TAG, "Camera manager initialized: $cameraType")
            } else {
                Log.w(TAG, "Camera manager initialization failed: ${result?.exceptionOrNull()?.message}")
            }
        }
    }
    
    private fun initializeBluetooth() {
        // Listen to Bluetooth connection state
        viewModelScope.launch {
            bluetoothClient.connectionState.collect { state ->
                Log.d(TAG, "Bluetooth state changed: $state")
                val clearLiveOutput = state != BluetoothClientState.CONNECTED
                if (clearLiveOutput) {
                    liveCameraMode = LiveCameraStreamingMode.OFF
                    stopVideoStreaming()
                    stopLiveInputCapture()
                    stopLiveAudioPlayback()
                }
                val connectionState = when (state) {
                    BluetoothClientState.DISCONNECTED -> ConnectionState.DISCONNECTED
                    BluetoothClientState.CONNECTING -> ConnectionState.CONNECTING
                    BluetoothClientState.CONNECTED -> ConnectionState.CONNECTED
                }
                
                _uiState.update { current ->
                    val nextState = current.copy(
                        bluetoothState = state,
                        connectionState = connectionState,
                        isConnected = state == BluetoothClientState.CONNECTED,
                        isSendingInput = false,
                        isAwaitingAnalysis = false,
                        hasVisibleOutput = false,
                        displayUsesResponseFontScale = false,
                        liveTranscription = if (clearLiveOutput) "" else current.liveTranscription,
                        liveAssistantText = if (clearLiveOutput) "" else current.liveAssistantText,
                        liveRagText = if (clearLiveOutput) "" else current.liveRagText,
                        liveRagIsFinal = if (clearLiveOutput) false else current.liveRagIsFinal,
                    )

                    when (state) {
                        BluetoothClientState.DISCONNECTED -> nextState.copy(
                            displayText = context.getString(R.string.not_connected),
                            hintText = context.getString(R.string.please_connect_phone),
                        )

                        BluetoothClientState.CONNECTING -> nextState.copy(
                            displayText = context.getString(R.string.connecting_status),
                            hintText = context.getString(R.string.please_wait),
                        )

                        BluetoothClientState.CONNECTED -> withIdlePrompt(
                            state = nextState.copy(
                                displayText = context.getString(R.string.connected_ready),
                                hintText = context.getString(R.string.tap_touchpad_start),
                            ),
                            defaultDisplayText = context.getString(R.string.connected_ready),
                            defaultHintText = context.getString(R.string.tap_touchpad_start),
                        )
                    }
                }

                syncLiveInputCapture(isBluetoothConnected = state == BluetoothClientState.CONNECTED)
            }
        }
        
        // Listen to connected device name
        viewModelScope.launch {
            bluetoothClient.connectedDeviceName.collect { name ->
                _uiState.update { it.copy(connectedDeviceName = name) }
            }
        }
        
        // Listen to messages from phone
        viewModelScope.launch {
            bluetoothClient.messageFlow.collect { message ->
                handlePhoneMessage(message)
            }
        }
        
        // Get paired devices
        refreshPairedDevices()
    }
    
    /**
     * Refresh paired devices list
     */
    fun refreshPairedDevices() {
        val devices = bluetoothClient.getPairedDevices()
        _uiState.update { it.copy(availableDevices = devices) }
        Log.d(TAG, "Found ${devices.size} paired devices")
    }
    
    /**
     * Connect to specified device
     */
    fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to device: ${device.name}")
        bluetoothClient.connect(device)
    }
    
    /**
     * Disconnect Bluetooth connection
     */
    fun disconnectBluetooth() {
        bluetoothClient.disconnect()
    }
    
    fun startRecording() {
        if (liveInputCaptureActive) {
            Log.d(TAG, "Ignoring legacy recording start while glasses live capture is active")
            return
        }

        // Check if connected
        if (_uiState.value.bluetoothState != BluetoothClientState.CONNECTED) {
            _uiState.update { it.copy(
                hasVisibleOutput = true,
                displayText = context.getString(R.string.please_connect_phone),
                hintText = context.getString(R.string.select_paired_device)
            ) }
            return
        }
        
        if (_uiState.value.isListening) return
        
        // Clear audio buffer and reset pagination
        audioBuffer.reset()
        resetPagination()
        
        _uiState.update { it.copy(
            isListening = true,
            isSendingInput = false,
            isAwaitingAnalysis = false,
            hasVisibleOutput = false,
            displayText = context.getString(R.string.listening),
            hintText = context.getString(R.string.tap_stop_recording),
            userTranscript = "",
            aiResponse = ""
        ) }
        
        Log.d(TAG, "Start recording")
        
        // Check RECORD_AUDIO permission before proceeding
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            _uiState.update { it.copy(
                displayText = context.getString(R.string.mic_permission_required),
                isListening = false,
                isSendingInput = false,
                isAwaitingAnalysis = false,
                hasVisibleOutput = true
            ) }
            return
        }
        
        // Notify phone that recording started
        viewModelScope.launch {
            bluetoothClient.sendVoiceStart()
        }
        
        // Start recording
        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    Constants.AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                // Verify permission again before AudioRecord initialization
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "RECORD_AUDIO permission lost during initialization")
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(
                            displayText = context.getString(R.string.mic_permission_required),
                            isListening = false,
                            isSendingInput = false,
                            isAwaitingAnalysis = false,
                            hasVisibleOutput = true
                        ) }
                    }
                    return@launch
                }
                
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    Constants.AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                
                // Verify AudioRecord was initialized successfully
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed")
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(
                            displayText = "Failed to initialize microphone",
                            isListening = false,
                            isSendingInput = false,
                            isAwaitingAnalysis = false,
                            hasVisibleOutput = true
                        ) }
                    }
                    audioRecord?.release()
                    audioRecord = null
                    return@launch
                }
                
                audioRecord?.startRecording()
                Log.d(TAG, "AudioRecord started recording")
                
                val buffer = ByteArray(Constants.AUDIO_BUFFER_SIZE)
                val liveStreaming = isLiveModeActive
                
                while (isActive && _uiState.value.isListening) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        if (liveStreaming) {
                            val chunk = buffer.copyOf(readSize)
                            bluetoothClient.sendVoiceData(chunk)
                        } else {
                            synchronized(audioBuffer) {
                                audioBuffer.write(buffer, 0, readSize)
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Recording ended, collected ${audioBuffer.size()} bytes")
                
            } catch (e: SecurityException) {
                Log.e(TAG, "Microphone permission error", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(
                        displayText = context.getString(R.string.mic_permission_required),
                        isListening = false,
                        isSendingInput = false,
                        isAwaitingAnalysis = false,
                        hasVisibleOutput = true
                    ) }
                }
            } finally {
                // Safely stop and release AudioRecord
                try {
                    if (audioRecord?.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord?.stop()
                    }
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "AudioRecord stop failed (already stopped or invalid state)", e)
                }
                try {
                    audioRecord?.release()
                } catch (e: Exception) {
                    Log.w(TAG, "AudioRecord release failed", e)
                }
                audioRecord = null
            }
        }
    }
    
    fun stopRecording() {
        _uiState.update { it.copy(
            isListening = false,
            isProcessing = true,
            isSendingInput = true,
            isAwaitingAnalysis = false,
            hasVisibleOutput = false,
            displayText = context.getString(R.string.sending_audio),
            hintText = context.getString(R.string.please_wait)
        ) }
        
        recordingJob?.cancel()
        recordingJob = null
        
        Log.d(TAG, "Stop recording, sending audio to phone")
        
        // Send audio via Bluetooth to phone for processing
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val liveStreaming = isLiveModeActive

                if (liveStreaming) {
                    val success = bluetoothClient.sendVoiceEnd(ByteArray(0))
                    withContext(Dispatchers.Main) {
                        if (success) {
                            _uiState.update { it.copy(
                                isProcessing = false,
                                isSendingInput = false,
                                isAwaitingAnalysis = true,
                                hasVisibleOutput = false,
                                displayText = context.getString(R.string.waiting_phone),
                                hintText = context.getString(R.string.ai_thinking)
                            ) }
                        } else {
                            _uiState.update { it.copy(
                                isProcessing = false,
                                isSendingInput = false,
                                isAwaitingAnalysis = false,
                                hasVisibleOutput = true,
                                displayText = context.getString(R.string.send_failed),
                                hintText = context.getString(R.string.reconnect_try_again)
                            ) }
                        }
                    }
                    return@launch
                }

                // Get recording data
                val audioData: ByteArray
                synchronized(audioBuffer) {
                    audioData = audioBuffer.toByteArray()
                }
                
                Log.d(TAG, "Audio data size: ${audioData.size} bytes")
                
                if (audioData.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(
                            isProcessing = false,
                            isSendingInput = false,
                            isAwaitingAnalysis = false,
                            hasVisibleOutput = true,
                            displayText = context.getString(R.string.no_voice_detected),
                            hintText = context.getString(R.string.please_try_again)
                        ) }
                    }
                    return@launch
                }
                
                // Send audio data to phone
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(
                        isSendingInput = true,
                        isAwaitingAnalysis = false,
                        hasVisibleOutput = false,
                        displayText = context.getString(R.string.sending)
                    ) }
                }
                
                val success = bluetoothClient.sendVoiceEnd(audioData)
                
                if (!success) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(
                            isProcessing = false,
                            isSendingInput = false,
                            isAwaitingAnalysis = false,
                            hasVisibleOutput = true,
                            displayText = context.getString(R.string.send_failed),
                            hintText = context.getString(R.string.reconnect_try_again)
                        ) }
                    }
                    return@launch
                }
                
                Log.d(TAG, "Audio sent to phone, waiting for processing result")
                
                // Update UI state, waiting for phone response
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(
                        isSendingInput = false,
                        isAwaitingAnalysis = true,
                        hasVisibleOutput = false,
                        displayText = context.getString(R.string.waiting_phone),
                        hintText = context.getString(R.string.ai_thinking)
                    ) }
                }
                
                // Phone will update UI via handlePhoneMessage callback when done
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending audio", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(
                        isProcessing = false,
                        isSendingInput = false,
                        isAwaitingAnalysis = false,
                        hasVisibleOutput = true,
                        displayText = context.getString(R.string.error_prefix, e.message ?: ""),
                        hintText = context.getString(R.string.please_try_again)
                    ) }
                }
            }
        }
    }
    
    fun toggleRecording() {
        if (_uiState.value.isListening) {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    /**
     * Handle message from phone
     */
    private fun handlePhoneMessage(message: Message) {
        Log.d(TAG, "Received from phone: ${message.type}, payload: ${message.payload}")
        
        when (message.type) {
            MessageType.AI_PROCESSING -> {
                _uiState.update { it.copy(
                    isProcessing = true,
                    isSendingInput = false,
                    isAwaitingAnalysis = true,
                    hasVisibleOutput = false,
                    displayUsesResponseFontScale = false,
                    displayText = message.payload ?: context.getString(R.string.processing)
                ) }
            }
            
            MessageType.USER_TRANSCRIPT -> {
                // User's speech recognized by phone
                _uiState.update { it.copy(
                    userTranscript = message.payload ?: "",
                    isSendingInput = false,
                    isAwaitingAnalysis = true,
                    hasVisibleOutput = false,
                    displayUsesResponseFontScale = false,
                    displayText = context.getString(R.string.you_said, message.payload ?: "")
                ) }
            }
            
            MessageType.AI_RESPONSE_TEXT -> {
                if (_uiState.value.isLiveModeActive &&
                    _uiState.value.liveRagDisplayMode == LiveRagDisplayMode.SPLIT_LIVE_AND_RAG
                ) {
                    val finalAssistantText = message.payload ?: ""
                    _uiState.update { it.copy(
                        aiResponse = finalAssistantText,
                        liveAssistantText = finalAssistantText,
                        hasVisibleOutput = finalAssistantText.isNotBlank() || it.liveRagText.isNotBlank(),
                        displayUsesResponseFontScale = true,
                        liveRagIsFinal = it.liveRagIsFinal,
                        isPaginated = false,
                        currentPage = 0,
                        totalPages = 1,
                    ) }
                } else {
                    handleAiResponseText(message)
                }
            }
            
            MessageType.AI_RESPONSE_TTS -> {
                // Play AI voice
                message.binaryData?.let { audioData ->
                    playAudio(audioData)
                }
            }

            MessageType.LIVE_AUDIO_CHUNK -> {
                message.binaryData?.let { audioData ->
                    liveAudioPlaybackQueue.enqueue(audioData)
                }
            }

            MessageType.LIVE_AUDIO_STOP -> {
                stopLiveAudioPlayback()
            }
             
            MessageType.AI_ERROR -> {
                _uiState.update { it.copy(
                    isProcessing = false,
                    isSendingInput = false,
                    isAwaitingAnalysis = false,
                    hasVisibleOutput = true,
                    displayUsesResponseFontScale = false,
                    displayText = context.getString(R.string.error_prefix, message.payload ?: ""),
                    hintText = context.getString(R.string.please_try_again)
                ) }
            }
            
            MessageType.DISPLAY_TEXT -> {
                _uiState.update { it.copy(
                    hasVisibleOutput = true,
                    displayUsesResponseFontScale = false,
                    displayText = message.payload ?: ""
                ) }
            }
            
            MessageType.DISPLAY_CLEAR -> {
                _uiState.update {
                    withIdlePrompt(
                        state = it.copy(
                            isSendingInput = false,
                            isAwaitingAnalysis = false,
                            hasVisibleOutput = false,
                            displayUsesResponseFontScale = false,
                            liveRagText = "",
                            liveRagIsFinal = false,
                            displayText = "",
                        ),
                        defaultDisplayText = "",
                        defaultHintText = context.getString(R.string.tap_touchpad_start),
                    )
                }
            }

            MessageType.SLEEP_MODE_CONFIG -> {
                _uiState.update {
                    it.copy(
                        sleepModeEnabled = message.payload?.equals("true", ignoreCase = true) == true
                    )
                }
            }

            MessageType.LIVE_MINIMAL_UI_CONFIG -> {
                _uiState.update {
                    it.copy(
                        liveMinimalUiEnabled = message.payload?.equals("true", ignoreCase = true) == true
                    )
                }
            }

            MessageType.RESPONSE_FONT_SCALE_CONFIG -> {
                val syncedPercent = snapResponseFontScalePercent(
                    message.payload?.toIntOrNull() ?: DEFAULT_RESPONSE_FONT_SCALE_PERCENT
                )
                _uiState.update { it.copy(responseFontScalePercent = syncedPercent) }
                reapplyCurrentResponseLayout()
            }
            
            MessageType.HEARTBEAT -> {
                viewModelScope.launch {
                    bluetoothClient.sendMessage(
                        Message(type = MessageType.HEARTBEAT_ACK)
                    )
                }
            }
            
            MessageType.CAPTURE_PHOTO -> {
                // Phone requested to capture photo
                Log.d(TAG, "Phone requested photo capture")
                captureAndSendPhoto()
            }
            
            // ========== Gemini Live Mode Message Handling ==========
            
            MessageType.LIVE_SESSION_START -> {
                // Phone started Live mode
                Log.d(TAG, "Live session started by phone")
                val liveSessionPayload = resolveLiveSessionControlPayload(
                    rawPayload = message.payload,
                    sessionActive = true,
                )
                isLiveModeActive = true
                applyLiveSessionConfig(liveSessionPayload)
                _uiState.update {
                    withIdlePrompt(
                        state = it.copy(
                            liveModeEnabled = liveSessionPayload.liveModeEnabled,
                            isLiveModeActive = true,
                            liveInputSource = liveSessionPayload.effectiveInputSource,
                            displayUsesResponseFontScale = false,
                            liveTranscription = "",
                            liveRagEnabled = liveSessionPayload.liveRagEnabled,
                            liveAssistantText = "",
                            liveRagText = "",
                            liveRagIsFinal = false,
                            liveRagDisplayMode = liveSessionPayload.ragDisplayMode,
                            hasVisibleOutput = !liveSessionPayload.canToggleFromGlasses,
                            displayText = if (liveSessionPayload.canToggleFromGlasses) {
                                ""
                            } else {
                                "🎙️ Live mode activated"
                            },
                            hintText = if (liveSessionPayload.canToggleFromGlasses) {
                                ""
                            } else {
                                "Real-time voice conversation..."
                            },
                        ),
                        defaultDisplayText = "🎙️ Live mode activated",
                        defaultHintText = "Real-time voice conversation...",
                    )
                }
                if (liveCameraMode == LiveCameraStreamingMode.INTERVAL ||
                    liveCameraMode == LiveCameraStreamingMode.REALTIME) {
                    startVideoStreaming()
                }
                syncLiveInputCapture()
            }
            
            MessageType.LIVE_SESSION_END -> {
                // Phone ended Live mode
                Log.d(TAG, "Live session ended by phone")
                val liveSessionPayload = resolveLiveSessionControlPayload(
                    rawPayload = message.payload,
                    sessionActive = false,
                )
                isLiveModeActive = false
                liveCameraMode = LiveCameraStreamingMode.OFF
                stopVideoStreaming()
                stopLiveAudioPlayback()
                applyLiveSessionConfig(liveSessionPayload)
                _uiState.update {
                    withIdlePrompt(
                        state = it.copy(
                            liveModeEnabled = liveSessionPayload.liveModeEnabled,
                            isLiveModeActive = false,
                            liveInputSource = liveSessionPayload.effectiveInputSource,
                            displayUsesResponseFontScale = false,
                            liveTranscription = "",
                            liveRagEnabled = liveSessionPayload.liveRagEnabled,
                            liveAssistantText = "",
                            liveRagText = "",
                            liveRagIsFinal = false,
                            liveRagDisplayMode = liveSessionPayload.ragDisplayMode,
                            hasVisibleOutput = false,
                            displayText = "",
                            hintText = "",
                        ),
                        defaultDisplayText = context.getString(R.string.tap_touchpad_start),
                        defaultHintText = context.getString(R.string.tap_touchpad_record),
                    )
                }
                syncLiveInputCapture()
            }
            
            MessageType.LIVE_TRANSCRIPTION -> {
                val parsed = LiveTranscriptPayload.fromPayload(message.payload)
                val text = parsed?.text ?: (message.payload ?: "")
                Log.d(TAG, "Live transcription: $text")
                when (parsed?.role) {
                    LiveTranscriptRole.USER -> {
                        _uiState.update { state ->
                            val isNewLiveTurn = state.isLiveModeActive &&
                                text.isNotBlank() &&
                                (state.userTranscript.isBlank() || !text.startsWith(state.userTranscript))
                            val clearedAssistantText = if (isNewLiveTurn) "" else state.liveAssistantText
                            val clearedRagText = if (isNewLiveTurn) "" else state.liveRagText
                            state.copy(
                                liveAssistantText = clearedAssistantText,
                                liveRagText = clearedRagText,
                                liveRagIsFinal = if (isNewLiveTurn) false else state.liveRagIsFinal,
                                userTranscript = text,
                                liveTranscription = text,
                                hasVisibleOutput = text.isNotBlank(),
                                displayUsesResponseFontScale = true,
                                displayText = if (
                                    shouldKeepLiveDisplayText(
                                        isLiveModeActive = state.isLiveModeActive,
                                        liveRagEnabled = state.liveRagEnabled,
                                        liveRagDisplayMode = state.liveRagDisplayMode,
                                        liveRagText = clearedRagText,
                                        liveAssistantText = clearedAssistantText,
                                    )
                                ) {
                                    state.displayText
                                } else {
                                    text
                                },
                            )
                        }
                    }

                    LiveTranscriptRole.THINKING -> {
                        _uiState.update { it.copy(
                            liveTranscription = text,
                            hasVisibleOutput = text.isNotBlank(),
                            displayUsesResponseFontScale = true,
                            displayText = if (
                                shouldKeepLiveDisplayText(
                                    isLiveModeActive = it.isLiveModeActive,
                                    liveRagEnabled = it.liveRagEnabled,
                                    liveRagDisplayMode = it.liveRagDisplayMode,
                                    liveRagText = it.liveRagText,
                                    liveAssistantText = it.liveAssistantText,
                                )
                            ) {
                                it.displayText
                            } else {
                                context.getString(R.string.live_thinking_summary_format, text)
                            },
                        ) }
                    }

                    LiveTranscriptRole.ASSISTANT, null -> {
                        _uiState.update { it.copy(
                            aiResponse = text,
                            liveTranscription = text,
                            liveAssistantText = text,
                            hasVisibleOutput = text.isNotBlank() || it.liveRagText.isNotBlank(),
                            displayUsesResponseFontScale = true,
                            displayText = if (
                                shouldKeepLiveDisplayText(
                                    isLiveModeActive = it.isLiveModeActive,
                                    liveRagEnabled = it.liveRagEnabled,
                                    liveRagDisplayMode = it.liveRagDisplayMode,
                                    liveRagText = it.liveRagText,
                                    liveAssistantText = it.liveAssistantText,
                                )
                            ) it.displayText else text,
                        ) }
                    }

                    LiveTranscriptRole.RAG -> {
                        _uiState.update { state ->
                            if (!state.liveRagEnabled) {
                                state
                            } else {
                                val effectiveMode = resolveEffectiveLiveRagDisplayMode(
                                    liveRagEnabled = state.liveRagEnabled,
                                    configuredMode = state.liveRagDisplayMode,
                                )
                                state.copy(
                                    liveRagText = text,
                                    liveRagIsFinal = parsed?.isFinal == true,
                                    hasVisibleOutput = state.liveAssistantText.isNotBlank() || text.isNotBlank(),
                                    displayUsesResponseFontScale = true,
                                    displayText = if (
                                        state.isLiveModeActive &&
                                        effectiveMode == LiveRagDisplayMode.SPLIT_LIVE_AND_RAG
                                    ) state.displayText else text,
                                )
                            }
                        }
                    }
                }
            }
            
            MessageType.PHOTO_ANALYSIS_RESULT -> {
                // Received photo analysis result from phone
                Log.d(TAG, "Photo analysis result: ${message.payload}")
                val analysisText = message.payload ?: context.getString(R.string.photo_analysis_no_result)
                
                // Clear capturing state and show result
                _uiState.update { it.copy(
                    isCapturingPhoto = false,
                    photoTransferProgress = 0f,
                    isProcessing = false,
                    isSendingInput = false,
                    isAwaitingAnalysis = false,
                    hasVisibleOutput = true
                ) }

                applyResponseDisplay(
                    responseText = analysisText,
                    mode = ResponseDisplayMode.PHOTO_ANALYSIS
                )
            }
            
            MessageType.REMOTE_RECORD_START -> {
                // Phone requested glasses to start recording
                Log.d(TAG, "Remote recording start command from phone")
                if (!_uiState.value.isListening) {
                    startRecording()
                } else {
                    Log.d(TAG, "Already recording, ignoring remote start command")
                }
            }
            
            MessageType.REMOTE_RECORD_STOP -> {
                // Phone requested glasses to stop recording
                Log.d(TAG, "Remote recording stop command from phone")
                if (_uiState.value.isListening) {
                    stopRecording()
                } else {
                    Log.d(TAG, "Not recording, ignoring remote stop command")
                }
            }
            
            // HEARTBEAT_ACK is handled internally by BluetoothSppClient
            MessageType.HEARTBEAT_ACK -> { /* no-op */ }
            
            else -> { 
                Log.d(TAG, "Unhandled message type: ${message.type}")
            }
        }
    }
    
    /**
     * Handle AI response text message with pagination support
     */
    private fun handleAiResponseText(message: Message) {
        applyResponseDisplay(
            responseText = message.payload ?: "",
            mode = ResponseDisplayMode.CHAT
        )
    }
    
    private fun playAudio(audioData: ByteArray) {
        try {
            val tempFile = kotlin.io.path.createTempFile(
                directory = context.cacheDir.toPath(),
                prefix = "rokid_tts_",
                suffix = ".mp3"
            ).toFile()
            tempFile.writeBytes(audioData)

            MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setOnCompletionListener { mp ->
                    mp.release()
                    tempFile.delete()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    tempFile.delete()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play TTS audio", e)
        }
    }

    private fun writeLiveAudioChunk(audioData: ByteArray) {
        try {
            val track = liveAudioTrack ?: createLiveAudioTrack().also { created ->
                liveAudioTrack = created
            }
            val result = track.write(audioData, 0, audioData.size)
            if (result < 0) {
                Log.w(TAG, "AudioTrack write failed: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play live audio", e)
            stopLiveAudioPlayback()
        }
    }

    private fun createLiveAudioTrack(): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            24_000,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(24_000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBufferSize * 2, 4096))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        return track
    }

    private fun stopLiveAudioPlayback() {
        liveAudioPlaybackQueue.stop()
        try {
            liveAudioTrack?.pause()
            liveAudioTrack?.flush()
            liveAudioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop live audio playback", e)
        } finally {
            liveAudioTrack = null
        }
    }

    private fun syncLiveInputCapture(
        isBluetoothConnected: Boolean = _uiState.value.bluetoothState == BluetoothClientState.CONNECTED,
        liveModeEnabled: Boolean = _uiState.value.liveModeEnabled,
        sessionActive: Boolean = isLiveModeActive,
        inputSource: LiveControlInputSource = _uiState.value.liveInputSource,
    ) {
        val shouldCapture = shouldCaptureLiveInputFromGlasses(
            isBluetoothConnected = isBluetoothConnected,
            liveModeEnabled = liveModeEnabled,
            sessionActive = sessionActive,
            inputSource = inputSource,
        )

        if (shouldCapture) {
            startLiveInputCapture()
        } else {
            stopLiveInputCapture()
        }
    }

    private fun startLiveInputCapture() {
        if (liveInputCaptureActive || liveInputCaptureJob?.isActive == true) {
            return
        }
        if (_uiState.value.isListening) {
            Log.d(TAG, "Skipping auto live capture because legacy recording is already active")
            return
        }
        if (_uiState.value.bluetoothState != BluetoothClientState.CONNECTED) {
            return
        }
        if (
            ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Cannot start glasses live capture without RECORD_AUDIO permission")
            return
        }

        liveInputCaptureActive = true
        Log.d(TAG, "Starting background live capture from glasses microphone")
        viewModelScope.launch {
            bluetoothClient.sendVoiceStart()
        }

        liveInputCaptureJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    Constants.AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                if (
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "RECORD_AUDIO permission missing during live capture initialization")
                    liveInputCaptureActive = false
                    return@launch
                }

                val captureConfig = resolveLiveMicCaptureConfig(
                    experimentEnabled = _uiState.value.experimentalLiveMicTuningEnabled,
                    selectedProfile = _uiState.value.experimentalLiveMicProfile,
                )
                val record = createLiveInputAudioRecord(
                    bufferSize = bufferSize,
                    config = captureConfig,
                )
                liveInputAudioRecord = record

                if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "Failed to initialize glasses live AudioRecord")
                    liveInputCaptureActive = false
                    releaseLiveInputAudioRecord()
                    return@launch
                }

                record.startRecording()
                val buffer = ByteArray(Constants.AUDIO_BUFFER_SIZE)

                while (isActive && liveInputCaptureActive) {
                    val readSize = record.read(buffer, 0, buffer.size)
                    if (readSize > 0) {
                        bluetoothClient.sendVoiceData(buffer.copyOf(readSize))
                    }
                }
            } catch (e: SecurityException) {
                liveInputCaptureActive = false
                Log.e(TAG, "Microphone permission error during glasses live capture", e)
            } catch (e: CancellationException) {
                if (GlassesLiveCaptureFailurePolicy.isExpectedStop(e)) {
                    Log.d(TAG, "Glasses live capture cancelled during normal stop")
                } else {
                    liveInputCaptureActive = false
                    Log.e(TAG, "Failed during glasses live capture", e)
                }
            } catch (e: Exception) {
                liveInputCaptureActive = false
                Log.e(TAG, "Failed during glasses live capture", e)
            } finally {
                releaseLiveInputAudioRecord()
            }
        }
    }

    private fun stopLiveInputCapture() {
        if (!liveInputCaptureActive && liveInputCaptureJob == null && liveInputAudioRecord == null) {
            return
        }

        liveInputCaptureActive = false
        liveInputCaptureJob?.cancel()
        liveInputCaptureJob = null
        releaseLiveInputAudioRecord()
        Log.d(TAG, "Stopped background live capture from glasses microphone")
    }

    private fun releaseLiveInputAudioRecord() {
        val record = liveInputAudioRecord ?: return
        liveInputAudioRecord = null
        releaseLiveInputAudioEffects()
        try {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Live input AudioRecord stop failed", e)
        }
        try {
            record.release()
        } catch (e: Exception) {
            Log.w(TAG, "Live input AudioRecord release failed", e)
        }
    }

    private fun createLiveInputAudioRecord(
        bufferSize: Int,
        config: LiveMicCaptureConfig,
    ): AudioRecord? {
        config.sourceCandidates.distinct().forEach { source ->
            val record = try {
                AudioRecord(
                    source,
                    Constants.AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create live AudioRecord for source=$source", e)
                null
            }

            if (record == null) {
                return@forEach
            }

            if (record.state == AudioRecord.STATE_INITIALIZED) {
                Log.d(TAG, "Using live input audio source=$source")
                configureLiveInputAudioEffects(record, config)
                return record
            }

            try {
                record.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release rejected live AudioRecord source=$source", e)
            }
        }

        return null
    }

    private fun configureLiveInputAudioEffects(
        record: AudioRecord,
        config: LiveMicCaptureConfig,
    ) {
        releaseLiveInputAudioEffects()
        val sessionId = record.audioSessionId
        if (sessionId == AudioRecord.ERROR || sessionId == AudioRecord.ERROR_BAD_VALUE) {
            return
        }

        if (config.enableNoiseSuppressor && NoiseSuppressor.isAvailable()) {
            liveInputNoiseSuppressor = runCatching {
                NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }.getOrNull()
        }
        if (config.enableAutomaticGainControl && AutomaticGainControl.isAvailable()) {
            liveInputAutomaticGainControl = runCatching {
                AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }.getOrNull()
        }
        if (config.enableAcousticEchoCanceler && AcousticEchoCanceler.isAvailable()) {
            liveInputAcousticEchoCanceler = runCatching {
                AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }.getOrNull()
        }
    }

    private fun releaseLiveInputAudioEffects() {
        runCatching { liveInputNoiseSuppressor?.release() }
        liveInputNoiseSuppressor = null
        runCatching { liveInputAutomaticGainControl?.release() }
        liveInputAutomaticGainControl = null
        runCatching { liveInputAcousticEchoCanceler?.release() }
        liveInputAcousticEchoCanceler = null
    }

    private fun applyLiveSessionConfig(payload: LiveSessionControlPayload) {
        val liveRagSplitScrollMode = payload.splitScrollMode
        val liveRagAutoScrollSpeedLevel = payload.autoScrollSpeedLevel.coerceIn(0, 4)
        val experimentalLiveMicProfile = payload.experimentalLiveMicProfile.coerceIn(0, 2)
        if (payload.cameraMode.isNullOrBlank()) {
            liveCameraMode = LiveCameraStreamingMode.REALTIME
            videoFrameIntervalMs = 1000L
            _uiState.update {
                it.copy(
                    liveModeEnabled = payload.liveModeEnabled,
                    liveInputSource = payload.effectiveInputSource,
                    experimentalLiveMicTuningEnabled = payload.experimentalLiveMicTuningEnabled,
                    experimentalLiveMicProfile = experimentalLiveMicProfile,
                    liveRagEnabled = false,
                    liveRagDisplayMode = LiveRagDisplayMode.RAG_RESULT_ONLY,
                    liveRagSplitScrollMode = liveRagSplitScrollMode,
                    liveRagAutoScrollSpeedLevel = liveRagAutoScrollSpeedLevel,
                )
            }
            return
        }

        try {
            liveCameraMode = when (payload.cameraMode) {
                "OFF" -> LiveCameraStreamingMode.OFF
                "MANUAL" -> LiveCameraStreamingMode.MANUAL
                "INTERVAL" -> LiveCameraStreamingMode.INTERVAL
                "REALTIME" -> LiveCameraStreamingMode.REALTIME
                else -> LiveCameraStreamingMode.REALTIME
            }
            val intervalSec = (payload.cameraIntervalSec ?: 1).coerceAtLeast(1)
            videoFrameIntervalMs = intervalSec * 1000L
            val liveRagEnabled = payload.liveRagEnabled
            val ragDisplayMode = resolveEffectiveLiveRagDisplayMode(
                liveRagEnabled = liveRagEnabled,
                configuredMode = payload.ragDisplayMode,
            )
            _uiState.update {
                it.copy(
                    liveModeEnabled = payload.liveModeEnabled,
                    liveInputSource = payload.effectiveInputSource,
                    experimentalLiveMicTuningEnabled = payload.experimentalLiveMicTuningEnabled,
                    experimentalLiveMicProfile = experimentalLiveMicProfile,
                    liveRagEnabled = liveRagEnabled,
                    liveRagDisplayMode = ragDisplayMode,
                    liveRagSplitScrollMode = liveRagSplitScrollMode,
                    liveRagAutoScrollSpeedLevel = liveRagAutoScrollSpeedLevel,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply live session config: ${e.message}")
            liveCameraMode = LiveCameraStreamingMode.REALTIME
            videoFrameIntervalMs = 1000L
            _uiState.update {
                it.copy(
                    liveModeEnabled = payload.liveModeEnabled,
                    liveInputSource = payload.effectiveInputSource,
                    experimentalLiveMicTuningEnabled = payload.experimentalLiveMicTuningEnabled,
                    experimentalLiveMicProfile = experimentalLiveMicProfile,
                    liveRagEnabled = false,
                    liveRagDisplayMode = LiveRagDisplayMode.RAG_RESULT_ONLY,
                    liveRagSplitScrollMode = liveRagSplitScrollMode,
                    liveRagAutoScrollSpeedLevel = liveRagAutoScrollSpeedLevel,
                )
            }
        }
    }
    
    /**
     * Paginate long text for glasses display
     * Splits text into pages based on character limit and line count
     */
    private fun paginateText(text: String): List<String> {
        return paginateResponseText(
            text = text,
            fontScalePercent = _uiState.value.responseFontScalePercent,
            baseMaxCharsPerPage = MAX_CHARS_PER_PAGE,
            maxLinesPerPage = MAX_LINES_PER_PAGE
        )
    }

    private fun applyResponseDisplay(
        responseText: String,
        mode: ResponseDisplayMode,
        preserveCurrentPage: Boolean = false
    ) {
        fullAiResponse = responseText
        currentResponseDisplayMode = mode
        responsePages = paginateText(responseText)

        val isPaginated = responsePages.size > 1
        val targetPage = if (preserveCurrentPage) {
            _uiState.value.currentPage.coerceAtMost((responsePages.size - 1).coerceAtLeast(0))
        } else {
            0
        }
        val pageText = responsePages.getOrElse(targetPage) { responseText }
        val displayText = if (mode == ResponseDisplayMode.PHOTO_ANALYSIS && isPaginated && targetPage == 0) {
            "$pageText (1/${responsePages.size})"
        } else {
            pageText
        }
        val hintText = resolveResponseHint(
            state = _uiState.value,
            isChatMode = mode == ResponseDisplayMode.CHAT,
            isPaginated = isPaginated,
            isLastPage = !isPaginated || targetPage == responsePages.lastIndex,
            swipeForMoreHint = context.getString(R.string.swipe_for_more),
            swipePagesHint = context.getString(R.string.swipe_left_right_pages),
            tapContinueHint = context.getString(R.string.tap_continue),
            photoSinglePageHint = context.getString(R.string.tap_touchpad_start),
            liveActiveHint = context.getString(R.string.live_phone_speak_hint),
            liveResumeHint = context.getString(R.string.live_phone_resume_hint),
        )

        _uiState.update { it.copy(
            isProcessing = false,
            isSendingInput = false,
            isAwaitingAnalysis = false,
            hasVisibleOutput = true,
            aiResponse = responseText,
            liveAssistantText = "",
            liveRagText = "",
            liveRagIsFinal = false,
            displayUsesResponseFontScale = true,
            displayText = displayText,
            hintText = hintText,
            currentPage = targetPage,
            totalPages = responsePages.size.coerceAtLeast(1),
            isPaginated = isPaginated
        ) }
    }

    private fun reapplyCurrentResponseLayout() {
        if (!_uiState.value.displayUsesResponseFontScale || fullAiResponse.isBlank()) {
            return
        }

        applyResponseDisplay(
            responseText = fullAiResponse,
            mode = currentResponseDisplayMode,
            preserveCurrentPage = true
        )
    }
    
    /**
     * Navigate to next page (swipe down)
     */
    fun nextPage() {
        val currentState = _uiState.value
        if (currentState.isPaginated && currentState.currentPage < currentState.totalPages - 1) {
            val newPage = currentState.currentPage + 1
            _uiState.update { it.copy(
                currentPage = newPage,
                displayText = responsePages.getOrElse(newPage) { "" },
                hintText = resolveResponseHint(
                    state = it,
                    isChatMode = currentResponseDisplayMode == ResponseDisplayMode.CHAT,
                    isPaginated = true,
                    isLastPage = newPage == currentState.totalPages - 1,
                    swipeForMoreHint = context.getString(R.string.swipe_for_more),
                    swipePagesHint = context.getString(R.string.swipe_left_right_pages),
                    tapContinueHint = context.getString(R.string.tap_continue),
                    photoSinglePageHint = context.getString(R.string.tap_touchpad_start),
                    liveActiveHint = context.getString(R.string.live_phone_speak_hint),
                    liveResumeHint = context.getString(R.string.live_phone_resume_hint),
                )
            ) }
        }
    }
    
    /**
     * Navigate to previous page (swipe up)
     */
    fun previousPage() {
        val currentState = _uiState.value
        if (currentState.isPaginated && currentState.currentPage > 0) {
            val newPage = currentState.currentPage - 1
            _uiState.update { it.copy(
                currentPage = newPage,
                displayText = responsePages.getOrElse(newPage) { "" },
                hintText = resolveResponseHint(
                    state = it,
                    isChatMode = currentResponseDisplayMode == ResponseDisplayMode.CHAT,
                    isPaginated = true,
                    isLastPage = false,
                    swipeForMoreHint = context.getString(R.string.swipe_for_more),
                    swipePagesHint = context.getString(R.string.swipe_left_right_pages),
                    tapContinueHint = context.getString(R.string.tap_continue),
                    photoSinglePageHint = context.getString(R.string.tap_touchpad_start),
                    liveActiveHint = context.getString(R.string.live_phone_speak_hint),
                    liveResumeHint = context.getString(R.string.live_phone_resume_hint),
                )
            ) }
        }
    }
    
    /**
     * Dismiss pagination and return to normal state
     */
    fun dismissPagination() {
        resetPagination()
        _uiState.update {
            withIdlePrompt(
                state = it.copy(
                    isProcessing = false,
                    isSendingInput = false,
                    isAwaitingAnalysis = false,
                    hasVisibleOutput = false,
                    liveAssistantText = "",
                    liveRagText = "",
                    liveRagIsFinal = false,
                    displayText = context.getString(R.string.tap_touchpad_start),
                    hintText = context.getString(R.string.tap_touchpad_record),
                ),
                defaultDisplayText = context.getString(R.string.tap_touchpad_start),
                defaultHintText = context.getString(R.string.tap_touchpad_record),
            )
        }
    }

    fun dismissOutput() {
        dismissPagination()
    }

    fun handleDirectionalNavigation(command: LiveRagManualScrollCommand): Boolean {
        val state = _uiState.value
        if (state.isPaginated) {
            when (command) {
                LiveRagManualScrollCommand.UP -> previousPage()
                LiveRagManualScrollCommand.DOWN -> nextPage()
            }
            return true
        }

        val livePanelContent = resolveCurrentLivePanelContent(state)
        if (!livePanelContent.manualScrollRightPanel) {
            return false
        }

        _liveRagManualScrollCommands.tryEmit(command)
        return true
    }

    fun handlePrimaryAction() {
        val state = _uiState.value
        when (resolvePrimaryAction(state)) {
            PrimaryActionOutcome.TOGGLE_LIVE_SESSION -> togglePhoneLiveSession()
            PrimaryActionOutcome.NEXT_PAGE -> nextPage()
            PrimaryActionOutcome.DISMISS_OUTPUT -> dismissOutput()
            PrimaryActionOutcome.TOGGLE_RECORDING -> toggleRecording()
        }
    }

    private fun togglePhoneLiveSession() {
        viewModelScope.launch {
            bluetoothClient.sendMessage(
                Message(type = MessageType.LIVE_SESSION_TOGGLE_REQUEST)
            )
        }
    }

    private fun resolveCurrentLivePanelContent(
        state: GlassesUiState = _uiState.value,
    ): GlassesLivePanelContent {
        return resolveLivePanelContent(
            isLiveModeActive = state.isLiveModeActive,
            liveRagEnabled = state.liveRagEnabled,
            ragDisplayMode = state.liveRagDisplayMode,
            splitScrollMode = state.liveRagSplitScrollMode,
            assistantText = state.liveAssistantText,
            ragText = state.liveRagText,
            ragTextFinalized = state.liveRagIsFinal,
        )
    }

    private fun resolveLiveSessionControlPayload(
        rawPayload: String?,
        sessionActive: Boolean,
    ): LiveSessionControlPayload {
        val parsed = LiveSessionControlPayload.fromPayload(rawPayload)
        val hasLiveModeEnabled = rawPayload?.contains("\"liveModeEnabled\"") == true

        return (parsed ?: LiveSessionControlPayload(
            sessionActive = sessionActive,
            liveModeEnabled = sessionActive,
        )).copy(
            sessionActive = sessionActive,
            liveModeEnabled = if (hasLiveModeEnabled) {
                parsed?.liveModeEnabled ?: false
            } else {
                sessionActive
            },
        )
    }

    private fun withIdlePrompt(
        state: GlassesUiState,
        defaultDisplayText: String,
        defaultHintText: String,
    ): GlassesUiState {
        val prompt = resolveIdlePrompt(
            state = state,
            defaultDisplayText = defaultDisplayText,
            defaultHintText = defaultHintText,
            livePhoneActiveHint = context.getString(R.string.live_phone_speak_hint),
            livePhoneResumeHint = context.getString(R.string.live_phone_resume_hint),
        )
        return state.copy(
            displayText = prompt.displayText,
            hintText = prompt.hintText,
        )
    }
    
    /**
     * Reset pagination state (when starting new conversation)
     */
    private fun resetPagination() {
        fullAiResponse = ""
        responsePages = emptyList()
        _uiState.update { it.copy(
            currentPage = 0,
            totalPages = 1,
            isPaginated = false
        ) }
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        stopLiveInputCapture()
        videoStreamingJob?.cancel()
        audioRecord?.release()
        stopLiveAudioPlayback()
        bluetoothClient.disconnect()
        cameraManager?.release()
        cxrServiceManager?.release()
    }
    
    // ==================== Live Mode: Real-time Video Streaming ====================
    
    /**
     * Start real-time video streaming
     * Captures camera frames at ~1fps, compresses to JPEG and sends to phone via Bluetooth,
     * which then forwards to GeminiLiveService's WebSocket.
     *
     * Notes:
     * - Requires CAMERA permission
     * - Uses Camera2 API (UnifiedCameraManager)
     * - JPEG compression quality reduced to 50% to reduce Bluetooth bandwidth
     * - Errors won't interrupt streaming, only logged
     */
    private fun startVideoStreaming() {
        if (videoStreamingJob?.isActive == true) {
            Log.w(TAG, "Video streaming already active")
            return
        }
        
        if (cameraManager == null) {
            Log.w(TAG, "Camera not available for video streaming")
            return
        }
        
        Log.d(TAG, "Starting Live mode video streaming (~1fps, quality=$videoFrameQuality)")
        
        videoStreamingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && isLiveModeActive) {
                try {
                    // Capture one camera frame
                    val rawImageData = cameraManager?.capturePhoto()
                    
                    if (rawImageData != null) {
                        // Compress to low-quality JPEG to reduce transfer size (video frames use smaller dimensions)
                        val compressedFrame = withContext(Dispatchers.Default) {
                            ImageCompressor.compressForTransfer(
                                rawImageData,
                                targetWidth = 640,
                                targetHeight = 480,
                                quality = videoFrameQuality
                            )
                        }
                        
                        Log.d(TAG, "Video frame captured: ${compressedFrame.size} bytes")
                        
                        // Send VIDEO_FRAME message to phone via Bluetooth
                        bluetoothClient.sendMessage(
                            Message(
                                type = MessageType.VIDEO_FRAME,
                                binaryData = compressedFrame
                            )
                        )
                    } else {
                        Log.w(TAG, "Failed to capture video frame")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error capturing video frame", e)
                }
                
                // Wait until next frame (~1fps)
                delay(videoFrameIntervalMs)
            }
            
            Log.d(TAG, "Video streaming loop ended")
        }
    }
    
    /**
     * Stop real-time video streaming
     */
    private fun stopVideoStreaming() {
        videoStreamingJob?.cancel()
        videoStreamingJob = null
        Log.d(TAG, "Video streaming stopped")
    }
    
    // ==================== Photo Capture ====================
    
    /**
     * Capture photo and send to phone for AI analysis.
     * Triggered by camera key press or voice command.
     */
    fun captureAndSendPhoto() {
        if (_uiState.value.isCapturingPhoto) {
            Log.w(TAG, "Photo capture already in progress")
            return
        }
        
        if (_uiState.value.bluetoothState != BluetoothClientState.CONNECTED) {
            Log.w(TAG, "Not connected to phone")
            _uiState.update { it.copy(
                hasVisibleOutput = true,
                displayText = context.getString(R.string.bluetooth_not_connected),
                hintText = context.getString(R.string.connect_phone_first)
            ) }
            return
        }
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    isCapturingPhoto = true,
                    isProcessing = true,
                    isSendingInput = false,
                    isAwaitingAnalysis = false,
                    hasVisibleOutput = false,
                    displayText = context.getString(R.string.capturing_photo),
                    hintText = context.getString(R.string.please_wait_short)
                ) }
                
                // Step 1: Capture photo
                val cameraType = cameraManager?.getCameraTypeName() ?: "Unknown"
                Log.d(TAG, "Capturing photo using: $cameraType")
                val rawImageData = cameraManager?.capturePhoto()
                
                if (rawImageData == null) {
                    val cameraState = cameraManager?.cameraState?.value
                    Log.e(TAG, "Failed to capture photo. Camera state: $cameraState")
                    _uiState.update { it.copy(
                        isCapturingPhoto = false,
                        isProcessing = false,
                        isSendingInput = false,
                        isAwaitingAnalysis = false,
                        hasVisibleOutput = true,
                        displayText = context.getString(R.string.capture_failed),
                        hintText = context.getString(R.string.capture_failed_hint)
                    ) }
                    return@launch
                }
                
                Log.d(TAG, "Photo captured: ${rawImageData.size} bytes using $cameraType")
                
                // Step 2: Compress photo
                _uiState.update { it.copy(
                    isCapturingPhoto = false,
                    isSendingInput = true,
                    isAwaitingAnalysis = false,
                    hasVisibleOutput = false,
                    displayText = context.getString(R.string.compressing_photo)
                ) }
                val compressedData = withContext(Dispatchers.Default) {
                    ImageCompressor.compressForTransfer(rawImageData)
                }
                Log.d(TAG, "Compressed: ${rawImageData.size} -> ${compressedData.size} bytes")
                
                // Step 3: Send to phone
                _uiState.update { it.copy(
                    isCapturingPhoto = false,
                    isSendingInput = true,
                    isAwaitingAnalysis = false,
                    hasVisibleOutput = false,
                    displayText = context.getString(R.string.transferring_photo),
                    photoTransferProgress = 0f
                ) }
                
                val socket = bluetoothClient.connectedSocket
                if (socket == null || !socket.isConnected) {
                    throw IllegalStateException("Bluetooth socket not connected")
                }
                
                photoTransferProtocol = socket.createPhotoTransferProtocol { current, total ->
                    val progress = current.toFloat() / total
                    _uiState.update { it.copy(photoTransferProgress = progress) }
                }
                
                val result = photoTransferProtocol?.sendPhoto(compressedData)
                
                result?.fold(
                    onSuccess = { stats ->
                        Log.d(TAG, "Photo transfer complete: $stats")
                        _uiState.update { it.copy(
                            isCapturingPhoto = false,
                            isSendingInput = false,
                            isAwaitingAnalysis = true,
                            hasVisibleOutput = false,
                            displayText = context.getString(R.string.photo_sent_waiting_ai),
                            hintText = context.getString(R.string.please_wait_short)
                        ) }
                        // Phone will send AI response via Bluetooth
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Photo transfer failed", error)
                        _uiState.update { it.copy(
                            isCapturingPhoto = false,
                            isProcessing = false,
                            isSendingInput = false,
                            isAwaitingAnalysis = false,
                            hasVisibleOutput = true,
                            displayText = context.getString(R.string.transfer_failed, error.message ?: ""),
                            hintText = context.getString(R.string.please_retry)
                        ) }
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Photo capture error", e)
                _uiState.update { it.copy(
                    isCapturingPhoto = false,
                    isProcessing = false,
                    isSendingInput = false,
                    isAwaitingAnalysis = false,
                    hasVisibleOutput = true,
                    displayText = context.getString(R.string.error_message, e.message ?: ""),
                    hintText = context.getString(R.string.please_retry)
                ) }
            }
        }
    }
    
    /**
     * ViewModel Factory
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GlassesViewModel::class.java)) {
                return GlassesViewModel(context.applicationContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
