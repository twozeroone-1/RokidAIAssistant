package com.example.rokidglasses

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.core.view.WindowCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rokidglasses.input.PrimaryTapAction
import com.example.rokidglasses.input.PrimaryTapActionResolver
import com.example.rokidglasses.input.RemoteKeyAction
import com.example.rokidglasses.input.SpriteButtonAction
import com.example.rokidglasses.input.SpriteButtonIntentInterpreter
import com.example.rokidglasses.input.TapUiSnapshot
import com.example.rokidglasses.focus.FocusRecoveryPolicy
import com.example.rokidglasses.focus.FocusRecoveryState
import com.example.rokidglasses.service.WakeWordService
import com.example.rokidglasses.service.photo.CameraService
import com.example.rokidglasses.ui.SleepModeIndicator
import com.example.rokidglasses.ui.theme.RokidGlassesTheme
import com.example.rokidglasses.viewmodel.GlassesDisplayStage
import com.example.rokidglasses.viewmodel.GlassesViewModel
import com.example.rokidglasses.viewmodel.deriveDisplayStage
import com.example.rokidglasses.viewmodel.toSleepModeSnapshot

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val EXTRA_FOCUS_RECOVERY = "focus_recovery"
        private const val EXTRA_TRIGGER_CAPTURE = "trigger_capture"
        private const val FOCUS_RECOVERY_DELAY_MS = 350L
        private const val ACTION_SPRITE_BUTTON_UP = "com.android.action.ACTION_SPRITE_BUTTON_UP"
        private const val ACTION_SPRITE_BUTTON_DOWN = "com.android.action.ACTION_SPRITE_BUTTON_DOWN"
        private const val ACTION_SPRITE_BUTTON_LONG_PRESS = "com.android.action.ACTION_SPRITE_BUTTON_LONG_PRESS"
    }
    
    // Hold reference to ViewModel for key events
    private var glassesViewModel: GlassesViewModel? = null
    private var suppressedKeyUpCode: Int? = null
    private val primaryTapActionResolver = PrimaryTapActionResolver()
    private val focusRecoveryPolicy = FocusRecoveryPolicy()
    private val focusRecoveryHandler = Handler(Looper.getMainLooper())
    private var isActivityResumed = false
    private var lastFocusRecoveryAtMs = 0L
    private var pendingFocusRecoveryReason: String? = null
    private var isSpriteButtonReceiverRegistered = false
    private val showDeviceSelectorState = mutableStateOf(false)
    private val focusRecoveryRunnable = Runnable {
        attemptFocusRecovery(pendingFocusRecoveryReason ?: "unknown")
    }
    private val spriteButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            Log.d(TAG, "Sprite button broadcast received: action=$action")
            when (SpriteButtonIntentInterpreter.interpret(action)) {
                SpriteButtonAction.None -> Unit
                SpriteButtonAction.TriggerCapturePhoto -> triggerPhotoCaptureFromSystemButton()
            }
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startServices()
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
        registerSpriteButtonReceiver()
        
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
                    showDeviceSelector = showDeviceSelectorState.value,
                    onShowDeviceSelectorChange = { showDeviceSelectorState.value = it },
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
        if (handleHardwareKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    
    /**
     * Handle physical key events from Rokid touchpad
     * - DPAD_UP / Volume Up: Previous page
     * - DPAD_DOWN / Volume Down: Next page
     * - DPAD_CENTER / Enter: Toggle recording (tap) or capture photo (long press)
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        android.util.Log.d("MainActivity", "onKeyDown: keyCode=$keyCode (${KeyEvent.keyCodeToString(keyCode)}), scanCode=${event?.scanCode}, repeat=${event?.repeatCount}")
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        android.util.Log.d("MainActivity", "onKeyUp: keyCode=$keyCode (${KeyEvent.keyCodeToString(keyCode)})")
        return super.onKeyUp(keyCode, event)
    }

    private fun handleHardwareKeyEvent(event: KeyEvent): Boolean {
        val viewModel = glassesViewModel ?: return false
        val keyCode = event.keyCode
        val uiState = viewModel.uiState.value
        val repeatCount = event.repeatCount

        if (suppressedKeyUpCode == keyCode) {
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN &&
            repeatCount == 0 &&
            viewModel.isRemoteKeyLearningActive() &&
            viewModel.captureRemoteLearningKey(keyCode)
        ) {
            suppressedKeyUpCode = keyCode
            return true
        }

        when (viewModel.resolveRemoteKeyAction(keyCode)) {
            RemoteKeyAction.ToggleRecording -> {
                if (event.action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
                    viewModel.triggerRemoteRecordShortcut()
                    suppressedKeyUpCode = keyCode
                }
                return true
            }
            RemoteKeyAction.CapturePhoto -> {
                if (event.action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
                    viewModel.triggerRemoteCameraShortcut()
                    suppressedKeyUpCode = keyCode
                }
                return true
            }
            RemoteKeyAction.None -> Unit
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN && uiState.isPaginated) {
                    viewModel.previousPage()
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN && uiState.isPaginated) {
                    viewModel.nextPage()
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                when {
                    event.action == KeyEvent.ACTION_DOWN && repeatCount == 1 -> {
                        Log.d(TAG, "Long press center = capture photo")
                        viewModel.captureAndSendPhoto()
                        true
                    }
                    event.action == KeyEvent.ACTION_DOWN -> true
                    event.action == KeyEvent.ACTION_UP && event.eventTime - event.downTime < 500 -> {
                        handlePrimaryTap(viewModel)
                        true
                    }
                    else -> true
                }
            }
            KeyEvent.KEYCODE_NOTIFICATION -> {
                if (event.action == KeyEvent.ACTION_UP && event.eventTime - event.downTime < 500) {
                    Log.d(TAG, "Touchpad alternate key mapped to primary action: $keyCode")
                    handlePrimaryTap(viewModel)
                    true
                } else if (event.action == KeyEvent.ACTION_DOWN) {
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_CAMERA, 27,
            KeyEvent.KEYCODE_FOCUS, 260, 261, 262, 263 -> {
                if (event.action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
                    Log.d(TAG, "Camera/Focus key pressed: $keyCode")
                    viewModel.captureAndSendPhoto()
                    true
                } else {
                    true
                }
            }
            KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_DOWN && repeatCount == 1) {
                    Log.d(TAG, "Long press center = capture photo")
                    viewModel.captureAndSendPhoto()
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWakeUpIntent(intent)
    }
    
    private fun handleWakeUpIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_FOCUS_RECOVERY, false) == true) {
            Log.d(TAG, "Focus recovery intent received")
        }
        if (intent?.getBooleanExtra(EXTRA_TRIGGER_CAPTURE, false) == true) {
            triggerPhotoCaptureFromSystemButton()
        }
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
        stopWakeWordService()
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

    private fun stopWakeWordService() {
        stopService(Intent(this, WakeWordService::class.java))
    }
    
    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        cancelFocusRecovery()
        // Ensure screen stays on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: scheduling focus recovery")
        scheduleFocusRecovery("activity_paused")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: scheduling focus recovery")
        scheduleFocusRecovery("activity_stopped")
    }

    override fun onDestroy() {
        isActivityResumed = false
        cancelFocusRecovery()
        unregisterSpriteButtonReceiver()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(TAG, "onWindowFocusChanged: hasFocus=$hasFocus")
        if (hasFocus) {
            cancelFocusRecovery()
        } else {
            scheduleFocusRecovery("window_focus_lost")
        }
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        Log.d(TAG, "onTopResumedActivityChanged: isTopResumedActivity=$isTopResumedActivity")
        if (isTopResumedActivity) {
            cancelFocusRecovery()
        } else {
            scheduleFocusRecovery("top_resumed_lost")
        }
    }

    private fun scheduleFocusRecovery(reason: String) {
        pendingFocusRecoveryReason = reason
        focusRecoveryHandler.removeCallbacks(focusRecoveryRunnable)
        focusRecoveryHandler.postDelayed(focusRecoveryRunnable, FOCUS_RECOVERY_DELAY_MS)
    }

    private fun cancelFocusRecovery() {
        pendingFocusRecoveryReason = null
        focusRecoveryHandler.removeCallbacks(focusRecoveryRunnable)
    }

    private fun attemptFocusRecovery(reason: String) {
        val state = FocusRecoveryState(
            isResumed = isActivityResumed,
            hasWindowFocus = hasWindowFocus(),
            isFinishing = isFinishing,
            isChangingConfigurations = isChangingConfigurations,
        )
        val nowMs = System.currentTimeMillis()
        if (!focusRecoveryPolicy.shouldRecover(state, nowMs, lastFocusRecoveryAtMs)) {
            Log.d(TAG, "Skip focus recovery: reason=$reason, state=$state")
            return
        }

        lastFocusRecoveryAtMs = nowMs
        Log.d(TAG, "Recovering focus: reason=$reason")
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_FOCUS_RECOVERY, true)
            }
        )
    }

    private fun registerSpriteButtonReceiver() {
        if (isSpriteButtonReceiverRegistered) {
            return
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_SPRITE_BUTTON_UP)
            addAction(ACTION_SPRITE_BUTTON_DOWN)
            addAction(ACTION_SPRITE_BUTTON_LONG_PRESS)
        }
        ContextCompat.registerReceiver(
            this,
            spriteButtonReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        isSpriteButtonReceiverRegistered = true
        Log.d(TAG, "Sprite button receiver registered")
    }

    private fun unregisterSpriteButtonReceiver() {
        if (!isSpriteButtonReceiverRegistered) {
            return
        }
        unregisterReceiver(spriteButtonReceiver)
        isSpriteButtonReceiverRegistered = false
        Log.d(TAG, "Sprite button receiver unregistered")
    }

    private fun triggerPhotoCaptureFromSystemButton() {
        val viewModel = glassesViewModel
        if (viewModel != null) {
            Log.d(TAG, "Trigger capture from sprite button")
            viewModel.captureAndSendPhoto()
            return
        }

        Log.d(TAG, "Sprite button received without ViewModel, relaunching activity")
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_TRIGGER_CAPTURE, true)
            }
        )
    }

    private fun handlePrimaryTap(viewModel: GlassesViewModel) {
        val state = viewModel.uiState.value
        when (primaryTapActionResolver.resolve(
            TapUiSnapshot(
                isPaginated = state.isPaginated,
                currentPage = state.currentPage,
                totalPages = state.totalPages,
                isConnected = state.isConnected,
            )
        )) {
            PrimaryTapAction.NextPage -> viewModel.nextPage()
            PrimaryTapAction.PrimaryAction -> viewModel.handlePrimaryAction()
            PrimaryTapAction.ShowDeviceSelector -> {
                viewModel.refreshPairedDevices()
                showDeviceSelectorState.value = true
            }
        }
    }
}

@Composable
fun GlassesMainScreen(
    viewModel: GlassesViewModel,
    showDeviceSelector: Boolean,
    onShowDeviceSelectorChange: (Boolean) -> Unit,
    onScreenTap: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val sleepModeStage = remember(uiState) { deriveDisplayStage(uiState.toSleepModeSnapshot()) }
    val shouldUseSleepMode = uiState.sleepModeEnabled && !uiState.isLiveModeActive
    
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
                    // If paginated, tap goes to next page or exits pagination on last page
                    if (uiState.currentPage < uiState.totalPages - 1) {
                        viewModel.nextPage()
                    } else {
                        viewModel.handlePrimaryAction()
                    }
                } else if (uiState.isConnected) {
                    // When connected, tap screen to advance/dismiss output or toggle recording
                    viewModel.handlePrimaryAction()
                } else {
                    // When disconnected, show device selector
                    viewModel.refreshPairedDevices()
                    onShowDeviceSelectorChange(true)
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
        } else {
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
        if (uiState.isPaginated && (!shouldUseSleepMode || sleepModeStage == GlassesDisplayStage.OUTPUT)) {
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
            MainDisplayArea(
                displayText = uiState.displayText,
                isProcessing = uiState.isProcessing && !shouldUseSleepMode,
                isPaginated = uiState.isPaginated,
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Hint text (bottom)
        if (!shouldUseSleepMode || sleepModeStage == GlassesDisplayStage.OUTPUT) {
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
                    onShowDeviceSelectorChange(false)
                },
                onDismiss = { onShowDeviceSelectorChange(false) }
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (sortedDevices.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_paired_devices) + "\n" + stringResource(R.string.pair_device_hint),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
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
                                    fontSize = 16.sp
                                )
                                if (isRecommended) {
                                    Text(
                                        text = "★ " + stringResource(R.string.recommended),
                                        color = Color(0xFF64B5F6),
                                        fontSize = 12.sp,
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
                fontSize = 10.sp
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
            fontSize = 12.sp
        )
    }
}

@Composable
fun MainDisplayArea(
    displayText: String,
    isProcessing: Boolean,
    isPaginated: Boolean = false,
    currentPage: Int = 0,
    totalPages: Int = 1,
    modifier: Modifier = Modifier
) {
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
        
        AnimatedContent(
            targetState = displayText,
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
        ) { text ->
            Text(
                text = text,
                color = Color.White,
                fontSize = if (isPaginated) 20.sp else 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = if (isPaginated) 28.sp else 32.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Navigation hints for paginated content
        if (isPaginated) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage > 0) {
                    Text(
                        text = "▲",
                        color = Color(0xFF64B5F6),
                        fontSize = 16.sp
                    )
                }
                if (currentPage < totalPages - 1) {
                    Text(
                        text = "▼",
                        color = Color(0xFF64B5F6),
                        fontSize = 16.sp
                    )
                }
            }
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
            fontSize = 12.sp,
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
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}
