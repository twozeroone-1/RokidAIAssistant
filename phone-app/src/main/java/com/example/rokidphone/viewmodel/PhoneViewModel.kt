package com.example.rokidphone.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rokidcommon.protocol.ConnectionState
import com.example.rokidcommon.protocol.LiveTranscriptPayload
import com.example.rokidcommon.protocol.LiveTranscriptRole
import com.example.rokidcommon.protocol.MessageType
import com.example.rokidphone.ConversationItem
import com.example.rokidphone.data.db.RecordingRepository
import com.example.rokidphone.data.db.RecordingSource
import com.example.rokidphone.data.db.RecordingState
import com.example.rokidphone.service.BluetoothConnectionState
import com.example.rokidphone.service.ServiceBridge
import com.example.rokidphone.service.ai.LiveSessionStatusSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "PhoneViewModel"

data class PhoneUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val bluetoothState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED,
    val connectedGlassesName: String? = null,
    val isServiceRunning: Boolean = false,
    val processingStatus: String? = null,
    val liveUsageSummary: String? = null,
    val liveSessionStatus: LiveSessionStatusSnapshot? = null,
    val conversations: List<ConversationItem> = emptyList(),
    val isScanning: Boolean = false,
    val availableDevices: List<String> = emptyList(),
    val showApiKeyWarning: Boolean = false,  // Flag to show API key warning dialog
    val showInitialSetup: Boolean = false,   // Flag to show initial setup dialog (no API key configured)
    val latestPhotoPath: String? = null,     // Path to the latest received photo
    val recordingState: RecordingState = RecordingState.Idle  // Recording state
)

