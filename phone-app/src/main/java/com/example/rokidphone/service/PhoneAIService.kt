package com.example.rokidphone.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.rokidcommon.Constants
import com.example.rokidcommon.protocol.LiveControlInputSource
import com.example.rokidcommon.protocol.Message
import com.example.rokidcommon.protocol.MessageType
import com.example.rokidcommon.protocol.LiveRagDisplayMode
import com.example.rokidcommon.protocol.LiveSessionControlPayload
import com.example.rokidcommon.protocol.LiveTranscriptPayload
import com.example.rokidcommon.protocol.LiveTranscriptRole
import com.example.rokidcommon.protocol.resolveEffectiveLiveRagDisplayMode
import com.example.rokidcommon.protocol.photo.PhotoTransferState
import com.example.rokidphone.BuildConfig
import com.example.rokidphone.MainActivity
import com.example.rokidphone.R
import com.example.rokidphone.data.AnswerMode
import com.example.rokidphone.data.AiProvider
import com.example.rokidphone.data.ApiSettings
import com.example.rokidphone.data.AvailableModels
import com.example.rokidphone.data.LiveInputSource
import com.example.rokidphone.data.SettingsRepository
import com.example.rokidphone.data.SettingsValidationResult
import com.example.rokidphone.data.db.ConversationRepository
import com.example.rokidphone.data.db.RecordingRepository
import com.example.rokidphone.data.log.LogManager
import com.example.rokidphone.data.toAnythingLlmSettings
import com.example.rokidphone.service.ai.AiServiceFactory
import com.example.rokidphone.service.ai.AiServiceProvider
import com.example.rokidphone.service.ai.GeminiLiveSession
import com.example.rokidphone.service.ai.GeminiLiveCostEstimator
import com.example.rokidphone.service.ai.LiveUsageMetadata
import com.example.rokidphone.service.ai.LiveRagToolAdapter
import com.example.rokidphone.service.ai.LiveRagTurnState
import com.example.rokidphone.service.ai.LiveSessionConfig
import com.example.rokidphone.service.ai.LiveSessionCoordinator
import com.example.rokidphone.service.ai.LiveSessionState
import com.example.rokidphone.service.ai.LiveTranscriptAccumulator
import com.example.rokidphone.service.ai.buildLiveToolDeclarations
import com.example.rokidphone.service.ai.LiveUsageSummaryRetention
import com.example.rokidphone.service.ai.resolveLiveErrorPresentation
import com.example.rokidphone.service.cxr.CxrMobileManager
import com.example.rokidphone.service.session.AiRequestSessionSupport
import com.example.rokidphone.service.stt.SttProvider
import com.example.rokidphone.service.stt.SttService
import com.example.rokidphone.service.stt.SttServiceFactory
import com.example.rokidphone.data.toSttCredentials
import com.example.rokidphone.data.validateForDocs
import com.example.rokidphone.service.ServiceBridge.notifyApiKeyMissing
import com.example.rokidphone.service.photo.PhotoData
import com.example.rokidphone.service.photo.PhotoRepository
import com.example.rokidphone.service.photo.ReceivedPhoto
import com.example.rokidphone.service.rag.AnythingLlmRagService
import com.example.rokidphone.service.rag.AssistantInputType
import com.example.rokidphone.service.rag.InputNormalizer
import com.example.rokidphone.service.rag.RouteDecision
import com.example.rokidphone.service.rag.RouteResolver
import com.example.rokidphone.service.rag.RouteTarget
import com.example.rokidphone.service.rag.SourcePreview
import com.example.rokidphone.service.rag.resolveDocsTextQuery
import com.example.rokidphone.service.rag.buildAssistantMessageMetadata
import com.example.rokidphone.service.rag.buildConversationMetadata
import com.example.rokidphone.service.rag.buildConversationModelId
import com.example.rokidphone.service.rag.buildConversationProviderId
import com.example.rokidphone.service.rag.buildGlassesAssistantResponse
import com.example.rokidphone.service.rag.markDocsAssistantFailure
import com.example.rokidphone.service.rag.markDocsAssistantHealthy
import com.rokid.cxr.client.utils.ValueUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Phone AI Service
 * Receives voice commands from glasses, processes AI computation, returns results
 * 
 * Architecture:
 * 1. Glasses record -> Send audio via Bluetooth to phone
 * 2. Phone performs speech recognition (Gemini API)
 * 3. Phone performs AI conversation (Gemini API)
 * 4. Phone sends results back to glasses via Bluetooth for display
 */
class PhoneAIService : Service() {
    
