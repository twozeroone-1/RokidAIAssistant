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
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rokidcommon.Constants
import com.example.rokidcommon.protocol.ConnectionState
import com.example.rokidcommon.protocol.LiveRagDisplayMode
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val isLiveModeActive: Boolean = false,
    val liveTranscription: String = "",
    val liveRagEnabled: Boolean = false,
    val liveRagDisplayMode: LiveRagDisplayMode = LiveRagDisplayMode.RAG_RESULT_ONLY,
    val liveAssistantText: String = "",
    val liveRagText: String = ""
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
                val connectionState = when (state) {
                    BluetoothClientState.DISCONNECTED -> ConnectionState.DISCONNECTED
                    BluetoothClientState.CONNECTING -> ConnectionState.CONNECTING
                    BluetoothClientState.CONNECTED -> ConnectionState.CONNECTED
                }
                
                _uiState.update { it.copy(
                    bluetoothState = state,
                    connectionState = connectionState,
                    isConnected = state == BluetoothClientState.CONNECTED,
                    isSendingInput = false,
                    isAwaitingAnalysis = false,
                    hasVisibleOutput = false,
                    displayText = when (state) {
                        BluetoothClientState.DISCONNECTED -> context.getString(R.string.not_connected)
                        BluetoothClientState.CONNECTING -> context.getString(R.string.connecting_status)
                        BluetoothClientState.CONNECTED -> context.getString(R.string.connected_ready)
                    },
                    hintText = when (state) {
                        BluetoothClientState.DISCONNECTED -> context.getString(R.string.please_connect_phone)
                        BluetoothClientState.CONNECTING -> context.getString(R.string.please_wait)
                        BluetoothClientState.CONNECTED -> context.getString(R.string.tap_touchpad_start)
                    }
                ) }
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
                        displayUsesResponseFontScale = false,
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
                    viewModelScope.launch(Dispatchers.IO) {
                        playLiveAudio(audioData)
                    }
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
                _uiState.update { it.copy(
                    isSendingInput = false,
                    isAwaitingAnalysis = false,
                    hasVisibleOutput = false,
                    displayUsesResponseFontScale = false,
                    displayText = "",
                    hintText = context.getString(R.string.tap_touchpad_start)
                ) }
            }

            MessageType.SLEEP_MODE_CONFIG -> {
                _uiState.update {
                    it.copy(
                        sleepModeEnabled = message.payload?.equals("true", ignoreCase = true) == true
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
                isLiveModeActive = true
                applyLiveSessionConfig(message.payload)
                _uiState.update { it.copy(
                    isLiveModeActive = true,
                    displayUsesResponseFontScale = false,
                    displayText = "🎙️ Live mode activated",
                    hintText = "Real-time voice conversation...",
                    liveTranscription = "",
                    liveRagEnabled = it.liveRagEnabled,
                    liveAssistantText = "",
                    liveRagText = "",
                    hasVisibleOutput = true
                ) }
                if (liveCameraMode == LiveCameraStreamingMode.INTERVAL ||
                    liveCameraMode == LiveCameraStreamingMode.REALTIME) {
                    startVideoStreaming()
                }
            }
            
            MessageType.LIVE_SESSION_END -> {
                // Phone ended Live mode
                Log.d(TAG, "Live session ended by phone")
                isLiveModeActive = false
                liveCameraMode = LiveCameraStreamingMode.OFF
                stopVideoStreaming()
                stopLiveAudioPlayback()
                _uiState.update { it.copy(
                    isLiveModeActive = false,
                    displayUsesResponseFontScale = false,
                    displayText = "Live mode ended",
                    hintText = context.getString(R.string.tap_touchpad_start),
                    liveTranscription = "",
                    liveRagEnabled = false,
                    liveAssistantText = "",
                    liveRagText = "",
                    liveRagDisplayMode = LiveRagDisplayMode.RAG_RESULT_ONLY,
                    hasVisibleOutput = true
                ) }
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
                                userTranscript = text,
                                liveTranscription = text,
                                hasVisibleOutput = text.isNotBlank(),
                                displayUsesResponseFontScale = false,
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
                            displayUsesResponseFontScale = false,
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
                            displayUsesResponseFontScale = false,
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
                                    hasVisibleOutput = state.liveAssistantText.isNotBlank() || text.isNotBlank(),
                                    displayUsesResponseFontScale = false,
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

    private fun playLiveAudio(audioData: ByteArray) {
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

    private fun applyLiveSessionConfig(payload: String?) {
        if (payload.isNullOrBlank()) {
            liveCameraMode = LiveCameraStreamingMode.REALTIME
            videoFrameIntervalMs = 1000L
            _uiState.update {
                it.copy(
                    liveRagEnabled = false,
                    liveRagDisplayMode = LiveRagDisplayMode.RAG_RESULT_ONLY,
                )
            }
            return
        }

        try {
            val json = JSONObject(payload)
            liveCameraMode = when (json.optString("cameraMode")) {
                "OFF" -> LiveCameraStreamingMode.OFF
                "MANUAL" -> LiveCameraStreamingMode.MANUAL
                "INTERVAL" -> LiveCameraStreamingMode.INTERVAL
                "REALTIME" -> LiveCameraStreamingMode.REALTIME
                else -> LiveCameraStreamingMode.REALTIME
            }
            val intervalSec = json.optInt("cameraIntervalSec", 1).coerceAtLeast(1)
            videoFrameIntervalMs = intervalSec * 1000L
            val liveRagEnabled = json.optBoolean("liveRagEnabled", false)
            val ragDisplayMode = resolveEffectiveLiveRagDisplayMode(
                liveRagEnabled = liveRagEnabled,
                configuredMode = LiveRagDisplayMode.fromRaw(json.optString("ragDisplayMode")),
            )
            _uiState.update {
                it.copy(
                    liveRagEnabled = liveRagEnabled,
                    liveRagDisplayMode = ragDisplayMode,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse live session config: ${e.message}")
            liveCameraMode = LiveCameraStreamingMode.REALTIME
            videoFrameIntervalMs = 1000L
            _uiState.update {
                it.copy(
                    liveRagEnabled = false,
                    liveRagDisplayMode = LiveRagDisplayMode.RAG_RESULT_ONLY,
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
        val hintText = when (mode) {
            ResponseDisplayMode.CHAT -> {
                if (isPaginated) context.getString(R.string.swipe_for_more)
                else context.getString(R.string.tap_continue)
            }
            ResponseDisplayMode.PHOTO_ANALYSIS -> {
                if (isPaginated) context.getString(R.string.swipe_left_right_pages)
                else context.getString(R.string.tap_touchpad_start)
            }
        }

        _uiState.update { it.copy(
            isProcessing = false,
            isSendingInput = false,
            isAwaitingAnalysis = false,
            hasVisibleOutput = true,
            aiResponse = responseText,
            liveAssistantText = "",
            liveRagText = "",
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
            val isLastPage = newPage == currentState.totalPages - 1
            _uiState.update { it.copy(
                currentPage = newPage,
                displayText = responsePages.getOrElse(newPage) { "" },
                hintText = if (isLastPage) context.getString(R.string.tap_continue) 
                          else context.getString(R.string.swipe_for_more)
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
                hintText = context.getString(R.string.swipe_for_more)
            ) }
        }
    }
    
    /**
     * Dismiss pagination and return to normal state
     */
    fun dismissPagination() {
        resetPagination()
        _uiState.update { it.copy(
            isProcessing = false,
            isSendingInput = false,
            isAwaitingAnalysis = false,
            hasVisibleOutput = false,
            liveAssistantText = "",
            liveRagText = "",
            displayText = context.getString(R.string.tap_touchpad_start),
            hintText = context.getString(R.string.tap_touchpad_record)
        ) }
    }

    fun dismissOutput() {
        dismissPagination()
    }

    fun handlePrimaryAction() {
        val state = _uiState.value
        when (resolvePrimaryAction(state)) {
            PrimaryActionOutcome.NEXT_PAGE -> nextPage()
            PrimaryActionOutcome.DISMISS_OUTPUT -> dismissOutput()
            PrimaryActionOutcome.TOGGLE_RECORDING -> toggleRecording()
        }
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