class PhoneViewModel(
    application: Application,
    private val recordingStateOverride: Flow<RecordingState>? = null,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        recordingStateOverride = null,
    )
    
    private val _uiState = MutableStateFlow(PhoneUiState())
    val uiState: StateFlow<PhoneUiState> = _uiState.asStateFlow()
    
    // Recording repository
    private val recordingRepository by lazy { RecordingRepository.getInstance(application, viewModelScope) }
    
    init {
        // Listen to recording state
        viewModelScope.launch {
            val recordingStateFlow = recordingStateOverride ?: recordingRepository.recordingState
            recordingStateFlow.collect { state ->
                _uiState.update { it.copy(recordingState = state) }
            }
        }
        
        // Listen to service state
        viewModelScope.launch {
            ServiceBridge.serviceStateFlow.collect { isRunning ->
                _uiState.update { it.copy(isServiceRunning = isRunning) }
            }
        }
        
        // Listen to Bluetooth connection state
        viewModelScope.launch {
            ServiceBridge.bluetoothStateFlow.collect { state ->
                Log.d(TAG, "Bluetooth state updated: $state")
                val connectionState = when (state) {
                    BluetoothConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
                    BluetoothConnectionState.LISTENING -> ConnectionState.DISCONNECTED
                    BluetoothConnectionState.CONNECTING -> ConnectionState.CONNECTING
                    BluetoothConnectionState.CONNECTED -> ConnectionState.CONNECTED
                }

                val clearedProcessingStatus = when (state) {
                    BluetoothConnectionState.DISCONNECTED,
                    BluetoothConnectionState.LISTENING -> null
                    BluetoothConnectionState.CONNECTING,
                    BluetoothConnectionState.CONNECTED -> _uiState.value.processingStatus
                }
                
                _uiState.update { it.copy(
                    bluetoothState = state,
                    connectionState = connectionState,
                    processingStatus = clearedProcessingStatus
                ) }
            }
        }
        
        // Listen to connected device name
        viewModelScope.launch {
            ServiceBridge.connectedDeviceNameFlow.collect { name ->
                Log.d(TAG, "Connected device name updated: $name")
                _uiState.update { it.copy(
                    connectedGlassesName = name
                ) }
            }
        }
        
        // Listen to API Key missing notifications
        viewModelScope.launch {
            ServiceBridge.apiKeyMissingFlow.collect {
                _uiState.update { it.copy(showApiKeyWarning = true) }
            }
        }
        
        // Listen to latest photo path for display
        viewModelScope.launch {
            ServiceBridge.latestPhotoPathFlow.collect { path ->
                Log.d(TAG, "Received latest photo path: $path")
                _uiState.update { it.copy(latestPhotoPath = path) }
            }
        }

        viewModelScope.launch {
            ServiceBridge.liveUsageSummaryFlow.collect { summary ->
                _uiState.update { it.copy(liveUsageSummary = summary) }
            }
        }

        viewModelScope.launch {
            ServiceBridge.liveSessionStatusFlow.collect { status ->
                _uiState.update { it.copy(liveSessionStatus = status) }
            }
        }
        
        // Listen to conversation messages (voice input from glasses and AI response)
        viewModelScope.launch {
            ServiceBridge.conversationFlow.collect { message ->
                when (message.type) {
                    MessageType.USER_TRANSCRIPT -> {
                        // User's speech
                        message.payload?.let { text ->
                            addConversation("user", text)
                        }
                    }
                    MessageType.LIVE_TRANSCRIPTION -> {
                        val payload = LiveTranscriptPayload.fromPayload(message.payload) ?: return@collect
                        when (payload.role) {
                            LiveTranscriptRole.USER -> upsertConversation("user", payload.text)
                            LiveTranscriptRole.ASSISTANT -> upsertConversation("assistant", payload.text)
                            LiveTranscriptRole.THINKING -> {
                                // Thinking summaries use processingStatus, not conversation bubbles.
                            }
                            LiveTranscriptRole.RAG -> {
                                // Glasses-side RAG display metadata stays out of the phone conversation list.
                            }
                        }
                    }
                    MessageType.AI_RESPONSE_TEXT -> {
                        // AI's response
                        message.payload?.let { text ->
                            addConversation("assistant", text)
                        }
                        _uiState.update { it.copy(processingStatus = null) }
                    }
                    MessageType.AI_PROCESSING -> {
                        // Update processing status
                        _uiState.update { it.copy(processingStatus = message.payload) }
                    }
                    MessageType.AI_ERROR -> {
                        _uiState.update { it.copy(processingStatus = null) }
                    }
                    else -> { /* Other types */ }
                }
            }
        }
    }
    
    /**
     * Request service to start Bluetooth listening
     * This restarts the server socket to accept new connections
     */
    fun startScanning() {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionState = ConnectionState.CONNECTING) }
            ServiceBridge.requestStartListening()
        }
    }
    
    /**
     * Request service to disconnect current Bluetooth connection
     * Note: UI state will be updated automatically through the bluetoothStateFlow
     */
    fun disconnect() {
        viewModelScope.launch {
            Log.d(TAG, "Requesting disconnect")
            ServiceBridge.requestDisconnect()
            // Don't manually override state here - let the flow update it naturally
            // This ensures UI stays in sync with actual Bluetooth state
        }
    }
    
    fun updateServiceStatus(isRunning: Boolean) {
        _uiState.update { it.copy(isServiceRunning = isRunning) }
    }
    
    fun updateProcessingStatus(status: String?) {
        _uiState.update { it.copy(processingStatus = status) }
    }
    
    fun addConversation(role: String, content: String) {
        _uiState.update { state ->
            val lastConversation = state.conversations.lastOrNull()
            if (lastConversation?.role == role && lastConversation.content == content) {
                return@update state
            }
            state.copy(
                conversations = state.conversations + ConversationItem(role, content)
            )
        }
    }

    private fun upsertConversation(role: String, content: String) {
        _uiState.update { state ->
            val conversations = state.conversations
            val lastConversation = conversations.lastOrNull()

            when {
                lastConversation == null -> state.copy(
                    conversations = listOf(ConversationItem(role, content))
                )
                lastConversation.role == role -> state.copy(
                    conversations = conversations.dropLast(1) + lastConversation.copy(content = content)
                )
                else -> state.copy(
                    conversations = conversations + ConversationItem(role, content)
                )
            }
        }
    }
    
    fun clearConversations() {
        _uiState.update { it.copy(conversations = emptyList()) }
    }
    
    fun dismissApiKeyWarning() {
        _uiState.update { it.copy(showApiKeyWarning = false) }
    }
    
    /**
     * Check if initial setup is needed (no API key configured at all)
     * Called from UI when settings are loaded
     */
    fun checkInitialSetup(hasAnyApiKey: Boolean) {
        if (!hasAnyApiKey) {
            _uiState.update { it.copy(showInitialSetup = true) }
        }
    }
    
    /**
     * Dismiss initial setup dialog
     */
    fun dismissInitialSetup() {
        _uiState.update { it.copy(showInitialSetup = false) }
    }
    
    /**
     * Request glasses to capture and send photo
     */
    fun requestCapturePhoto() {
        viewModelScope.launch {
            ServiceBridge.requestCapturePhoto()
        }
    }
    
    // ==================== Recording Control ====================
    
    /**
     * Start recording from phone microphone
     */
    fun startPhoneRecording() {
        viewModelScope.launch {
            val result = recordingRepository.startPhoneRecording()
            result.onFailure { error ->
                Log.e(TAG, "Failed to start phone recording", error)
            }
        }
    }
    
    /**
     * Start recording from glasses microphone
     */
    fun startGlassesRecording() {
        viewModelScope.launch {
            val result = recordingRepository.startGlassesRecording()
            result.onSuccess { recordingId ->
                // Send command to glasses to start recording
                ServiceBridge.requestStartGlassesRecording(recordingId)
            }
            result.onFailure { error ->
                Log.e(TAG, "Failed to start glasses recording", error)
            }
        }
    }
    
    /**
     * Pause current recording (if supported)
     */
    fun pauseRecording() {
        viewModelScope.launch {
            recordingRepository.pauseRecording()
        }
    }
    
    /**
     * Stop current recording and send to AI for analysis
     */
    fun stopRecording() {
        viewModelScope.launch {
            val result = recordingRepository.stopRecording()
            result.onSuccess { recording ->
                recording?.let {
                    Log.d(TAG, "Recording stopped: ${it.id}, source: ${it.source}, duration: ${it.durationMs}ms")
                    
                    // Only request transcription for phone recordings
                    // Glasses recordings are processed via Bluetooth when the audio data arrives
                    if (it.source == RecordingSource.PHONE && it.filePath.isNotBlank()) {
                        ServiceBridge.requestTranscribeRecording(it.id, it.filePath)
                    } else if (it.source == RecordingSource.GLASSES) {
                        Log.d(TAG, "Glasses recording stopped, sending stop command and waiting for audio via Bluetooth")
                        // Send stop command to glasses - audio data will be received via Bluetooth
                        // The processVoiceData() in PhoneAIService will handle transcription
                        ServiceBridge.requestStopGlassesRecording()
                    }
                }
            }
            result.onFailure { error ->
                Log.e(TAG, "Failed to stop recording", error)
            }
        }
    }
}