    companion object {
        private const val TAG = "PhoneAIService"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val routeResolver = RouteResolver()
    private val ragService = AnythingLlmRagService()
    private val inputNormalizer = InputNormalizer()

    private data class RoutedAssistantResult(
        val text: String,
        val route: RouteDecision,
        val sources: List<SourcePreview> = emptyList(),
        val modelId: String,
    )
    
    // AI service (supports multiple providers)
    private var aiService: AiServiceProvider? = null
    
    // Speech recognition service (may differ from chat service)
    private var speechService: AiServiceProvider? = null
    
    // Dedicated STT service (for specialized STT providers like Deepgram, Azure, etc.)
    private var sttService: SttService? = null
    
    // TTS service
    private var ttsService: TextToSpeechService? = null
    
    // Bluetooth manager (legacy SPP connection)
    private var bluetoothManager: BluetoothSppManager? = null
    
    // CXR-M SDK Manager (for Rokid glasses connection and photo capture)
    private var cxrManager: CxrMobileManager? = null
    
    // Live session coordinator (real-time bidirectional voice)
    private var liveCoordinator: LiveSessionCoordinator? = null
    private var liveCollectorJobs: List<Job> = emptyList()
    private var latestValidatedSettings: ApiSettings? = null
    private var liveSessionAnnouncedToGlasses = false
    
    // Photo repository for managing received photos
    private var photoRepository: PhotoRepository? = null
    
    // Conversation repository for persisting voice conversations
    private var conversationRepository: ConversationRepository? = null
    
    // Recording repository for saving glasses recordings
    private var recordingRepository: RecordingRepository? = null
    
    // Current voice conversation ID (for grouping voice interactions)
    private var currentVoiceConversationId: String? = null
    private var currentLiveTurnConversationId: String? = null
    private var pendingLiveUserTranscript: String = ""
    private var pendingLiveAssistantTranscript: String = ""
    private var pendingLiveThoughtSummary: String = ""
    private var pendingLiveRagTurnState: LiveRagTurnState = LiveRagTurnState()
    private var latestLiveUsageMetadata: LiveUsageMetadata? = null
    private var lastSyncedResponseFontScalePercent: Int? = null
    
    // Track recording IDs currently being processed to prevent duplicate transcription
    private val processingRecordingIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    
    private val _messageFlow = MutableSharedFlow<Message>()
    val messageFlow = _messageFlow.asSharedFlow()
    
    override fun onCreate() {
        super.onCreate()
        initializeServices()
        startForeground(Constants.NOTIFICATION_ID, createNotification())
        
        // Notify UI service has started (immediate state update)
        ServiceBridge.updateServiceState(true)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Notify UI service has stopped (immediate state update)
        ServiceBridge.updateServiceState(false)
        
        serviceScope.cancel()
        liveCollectorJobs.forEach { it.cancel() }
        liveCollectorJobs = emptyList()
        liveCoordinator?.release()
        liveCoordinator = null
        currentLiveTurnConversationId = null
        clearLiveUsageSummary()
        bluetoothManager?.disconnect()
        cxrManager?.release()
        ttsService?.shutdown()
        sttService?.release()
    }
    
    private fun initializeServices() {
        Log.d(TAG, "Initializing services...")
        val settingsRepository = SettingsRepository.getInstance(this)
        
        try {
            val settings = settingsRepository.getSettings()
            
            // Initialize conversation repository for persisting voice conversations
            conversationRepository = ConversationRepository.getInstance(this)
            
            // Create or get the current voice conversation session
            serviceScope.launch {
                ensureVoiceConversationSession(settings)
            }
            
            // Validate model and provider compatibility
            val validatedSettings = validateAndCorrectSettings(settings)
            latestValidatedSettings = validatedSettings
            
            // Use factory to create AI service
            aiService = createAiService(validatedSettings)
            Log.d(TAG, "AI service created: ${aiService != null}")
            
            // Set speech recognition service (prefer providers supporting STT)
            speechService = createSpeechService(validatedSettings)
            Log.d(TAG, "Speech service created: ${speechService != null}")
            
            // Create dedicated STT service if a specialized provider is selected
            sttService = createSttService(validatedSettings)
            Log.d(TAG, "Dedicated STT service created: ${sttService != null}, provider: ${validatedSettings.sttProvider}")
            
            Log.d(
                TAG,
                "Using active assistant: ${validatedSettings.getEffectiveAssistantProvider()}, " +
                    "model: ${validatedSettings.getEffectiveAssistantModelId()}"
            )
            var lastAppliedSettings = validatedSettings
            
            // Monitor settings changes
            serviceScope.launch {
                settingsRepository.settingsFlow.collect { newSettings ->
                    Log.d(TAG, "Settings changed, updating services...")
                    val validatedNewSettings = validateAndCorrectSettings(newSettings)
                    latestValidatedSettings = validatedNewSettings
                    if (PhoneSettingsSyncPolicy.requiresServiceRefresh(lastAppliedSettings, validatedNewSettings)) {
                        aiService = createAiService(validatedNewSettings)
                        speechService = createSpeechService(validatedNewSettings)
                        sttService = createSttService(validatedNewSettings)
                    }
                    handleLiveModeTransition(validatedNewSettings)
                    syncSleepModeSetting(validatedNewSettings)
                    syncResponseFontScaleSetting(validatedNewSettings)
                    syncLiveSessionStateToGlasses(validatedNewSettings)
                    lastAppliedSettings = validatedNewSettings
                    
                    Log.d(
                        TAG,
                        "Services updated: active assistant=${validatedNewSettings.getEffectiveAssistantProvider()}, " +
                            "model=${validatedNewSettings.getEffectiveAssistantModelId()}, " +
                            "STT=${validatedNewSettings.sttProvider}"
                    )
                }
            }
            
            // Initialize TTS
            ttsService = TextToSpeechService(this)
            Log.d(TAG, "TTS service initialized")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AI services, but continuing with Bluetooth", e)
        }
        
        // Initialize Bluetooth manager (independent of AI services)
        try {
            bluetoothManager = BluetoothSppManager(this, serviceScope)
            
            // Start listening for Bluetooth connections (only if permission granted)
            if (bluetoothManager?.hasBluetoothPermission() == true) {
                bluetoothManager?.startListening()
                Log.d(TAG, "Bluetooth manager started listening")
            } else {
                Log.w(TAG, "Bluetooth permission not granted, waiting for permission")
            }
            
            // Monitor Bluetooth connection state
            serviceScope.launch {
                try {
                    bluetoothManager?.connectionState?.collect { state ->
                        Log.d(TAG, "Bluetooth state: $state")
                        ServiceBridge.updateBluetoothState(state)
                        
                        // Update notification
                        updateNotification(state)
                        
                        // Initialize CXR Bluetooth when SPP connected
                        if (state == BluetoothConnectionState.CONNECTED) {
                            val settings = latestValidatedSettings ?: settingsRepository.getSettings()
                            bluetoothManager?.connectedDevice?.let { device ->
                                Log.d(TAG, "Initializing CXR Bluetooth with device: ${device.name}")
                                cxrManager?.initBluetooth(device)
                            }
                            syncSleepModeSetting(settings)
                            syncResponseFontScaleSetting(settings, force = true)
                            syncLiveSessionStateToGlasses(settings)
                        }
                        latestValidatedSettings?.let { handleLiveModeTransition(it) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in Bluetooth state collector", e)
                }
            }
            
            // Monitor connected device name
            serviceScope.launch {
                try {
                    bluetoothManager?.connectedDeviceName?.collect { name ->
                        Log.d(TAG, "Connected device name: $name")
                        ServiceBridge.updateConnectedDeviceName(name)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in device name collector", e)
                }
            }
            
            // Listen for messages from glasses
            serviceScope.launch {
                try {
                    bluetoothManager?.messageFlow?.collect { message ->
                        handleGlassesMessage(message)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in message flow collector", e)
                }
            }
            
            // Initialize photo repository
            photoRepository = PhotoRepository(this, serviceScope)
            
            // Initialize recording repository for saving glasses recordings
            recordingRepository = RecordingRepository.getInstance(this, serviceScope)
            
            // Listen for received photos from glasses
            serviceScope.launch {
                try {
                    bluetoothManager?.receivedPhoto?.collect { receivedPhoto ->
                        handleReceivedPhoto(receivedPhoto)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in received photo collector", e)
                }
            }
            
            // Monitor photo transfer state
            serviceScope.launch {
                try {
                    bluetoothManager?.photoTransferState?.collect { state ->
                        handlePhotoTransferState(state)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in photo transfer state collector", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Bluetooth manager", e)
        }
        
        // Listen for send-to-glasses requests from text chat
        serviceScope.launch {
            ServiceBridge.sendToGlassesFlow.collect { message ->
                Log.d(TAG, "Forwarding message to glasses: type=${message.type}")
                bluetoothManager?.sendMessage(message)
            }
        }

        // Listen for capture photo requests from UI
        serviceScope.launch {
            ServiceBridge.capturePhotoFlow.collect {
                Log.d(TAG, "Capture photo request from UI")
                requestGlassesToCapturePhoto()
            }
        }
        
        // Listen for connection control requests from UI
        serviceScope.launch {
            ServiceBridge.startListeningFlow.collect {
                Log.d(TAG, "Start listening request from UI")
                bluetoothManager?.let { manager ->
                    // Restart Bluetooth listening
                    manager.stopListening()
                    kotlinx.coroutines.delay(300) // Wait for socket cleanup
                    if (manager.hasBluetoothPermission()) {
                        manager.startListening()
                        Log.d(TAG, "Bluetooth listening restarted")
                    }
                }
            }
        }
        
        serviceScope.launch {
            ServiceBridge.disconnectFlow.collect {
                Log.d(TAG, "Disconnect request from UI")
                bluetoothManager?.disconnect(restartListening = true)
            }
        }
        
        // Listen for transcription requests from UI (phone recordings)
        serviceScope.launch {
            try {
                ServiceBridge.transcribeRecordingFlow.collect { request ->
                    Log.d(TAG, "Transcription request received: ${request.recordingId}")
                    processPhoneRecording(request.recordingId, request.filePath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in transcription request collector", e)
            }
        }
        
        // Listen for glasses recording start requests from UI
        serviceScope.launch {
            try {
                ServiceBridge.startGlassesRecordingFlow.collect { recordingId ->
                    Log.d(TAG, "Glasses recording start command: $recordingId")
                    bluetoothManager?.sendMessage(
                        Message(type = MessageType.REMOTE_RECORD_START, payload = recordingId)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in glasses recording start collector", e)
            }
        }
        
        // Listen for glasses recording stop requests from UI
        serviceScope.launch {
            try {
                ServiceBridge.stopGlassesRecordingFlow.collect {
                    Log.d(TAG, "Glasses recording stop command")
                    bluetoothManager?.sendMessage(
                        Message(type = MessageType.REMOTE_RECORD_STOP)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in glasses recording stop collector", e)
            }
        }
        
        // Initialize CXR-M SDK (for Rokid glasses photo capture)
        initializeCxrSdk()
        latestValidatedSettings?.let { handleLiveModeTransition(it) }
    }
    
    /**
     * Request glasses to capture and send photo via SPP
     */
    private suspend fun requestGlassesToCapturePhoto() {
        if (bluetoothManager?.connectionState?.value != BluetoothConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot capture photo: not connected to glasses")
            return
        }
        
        if (aiService == null) {
            Log.e(TAG, "AI service is not configured, aborting photo capture")
            // Notify UI to show API key warning dialog
            notifyApiKeyMissing()
            // Also send error message to glasses
            bluetoothManager?.sendMessage(Message.aiError("AI service is not configured. Please set up an AI provider in Settings."))
            return
        }
        
        Log.d(TAG, "Sending CAPTURE_PHOTO command to glasses")
        bluetoothManager?.sendMessage(Message(type = MessageType.CAPTURE_PHOTO))
    }

    private suspend fun syncSleepModeSetting(settings: ApiSettings) {
        bluetoothManager?.sendMessage(
            Message(
                type = MessageType.SLEEP_MODE_CONFIG,
                payload = settings.glassesSleepModeEnabled.toString()
            )
        )
    }

    private suspend fun syncResponseFontScaleSetting(
        settings: ApiSettings,
        force: Boolean = false
    ) {
        val normalizedPercent = ApiSettings.snapResponseFontScalePercent(settings.responseFontScalePercent)
        if (bluetoothManager?.connectionState?.value != BluetoothConnectionState.CONNECTED) {
            return
        }
        if (!PhoneSettingsSyncPolicy.shouldSyncResponseFontScale(lastSyncedResponseFontScalePercent, normalizedPercent, force)) {
            return
        }

        bluetoothManager?.sendMessage(Message.responseFontScaleConfig(normalizedPercent))
        lastSyncedResponseFontScalePercent = normalizedPercent
    }
    
    /**
     * Initialize CXR-M SDK for Rokid glasses connection
     * This enables:
     * - AI key event listening (long press on glasses)
     * - Remote photo capture from glasses
     */
    private fun initializeCxrSdk() {
        if (!CxrMobileManager.isSdkAvailable()) {
            Log.w(TAG, "CXR-M SDK not available")
            return
        }
        
        try {
            cxrManager = CxrMobileManager(this)
            
            // Set AI event listener for glasses key press
            cxrManager?.setAiEventListener(
                onKeyDown = {
                    Log.d(TAG, "CXR: AI key pressed on glasses")
                    // Trigger photo capture when AI key is pressed
                    serviceScope.launch {
                        capturePhotoFromGlasses()
                    }
                },
                onKeyUp = {
                    Log.d(TAG, "CXR: AI key released")
                },
                onExit = {
                    Log.d(TAG, "CXR: AI scene exited")
                }
            )
            
            // Monitor CXR Bluetooth connection state
            serviceScope.launch {
                cxrManager?.bluetoothState?.collect { state ->
                    Log.d(TAG, "CXR Bluetooth state: $state")
                    when (state) {
                        is CxrMobileManager.BluetoothState.Connected -> {
                            Log.d(TAG, "CXR connected: ${state.macAddress}")
                        }
                        is CxrMobileManager.BluetoothState.Disconnected -> {
                            Log.d(TAG, "CXR disconnected")
                        }
                        is CxrMobileManager.BluetoothState.Failed -> {
                            Log.e(TAG, "CXR connection failed: ${state.error}")
                        }
                        else -> {}
                    }
                }
            }
            
            Log.d(TAG, "CXR-M SDK initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CXR-M SDK", e)
        }
    }
    
    /**
     * Capture photo from glasses using CXR-M SDK
     */
    private suspend fun capturePhotoFromGlasses() {
        val cxr = cxrManager ?: run {
            Log.w(TAG, "CXR manager not available, using legacy photo transfer")
            return
        }
        
        if (!cxr.isBluetoothConnected()) {
            Log.w(TAG, "CXR not connected to glasses")
            bluetoothManager?.sendMessage(Message.aiError(getString(R.string.glasses_not_connected_cxr)))
            return
        }
        
        if (aiService == null) {
            Log.e(TAG, "AI service is not configured, aborting photo capture")
            bluetoothManager?.sendMessage(Message.aiError("AI service is not configured. Please set up an AI provider in Settings."))
            return
        }
        
        Log.d(TAG, "Capturing photo from glasses via CXR SDK...")
        
        // Notify glasses: taking photo
        cxr.sendTtsContent(getString(R.string.taking_photo))
        
        // Take photo using CXR SDK
        val status = cxr.takePhoto(
            width = 1280,
            height = 720,
            quality = 80
        ) { resultStatus, photoData ->
            serviceScope.launch {
                when (resultStatus) {
                    ValueUtil.CxrStatus.RESPONSE_SUCCEED -> {
                        if (photoData != null && photoData.isNotEmpty()) {
                            Log.d(TAG, "CXR photo received: ${photoData.size} bytes")
                            handleCxrPhotoResult(photoData)
                        } else {
                            Log.e(TAG, "CXR photo is empty")
                            bluetoothManager?.sendMessage(Message.aiError(getString(R.string.photo_empty)))
                        }
                    }
                    ValueUtil.CxrStatus.RESPONSE_TIMEOUT -> {
                        Log.e(TAG, "CXR photo timeout")
                        bluetoothManager?.sendMessage(Message.aiError(getString(R.string.photo_timeout)))
                    }
                    else -> {
                        Log.e(TAG, "CXR photo failed: $resultStatus")
                        bluetoothManager?.sendMessage(Message.aiError(getString(R.string.photo_capture_failed, resultStatus)))
                    }
                }
            }
        }
        
        Log.d(TAG, "CXR takePhoto request status: $status")
    }
    
    /**
     * Handle photo captured via CXR SDK
     */
    private suspend fun handleCxrPhotoResult(photoData: ByteArray) {
        try {
            Log.d(TAG, "Processing CXR photo: ${photoData.size} bytes")
            
            // Create a ReceivedPhoto object
            val receivedPhoto = ReceivedPhoto(
                data = photoData,
                timestamp = System.currentTimeMillis(),
                transferTimeMs = 0  // Direct capture, no transfer time
            )
            
            // Process the photo
            handleReceivedPhoto(receivedPhoto)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process CXR photo", e)
            bluetoothManager?.sendMessage(Message.aiError(getString(R.string.photo_process_failed, e.message ?: "")))
        }
    }
    
    private suspend fun handleGlassesMessage(message: Message) {
        Log.d(TAG, "Received message from glasses: ${message.type}")
        
        when (message.type) {
            MessageType.VOICE_DATA -> {
                message.binaryData?.let { audioChunk ->
                    liveCoordinator?.receiveGlassesAudioChunk(audioChunk)
                }
            }
            MessageType.VOICE_END -> {
                val isLiveActive = liveCoordinator?.sessionState?.value == LiveSessionState.ACTIVE
                val voiceEndAudio = message.binaryData
                voiceEndAudio?.let { audioData ->
                    Log.d(TAG, "Processing voice data: ${audioData.size} bytes")

                    if (isLiveActive) {
                        Log.d(TAG, "Live mode active, signaling end of turn")
                        liveCoordinator?.endOfTurn()
                    } else {
                        processVoiceData(audioData)
                    }
                }

                if (isLiveActive && (voiceEndAudio == null || voiceEndAudio.isEmpty())) {
                    liveCoordinator?.endOfTurn()
                }
            }
            MessageType.VOICE_START -> {
                Log.d(TAG, "Voice recording started on glasses")
                
                // In Live mode, audio is streamed in real-time — no STT step needed
                if (liveCoordinator?.sessionState?.value == LiveSessionState.ACTIVE) {
                    Log.d(TAG, "Live mode active, audio streams directly to Gemini")
                    return
                }
                
                // Check STT service availability before allowing recording
                if (sttService == null && speechService == null) {
                    Log.e(TAG, "STT service not available - API key not configured")
                    val errorMsg = getString(R.string.service_not_ready)
                    bluetoothManager?.sendMessage(Message.aiError(errorMsg))
                    ServiceBridge.emitConversation(Message(
                        type = MessageType.AI_ERROR,
                        payload = errorMsg
                    ))
                    notifyApiKeyMissing()
                    return
                }
                
                // Notify phone UI
                ServiceBridge.emitConversation(Message(
                    type = MessageType.AI_PROCESSING,
                    payload = getString(R.string.glasses_recording)
                ))
            }
            MessageType.HEARTBEAT -> {
                bluetoothManager?.sendMessage(Message(type = MessageType.HEARTBEAT_ACK))
            }
            // Receive real-time video frames from glasses and forward to Gemini Live Session
            MessageType.VIDEO_FRAME -> {
                message.binaryData?.let { frameData ->
                    if (liveCoordinator?.sessionState?.value == LiveSessionState.ACTIVE) {
                        Log.d(TAG, "Forwarding video frame to Live session: ${frameData.size} bytes")
                        liveCoordinator?.receiveVideoFrame(frameData)
                    } else {
                        Log.w(TAG, "Received VIDEO_FRAME but Live session is not active")
                    }
                }
            }
            // Receive real-time transcription text from glasses and forward to phone UI
            MessageType.LIVE_TRANSCRIPTION -> {
                message.payload?.let { text ->
                    Log.d(TAG, "Received live transcription from glasses: $text")
                    ServiceBridge.emitConversation(Message(
                        type = MessageType.LIVE_TRANSCRIPTION,
                        payload = text
                    ))
                }
            }
            MessageType.LIVE_SESSION_TOGGLE_REQUEST -> {
                Log.d(TAG, "Received live session toggle request from glasses")
                handleGlassesLiveSessionToggleRequest()
            }
            else -> { 
                Log.d(TAG, "Unhandled message type: ${message.type}")
            }
        }
    }
    
    /**
     * Handle received photo from glasses
     */
    private suspend fun handleReceivedPhoto(receivedPhoto: ReceivedPhoto) {
        Log.d(TAG, "Received photo: ${receivedPhoto.data.size} bytes, transfer time: ${receivedPhoto.transferTimeMs}ms")
        
        // Process and store the photo
        val photoData = photoRepository?.processReceivedPhoto(receivedPhoto)
        
        if (photoData != null) {
            Log.d(TAG, "Photo saved: ${photoData.filePath}")
            
            // Notify UI about the photo path for display
            ServiceBridge.emitLatestPhotoPath(photoData.filePath)
            
            // Notify UI that a photo was received
            ServiceBridge.emitConversation(Message(
                type = MessageType.AI_PROCESSING,
                payload = getString(R.string.photo_received)
            ))
            
            // Analyze the photo with AI
            analyzePhotoWithAI(photoData)
        } else {
            Log.e(TAG, "Failed to process received photo")
            bluetoothManager?.sendMessage(Message.aiError(getString(R.string.photo_processing_failed)))
        }
    }
    
    /**
     * Handle photo transfer state changes
     */
    private fun handlePhotoTransferState(state: PhotoTransferState) {
        when (state) {
            is PhotoTransferState.Idle -> {
                Log.d(TAG, "Photo transfer: Idle")
            }
            is PhotoTransferState.InProgress -> {
                Log.d(TAG, "Photo transfer: ${state.currentChunk}/${state.totalChunks} " +
                        "(${state.progressPercent.toInt()}%)")
            }
            is PhotoTransferState.Success -> {
                Log.d(TAG, "Photo transfer: Success (${state.data.size} bytes)")
            }
            is PhotoTransferState.Error -> {
                Log.e(TAG, "Photo transfer error: ${state.message}")
                serviceScope.launch {
                    bluetoothManager?.sendMessage(Message.aiError(
                        getString(R.string.photo_transfer_failed, state.message)
                    ))
                }
            }
        }
    }
    
    /**
     * Analyze photo with AI and send results back to glasses
     */
    private suspend fun analyzePhotoWithAI(photoData: PhotoData) {
        try {
            // Get photo bytes for AI analysis
            val photoBytes = photoRepository?.getPhotoBytes(photoData) ?: return
            val settings = SettingsRepository.getInstance(this).getSettings()
            
            Log.d(TAG, "Analyzing photo with AI: ${photoBytes.size} bytes")
            
            // Notify glasses: analyzing photo
            bluetoothManager?.sendMessage(Message.aiProcessing(getString(R.string.analyzing_photo)))
            
            ServiceBridge.emitConversation(Message(
                type = MessageType.AI_PROCESSING,
                payload = getString(R.string.analyzing_photo)
            ))
            
            // Save photo request into history so docs/general routing is visible in the existing conversation log.
            val photoPrompt = if (settings.answerMode == AnswerMode.DOCS) {
                "Photo input"
            } else {
                "Photo analysis"
            }
            val requestConversationId = prepareRequestConversation(settings, photoPrompt)
            saveUserMessage(
                content = photoPrompt,
                imagePath = photoData.filePath,
                conversationId = requestConversationId,
            )
            ServiceBridge.emitConversation(Message(
                type = MessageType.USER_TRANSCRIPT,
                payload = photoPrompt
            ))

            val routedResult = resolvePhotoAssistantResponse(settings, photoBytes)
            val cleanedResult = routedResult.text
            
            Log.d(TAG, "Photo analysis result: $cleanedResult")
            
            // Update photo data with analysis result
            photoData.analysisResult = cleanedResult
            saveAssistantMessage(
                content = cleanedResult,
                modelId = routedResult.modelId,
                route = routedResult.route,
                sources = routedResult.sources,
                settings = settings,
                conversationId = requestConversationId,
            )
            
            // Send result to glasses
            bluetoothManager?.sendMessage(Message(
                type = MessageType.PHOTO_ANALYSIS_RESULT,
                payload = buildGlassesAssistantResponse(
                    answerText = cleanedResult,
                    route = routedResult.route,
                    sources = routedResult.sources,
                    normalizer = inputNormalizer,
                )
            ))
            
            // Notify phone UI
            ServiceBridge.emitConversation(Message(
                type = MessageType.AI_RESPONSE_TEXT,
                payload = cleanedResult
            ))
            
            // TTS voice playback
            maybeSpeakAssistantResponse(settings, cleanedResult)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze photo", e)
            bluetoothManager?.sendMessage(Message.aiError(
                getString(R.string.photo_analysis_failed, e.message ?: "")
            ))
        }
    }
    
    /**
     * Process voice data received from glasses
     */
    private suspend fun processVoiceData(audioData: ByteArray) {
        try {
            // Check if any speech service is available
            if (sttService == null && speechService == null) {
                Log.e(TAG, "Speech service not available - no API key configured")
                val errorMsg = getString(R.string.service_not_ready)
                bluetoothManager?.sendMessage(Message.aiError(errorMsg))
                ServiceBridge.emitConversation(Message(
                    type = MessageType.AI_ERROR,
                    payload = errorMsg
                ))
                // Notify UI to show settings prompt
                notifyApiKeyMissing()
                return
            }
            
            // 1. Notify glasses: recognizing
            bluetoothManager?.sendMessage(Message.aiProcessing(getString(R.string.recognizing_speech)))
            ServiceBridge.emitConversation(Message(
                type = MessageType.AI_PROCESSING,
                payload = getString(R.string.recognizing_speech)
            ))
            
            // 2. Speech recognition - prefer dedicated STT service if available
            Log.d(TAG, "Starting speech recognition...")
            val settings = SettingsRepository.getInstance(this).getSettings()
            val transcriptResult = if (sttService != null) {
                Log.d(TAG, "Using dedicated STT service: ${sttService?.provider?.name}")
                sttService?.transcribe(audioData, settings.speechLanguage)
            } else {
                Log.d(TAG, "Using AI-based speech service, language: ${settings.speechLanguage}")
                speechService?.transcribe(audioData, settings.speechLanguage)
            }
            
            val transcript = when (transcriptResult) {
                is SpeechResult.Success -> {
                    Log.d(TAG, "Transcript: ${transcriptResult.text}")
                    transcriptResult.text
                }
                is SpeechResult.Error -> {
                    Log.e(TAG, "Transcription error: ${transcriptResult.message}")
                    bluetoothManager?.sendMessage(Message.aiError(transcriptResult.message))
                    ServiceBridge.emitConversation(Message(
                        type = MessageType.AI_ERROR,
                        payload = transcriptResult.message
                    ))
                    // Check if error is API key related
                    if (transcriptResult.message.contains("API", ignoreCase = true) ||
                        transcriptResult.message.contains("key", ignoreCase = true) ||
                        transcriptResult.message.contains("401") ||
                        transcriptResult.message.contains("403")) {
                        notifyApiKeyMissing()
                    }
                    return
                }
                null -> {
                    val errorMsg = getString(R.string.service_not_ready)
                    bluetoothManager?.sendMessage(Message.aiError(errorMsg))
                    ServiceBridge.emitConversation(Message(
                        type = MessageType.AI_ERROR,
                        payload = errorMsg
                    ))
                    notifyApiKeyMissing()
                    return
                }
            }
            
            // 3. Send user voice text to glasses and phone UI
            bluetoothManager?.sendMessage(Message(
                type = MessageType.USER_TRANSCRIPT,
                payload = transcript
            ))
            
            ServiceBridge.emitConversation(Message(
                type = MessageType.USER_TRANSCRIPT,
                payload = transcript
            ))
            
            // 3.1 Save user message to database for history
            val requestConversationId = prepareRequestConversation(settings, transcript)
            saveUserMessage(transcript, conversationId = requestConversationId)
            
            // 4. Notify thinking
            bluetoothManager?.sendMessage(Message.aiProcessing(getString(R.string.thinking)))
            ServiceBridge.emitConversation(Message(
                type = MessageType.AI_PROCESSING,
                payload = getString(R.string.thinking)
            ))
            
            // 5. AI conversation / Docs routing
            Log.d(TAG, "Getting assistant response...")
            val routedResult = resolveTextAssistantResponse(
                settings = settings,
                text = transcript,
                inputType = AssistantInputType.VOICE,
            )
            val aiResponse = routedResult.text
            
            Log.d(TAG, "AI response: $aiResponse")
            
            // 6. Send AI response to glasses and phone UI
            bluetoothManager?.sendMessage(
                Message.aiResponseText(
                    buildGlassesAssistantResponse(
                        answerText = aiResponse,
                        route = routedResult.route,
                        sources = routedResult.sources,
                        normalizer = inputNormalizer,
                    )
                )
            )
            
            ServiceBridge.emitConversation(Message(
                type = MessageType.AI_RESPONSE_TEXT,
                payload = aiResponse
            ))
            
            // 6.1 Save AI response to database for history
            saveAssistantMessage(
                content = aiResponse,
                modelId = routedResult.modelId,
                route = routedResult.route,
                sources = routedResult.sources,
                settings = settings,
                conversationId = requestConversationId,
            )
            
            // 6.2 Save glasses recording to database (with transcript and AI response)
            try {
                recordingRepository?.saveGlassesRecording(
                    audioData = audioData,
                    transcript = transcript,
                    aiResponse = aiResponse,
                    providerId = buildConversationProviderId(settings),
                    modelId = routedResult.modelId
                )
                Log.d(TAG, "Glasses recording saved to database")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save glasses recording", e)
            }
            
            // 7. TTS voice playback (optional)
            maybeSpeakAssistantResponse(settings, aiResponse)
            
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // Service is being stopped, don't treat this as an error
            Log.d(TAG, "Voice processing cancelled (service stopping)")
            throw e  // Re-throw to properly propagate cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Error processing voice data", e)
            val errorMessage = getString(R.string.processing_failed, e.message ?: "")
            bluetoothManager?.sendMessage(Message.aiError(errorMessage))
            ServiceBridge.emitConversation(Message(
                type = MessageType.AI_ERROR,
                payload = errorMessage
            ))
        }
    }
    
    // ========== Gemini Live Mode ==========
    
    /**
     * Handle Live mode transitions.
     * Called whenever settings change.
     */
    private fun handleLiveModeTransition(settings: ApiSettings) {
        if (!settings.liveModeEnabled) {
            shutdownLiveCoordinator()
            return
        }

        if (settings.getGeminiApiKeys().isEmpty()) {
            Log.e(TAG, "Cannot start Live session: Gemini API key not configured")
            shutdownLiveCoordinator()
            serviceScope.launch {
                notifyApiKeyMissing()
            }
            return
        }

        val coordinator = liveCoordinator ?: createLiveCoordinator()
        coordinator.sync(settings, isGlassesConnected())
    }

    private fun createLiveCoordinator(): LiveSessionCoordinator {
        val coordinator = LiveSessionCoordinator(serviceScope) { config: LiveSessionConfig ->
            val settings = latestValidatedSettings ?: SettingsRepository.getInstance(this).getSettings()
            val liveRagAdapter = if (config.liveRagEnabled) LiveRagToolAdapter(ragService) else null
            val liveRagDisplayMode = config.liveRagDisplayMode
            val extraToolDeclarations = buildLiveToolDeclarations(
                googleSearchEnabled = config.googleSearchAvailableForAttempt,
                liveRagDeclaration = liveRagAdapter?.let { LiveRagToolAdapter.declaration() }
            )
            GeminiLiveSession(
                context = this,
                apiKey = config.apiKey,
                modelId = config.modelId,
                systemPrompt = buildSystemPromptWithLanguage(config.systemPrompt, settings.responseLanguage),
                liveVoiceName = config.liveVoiceName,
                capturePhoneAudio = config.capturePhoneAudio,
                playbackPhoneAudio = config.playbackPhoneAudio,
                enableLongSession = config.liveLongSessionEnabled,
                sessionResumptionHandle = config.sessionResumptionHandle,
                thinkingLevel = config.liveThinkingLevel,
                includeThoughtSummaries = config.liveThoughtSummariesEnabled,
                extraToolDeclarations = extraToolDeclarations,
                routerConfigurator = { router ->
                    liveRagAdapter?.let { adapter ->
                        router.registerHandler(LiveRagToolAdapter.FUNCTION_NAME) { call ->
                            pendingLiveRagTurnState = pendingLiveRagTurnState.markToolInvoked()
                            if (liveRagDisplayMode == LiveRagDisplayMode.RAG_RESULT_ONLY) {
                                emitLiveRagTranscript(getString(R.string.live_rag_searching))
                            }
                            val result = adapter.execute(call, settings.toAnythingLlmSettings())
                            pendingLiveRagTurnState = pendingLiveRagTurnState.applyToolResult(result)
                            val ragDisplayText = pendingLiveRagTurnState.ragAnswerText
                                ?: getString(R.string.live_rag_no_result)
                            emitLiveRagTranscript(
                                text = ragDisplayText,
                                isFinal = true,
                            )
                            result
                        }
                    }
                },
            )
        }
        liveCoordinator = coordinator
        collectLiveCoordinatorEvents(coordinator)
        return coordinator
    }

    private fun collectLiveCoordinatorEvents(coordinator: LiveSessionCoordinator) {
        liveCollectorJobs.forEach { it.cancel() }
        liveCollectorJobs = listOf(
            serviceScope.launch {
                coordinator.userTranscription.collect { text ->
                    if (pendingLiveUserTranscript.isBlank()) {
                        resetPendingLiveRagTurnState()
                        val settings = latestValidatedSettings ?: SettingsRepository.getInstance(this@PhoneAIService).getSettings()
                        val effectiveRagDisplayMode = resolveEffectiveLiveRagDisplayMode(
                            liveRagEnabled = settings.liveRagEnabled,
                            configuredMode = settings.liveRagDisplayMode,
                        )
                        if (settings.liveRagEnabled && effectiveRagDisplayMode == LiveRagDisplayMode.SPLIT_LIVE_AND_RAG) {
                            emitLiveRagTranscript(getString(R.string.live_rag_searching))
                        }
                    }
                    if (
                        LiveUsageSummaryRetention.shouldClearForIncomingUserTranscript(
                            hasPersistedUsageSummary = latestLiveUsageMetadata != null,
                            currentUserTranscript = pendingLiveUserTranscript,
                            incomingTranscript = text,
                        )
                    ) {
                        clearLiveUsageSummary()
                    }
                    pendingLiveThoughtSummary = ""
                    pendingLiveUserTranscript = LiveTranscriptAccumulator.merge(
                        current = pendingLiveUserTranscript,
                        incoming = text,
                    )
                    bluetoothManager?.sendMessage(
                        Message(
                            type = MessageType.LIVE_TRANSCRIPTION,
                            payload = LiveTranscriptPayload(
                                role = LiveTranscriptRole.USER,
                                text = pendingLiveUserTranscript,
                            ).toPayloadString()
                        )
                    )
                    ServiceBridge.emitConversation(
                        Message(
                            type = MessageType.LIVE_TRANSCRIPTION,
                            payload = LiveTranscriptPayload(
                                role = LiveTranscriptRole.USER,
                                text = pendingLiveUserTranscript,
                            ).toPayloadString()
                        )
                    )
                }
            },
            serviceScope.launch {
                coordinator.assistantTranscription.collect { text ->
                    pendingLiveAssistantTranscript = LiveTranscriptAccumulator.merge(
                        current = pendingLiveAssistantTranscript,
                        incoming = text,
                    )
                    bluetoothManager?.sendMessage(
                        Message(
                            type = MessageType.LIVE_TRANSCRIPTION,
                            payload = LiveTranscriptPayload(
                                role = LiveTranscriptRole.ASSISTANT,
                                text = pendingLiveAssistantTranscript,
                            ).toPayloadString()
                        )
                    )
                    ServiceBridge.emitConversation(
                        Message(
                            type = MessageType.LIVE_TRANSCRIPTION,
                            payload = LiveTranscriptPayload(
                                role = LiveTranscriptRole.ASSISTANT,
                                text = pendingLiveAssistantTranscript,
                            ).toPayloadString()
                        )
                    )
                }
            },
            serviceScope.launch {
                coordinator.assistantThoughtSummary.collect { text ->
                    pendingLiveThoughtSummary = LiveTranscriptAccumulator.merge(
                        current = pendingLiveThoughtSummary,
                        incoming = text,
                    )
                    bluetoothManager?.sendMessage(
                        Message(
                            type = MessageType.LIVE_TRANSCRIPTION,
                            payload = LiveTranscriptPayload(
                                role = LiveTranscriptRole.THINKING,
                                text = pendingLiveThoughtSummary,
                            ).toPayloadString()
                        )
                    )
                    ServiceBridge.emitConversation(
                        Message(
                            type = MessageType.AI_PROCESSING,
                            payload = getString(
                                R.string.live_thought_summary_status,
                                pendingLiveThoughtSummary
                            )
                        )
                    )
                }
            },
            serviceScope.launch {
                coordinator.assistantAudio.collect { audioData ->
                    if (coordinator.shouldForwardAudioToGlasses) {
                        bluetoothManager?.sendMessage(
                            Message(
                                type = MessageType.LIVE_AUDIO_CHUNK,
                                binaryData = audioData
                            )
                        )
                    }
                }
            },
            serviceScope.launch {
                coordinator.usageMetadata.collect { metadata ->
                    publishLiveUsageSummary(metadata)
                }
            },
            serviceScope.launch {
                coordinator.sessionStatus.collect { status ->
                    ServiceBridge.updateLiveSessionStatus(status)
                }
            },
            serviceScope.launch {
                coordinator.turnComplete.collect {
                    pendingLiveThoughtSummary = ""
                    val settings = latestValidatedSettings ?: SettingsRepository.getInstance(this@PhoneAIService).getSettings()
                    val effectiveRagDisplayMode = resolveEffectiveLiveRagDisplayMode(
                        liveRagEnabled = settings.liveRagEnabled,
                        configuredMode = settings.liveRagDisplayMode,
                    )
                    when {
                        settings.liveRagEnabled &&
                            effectiveRagDisplayMode == LiveRagDisplayMode.SPLIT_LIVE_AND_RAG -> {
                            emitLiveRagTranscript(
                                text = pendingLiveRagTurnState.resolveSplitPanels(
                                    assistantText = pendingLiveAssistantTranscript,
                                    searchingLabel = getString(R.string.live_rag_searching),
                                    noResultLabel = getString(R.string.live_rag_no_result),
                                    turnComplete = true,
                                ).rightText,
                                isFinal = true,
                            )
                        }
                        else -> {
                            val finalText = pendingLiveRagTurnState.resolveFinalText(
                                displayMode = effectiveRagDisplayMode,
                                assistantText = pendingLiveAssistantTranscript,
                                noResultLabel = getString(R.string.live_rag_no_result),
                            )
                            if (!finalText.isNullOrBlank()) {
                                bluetoothManager?.sendMessage(Message.aiResponseText(finalText))
                            }
                        }
                    }
                    persistCompletedLiveTurn()
                }
            },
            serviceScope.launch {
                coordinator.interrupted.collect {
                    Log.d(TAG, "Live session interrupted by user")
                    if (coordinator.shouldForwardAudioToGlasses) {
                        bluetoothManager?.sendMessage(
                            Message(type = MessageType.LIVE_AUDIO_STOP)
                        )
                    }
                    pendingLiveThoughtSummary = ""
                    pendingLiveAssistantTranscript = ""
                    currentLiveTurnConversationId = null
                    resetPendingLiveRagTurnState()
                    ServiceBridge.emitConversation(
                        Message(type = MessageType.AI_PROCESSING, payload = null)
                    )
                }
            },
            serviceScope.launch {
                coordinator.sessionNotices.collect { notice ->
                    Log.w(TAG, "Live session notice: $notice")
                    ServiceBridge.emitConversation(
                        Message(
                            type = MessageType.AI_PROCESSING,
                            payload = notice
                        )
                    )
                }
            },
            serviceScope.launch {
                coordinator.errorMessage.collect { errorMessage ->
                    if (errorMessage.isNullOrBlank()) {
                        return@collect
                    }
                    handleLiveSessionError(errorMessage)
                }
            },
            serviceScope.launch {
                coordinator.sessionState.collect { state ->
                    handleLiveSessionState(state)
                }
            }
        )
    }

    private suspend fun persistCompletedLiveTurn() {
        Log.d(TAG, "Live turn complete")
        val settings = latestValidatedSettings ?: SettingsRepository.getInstance(this).getSettings()
        val titleSeed = pendingLiveUserTranscript.ifBlank {
            pendingLiveAssistantTranscript.ifBlank { "Live turn" }
        }
        val conversationId = prepareLiveTurnConversation(settings, titleSeed)

        if (pendingLiveUserTranscript.isNotBlank()) {
            saveUserMessage(pendingLiveUserTranscript, conversationId = conversationId)
        }

        if (pendingLiveAssistantTranscript.isNotBlank()) {
            saveAssistantMessage(
                content = pendingLiveAssistantTranscript,
                modelId = "gemini-live",
                settings = settings,
                conversationId = conversationId,
            )
        }

        pendingLiveUserTranscript = ""
        pendingLiveAssistantTranscript = ""
        if (settings.alwaysStartNewAiSession) {
            currentLiveTurnConversationId = null
        }
        resetPendingLiveRagTurnState()
    }

    private fun handleLiveSessionState(state: LiveSessionState) {
        Log.d(TAG, "Live session state: $state")
        when (state) {
            LiveSessionState.ACTIVE -> {
                if (!liveSessionAnnouncedToGlasses) {
                    liveSessionAnnouncedToGlasses = true
                    val settings = latestValidatedSettings ?: SettingsRepository.getInstance(this).getSettings()
                    serviceScope.launch {
                        sendLiveSessionStateToGlasses(
                            settings = settings,
                            sessionActive = true,
                        )
                    }
                }
            }

            LiveSessionState.CONNECTING,
            LiveSessionState.RECONNECTING -> {
                if (LiveUsageSummaryRetention.shouldClearForSessionState(state)) {
                    clearLiveUsageSummary()
                }
            }

            LiveSessionState.ERROR -> {
                currentLiveTurnConversationId = null
                pendingLiveUserTranscript = ""
                pendingLiveAssistantTranscript = ""
                resetPendingLiveRagTurnState()
                serviceScope.launch {
                    val presentation = resolveLiveErrorPresentation(liveCoordinator?.errorMessage?.value)
                    bluetoothManager?.sendMessage(Message.aiError(presentation.userMessage))
                    if (liveSessionAnnouncedToGlasses) {
                        val settings = latestValidatedSettings ?: SettingsRepository.getInstance(this@PhoneAIService).getSettings()
                        sendLiveSessionStateToGlasses(
                            settings = settings,
                            sessionActive = false,
                        )
                    }
                }
                liveSessionAnnouncedToGlasses = false
            }

            LiveSessionState.IDLE, LiveSessionState.STOPPING -> {
                currentLiveTurnConversationId = null
                pendingLiveUserTranscript = ""
                pendingLiveAssistantTranscript = ""
                resetPendingLiveRagTurnState()
                if (liveSessionAnnouncedToGlasses) {
                    liveSessionAnnouncedToGlasses = false
                    serviceScope.launch {
                        val settings = latestValidatedSettings ?: SettingsRepository.getInstance(this@PhoneAIService).getSettings()
                        sendLiveSessionStateToGlasses(
                            settings = settings,
                            sessionActive = false,
                        )
                    }
                }
            }

            else -> Unit
        }
    }

    private fun shutdownLiveCoordinator() {
        liveCollectorJobs.forEach { it.cancel() }
        liveCollectorJobs = emptyList()
        liveCoordinator?.release()
        liveCoordinator = null
        ServiceBridge.updateLiveSessionStatus(null)
        currentLiveTurnConversationId = null
        pendingLiveUserTranscript = ""
        pendingLiveAssistantTranscript = ""
        resetPendingLiveRagTurnState()
        clearLiveUsageSummary()

        if (liveSessionAnnouncedToGlasses) {
            liveSessionAnnouncedToGlasses = false
            serviceScope.launch {
                val settings = latestValidatedSettings ?: SettingsRepository.getInstance(this@PhoneAIService).getSettings()
                sendLiveSessionStateToGlasses(
                    settings = settings,
                    sessionActive = false,
                )
            }
        }
    }

    private suspend fun handleLiveSessionError(rawError: String) {
        val presentation = resolveLiveErrorPresentation(rawError)
        ServiceBridge.emitConversation(
            Message(
                type = MessageType.AI_ERROR,
                payload = presentation.userMessage
            )
        )
        if (presentation.shouldNotifyApiKeyMissing) {
            notifyApiKeyMissing()
        }
    }

    private fun publishLiveUsageSummary(metadata: LiveUsageMetadata) {
        if (metadata == latestLiveUsageMetadata) {
            return
        }

        latestLiveUsageMetadata = metadata
        val estimatedModelCost = GeminiLiveCostEstimator.estimate(metadata)
        ServiceBridge.updateLiveUsageSummary(
            metadata.toStatusSummary(estimatedModelCostUsd = estimatedModelCost?.usd)
        )
        val logSummary = metadata.toLogSummary(estimatedModelCostUsd = estimatedModelCost?.usd)
        val logSuffix = estimatedModelCost?.let { ", fallback=${it.usedFallbackHeuristics}" }.orEmpty()
        LogManager.getInstance(this).i(TAG, "Live usage updated: $logSummary$logSuffix")
    }

    private fun clearLiveUsageSummary() {
        latestLiveUsageMetadata = null
        ServiceBridge.updateLiveUsageSummary(null)
    }

    private fun resetPendingLiveRagTurnState() {
        pendingLiveRagTurnState = LiveRagTurnState()
    }

    private suspend fun emitLiveRagTranscript(
        text: String,
        isFinal: Boolean = false,
    ) {
        bluetoothManager?.sendMessage(
            Message(
                type = MessageType.LIVE_TRANSCRIPTION,
                payload = LiveTranscriptPayload(
                    role = LiveTranscriptRole.RAG,
                    text = text,
                    isFinal = isFinal,
                ).toPayloadString()
            )
        )
    }

    private fun isGlassesConnected(): Boolean {
        return bluetoothManager?.connectionState?.value == BluetoothConnectionState.CONNECTED
    }

    private fun buildLiveSessionControlPayload(
        settings: ApiSettings,
        sessionActive: Boolean,
    ): String {
        val effectiveRagDisplayMode = resolveEffectiveLiveRagDisplayMode(
            liveRagEnabled = settings.liveRagEnabled,
            configuredMode = settings.liveRagDisplayMode,
        )
        return LiveSessionControlPayload(
            sessionActive = sessionActive,
            liveModeEnabled = settings.liveModeEnabled,
            effectiveInputSource = resolveLiveControlInputSource(settings),
            cameraMode = settings.liveCameraMode.name,
            cameraIntervalSec = settings.liveCameraIntervalSec,
            liveRagEnabled = settings.liveRagEnabled,
            ragDisplayMode = effectiveRagDisplayMode,
            splitScrollMode = settings.liveRagSplitScrollMode,
            autoScrollSpeedLevel = ApiSettings.clampLiveRagAutoScrollSpeedLevel(
                settings.liveRagAutoScrollSpeedLevel
            ),
        ).toPayloadString()
    }

    private fun resolveLiveControlInputSource(settings: ApiSettings): LiveControlInputSource {
        return when (
            liveCoordinator?.sessionStatus?.value?.inputSource ?: when (settings.liveInputSource) {
                LiveInputSource.AUTO -> if (isGlassesConnected()) LiveInputSource.GLASSES else LiveInputSource.PHONE
                else -> settings.liveInputSource
            }
        ) {
            LiveInputSource.PHONE -> LiveControlInputSource.PHONE
            LiveInputSource.GLASSES -> LiveControlInputSource.GLASSES
            LiveInputSource.AUTO -> LiveControlInputSource.UNKNOWN
        }
    }

    private suspend fun sendLiveSessionStateToGlasses(
        settings: ApiSettings,
        sessionActive: Boolean,
    ) {
        bluetoothManager?.sendMessage(
            Message(
                type = if (sessionActive) {
                    MessageType.LIVE_SESSION_START
                } else {
                    MessageType.LIVE_SESSION_END
                },
                payload = buildLiveSessionControlPayload(
                    settings = settings,
                    sessionActive = sessionActive,
                )
            )
        )
    }

    private suspend fun syncLiveSessionStateToGlasses(settings: ApiSettings) {
        if (!isGlassesConnected()) {
            return
        }

        val sessionState = liveCoordinator?.sessionState?.value
        val sessionActive = sessionState == LiveSessionState.ACTIVE
        if (!settings.liveModeEnabled && !liveSessionAnnouncedToGlasses && !sessionActive) {
            return
        }

        sendLiveSessionStateToGlasses(
            settings = settings,
            sessionActive = sessionActive,
        )
        liveSessionAnnouncedToGlasses = sessionActive
    }

    private suspend fun handleGlassesLiveSessionToggleRequest() {
        val settings = latestValidatedSettings ?: SettingsRepository.getInstance(this).getSettings()
        val sessionActive = liveCoordinator?.sessionState?.value == LiveSessionState.ACTIVE
        val livePayload = LiveSessionControlPayload.fromPayload(
            buildLiveSessionControlPayload(
                settings = settings,
                sessionActive = sessionActive,
            )
        ) ?: return

        if (!livePayload.canToggleFromGlasses) {
            sendLiveSessionStateToGlasses(
                settings = settings,
                sessionActive = sessionActive,
            )
            return
        }

        if (sessionActive) {
            shutdownLiveCoordinator()
        } else {
            handleLiveModeTransition(settings)
        }
    }
    
    /**
     * Process phone recording - transcribe and analyze with AI
     * Called when user stops recording from phone microphone
     * @param recordingId The ID of the recording in database
     * @param filePath The path to the WAV file
     */
    private suspend fun processPhoneRecording(recordingId: String, filePath: String) {
        // Deduplicate: skip if this recording is already being processed
        if (!processingRecordingIds.add(recordingId)) {
            Log.w(TAG, "Recording $recordingId is already being processed, skipping duplicate request")
            return
        }
        
        try {
            // Early check for empty file path
            if (filePath.isBlank()) {
                Log.w(TAG, "Recording $recordingId has empty file path, skipping")
                return
            }
            
            // Check if already processed in database (prevents duplicate processing)
            val existingRecording = recordingRepository?.getRecordingById(recordingId)
            if (existingRecording != null && 
                !existingRecording.transcript.isNullOrBlank() && 
                !existingRecording.aiResponse.isNullOrBlank()) {
                Log.d(TAG, "Recording $recordingId already has transcript and AI response, skipping duplicate")
                return
            }
            
            // Check settings - should we auto-analyze?
            val settings = SettingsRepository.getInstance(this).getSettings()
            if (!settings.autoAnalyzeRecordings) {
                Log.d(TAG, "Auto-analyze disabled, skipping recording: $recordingId")
                return
            }
            
            Log.d(TAG, "Processing phone recording: $recordingId, path: $filePath")
            
            // Check if any speech service is available
            if (sttService == null && speechService == null) {
                Log.e(TAG, "Speech service not available - no API key configured")
                recordingRepository?.markError(recordingId, getString(R.string.service_not_ready))
                notifyApiKeyMissing()
                return
            }
            
            // Read audio file
            val audioFile = java.io.File(filePath)
            if (!audioFile.exists()) {
                Log.e(TAG, "Recording file not found: $filePath")
                recordingRepository?.markError(recordingId, "File not found")
                return
            }
            
            val audioData = audioFile.readBytes()
            Log.d(TAG, "Read audio file: ${audioData.size} bytes")
            
            // Notify UI and glasses: transcribing
            if (settings.pushRecordingToGlasses) {
                bluetoothManager?.sendMessage(Message.aiProcessing(getString(R.string.recognizing_speech)))
            }
            ServiceBridge.emitConversation(Message(
                type = MessageType.AI_PROCESSING,
                payload = getString(R.string.recognizing_speech)
            ))
            
            // 1. Speech recognition
            val transcriptResult = performSpeechRecognition(audioData, filePath, settings.speechLanguage)
            val transcript = extractTranscript(transcriptResult, recordingId, settings) ?: return
            
            // 2. Update recording with transcript
            recordingRepository?.updateTranscript(recordingId, transcript)
            
            // 3. Notify UI and glasses: analyzing
            if (settings.pushRecordingToGlasses) {
                bluetoothManager?.sendMessage(Message.aiProcessing(getString(R.string.thinking)))
            }
            ServiceBridge.emitConversation(Message(
                type = MessageType.AI_PROCESSING,
                payload = getString(R.string.thinking)
            ))
            
            // 4. AI conversation / Docs routing
            Log.d(TAG, "Getting assistant response for phone recording...")
            val routedResult = resolveTextAssistantResponse(
                settings = settings,
                text = transcript,
                inputType = AssistantInputType.VOICE,
            )
            val aiResponse = routedResult.text
            
            Log.d(TAG, "Phone recording AI response: $aiResponse")
            
            // 5. Update recording with AI response
            recordingRepository?.updateAiResponse(
                id = recordingId,
                response = aiResponse,
                providerId = buildConversationProviderId(settings),
                modelId = routedResult.modelId
            )
            
            // 6. Send result to glasses (if enabled) and phone UI
            if (settings.pushRecordingToGlasses) {
                bluetoothManager?.sendMessage(Message(type = MessageType.USER_TRANSCRIPT, payload = transcript))
                bluetoothManager?.sendMessage(
                    Message.aiResponseText(
                        buildGlassesAssistantResponse(
                            answerText = aiResponse,
                            route = routedResult.route,
                            sources = routedResult.sources,
                            normalizer = inputNormalizer,
                        )
                    )
                )
            }
            
            ServiceBridge.emitConversation(Message(
                type = MessageType.USER_TRANSCRIPT,
                payload = transcript
            ))
            ServiceBridge.emitConversation(Message(
                type = MessageType.AI_RESPONSE_TEXT,
                payload = aiResponse
            ))
            
            // 7. Save to conversation history
            val requestConversationId = prepareRequestConversation(settings, transcript)
            saveUserMessage(transcript, conversationId = requestConversationId)
            saveAssistantMessage(
                content = aiResponse,
                modelId = routedResult.modelId,
                route = routedResult.route,
                sources = routedResult.sources,
                settings = settings,
                conversationId = requestConversationId,
            )
            
            // 8. TTS playback (optional)
            maybeSpeakAssistantResponse(settings, aiResponse)
            
            Log.d(TAG, "Phone recording processed successfully: $recordingId")
            
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            Log.d(TAG, "Phone recording processing cancelled (service stopping)")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error processing phone recording", e)
            notifyProcessingError(recordingId, e.message ?: "Unknown error")
        } finally {
            processingRecordingIds.remove(recordingId)
        }
    }
    
    /**
     * Determine audio MIME type from file extension
     */
    private fun getAudioMimeType(filePath: String): String = when {
        filePath.endsWith(".m4a", ignoreCase = true) || filePath.endsWith(".mp4", ignoreCase = true) -> "audio/mp4"
        filePath.endsWith(".aac", ignoreCase = true) -> "audio/aac"
        filePath.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
        filePath.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
        else -> "audio/wav"
    }
    
    /**
     * Perform speech recognition using the best available STT service
     */
    private suspend fun performSpeechRecognition(
        audioData: ByteArray,
        filePath: String,
        languageCode: String
    ): SpeechResult? {
        val isEncodedAudio = filePath.endsWith(".m4a", ignoreCase = true) ||
                filePath.endsWith(".mp4", ignoreCase = true) ||
                filePath.endsWith(".aac", ignoreCase = true) ||
                filePath.endsWith(".mp3", ignoreCase = true) ||
                filePath.endsWith(".ogg", ignoreCase = true)
        val audioMimeType = getAudioMimeType(filePath)
        
        Log.d(TAG, "Starting speech recognition for phone recording... (encoded=$isEncodedAudio, mimeType=$audioMimeType)")
        
        val stt = sttService
        val speech = speechService
        val serviceName = if (stt != null) "dedicated STT (${stt.provider.name})" else "AI-based speech"
        Log.d(TAG, "Using $serviceName service, language: $languageCode")
        
        return when {
            stt != null && isEncodedAudio -> stt.transcribeAudioFile(audioData, audioMimeType, languageCode)
            stt != null -> stt.transcribe(audioData, languageCode)
            speech != null && isEncodedAudio -> speech.transcribeAudioFile(audioData, audioMimeType, languageCode)
            speech != null -> speech.transcribe(audioData, languageCode)
            else -> null
        }
    }
    
    /**
     * Extract transcript text from SpeechResult, notifying errors as needed.
     * Returns null if transcription failed (caller should return early).
     */
    private suspend fun extractTranscript(
        result: SpeechResult?,
        recordingId: String,
        settings: ApiSettings
    ): String? {
        return when (result) {
            is SpeechResult.Success -> {
                Log.d(TAG, "Phone recording transcript: ${result.text}")
                result.text
            }
            is SpeechResult.Error -> {
                Log.e(TAG, "Phone recording transcription error: ${result.message}")
                notifyRecordingError(recordingId, result.message, settings)
                null
            }
            null -> {
                notifyRecordingError(recordingId, getString(R.string.service_not_ready), settings)
                notifyApiKeyMissing()
                null
            }
        }
    }
    
    /**
     * Notify recording error to database, glasses, and phone UI
     */
    private suspend fun notifyRecordingError(recordingId: String, message: String, settings: ApiSettings) {
        recordingRepository?.markError(recordingId, message)
        if (settings.pushRecordingToGlasses) {
            bluetoothManager?.sendMessage(Message.aiError(message))
        }
        ServiceBridge.emitConversation(Message(type = MessageType.AI_ERROR, payload = message))
    }

    private fun maybeSpeakAssistantResponse(settings: ApiSettings, text: String) {
        if (!settings.autoReadResponsesAloud) {
            Log.d(TAG, "Skipping automatic TTS playback because auto-read is disabled")
            return
        }
        ttsService?.speak(text) { }
    }
    
    /**
     * Notify processing error with best-effort error propagation
     */
    private suspend fun notifyProcessingError(recordingId: String, errorMsg: String) {
        recordingRepository?.markError(recordingId, errorMsg)
        try {
            val settings = SettingsRepository.getInstance(this).getSettings()
            if (settings.pushRecordingToGlasses) {
                bluetoothManager?.sendMessage(Message.aiError(errorMsg))
            }
            ServiceBridge.emitConversation(Message(type = MessageType.AI_ERROR, payload = errorMsg))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to notify error state", e)
        }
    }

    private suspend fun resolveTextAssistantResponse(
        settings: ApiSettings,
        text: String,
        inputType: AssistantInputType,
    ): RoutedAssistantResult {
        val route = routeResolver.resolve(settings, inputType)
        val normalizedText = inputNormalizer.normalizeText(text)

        return when (route.target) {
            RouteTarget.GENERAL_AI -> {
                val responseText = aiService?.chat(normalizedText)
                    ?: throw IllegalStateException("AI service not configured")
                RoutedAssistantResult(
                    text = cleanMarkdown(responseText),
                    route = route,
                    modelId = buildConversationModelId(settings),
                )
            }

            RouteTarget.DOCS_RAG -> {
                val validation = settings.validateForDocs()
                if (validation !is SettingsValidationResult.Valid) {
                    throw IllegalStateException(validation.toDocsErrorMessage())
                }

                val docsResolution = resolveDocsTextQuery(
                    settings = settings,
                    route = route,
                    normalizedQuestion = normalizedText,
                    ragService = ragService,
                    generalAnswer = { question ->
                        aiService?.chat(question)
                            ?: throw IllegalStateException("AI service not configured")
                    },
                    onDocsHealthy = { message ->
                        markDocsAssistantHealthy(
                            settingsRepository = SettingsRepository.getInstance(this),
                            message = message,
                        )
                    },
                    onDocsFailure = { message ->
                        markDocsAssistantFailure(
                            settingsRepository = SettingsRepository.getInstance(this),
                            message = message,
                        )
                    },
                )

                RoutedAssistantResult(
                    text = cleanMarkdown(docsResolution.answerText),
                    route = docsResolution.route,
                    sources = docsResolution.sources,
                    modelId = buildConversationModelId(settings),
                )
            }

            else -> throw IllegalStateException("Unsupported route for text input")
        }
    }

    private suspend fun resolvePhotoAssistantResponse(
        settings: ApiSettings,
        photoBytes: ByteArray,
    ): RoutedAssistantResult {
        val route = routeResolver.resolve(settings, AssistantInputType.PHOTO)
        return when (route.target) {
            RouteTarget.GENERAL_AI,
            RouteTarget.GENERAL_AI_FALLBACK -> {
                val responseText = aiService?.analyzeImage(
                    photoBytes,
                    getString(R.string.image_analysis_prompt)
                ) ?: throw IllegalStateException("AI service not configured")

                RoutedAssistantResult(
                    text = cleanMarkdown(responseText),
                    route = route,
                    modelId = buildConversationModelId(settings),
                )
            }

            RouteTarget.DOCS_PHOTO_CONTEXT_RAG -> {
                val validation = settings.validateForDocs()
                if (validation !is SettingsValidationResult.Valid) {
                    throw IllegalStateException(validation.toDocsErrorMessage())
                }

                val sceneDescription = aiService?.analyzeImage(
                    photoBytes,
                    "Describe this image for document lookup. Focus on visible equipment, UI state, text, and user intent."
                ) ?: throw IllegalStateException("AI service not configured")

                val ragAnswer = ragService.answer(
                    settings = settings.toAnythingLlmSettings(),
                    question = inputNormalizer.combinePhotoContext(sceneDescription),
                ).getOrElse { error ->
                    markDocsAssistantFailure(settingsRepository = SettingsRepository.getInstance(this), message = error.message ?: "Docs photo lookup failed.")
                    throw error
                }
                markDocsAssistantHealthy(
                    settingsRepository = SettingsRepository.getInstance(this),
                    message = "AnythingLLM responded from ${settings.anythingLlmWorkspaceSlug.ifBlank { "workspace" }}.",
                )

                RoutedAssistantResult(
                    text = cleanMarkdown(ragAnswer.answerText),
                    route = route,
                    sources = ragAnswer.sources,
                    modelId = buildConversationModelId(settings),
                )
            }

            RouteTarget.DOCS_RAG -> throw IllegalStateException("Unsupported route for photo input")
        }
    }

    private fun SettingsValidationResult.toDocsErrorMessage(): String {
        return when (this) {
            is SettingsValidationResult.InvalidConfiguration -> message
            is SettingsValidationResult.MissingApiKey -> "Required API key is missing."
            is SettingsValidationResult.MissingSpeechService -> "Speech service is not configured."
            SettingsValidationResult.Valid -> "Settings are valid."
        }
    }
    
    /**
     * Clean markdown formatting from AI response for better display
     */
    private fun cleanMarkdown(text: String): String {
        return text
            // Remove bold/italic markers
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")  // **bold**
            .replace(Regex("\\*(.+?)\\*"), "$1")        // *italic*
            .replace(Regex("__(.+?)__"), "$1")          // __bold__
            .replace(Regex("_(.+?)_"), "$1")            // _italic_
            // Remove headers
            .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
            // Remove code blocks
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("`(.+?)`"), "$1")
            // Remove links but keep text
            .replace(Regex("\\[(.+?)]\\(.+?\\)"), "$1")
            // Remove bullet points
            .replace(Regex("^[\\-*+]\\s+", RegexOption.MULTILINE), "• ")
            // Remove numbered lists formatting
            .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
            // Clean up extra whitespace
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private suspend fun prepareRequestConversation(
        settings: ApiSettings,
        titleSeed: String,
    ): String? {
        AiRequestSessionSupport.clearHistoryIfNeeded(settings, listOf(aiService, speechService))
        if (!settings.alwaysStartNewAiSession) {
            ensureVoiceConversationSession(settings)
            return currentVoiceConversationId
        }
        return createRequestConversation(settings, titleSeed)
    }

    private suspend fun prepareLiveTurnConversation(
        settings: ApiSettings,
        titleSeed: String,
    ): String? {
        if (!settings.alwaysStartNewAiSession) {
            ensureVoiceConversationSession(settings)
            return currentVoiceConversationId
        }
        if (currentLiveTurnConversationId == null) {
            currentLiveTurnConversationId = createRequestConversation(settings, titleSeed)
        }
        return currentLiveTurnConversationId
    }

    private suspend fun createRequestConversation(
        settings: ApiSettings,
        titleSeed: String,
    ): String? {
        return try {
            val conversation = conversationRepository?.createConversation(
                providerId = buildConversationProviderId(settings),
                modelId = buildConversationModelId(settings),
                title = buildRequestConversationTitle(titleSeed),
                systemPrompt = settings.systemPrompt,
            )
            conversation?.id
        } catch (e: Exception) {
            Log.e(TAG, "Error creating request conversation", e)
            null
        }
    }

    private fun buildRequestConversationTitle(titleSeed: String): String {
        val normalized = titleSeed
            .replace("\r\n", "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(50)
        return if (normalized.isNotBlank()) normalized else "New Conversation"
    }
    
    /**
     * Ensure a voice conversation session exists for persisting voice interactions
     * Creates a new conversation if needed, or continues using existing one from today
     */
    private suspend fun ensureVoiceConversationSession(settings: ApiSettings) {
        try {
            if (currentVoiceConversationId == null) {
                // First, try to find an existing voice session from today
                val existingSession = conversationRepository?.findTodayVoiceSession()
                
                if (existingSession != null) {
                    // Reuse existing session from today
                    currentVoiceConversationId = existingSession.id
                    Log.d(TAG, "Reusing existing voice conversation session: $currentVoiceConversationId")
                } else {
                    // Create a new conversation for voice interactions
                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    val title = getString(R.string.voice_session_title, dateFormat.format(java.util.Date()))
                    
                    val conversation = conversationRepository?.createConversation(
                        providerId = buildConversationProviderId(settings),
                        modelId = buildConversationModelId(settings),
                        title = title,
                        systemPrompt = settings.systemPrompt
                    )
                    
                    currentVoiceConversationId = conversation?.id
                    Log.d(TAG, "Created voice conversation session: $currentVoiceConversationId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating voice conversation session", e)
        }
    }
    
    /**
     * Save user message to database
     */
    private suspend fun saveUserMessage(
        content: String,
        imagePath: String? = null,
        conversationId: String? = null,
    ) {
        val settings = SettingsRepository.getInstance(this).getSettings()
        val targetConversationId = conversationId ?: run {
            ensureVoiceConversationSession(settings)
            currentVoiceConversationId
        }
        targetConversationId?.let { resolvedConversationId ->
            try {
                conversationRepository?.addUserMessage(
                    conversationId = resolvedConversationId,
                    content = content,
                    imagePath = imagePath,
                )
                Log.d(TAG, "Saved user message to conversation: $resolvedConversationId")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving user message", e)
            }
        }
    }
    
    /**
     * Save AI response to database
     */
    private suspend fun saveAssistantMessage(
        content: String,
        modelId: String?,
        route: RouteDecision? = null,
        sources: List<SourcePreview> = emptyList(),
        settings: ApiSettings = SettingsRepository.getInstance(this).getSettings(),
        conversationId: String? = null,
    ) {
        val targetConversationId = conversationId ?: run {
            ensureVoiceConversationSession(settings)
            currentVoiceConversationId
        }
        targetConversationId?.let { resolvedConversationId ->
            try {
                conversationRepository?.addAssistantMessage(
                    conversationId = resolvedConversationId,
                    content = content,
                    modelId = modelId,
                    metadata = route?.let { buildAssistantMessageMetadata(it, settings, sources) },
                    conversationMetadata = route?.let { buildConversationMetadata(it, settings) },
                )
                if (conversationId != null && settings.alwaysStartNewAiSession) {
                    val messageCount = conversationRepository?.getMessageCount(resolvedConversationId) ?: 0
                    if (messageCount <= 2) {
                        conversationRepository?.autoGenerateTitle(resolvedConversationId)
                    }
                }
                Log.d(TAG, "Saved assistant message to conversation: $resolvedConversationId")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving assistant message", e)
            }
        }
    }
    
    private fun updateNotification(state: BluetoothConnectionState) {
        val statusText = when (state) {
            BluetoothConnectionState.DISCONNECTED -> getString(R.string.disconnected)
            BluetoothConnectionState.LISTENING -> getString(R.string.waiting_glasses)
            BluetoothConnectionState.CONNECTING -> getString(R.string.connecting)
            BluetoothConnectionState.CONNECTED -> getString(R.string.connected_glasses)
        }
        
        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(createPendingIntent())
            .setOngoing(true)
            .build()
        
        startForeground(Constants.NOTIFICATION_ID, notification)
    }
    
    private fun createPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Rokid AI Assistant")
            .setContentText("Service running, waiting for glasses connection...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(createPendingIntent())
            .setOngoing(true)
            .build()
    }
    
    /**
     * Prepend an explicit language-enforcement instruction to [basePrompt].
     *
     * Older / smaller models (e.g. Gemini 2.0 Flash) may ignore a general
     * "respond in the user's language" instruction unless it is prominently placed at
     * the very beginning of the system prompt.  This approach keeps the original
     * user-written base prompt intact while ensuring model compliance.
     *
     * If [responseLanguage] is blank the base prompt is returned unchanged — the model
     * will rely on the conversation language as usual.
     *
     * TODO: Verify via manual testing that the prepended instruction does not conflict
     *       with any user-customised system prompt.
     */
    private fun buildSystemPromptWithLanguage(basePrompt: String, responseLanguage: String): String {
        if (responseLanguage.isBlank()) return basePrompt

        val locale = java.util.Locale.forLanguageTag(responseLanguage)

        // Short English language name used in the closing phrase, e.g. "Korean", "French".
        // Falls back to the raw tag if the JVM returns an empty string for an unknown code.
        val englishLanguageName = locale.getDisplayLanguage(java.util.Locale.ENGLISH)
            .takeIf { it.isNotBlank() } ?: responseLanguage

        // Full English label including region qualifier for disambiguation when needed,
        // e.g. "Chinese (Taiwan)" vs "Chinese (China)", "French (France)".
        val fullEnglishLabel = locale.getDisplayName(java.util.Locale.ENGLISH)
            .takeIf { it.isNotBlank() } ?: englishLanguageName

        // Native self-name, e.g. "한국어", "日本語", "français".
        // Omitted when it is identical to the English name (e.g. for "English").
        val nativeName = locale.getDisplayLanguage(locale)
            .takeIf { it.isNotBlank() && !it.equals(englishLanguageName, ignoreCase = true) }

        val languageLabel = if (nativeName != null) "$fullEnglishLabel ($nativeName)" else fullEnglishLabel

        val langInstruction =
            "CRITICAL INSTRUCTION: You MUST respond ONLY in $languageLabel. " +
            "Never mix in other languages. All responses must be in pure $englishLanguageName.\n\n"

        return langInstruction + basePrompt
    }

    /**
     * Create AI service
     */
    private fun createAiService(settings: ApiSettings): AiServiceProvider {
        // If no API key configured, notify user
        val effectiveSettings = if (settings.getCurrentApiKey().isBlank()) {
            // Check if BuildConfig has a valid Gemini API key as fallback
            if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
                Log.d(TAG, "No API key for ${settings.aiProvider}, using development fallback")
                settings.copy(
                    aiProvider = AiProvider.GEMINI,
                    aiModelId = "gemini-2.5-flash",
                    geminiApiKey = BuildConfig.GEMINI_API_KEY
                )
            } else {
                // No fallback available - user must configure API key
                Log.w(TAG, "No API key configured. Please set up an API key in Settings.")
                settings
            }
        } else {
            settings
        }
        
        val promptEnhancedSettings = effectiveSettings.copy(
            systemPrompt = buildSystemPromptWithLanguage(
                effectiveSettings.systemPrompt,
                effectiveSettings.responseLanguage
            )
        )
        return AiServiceFactory.createService(promptEnhancedSettings)
    }
    
    /**
     * Create speech recognition service
     * Prefer providers supporting STT
     */
    private fun createSpeechService(settings: ApiSettings): AiServiceProvider? {
        // Check if current provider supports speech recognition
        if (settings.aiProvider.supportsSpeech && settings.getCurrentApiKey().isNotBlank()) {
            Log.d(TAG, "Using current provider ${settings.aiProvider} for STT")
            return AiServiceFactory.createService(settings)
        }
        
        // Try other configured providers that support STT
        val sttProviders = listOf(AiProvider.GEMINI, AiProvider.OPENAI, AiProvider.GROQ, AiProvider.XAI)
        for (provider in sttProviders) {
            val apiKey = settings.getApiKeyForProvider(provider)
            if (apiKey.isNotBlank()) {
                val sttModel = getSttFallbackModelId(provider)
                if (sttModel != null) {
                    Log.d(TAG, "Using ${provider.name} for speech recognition, model: $sttModel")
                    return AiServiceFactory.createService(settings.copy(
                        aiProvider = provider,
                        aiModelId = sttModel
                    ))
                }
            }
        }
        
        // Try fallback to BuildConfig Gemini key
        if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            Log.d(TAG, "No STT provider configured, using fallback Gemini")
            return AiServiceFactory.createService(settings.copy(
                aiProvider = AiProvider.GEMINI,
                aiModelId = "gemini-2.5-flash",
                geminiApiKey = BuildConfig.GEMINI_API_KEY
            ))
        }
        
        // No speech service available
        Log.e(TAG, "No speech recognition service available!")
        return null
    }
    
    /**
     * Get known-working STT model for a provider.
     * Uses stable model IDs that are confirmed to exist in each provider's API,
     * rather than picking the first model from the display list (which may include
     * unreleased/preview models like gemini-3-pro).
     */
    private fun getSttFallbackModelId(provider: AiProvider): String? {
        return when (provider) {
            AiProvider.GEMINI -> "gemini-2.5-flash"
            AiProvider.OPENAI -> "gpt-5-mini"
            AiProvider.GROQ -> "whisper-large-v3"
            AiProvider.XAI -> "grok-2-latest"
            else -> AvailableModels.getModelsForProvider(provider).firstOrNull()?.id
        }
    }
    
    /**
     * Create dedicated STT service for specialized providers
     * Uses SttServiceFactory for providers like Deepgram, Azure, Aliyun, etc.
     */
    private fun createSttService(settings: ApiSettings): SttService? {
        val sttProvider = settings.sttProvider
        
        // Check if this is a provider that uses main AI API keys (handled by speechService)
        val aiBasedProviders = listOf(
            SttProvider.GEMINI,
            SttProvider.OPENAI_WHISPER,
            SttProvider.GROQ_WHISPER
        )
        
        if (sttProvider in aiBasedProviders) {
            Log.d(TAG, "STT provider ${sttProvider.name} uses main AI service, no dedicated STT service needed")
            return null
        }
        
        // Create dedicated STT service using SttServiceFactory
        val sttCredentials = settings.toSttCredentials()
        val service = SttServiceFactory.createService(sttCredentials, settings)
        
        if (service != null) {
            Log.d(TAG, "Created dedicated STT service for provider: ${sttProvider.name}")
        } else {
            Log.w(TAG, "Failed to create STT service for ${sttProvider.name} - credentials may be missing")
        }
        
        return service
    }
    
    /**
     * Check if speech service is available
     */
    fun isSpeechServiceAvailable(): Boolean = speechService != null || sttService != null
    
    /**
     * Validate and correct settings
     * Ensure selected model is compatible with AI provider
     */
    private fun validateAndCorrectSettings(settings: ApiSettings): ApiSettings {
        // Migrate deprecated model IDs to their replacements
        val deprecatedModelMigrations = mapOf(
            "sonar-reasoning" to "sonar-reasoning-pro"
        )
        val migratedSettings = deprecatedModelMigrations[settings.aiModelId]?.let { replacement ->
            Log.w(TAG, "Model '${settings.aiModelId}' is deprecated, migrating to '$replacement'")
            settings.copy(aiModelId = replacement)
        } ?: settings
        
        val modelInfo = AvailableModels.findModel(migratedSettings.aiModelId)
        
        // If model info not found, use provider's default model
        if (modelInfo == null) {
            Log.w(TAG, "Unknown model: ${migratedSettings.aiModelId}, using default model for ${migratedSettings.aiProvider}")
            val defaultModel = AvailableModels.getModelsForProvider(migratedSettings.aiProvider).firstOrNull()
            return if (defaultModel != null) {
                migratedSettings.copy(aiModelId = defaultModel.id)
            } else {
                // Fall back to Gemini
                migratedSettings.copy(
                    aiProvider = AiProvider.GEMINI,
                    aiModelId = "gemini-2.5-flash"
                )
            }
        }
        
        // If model doesn't match provider, correct the provider
        if (modelInfo.provider != migratedSettings.aiProvider) {
            Log.w(TAG, "Model ${migratedSettings.aiModelId} belongs to ${modelInfo.provider}, correcting provider")
            return migratedSettings.copy(aiProvider = modelInfo.provider)
        }
        
        // Check if provider has API key
        if (migratedSettings.getCurrentApiKey().isBlank()) {
            Log.w(TAG, "No API key for ${migratedSettings.aiProvider}, checking for fallback")
            // Try to use provider that has API key
            for (provider in AiProvider.entries) {
                val apiKey = migratedSettings.getApiKeyForProvider(provider)
                if (apiKey.isNotBlank()) {
                    val defaultModel = AvailableModels.getModelsForProvider(provider).firstOrNull()
                    if (defaultModel != null) {
                        Log.d(TAG, "Falling back to ${provider.name}")
                        return migratedSettings.copy(
                            aiProvider = provider,
                            aiModelId = defaultModel.id
                        )
                    }
                }
            }
        }
        
        return migratedSettings
    }
}

/**
 * STT Service (Simplified version)
 */
class SpeechToTextService(private val apiKey: String) {
    
    suspend fun transcribe(audioData: ByteArray): String? {
        return withContext(Dispatchers.IO) {
            try {
                // TODO: Implement OpenAI Whisper API call
                // Returning mock result here
                "This is a test voice input"
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * TTS Service
 * Routes through EdgeTtsClient (high-quality neural voices) or System TTS
 * based on user preference in ApiSettings.ttsProvider.
 */
class TextToSpeechService(private val context: android.content.Context) {

    private val TAG = "TextToSpeechService"

    private var tts: android.speech.tts.TextToSpeech? = null
    private var systemTtsReady = false
    private val edgeTtsClient = com.example.rokidphone.service.EdgeTtsClient()
    private val ttsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Always initialise system TTS so it's available as fallback / if user picks SYSTEM_TTS
        tts = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                val defaultLocale = java.util.Locale.getDefault()
                val langResult = tts?.setLanguage(defaultLocale)
                systemTtsReady = langResult != android.speech.tts.TextToSpeech.LANG_MISSING_DATA
                        && langResult != android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                android.util.Log.e(TAG, "System TTS initialisation failed (status=$status)")
            }
        }
    }

    /**
     * Primary speak entry-point. Reads TTS provider preference from [SettingsRepository].
     */
    fun speak(text: String, onAudioChunk: (ByteArray) -> Unit) {
        val settings = SettingsRepository.getInstance(context).getSettings()

        when (settings.ttsProvider) {
            com.example.rokidphone.data.TtsProvider.EDGE_TTS -> speakWithEdge(text, settings, onAudioChunk)
            com.example.rokidphone.data.TtsProvider.SYSTEM_TTS -> speakWithSystemTts(text, settings)
            com.example.rokidphone.data.TtsProvider.GOOGLE_TRANSLATE_TTS -> speakWithSystemTts(text, settings)
        }
    }

    // ── Edge TTS ─────────────────────────────────────────

    private fun speakWithEdge(text: String, settings: com.example.rokidphone.data.ApiSettings, onAudioChunk: (ByteArray) -> Unit) {
        ttsScope.launch {
            try {
                // Resolve voice
                val voice = if (settings.ttsVoiceOverride.isNotBlank()) {
                    settings.ttsVoiceOverride
                } else {
                    val locale = try {
                        java.util.Locale.forLanguageTag(settings.speechLanguage)
                    } catch (_: Exception) { java.util.Locale.getDefault() }
                    com.example.rokidphone.ui.detectEdgeVoice(text, locale)
                }

                // Format rate & pitch
                val rate = com.example.rokidphone.ui.formatEdgeRate(settings.ttsSpeechRate)
                val pitch = com.example.rokidphone.ui.formatEdgePitch(settings.ttsPitch)

                android.util.Log.d(TAG, "Edge TTS: voice=$voice, rate=$rate, pitch=$pitch")

                val result = edgeTtsClient.synthesize(text, voice, rate, pitch)

                result.onSuccess { audioData ->
                    if (audioData.isNotEmpty()) {
                        onAudioChunk(audioData)
                        withContext(Dispatchers.Main) {
                            playAudioData(audioData)
                        }
                    } else {
                        android.util.Log.w(TAG, "Edge TTS returned empty data, falling back to system TTS")
                        withContext(Dispatchers.Main) { speakWithSystemTts(text, settings) }
                    }
                }

                result.onFailure { err ->
                    android.util.Log.w(TAG, "Edge TTS failed: ${err.message}, falling back to system TTS")
                    withContext(Dispatchers.Main) { speakWithSystemTts(text, settings) }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Edge TTS error", e)
                withContext(Dispatchers.Main) { speakWithSystemTts(text, null) }
            }
        }
    }

    // ── System TTS ───────────────────────────────────────

    private fun speakWithSystemTts(text: String, settings: com.example.rokidphone.data.ApiSettings?) {
        if (!systemTtsReady || tts == null) {
            android.util.Log.e(TAG, "System TTS not ready")
            return
        }

        val locale = detectLocaleForText(text)
        val langResult = tts?.setLanguage(locale)
        if (langResult == android.speech.tts.TextToSpeech.LANG_MISSING_DATA ||
            langResult == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
            android.util.Log.w(TAG, "System TTS: locale '$locale' not supported, using device default")
            tts?.setLanguage(java.util.Locale.getDefault())
        } else {
            // Explicitly set a native voice to avoid non-native accent
            val nativeVoice = tts?.voices?.firstOrNull { 
                it.locale.language == locale.language && !it.isNetworkConnectionRequired 
            }
            if (nativeVoice != null) {
                tts?.voice = nativeVoice
            }
        }

        tts?.setSpeechRate(settings?.systemTtsSpeechRate ?: 1.0f)
        tts?.setPitch(settings?.systemTtsPitch ?: 1.0f)
        tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // ── Audio playback ───────────────────────────────────

    private fun playAudioData(audioData: ByteArray) {
        try {
            val tempFile = java.io.File.createTempFile("tts_", ".mp3", context.cacheDir)
            java.io.FileOutputStream(tempFile).use { it.write(audioData) }
            android.media.MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                setOnCompletionListener { mp -> mp.release(); tempFile.delete() }
                setOnErrorListener { mp, _, _ -> mp.release(); tempFile.delete(); true }
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to play audio", e)
        }
    }

    // ── Lifecycle ────────────────────────────────────────

    fun shutdown() {
        ttsScope.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun detectLocaleForText(text: String): java.util.Locale {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return java.util.Locale.getDefault()

        return when {
            trimmed.any { it in '\uAC00'..'\uD7AF' } -> java.util.Locale.KOREAN
            trimmed.any { it in '\u4E00'..'\u9FFF' } -> java.util.Locale.TRADITIONAL_CHINESE
            trimmed.any { it in '\u3040'..'\u30FF' } -> java.util.Locale.JAPANESE
            else -> java.util.Locale.getDefault()
        }
    }
}

/**
 * Bluetooth Manager (Simplified version)
 */
class BluetoothManager(private val context: android.content.Context) {
    
    private val _messageFlow = MutableSharedFlow<Message>()
    val messageFlow = _messageFlow.asSharedFlow()
    
    suspend fun sendMessage(message: Message) {
        // TODO: Implement Bluetooth send
    }
    
    fun disconnect() {
        // TODO: Implement Bluetooth disconnect
    }
}
