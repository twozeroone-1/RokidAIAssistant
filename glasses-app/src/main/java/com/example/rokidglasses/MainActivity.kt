package com.example.rokidglasses

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.core.view.WindowCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rokidglasses.service.WakeWordService
import com.example.rokidglasses.service.photo.CameraService
import com.example.rokidglasses.ui.SleepModeIndicator
import com.example.rokidglasses.ui.theme.GlassesTypographyTokens
import com.example.rokidglasses.ui.theme.RokidGlassesTheme
import com.example.rokidglasses.viewmodel.GlassesDisplayStage
import com.example.rokidglasses.viewmodel.GlassesLivePanelContent
import com.example.rokidglasses.viewmodel.GlassesViewModel
import com.example.rokidglasses.viewmodel.deriveDisplayStage
import com.example.rokidglasses.viewmodel.LiveRagAutoScrollDirection
import com.example.rokidglasses.viewmodel.LiveRagManualScrollCommand
import com.example.rokidglasses.viewmodel.resolveLivePanelContent
import com.example.rokidglasses.viewmodel.resolveDisplayTextRenderState
import com.example.rokidglasses.viewmodel.resolveLiveRagAutoScrollDurationMillis
import com.example.rokidglasses.viewmodel.resolveLiveRagManualScrollTarget
import com.example.rokidglasses.viewmodel.resolveLiveMinimalDisplayText
import com.example.rokidglasses.viewmodel.shouldUseLiveMinimalResponseFontScale
import com.example.rokidglasses.viewmodel.responseFontScaleMultiplier
import com.example.rokidglasses.viewmodel.shouldUseLiveMinimalUi
import com.example.rokidglasses.viewmodel.toSleepModeSnapshot
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // Hold reference to ViewModel for key events
    private var glassesViewModel: GlassesViewModel? = null
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startWakeWordService()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Full screen immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        }
        
        checkPermissions()
        
        // Handle wake up intent
        handleWakeUpIntent(intent)
        
        setContent {
            RokidGlassesTheme {
                val viewModel: GlassesViewModel = viewModel(
                    factory = GlassesViewModel.Factory(this)
                )
                // Store reference for key events
                glassesViewModel = viewModel
                
                GlassesMainScreen(
                    viewModel = viewModel,
                    onScreenTap = { /* Screen tap triggers recording */ }
                )
            }
        }
    }
    
    /**
     * Catch ALL key events at dispatch level for debugging
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        android.util.Log.d("MainActivity", "dispatchKeyEvent: action=${event.action}, keyCode=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)}), scanCode=${event.scanCode}")
        return super.dispatchKeyEvent(event)
    }
    
    /**
     * Handle physical key events from Rokid touchpad
     * - DPAD_UP / Volume Up: Previous page or manual RAG scroll up
     * - DPAD_DOWN / Volume Down: Next page or manual RAG scroll down
     * - DPAD_CENTER / Enter: Toggle recording (tap) or capture photo (long press)
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Debug: Log all key events to identify Rokid camera button keycode
        android.util.Log.d("MainActivity", "onKeyDown: keyCode=$keyCode (${KeyEvent.keyCodeToString(keyCode)}), scanCode=${event?.scanCode}, repeat=${event?.repeatCount}")
        
        val viewModel = glassesViewModel ?: return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            // Swipe up on touchpad / Volume up = Previous page
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_VOLUME_UP -> {
                if (viewModel.handleDirectionalNavigation(LiveRagManualScrollCommand.UP)) {
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            // Swipe down on touchpad / Volume down = Next page
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (viewModel.handleDirectionalNavigation(LiveRagManualScrollCommand.DOWN)) {
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            // Tap on touchpad / Enter = Toggle recording or exit pagination
            // Long press = Take photo (workaround for camera button)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event?.repeatCount == 1) {
                    // First repeat = long press started, capture photo
                    android.util.Log.d("MainActivity", "Long press center = capture photo")
                    viewModel.captureAndSendPhoto()
                    true
                } else if (event?.repeatCount == 0) {
                    // Normal tap handling (will only trigger if key released before repeat)
                    true // Consume but wait for key up
                } else {
                    true // Consume subsequent repeats
                }
            }
            // Some Rokid firmware routes the touchpad tap through NOTIFICATION instead of DPAD_CENTER.
            KeyEvent.KEYCODE_NOTIFICATION -> true
            // Camera button - take photo and send to phone for AI analysis
            KeyEvent.KEYCODE_CAMERA, 27, 
            KeyEvent.KEYCODE_FOCUS, // Some devices use focus key for camera
            260, 261, 262, 263 -> { // Additional camera-related keycodes
                android.util.Log.d("MainActivity", "Camera/Focus key pressed: $keyCode")
                viewModel.captureAndSendPhoto()
                true
            }
            // Long press back = take photo (alternative trigger)
            KeyEvent.KEYCODE_BACK -> {
                if (event?.repeatCount == 1) {
                    android.util.Log.d("MainActivity", "Long press back = capture photo")
                    viewModel.captureAndSendPhoto()
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
    
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        android.util.Log.d("MainActivity", "onKeyUp: keyCode=$keyCode (${KeyEvent.keyCodeToString(keyCode)})")
        
        val viewModel = glassesViewModel ?: return super.onKeyUp(keyCode, event)
        val uiState = viewModel.uiState.value
        
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // Only handle short tap (not long press which was already handled)
                if (event?.eventTime?.minus(event.downTime) ?: 0 < 500) {
                    viewModel.handlePrimaryAction()
                }
                true
            }
            KeyEvent.KEYCODE_NOTIFICATION -> {
                if (event?.eventTime?.minus(event.downTime) ?: 0 < 500) {
                    android.util.Log.d("MainActivity", "Touchpad alternate key mapped to primary action: $keyCode")
                    viewModel.handlePrimaryAction()
                }
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWakeUpIntent(intent)
    }
    
    private fun handleWakeUpIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("wake_up", false) == true) {
            // Woke up by voice, can auto start recording
            // TODO: Notify ViewModel to start recording
        }
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA  // Camera permission for photo capture
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ))
        }
        
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (notGranted.isNotEmpty()) {
            Log.w(TAG, "Missing permissions: ${notGranted.joinToString(", ")}")
            permissionLauncher.launch(notGranted.toTypedArray())
        } else {
            Log.d(TAG, "All permissions granted")
            startServices()
        }
    }
    
    private fun startServices() {
        startWakeWordService()
        startCameraService()
    }
    
    private fun startCameraService() {
        if (!CameraService.isRunning) {
            val serviceIntent = Intent(this, CameraService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }
    
    private fun startWakeWordService() {
        if (!WakeWordService.isRunning) {
            val serviceIntent = Intent(this, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Ensure screen stays on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
fun GlassesMainScreen(
    viewModel: GlassesViewModel,
    onScreenTap: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeviceSelector by remember { mutableStateOf(false) }
    val sleepModeStage = remember(uiState) { deriveDisplayStage(uiState.toSleepModeSnapshot()) }
    val shouldUseSleepMode = uiState.sleepModeEnabled && !uiState.isLiveModeActive
    val shouldUseLiveMinimalUi = uiState.shouldUseLiveMinimalUi()
    val displayText = remember(uiState, shouldUseLiveMinimalUi) {
        if (shouldUseLiveMinimalUi) {
            uiState.resolveLiveMinimalDisplayText()
        } else {
            uiState.displayText
        }
    }
    val useResponseFontScale = remember(uiState, shouldUseLiveMinimalUi) {
        if (shouldUseLiveMinimalUi) {
            uiState.shouldUseLiveMinimalResponseFontScale()
        } else {
            uiState.displayUsesResponseFontScale
        }
    }
    
    // Track swipe gesture for pagination
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 50f
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(uiState.isPaginated) {
                if (uiState.isPaginated) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            when {
                                swipeOffset > swipeThreshold -> viewModel.previousPage() // Swipe down = previous
                                swipeOffset < -swipeThreshold -> viewModel.nextPage() // Swipe up = next
                            }
                            swipeOffset = 0f
                        },
                        onDragCancel = { swipeOffset = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            swipeOffset += dragAmount
                        }
                    )
                }
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (uiState.isPaginated) {
                    viewModel.handlePrimaryAction()
                } else if (uiState.isConnected) {
                    viewModel.handlePrimaryAction()
                } else {
                    // When disconnected, show device selector
                    viewModel.refreshPairedDevices()
                    showDeviceSelector = true
                }
            }
    ) {
        // Status indicator (top right)
        if (shouldUseSleepMode) {
            SleepModeIndicator(
                stage = sleepModeStage,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        } else if (!shouldUseLiveMinimalUi) {
            StatusIndicator(
                isConnected = uiState.isConnected,
                isListening = uiState.isListening,
                deviceName = uiState.connectedDeviceName,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }
        
        // Page indicator (top left) - only show when paginated
        if (
            uiState.isPaginated &&
            !shouldUseLiveMinimalUi &&
            (!shouldUseSleepMode || sleepModeStage == GlassesDisplayStage.OUTPUT)
        ) {
            PageIndicator(
                currentPage = uiState.currentPage + 1,
                totalPages = uiState.totalPages,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }
        
        // Main display area (centered)
        if (!shouldUseSleepMode || sleepModeStage == GlassesDisplayStage.OUTPUT) {
            val livePanelContent = resolveLivePanelContent(
                isLiveModeActive = uiState.isLiveModeActive,
                liveRagEnabled = uiState.liveRagEnabled,
                ragDisplayMode = uiState.liveRagDisplayMode,
                splitScrollMode = uiState.liveRagSplitScrollMode,
                assistantText = uiState.liveAssistantText,
                ragText = uiState.liveRagText,
                ragTextFinalized = uiState.liveRagIsFinal,
            )
            MainDisplayArea(
                displayText = displayText,
                livePanelContent = livePanelContent,
                isProcessing = uiState.isProcessing && !shouldUseSleepMode && !shouldUseLiveMinimalUi,
                responseFontScalePercent = uiState.responseFontScalePercent,
                useResponseFontScale = useResponseFontScale,
                liveRagAutoScrollSpeedLevel = uiState.liveRagAutoScrollSpeedLevel,
                liveRagAutoScrollDirection = uiState.liveRagAutoScrollDirection,
                liveRagManualScrollCommands = viewModel.liveRagManualScrollCommands,
                showSplitPanelTitles = !shouldUseLiveMinimalUi,
                showPaginationNavigationHints = !shouldUseLiveMinimalUi,
                isPaginated = uiState.isPaginated,
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Hint text (bottom)
        if (!shouldUseLiveMinimalUi && (!shouldUseSleepMode || sleepModeStage == GlassesDisplayStage.OUTPUT)) {
            HintText(
                hint = uiState.hintText,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
        
        // Device selector dialog
        if (showDeviceSelector) {
            DeviceSelectorDialog(
                devices = uiState.availableDevices,
                cxrConnectedPhoneName = uiState.cxrConnectedPhoneName,
                onDeviceSelected = { device ->
                    viewModel.connectToDevice(device)
                    showDeviceSelector = false
                },
                onDismiss = { showDeviceSelector = false }
            )
        }
    }
}

@Composable
fun DeviceSelectorDialog(
    devices: List<android.bluetooth.BluetoothDevice>,
    cxrConnectedPhoneName: String? = null,
    onDeviceSelected: (android.bluetooth.BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    // Sort devices: CXR-connected phone first, then by name
    val sortedDevices = remember(devices, cxrConnectedPhoneName) {
        if (cxrConnectedPhoneName != null) {
            devices.sortedByDescending { 
                @Suppress("MissingPermission")
                it.name?.equals(cxrConnectedPhoneName, ignoreCase = true) == true 
            }
        } else {
            devices
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text(
                text = stringResource(R.string.select_phone),
                color = Color.White,
                fontSize = GlassesTypographyTokens.DeviceSelectorTitleSp.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (sortedDevices.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_paired_devices) + "\n" + stringResource(R.string.pair_device_hint),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = GlassesTypographyTokens.DeviceSelectorBodySp.sp
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortedDevices.forEach { device ->
                        @Suppress("MissingPermission")
                        val deviceName = device.name ?: stringResource(R.string.unknown_device)
                        val isRecommended = cxrConnectedPhoneName != null && 
                            deviceName.equals(cxrConnectedPhoneName, ignoreCase = true)
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDeviceSelected(device) },
                            color = if (isRecommended) Color(0xFF1E3A5F) else Color(0xFF2A2A2A),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = deviceName,
                                    color = Color.White,
                                    fontSize = GlassesTypographyTokens.DeviceSelectorDeviceNameSp.sp
                                )
                                if (isRecommended) {
                                    Text(
                                        text = "★ " + stringResource(R.string.recommended),
                                        color = Color(0xFF64B5F6),
                                        fontSize = GlassesTypographyTokens.DeviceSelectorRecommendedSp.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color(0xFF64B5F6))
            }
        }
    )
}

@Composable
fun StatusIndicator(
    isConnected: Boolean,
    isListening: Boolean,
    deviceName: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Connection status
            StatusDot(
                color = if (isConnected) Color(0xFF64B5F6) else Color(0xFFFF5722),
                label = if (isConnected) stringResource(R.string.connected) else stringResource(R.string.tap_to_connect)
            )
            
            // Recording status
            AnimatedVisibility(
                visible = isListening,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                StatusDot(
                    color = Color(0xFFF44336),
                    label = stringResource(R.string.recording),
                    pulsing = true
                )
            }
        }
        
        // Display connected device name
        if (isConnected && deviceName != null) {
            Text(
                text = deviceName,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = GlassesTypographyTokens.StatusDeviceNameSp.sp
            )
        }
    }
}

@Composable
fun StatusDot(
    color: Color,
    label: String,
    pulsing: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = androidx.compose.foundation.shape.CircleShape)
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = GlassesTypographyTokens.StatusLabelSp.sp
        )
    }
}

@Composable
fun MainDisplayArea(
    displayText: String,
    livePanelContent: GlassesLivePanelContent,
    isProcessing: Boolean,
    responseFontScalePercent: Int,
    useResponseFontScale: Boolean,
    liveRagAutoScrollSpeedLevel: Int,
    liveRagAutoScrollDirection: LiveRagAutoScrollDirection,
    liveRagManualScrollCommands: Flow<LiveRagManualScrollCommand>,
    showSplitPanelTitles: Boolean,
    showPaginationNavigationHints: Boolean,
    isPaginated: Boolean = false,
    currentPage: Int = 0,
    totalPages: Int = 1,
    modifier: Modifier = Modifier
) {
    val responseScale = if (useResponseFontScale) {
        responseFontScaleMultiplier(responseFontScalePercent)
    } else {
        1f
    }
    val fontSize = if (isPaginated) {
        GlassesTypographyTokens.MainResponsePaginatedSp * responseScale
    } else {
        GlassesTypographyTokens.MainResponseSingleSp * responseScale
    }
    val lineHeight = if (isPaginated) {
        GlassesTypographyTokens.MainResponsePaginatedLineHeightSp * responseScale
    } else {
        GlassesTypographyTokens.MainResponseSingleLineHeightSp * responseScale
    }
    val splitFontSize = GlassesTypographyTokens.MainResponseSplitSp * responseScale
    val splitLineHeight = GlassesTypographyTokens.MainResponseSplitLineHeightSp * responseScale
    val displayRenderState = resolveDisplayTextRenderState(
        text = displayText,
        responseFontScalePercent = responseFontScalePercent,
        useResponseFontScale = useResponseFontScale,
        isPaginated = isPaginated,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = isProcessing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color(0xFF64B5F6),
                strokeWidth = 3.dp
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (livePanelContent.showSplitPanels) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SplitPanel(
                    title = stringResource(R.string.live_split_panel_live),
                    text = livePanelContent.leftText,
                    showTitle = showSplitPanelTitles,
                    modifier = Modifier.weight(1f),
                    fontSize = splitFontSize,
                    lineHeight = splitLineHeight,
                    autoScroll = livePanelContent.autoScrollPanels,
                    autoScrollDirection = liveRagAutoScrollDirection,
                    autoScrollSpeedLevel = liveRagAutoScrollSpeedLevel,
                    manualScroll = livePanelContent.manualScrollPanels,
                    manualScrollCommands = liveRagManualScrollCommands,
                )
                SplitPanel(
                    title = stringResource(R.string.live_split_panel_rag),
                    text = livePanelContent.rightText,
                    showTitle = showSplitPanelTitles,
                    modifier = Modifier.weight(1f),
                    fontSize = splitFontSize,
                    lineHeight = splitLineHeight,
                    autoScroll = livePanelContent.autoScrollPanels,
                    autoScrollDirection = liveRagAutoScrollDirection,
                    autoScrollSpeedLevel = liveRagAutoScrollSpeedLevel,
                    manualScroll = livePanelContent.manualScrollPanels,
                    manualScrollCommands = liveRagManualScrollCommands,
                )
            }
        } else {
            AnimatedContent(
                targetState = displayRenderState,
                transitionSpec = {
                    if (isPaginated) {
                        // Slide animation for pagination
                        slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                    } else {
                        fadeIn() togetherWith fadeOut()
                    }
                },
                label = "display_text"
            ) { renderState ->
                Text(
                    text = renderState.text,
                    color = Color.White,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = lineHeight.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Navigation hints for paginated content
        if (showPaginationNavigationHints && isPaginated && !livePanelContent.showSplitPanels) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage > 0) {
                    Text(
                        text = "▲",
                        color = Color(0xFF64B5F6),
                        fontSize = GlassesTypographyTokens.PageNavigationIconSp.sp
                    )
                }
                if (currentPage < totalPages - 1) {
                    Text(
                        text = "▼",
                        color = Color(0xFF64B5F6),
                        fontSize = GlassesTypographyTokens.PageNavigationIconSp.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitPanel(
    title: String,
    text: String,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
    fontSize: Float,
    lineHeight: Float,
    autoScroll: Boolean,
    autoScrollDirection: LiveRagAutoScrollDirection,
    autoScrollSpeedLevel: Int,
    manualScroll: Boolean,
    manualScrollCommands: Flow<LiveRagManualScrollCommand>?,
) {
    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableStateOf(0) }

    LaunchedEffect(text, autoScroll, manualScroll, autoScrollSpeedLevel, autoScrollDirection) {
        if (text.isBlank()) {
            scrollState.scrollTo(0)
            return@LaunchedEffect
        }

        if (!autoScroll) {
            if (!manualScroll) {
                scrollState.scrollTo(0)
            }
            return@LaunchedEffect
        }

        withFrameNanos { }
        withFrameNanos { }
        val targetValue = when (autoScrollDirection) {
            LiveRagAutoScrollDirection.UP -> 0
            LiveRagAutoScrollDirection.DOWN -> scrollState.maxValue
        }
        val scrollDistancePx = abs(targetValue - scrollState.value)
        val durationMillis = resolveLiveRagAutoScrollDurationMillis(
            maxScrollPx = scrollDistancePx,
            speedLevel = autoScrollSpeedLevel,
        ) ?: return@LaunchedEffect
        if (scrollDistancePx <= 0) {
            return@LaunchedEffect
        }
        scrollState.animateScrollTo(
            value = targetValue,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = LinearEasing,
            ),
        )
    }

    LaunchedEffect(manualScroll, manualScrollCommands, viewportHeightPx) {
        if (!manualScroll || manualScrollCommands == null) {
            return@LaunchedEffect
        }

        manualScrollCommands.collect { command ->
            val targetValue = resolveLiveRagManualScrollTarget(
                currentScrollPx = scrollState.value,
                maxScrollPx = scrollState.maxValue,
                viewportHeightPx = viewportHeightPx,
                command = command,
            )
            if (targetValue != null) {
                scrollState.animateScrollTo(
                    value = targetValue,
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = LinearEasing,
                    ),
                )
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showTitle) {
            Text(
                text = title,
                color = Color(0xFF64B5F6),
                fontSize = GlassesTypographyTokens.MainResponseSplitTitleSp.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .onSizeChanged { viewportHeightPx = it.height }
                .verticalScroll(scrollState)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = fontSize.sp,
                lineHeight = lineHeight.sp,
            )
        }
    }
}

@Composable
fun PageIndicator(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF2A2A2A),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = stringResource(R.string.page_indicator, currentPage, totalPages),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = GlassesTypographyTokens.PageIndicatorSp.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun HintText(
    hint: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = hint,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = GlassesTypographyTokens.HintSp.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}
