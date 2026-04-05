package com.example.rokidphone.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.rokidcommon.protocol.LiveRagDisplayMode
import com.example.rokidcommon.protocol.LiveRagSplitScrollMode
import com.example.rokidphone.R
import com.example.rokidphone.data.*
import com.example.rokidphone.service.ai.AiServiceFactory
import com.example.rokidphone.service.stt.SttProvider
import com.example.rokidphone.service.stt.SttServiceFactory
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: ApiSettings,
    onSettingsChange: (ApiSettings) -> Unit,
    onBack: () -> Unit,
    onNavigateToLogViewer: () -> Unit = {},
    onNavigateToLlmParameters: () -> Unit = {},
    onNavigateToTtsSettings: () -> Unit = {},
    onNavigateToDocsSettings: () -> Unit = {},
    onTestConnection: (ApiSettings) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showProviderDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showSpeechServiceDialog by remember { mutableStateOf(false) }
    var showSystemPromptDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCustomModelDialog by remember { mutableStateOf(false) }
    var showLiveInputDialog by remember { mutableStateOf(false) }
    var showExperimentalLiveMicProfileDialog by remember { mutableStateOf(false) }
    var showLiveOutputDialog by remember { mutableStateOf(false) }
    var showLiveCameraModeDialog by remember { mutableStateOf(false) }
    var showLiveCameraIntervalDialog by remember { mutableStateOf(false) }
    var showLiveVoiceDialog by remember { mutableStateOf(false) }
    var showLiveThinkingDialog by remember { mutableStateOf(false) }
    var currentLanguage by remember { mutableStateOf(LanguageManager.getCurrentLanguage(context)) }

    val liveInputLabel = remember(settings.liveInputSource) {
        when (settings.liveInputSource) {
            LiveInputSource.AUTO -> context.getString(R.string.live_input_source_auto_label)
            LiveInputSource.GLASSES -> context.getString(R.string.live_input_source_glasses_label)
            LiveInputSource.PHONE -> context.getString(R.string.live_input_source_phone_label)
        }
    }
    val liveOutputLabel = remember(settings.liveOutputTarget) {
        when (settings.liveOutputTarget) {
            LiveOutputTarget.AUTO -> context.getString(R.string.live_output_target_auto_label)
            LiveOutputTarget.GLASSES -> context.getString(R.string.live_output_target_glasses_label)
            LiveOutputTarget.PHONE -> context.getString(R.string.live_output_target_phone_label)
            LiveOutputTarget.BOTH -> context.getString(R.string.live_output_target_both_label)
        }
    }
    val experimentalLiveMicProfileLabel = remember(settings.experimentalLiveMicProfile, context) {
        when (ApiSettings.clampExperimentalLiveMicProfile(settings.experimentalLiveMicProfile)) {
            0 -> context.getString(R.string.experimental_live_mic_profile_near)
            1 -> context.getString(R.string.experimental_live_mic_profile_far)
            else -> context.getString(R.string.experimental_live_mic_profile_panorama)
        }
    }
    val liveCameraModeLabel = remember(settings.liveCameraMode) {
        when (settings.liveCameraMode) {
            LiveCameraMode.OFF -> "Off"
            LiveCameraMode.MANUAL -> "Manual capture"
            LiveCameraMode.INTERVAL -> "Interval"
            LiveCameraMode.REALTIME -> "Realtime"
        }
    }
    val liveThinkingLabel = remember(settings.liveThinkingLevel, context) {
        when (settings.liveThinkingLevel) {
            LiveThinkingLevel.DEFAULT -> context.getString(R.string.live_thinking_default)
            LiveThinkingLevel.MINIMAL -> context.getString(R.string.live_thinking_minimal)
            LiveThinkingLevel.LOW -> context.getString(R.string.live_thinking_low)
            LiveThinkingLevel.MEDIUM -> context.getString(R.string.live_thinking_medium)
            LiveThinkingLevel.HIGH -> context.getString(R.string.live_thinking_high)
        }
    }
    val liveVoiceLabel = remember(settings.liveVoiceName) {
        settings.getLiveVoice().displayLabel
    }
    val currentModel = remember(settings.aiProvider, settings.aiModelId, settings.customModelName) {
        AvailableModels.findModel(settings.aiModelId)
    }
    val currentModelLabel = remember(currentModel, settings.aiProvider, settings.customModelName, settings.aiModelId) {
        if (settings.aiProvider == AiProvider.CUSTOM) {
            settings.customModelName.ifBlank { "custom" }
        } else {
            currentModel?.displayName ?: settings.aiModelId
        }
    }
    val aiServicePresentation = remember(settings.liveModeEnabled, currentModelLabel) {
        resolveAiServicePresentation(settings, currentModelLabel)
    }
    val realtimeSettingItems = remember(settings) {
        realtimeConversationSettingItems(settings)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.api_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Language settings section
            item {
                SettingsSection(title = stringResource(R.string.language_settings)) {
                    SettingsRow(
                        title = stringResource(R.string.app_language),
                        subtitle = "${currentLanguage.nativeName} (${currentLanguage.displayName})",
                        onClick = { showLanguageDialog = true }
                    )
                }
            }
            
            // AI service settings section
            item {
                SettingsSection(title = stringResource(R.string.ai_service)) {
                    SettingsRowWithSwitch(
                        title = stringResource(R.string.realtime_conversation),
                        subtitle = stringResource(R.string.realtime_conversation_description),
                        checked = settings.liveModeEnabled,
                        onCheckedChange = { enabled ->
                            onSettingsChange(settings.copy(liveModeEnabled = enabled))
                        }
                    )

                    HorizontalDivider()

                    if (aiServicePresentation.showLiveLockCard) {
                        LiveModeLockCard()
                    } else {
                        SettingsRow(
                            title = stringResource(R.string.ai_provider),
                            subtitle = stringResource(settings.aiProvider.displayNameResId),
                            onClick = { showProviderDialog = true }
                        )

                        HorizontalDivider()

                        SettingsRow(
                            title = stringResource(R.string.ai_model),
                            subtitle = currentModelLabel,
                            onClick = { showModelDialog = true }
                        )
                    }
                }
            }

            if (settings.liveModeEnabled) {
                item {
                    SettingsSection(title = stringResource(R.string.realtime_conversation)) {
                        realtimeSettingItems.forEachIndexed { index, item ->
                            when (item.key) {
                                RealtimeConversationSettingKey.LIVE_INPUT_SOURCE -> {
                                    SettingsRow(
                                        title = stringResource(R.string.live_input_source),
                                        subtitle = liveInputLabel,
                                        onClick = { showLiveInputDialog = true }
                                    )
                                }

                                RealtimeConversationSettingKey.LIVE_OUTPUT_TARGET -> {
                                    SettingsRow(
                                        title = stringResource(R.string.live_output_target),
                                        subtitle = if (settings.liveAnswerAudioEnabled) {
                                            liveOutputLabel
                                        } else {
                                            stringResource(R.string.live_output_target_disabled_summary)
                                        },
                                        onClick = { showLiveOutputDialog = true },
                                        enabled = settings.liveAnswerAudioEnabled
                                    )
                                }

                                RealtimeConversationSettingKey.LIVE_ANSWER_AUDIO -> {
                                    SettingsRowWithSwitch(
                                        title = stringResource(R.string.live_answer_audio),
                                        subtitle = stringResource(R.string.live_answer_audio_description),
                                        checked = settings.liveAnswerAudioEnabled,
                                        onCheckedChange = { enabled ->
                                            onSettingsChange(settings.copy(liveAnswerAudioEnabled = enabled))
                                        }
                                    )
                                }

                                RealtimeConversationSettingKey.LIVE_BARGE_IN -> {
                                    SettingsRowWithSwitch(
                                        title = stringResource(R.string.live_barge_in),
                                        subtitle = stringResource(R.string.live_barge_in_description),
                                        checked = settings.liveBargeInEnabled,
                                        onCheckedChange = { enabled ->
                                            onSettingsChange(settings.copy(liveBargeInEnabled = enabled))
                                        }
                                    )
                                }

                                RealtimeConversationSettingKey.LIVE_VOICE -> {
                                    SettingsRow(
                                        title = stringResource(R.string.live_voice),
                                        subtitle = liveVoiceLabel,
                                        onClick = { showLiveVoiceDialog = true }
                                    )
                                }

                                RealtimeConversationSettingKey.LIVE_GOOGLE_SEARCH -> {
                                    SettingsRowWithSwitch(
                                        title = stringResource(R.string.live_google_search),
                                        subtitle = stringResource(R.string.live_google_search_description),
                                        checked = settings.liveGoogleSearchEnabled,
                                        onCheckedChange = { enabled ->
                                            onSettingsChange(settings.copy(liveGoogleSearchEnabled = enabled))
                                        }
                                    )
                                }

                                RealtimeConversationSettingKey.LIVE_RAG -> {
                                    SettingsRowWithSwitch(
                                        title = stringResource(R.string.live_rag),
                                        subtitle = stringResource(R.string.live_rag_description),
                                        checked = settings.liveRagEnabled,
                                        onCheckedChange = { enabled ->
                                            onSettingsChange(settings.copy(liveRagEnabled = enabled))
                                        }
                                    )

                                    if (settings.liveRagEnabled) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LiveRagDisplayModeSelector(
                                            selectedMode = settings.liveRagDisplayMode,
                                            onModeSelected = { mode ->
                                                onSettingsChange(settings.copy(liveRagDisplayMode = mode))
                                            }
                                        )

                                        if (settings.liveRagDisplayMode == LiveRagDisplayMode.SPLIT_LIVE_AND_RAG) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            LiveRagSplitScrollModeSelector(
                                                selectedMode = settings.liveRagSplitScrollMode,
                                                onModeSelected = { mode ->
                                                    onSettingsChange(settings.copy(liveRagSplitScrollMode = mode))
                                                }
                                            )

                                            if (settings.liveRagSplitScrollMode == LiveRagSplitScrollMode.AUTO) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                SettingsSliderRow(
                                                    title = stringResource(R.string.live_rag_auto_scroll_speed),
                                                    subtitle = stringResource(R.string.live_rag_auto_scroll_speed_description),
                                                    value = settings.liveRagAutoScrollSpeedLevel.toFloat(),
                                                    valueRange = ApiSettings.MIN_LIVE_RAG_AUTO_SCROLL_SPEED_LEVEL.toFloat()..
                                                        ApiSettings.MAX_LIVE_RAG_AUTO_SCROLL_SPEED_LEVEL.toFloat(),
                                                    steps = ApiSettings.MAX_LIVE_RAG_AUTO_SCROLL_SPEED_LEVEL -
                                                        ApiSettings.MIN_LIVE_RAG_AUTO_SCROLL_SPEED_LEVEL - 1,
                                                    valueText = liveRagAutoScrollSpeedLabel(settings.liveRagAutoScrollSpeedLevel),
                                                    onValueChange = { value ->
                                                        onSettingsChange(
                                                            settings.copy(
                                                                liveRagAutoScrollSpeedLevel = ApiSettings.clampLiveRagAutoScrollSpeedLevel(
                                                                    value.roundToInt()
                                                                )
                                                            )
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                RealtimeConversationSettingKey.LIVE_THINKING_LEVEL -> {
                                    SettingsRow(
                                        title = stringResource(R.string.live_thinking_level),
                                        subtitle = liveThinkingLabel,
                                        onClick = { showLiveThinkingDialog = true }
                                    )
                                }

                                RealtimeConversationSettingKey.LIVE_LONG_SESSION -> {
                                    SettingsRowWithSwitch(
                                        title = stringResource(R.string.live_long_session),
                                        subtitle = stringResource(R.string.live_long_session_description),
                                        checked = settings.liveLongSessionEnabled,
                                        onCheckedChange = { enabled ->
                                            onSettingsChange(settings.copy(liveLongSessionEnabled = enabled))
                                        }
                                    )
                                }

                                RealtimeConversationSettingKey.LIVE_MINIMAL_UI -> {
                                    SettingsRowWithSwitch(
                                        title = stringResource(R.string.live_minimal_ui),
                                        subtitle = stringResource(R.string.live_minimal_ui_description),
                                        checked = settings.liveMinimalUiEnabled,
                                        onCheckedChange = { enabled ->
                                            onSettingsChange(settings.copy(liveMinimalUiEnabled = enabled))
                                        }
                                    )
                                }

                                RealtimeConversationSettingKey.EXPERIMENTAL_LIVE_MIC_TUNING -> {
                                    SettingsRowWithSwitch(
                                        title = stringResource(R.string.experimental_live_mic_tuning),
                                        subtitle = stringResource(R.string.experimental_live_mic_tuning_description),
                                        checked = settings.experimentalLiveMicTuningEnabled,
                                        onCheckedChange = { enabled ->
                                            onSettingsChange(
                                                settings.copy(experimentalLiveMicTuningEnabled = enabled)
                                            )
                                        }
                                    )
                                }

                                RealtimeConversationSettingKey.EXPERIMENTAL_LIVE_MIC_PROFILE -> {
                                    SettingsRow(
                                        title = stringResource(R.string.experimental_live_mic_profile),
                                        subtitle = experimentalLiveMicProfileLabel,
                                        onClick = { showExperimentalLiveMicProfileDialog = true }
                                    )
                                }

                                RealtimeConversationSettingKey.LIVE_THOUGHT_SUMMARIES -> {
                                    SettingsRowWithSwitch(
                                        title = stringResource(R.string.live_thought_summaries),
                                        subtitle = stringResource(R.string.live_thought_summaries_description),
                                        checked = settings.liveThoughtSummariesEnabled,
                                        onCheckedChange = { enabled ->
                                            onSettingsChange(settings.copy(liveThoughtSummariesEnabled = enabled))
                                        }
                                    )
                                }

                                RealtimeConversationSettingKey.LIVE_CAMERA_MODE -> {
                                    SettingsRow(
                                        title = stringResource(R.string.live_camera_mode),
                                        subtitle = liveCameraModeLabel,
                                        onClick = { showLiveCameraModeDialog = true }
                                    )

                                    if (settings.liveCameraMode == LiveCameraMode.REALTIME) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = stringResource(R.string.live_camera_realtime_warning),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                RealtimeConversationSettingKey.LIVE_CAMERA_INTERVAL -> {
                                    SettingsRow(
                                        title = stringResource(R.string.live_camera_interval),
                                        subtitle = "${settings.liveCameraIntervalSec}s",
                                        onClick = { showLiveCameraIntervalDialog = true }
                                    )
                                }
                            }

                            if (index < realtimeSettingItems.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.docs_assistant)) {
                    SettingsRow(
                        title = stringResource(R.string.docs_assistant),
                        subtitle = if (settings.answerMode == AnswerMode.DOCS) {
                            stringResource(R.string.docs_mode_enabled)
                        } else {
                            stringResource(R.string.general_mode_enabled)
                        },
                        onClick = onNavigateToDocsSettings
                    )
                }
            }
            
            // Custom Provider Settings (only shown for CUSTOM provider)
            if (!settings.liveModeEnabled && settings.aiProvider == AiProvider.CUSTOM) {
                item {
                    CustomProviderSection(
                        baseUrl = settings.customBaseUrl,
                        onBaseUrlChange = { onSettingsChange(settings.copy(customBaseUrl = it)) },
                        modelName = settings.customModelName,
                        onModelNameChange = { onSettingsChange(settings.copy(customModelName = it)) },
                        apiKey = settings.customApiKey,
                        onApiKeyChange = { onSettingsChange(settings.copy(customApiKey = it)) },
                        onTestConnection = { onTestConnection(settings) }
                    )
                }
            }
            
            // API Key settings section (for non-custom providers)
            if (settings.liveModeEnabled || settings.aiProvider != AiProvider.CUSTOM) {
                item {
                                SettingsSection(title = stringResource(R.string.api_keys)) {
                        when (if (settings.liveModeEnabled) AiProvider.GEMINI_LIVE else settings.aiProvider) {
                            AiProvider.GEMINI -> {
                                ApiKeyField(
                                    label = stringResource(R.string.gemini_api_key),
                                    value = settings.geminiApiKey,
                                    onValueChange = { onSettingsChange(settings.copy(geminiApiKey = it)) },
                                    isActive = true,
                                    allowMultiline = true,
                                    helperText = stringResource(
                                        R.string.gemini_api_key_pool_hint,
                                        settings.getGeminiApiKeys().size
                                    )
                                )
                            }
                            AiProvider.OPENAI -> {
                                ApiKeyField(
                                    label = stringResource(R.string.openai_api_key),
                                    value = settings.openaiApiKey,
                                    onValueChange = { onSettingsChange(settings.copy(openaiApiKey = it)) },
                                    isActive = true
                                )
                            }
                            AiProvider.ANTHROPIC -> {
                                ApiKeyField(
                                    label = stringResource(R.string.anthropic_api_key),
                                    value = settings.anthropicApiKey,
                                    onValueChange = { onSettingsChange(settings.copy(anthropicApiKey = it)) },
                                    isActive = true
                                )
                            }
                            AiProvider.DEEPSEEK -> {
                                ApiKeyField(
                                    label = stringResource(R.string.deepseek_api_key),
                                    value = settings.deepseekApiKey,
                                    onValueChange = { onSettingsChange(settings.copy(deepseekApiKey = it)) },
                                    isActive = true
                                )
                            }
                            AiProvider.GROQ -> {
                                ApiKeyField(
                                    label = stringResource(R.string.groq_api_key),
                                    value = settings.groqApiKey,
                                    onValueChange = { onSettingsChange(settings.copy(groqApiKey = it)) },
                                    isActive = true
                                )
                            }
                            AiProvider.XAI -> {
                                ApiKeyField(
                                    label = stringResource(R.string.xai_api_key),
                                    value = settings.xaiApiKey,
                                    onValueChange = { onSettingsChange(settings.copy(xaiApiKey = it)) },
                                    isActive = true
                                )
                            }
                            AiProvider.ALIBABA -> {
                                ApiKeyField(
                                    label = stringResource(R.string.alibaba_api_key),
                                    value = settings.alibabaApiKey,
                                    onValueChange = { onSettingsChange(settings.copy(alibabaApiKey = it)) },
                                    isActive = true
                                )
                            }
                            AiProvider.ZHIPU -> {
                                ApiKeyField(
                                    label = stringResource(R.string.zhipu_api_key),
                                    value = settings.zhipuApiKey,
                                    onValueChange = { onSettingsChange(settings.copy(zhipuApiKey = it)) },
                                    isActive = true
                                )
                            }
                            AiProvider.BAIDU -> {
                                // Baidu requires both API Key and Secret Key
                                ApiKeyField(
                                    label = stringResource(R.string.baidu_api_key),
                                    value = settings.baiduApiKey,
                                    onValueChange = { onSettingsChange(settings.copy(baiduApiKey = it)) },
                                    isActive = true
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                ApiKeyField(
                                    label = stringResource(R.string.baidu_secret_key),
                                    value = settings.baiduSecretKey,
                                    onValueChange = { onSettingsChange(settings.copy(baiduSecretKey = it)) },
                                    isActive = true
                                )
                            }
                            AiProvider.PERPLEXITY -> {
                                ApiKeyField(
                                    label = stringResource(R.string.perplexity_api_key),
                                    value = settings.perplexityApiKey,
                                    onValueChange = { onSettingsChange(settings.copy(perplexityApiKey = it)) },
                                    isActive = true
                                )
                            }
                            AiProvider.MOONSHOT -> {
                                ApiKeyField(
                                    label = stringResource(R.string.moonshot_api_key),
                                    value = settings.moonshotApiKey,
                                    onValueChange = { onSettingsChange(settings.copy(moonshotApiKey = it)) },
                                    isActive = true
                                )
                            }
                            AiProvider.GEMINI_LIVE -> {
                                // Gemini Live shares the Gemini API key
                                ApiKeyField(
                                    label = stringResource(R.string.gemini_api_key),
                                    value = settings.geminiApiKey,
                                    onValueChange = { onSettingsChange(settings.copy(geminiApiKey = it)) },
                                    isActive = true,
                                    allowMultiline = true,
                                    helperText = stringResource(
                                        R.string.gemini_api_key_pool_hint,
                                        settings.getGeminiApiKeys().size
                                    )
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }
            
            // Speech recognition settings section
            item {
                SettingsSection(title = stringResource(R.string.speech_recognition)) {
                    SettingsRow(
                        title = stringResource(R.string.speech_recognition_service),
                        subtitle = stringResource(settings.sttProvider.displayNameResId),
                        onClick = { showSpeechServiceDialog = true }
                    )
                    
                    // Dynamic credential fields based on selected STT provider
                    SttCredentialsFields(
                        provider = settings.sttProvider,
                        settings = settings,
                        onSettingsChange = onSettingsChange
                    )
                }
            }
            
            // Advanced settings section
            item {
                SettingsSection(title = stringResource(R.string.advanced_settings)) {
                    SettingsRow(
                        title = stringResource(R.string.llm_parameters),
                        subtitle = stringResource(R.string.llm_parameters_subtitle),
                        onClick = onNavigateToLlmParameters,
                        icon = Icons.Default.Tune
                    )
                    
                    HorizontalDivider()
                    
                    SettingsRow(
                        title = stringResource(R.string.tts_settings_title),
                        subtitle = stringResource(R.string.tts_settings_subtitle),
                        onClick = onNavigateToTtsSettings,
                        icon = Icons.AutoMirrored.Filled.VolumeUp
                    )
                    
                    HorizontalDivider()
                    
                    SettingsRow(
                        title = stringResource(R.string.system_prompt),
                        subtitle = settings.systemPrompt.take(50) + if (settings.systemPrompt.length > 50) "..." else "",
                        onClick = { showSystemPromptDialog = true }
                    )

                    SettingsRowWithSwitch(
                        title = stringResource(R.string.always_start_new_ai_session),
                        subtitle = stringResource(R.string.always_start_new_ai_session_description),
                        checked = settings.alwaysStartNewAiSession,
                        onCheckedChange = { onSettingsChange(settings.copy(alwaysStartNewAiSession = it)) }
                    )
                }
            }
            
            // Recording settings section
            item {
                SettingsSection(title = stringResource(R.string.recording_settings)) {
                    SettingsRowWithSwitch(
                        title = stringResource(R.string.auto_analyze_recordings),
                        subtitle = stringResource(R.string.auto_analyze_recordings_description),
                        checked = settings.autoAnalyzeRecordings,
                        onCheckedChange = { onSettingsChange(settings.copy(autoAnalyzeRecordings = it)) }
                    )
                    SettingsRowWithSwitch(
                        title = stringResource(R.string.push_chat_to_glasses),
                        subtitle = stringResource(R.string.push_chat_to_glasses_description),
                        checked = settings.pushChatToGlasses,
                        onCheckedChange = { onSettingsChange(settings.copy(pushChatToGlasses = it)) }
                    )
                    SettingsRowWithSwitch(
                        title = stringResource(R.string.push_recording_to_glasses),
                        subtitle = stringResource(R.string.push_recording_to_glasses_description),
                        checked = settings.pushRecordingToGlasses,
                        onCheckedChange = { onSettingsChange(settings.copy(pushRecordingToGlasses = it)) }
                    )
                    SettingsRowWithSwitch(
                        title = stringResource(R.string.glasses_sleep_mode),
                        subtitle = stringResource(R.string.glasses_sleep_mode_description),
                        checked = settings.glassesSleepModeEnabled,
                        onCheckedChange = { onSettingsChange(settings.copy(glassesSleepModeEnabled = it)) }
                    )
                    HorizontalDivider()
                    SettingsSliderRow(
                        title = stringResource(R.string.glasses_response_font_scale),
                        subtitle = stringResource(R.string.glasses_response_font_scale_description),
                        value = settings.responseFontScalePercent.toFloat(),
                        valueRange = ApiSettings.MIN_RESPONSE_FONT_SCALE_PERCENT.toFloat()..
                            ApiSettings.MAX_RESPONSE_FONT_SCALE_PERCENT.toFloat(),
                        steps = ((ApiSettings.MAX_RESPONSE_FONT_SCALE_PERCENT -
                            ApiSettings.MIN_RESPONSE_FONT_SCALE_PERCENT) /
                            ApiSettings.RESPONSE_FONT_SCALE_STEP_PERCENT) - 1,
                        valueText = stringResource(
                            R.string.glasses_response_font_scale_value,
                            settings.responseFontScalePercent
                        ),
                        onValueChange = { rawValue ->
                            val snappedValue = ApiSettings.snapResponseFontScalePercent(rawValue.roundToInt())
                            if (snappedValue != settings.responseFontScalePercent) {
                                onSettingsChange(
                                    settings.copy(
                                        responseFontScalePercent = snappedValue
                                    )
                                )
                            }
                        }
                    )
                }
            }
            
            // Status display
            item {
                val isValid = settings.isValid()
                val statusText = when {
                    isValid -> stringResource(R.string.settings_complete)
                    settings.liveModeEnabled ->
                        stringResource(R.string.please_enter_api_key, stringResource(R.string.provider_gemini_live))
                    settings.aiProvider == AiProvider.CUSTOM && settings.customBaseUrl.isBlank() -> 
                        stringResource(R.string.invalid_url)
                    settings.aiProvider == AiProvider.CUSTOM -> 
                        stringResource(R.string.settings_complete)
                    else -> stringResource(R.string.please_enter_api_key, stringResource(settings.aiProvider.displayNameResId))
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isValid) 
                            MaterialTheme.colorScheme.primaryContainer
                        else 
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isValid) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = statusText)
                    }
                }
            }
            
            // Developer Tools section
            item {
                SettingsSection(title = stringResource(R.string.developer_tools)) {
                    SettingsRow(
                        title = stringResource(R.string.log_viewer),
                        subtitle = stringResource(R.string.log_viewer_description),
                        onClick = onNavigateToLogViewer,
                        icon = Icons.Default.BugReport
                    )
                }
            }

            // Support section
            item {
                SettingsSection(title = "Support") {
                    KofiButton(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    
    // Dialogs
    if (showProviderDialog) {
        ProviderSelectionDialog(
            currentProvider = settings.aiProvider,
            onSelect = { provider ->
                onSettingsChange(settings.copy(
                    aiProvider = provider,
                    aiModelId = AvailableModels.getModelsForProvider(provider).firstOrNull()?.id 
                        ?: settings.aiModelId
                ))
                showProviderDialog = false
            },
            onDismiss = { showProviderDialog = false }
        )
    }
    
    if (showModelDialog) {
        ModelSelectionDialog(
            currentModelId = settings.aiModelId,
            models = AvailableModels.getModelsForProvider(settings.aiProvider),
            onSelect = { modelId ->
                onSettingsChange(settings.copy(aiModelId = modelId))
                showModelDialog = false
            },
            onDismiss = { showModelDialog = false }
        )
    }
    
    if (showSpeechServiceDialog) {
        SttProviderSelectionDialog(
            currentProvider = settings.sttProvider,
            onSelect = { provider ->
                onSettingsChange(settings.copy(sttProvider = provider))
                showSpeechServiceDialog = false
            },
            onDismiss = { showSpeechServiceDialog = false }
        )
    }
    
    if (showSystemPromptDialog) {
        SystemPromptDialog(
            currentPrompt = settings.systemPrompt,
            onSave = { prompt ->
                onSettingsChange(settings.copy(systemPrompt = prompt))
                showSystemPromptDialog = false
            },
            onDismiss = { showSystemPromptDialog = false }
        )
    }
    
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = currentLanguage,
            onSelect = { language ->
                val settingsRepository = SettingsRepository.getInstance(context)
                
                // Check if current system prompt is default (needs update when language changes)
                val isUsingDefaultPrompt = settingsRepository.isUsingDefaultSystemPrompt()
                
                // Get new language's default prompt BEFORE changing the language
                val newDefaultPrompt = settingsRepository.getDefaultSystemPromptForLanguage(language)
                
                // Get the corresponding speech language code
                val newSpeechLanguage = when (language) {
                    AppLanguage.SIMPLIFIED_CHINESE -> "zh-CN"
                    AppLanguage.TRADITIONAL_CHINESE -> "zh-TW"
                    AppLanguage.JAPANESE -> "ja-JP"
                    AppLanguage.KOREAN -> "ko-KR"
                    AppLanguage.FRENCH -> "fr-FR"
                    AppLanguage.SPANISH -> "es-ES"
                    AppLanguage.ITALIAN -> "it-IT"
                    AppLanguage.RUSSIAN -> "ru-RU"
                    AppLanguage.THAI -> "th-TH"
                    AppLanguage.UKRAINIAN -> "uk-UA"
                    AppLanguage.VIETNAMESE -> "vi-VN"
                    AppLanguage.ARABIC -> "ar-SA"
                    else -> "en-US"
                }
                
                // Change the language
                LanguageManager.setLanguage(context, language)
                currentLanguage = language
                
                // Update settings: system prompt and speech language
                var updatedSettings = settings.copy(
                    speechLanguage = newSpeechLanguage,
                    responseLanguage = newSpeechLanguage
                )
                if (isUsingDefaultPrompt) {
                    updatedSettings = updatedSettings.copy(systemPrompt = newDefaultPrompt)
                }
                onSettingsChange(updatedSettings)
                
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showLiveInputDialog) {
        SimpleSelectionDialog(
            title = stringResource(R.string.live_input_source),
            options = listOf(
                stringResource(R.string.live_input_source_auto_label) to {
                    onSettingsChange(settings.copy(liveInputSource = LiveInputSource.AUTO))
                },
                stringResource(R.string.live_input_source_glasses_label) to {
                    onSettingsChange(settings.copy(liveInputSource = LiveInputSource.GLASSES))
                },
                stringResource(R.string.live_input_source_phone_label) to {
                    onSettingsChange(settings.copy(liveInputSource = LiveInputSource.PHONE))
                },
            ),
            onDismiss = { showLiveInputDialog = false }
        )
    }

    if (showLiveThinkingDialog) {
        SimpleSelectionDialog(
            title = stringResource(R.string.live_thinking_level),
            options = listOf(
                stringResource(R.string.live_thinking_default) to {
                    onSettingsChange(settings.copy(liveThinkingLevel = LiveThinkingLevel.DEFAULT))
                },
                stringResource(R.string.live_thinking_minimal) to {
                    onSettingsChange(settings.copy(liveThinkingLevel = LiveThinkingLevel.MINIMAL))
                },
                stringResource(R.string.live_thinking_low) to {
                    onSettingsChange(settings.copy(liveThinkingLevel = LiveThinkingLevel.LOW))
                },
                stringResource(R.string.live_thinking_medium) to {
                    onSettingsChange(settings.copy(liveThinkingLevel = LiveThinkingLevel.MEDIUM))
                },
                stringResource(R.string.live_thinking_high) to {
                    onSettingsChange(settings.copy(liveThinkingLevel = LiveThinkingLevel.HIGH))
                },
            ),
            onDismiss = { showLiveThinkingDialog = false }
        )
    }

    if (showExperimentalLiveMicProfileDialog) {
        SimpleSelectionDialog(
            title = stringResource(R.string.experimental_live_mic_profile),
            options = listOf(
                stringResource(R.string.experimental_live_mic_profile_near) to {
                    onSettingsChange(settings.copy(experimentalLiveMicProfile = 0))
                },
                stringResource(R.string.experimental_live_mic_profile_far) to {
                    onSettingsChange(settings.copy(experimentalLiveMicProfile = 1))
                },
                stringResource(R.string.experimental_live_mic_profile_panorama) to {
                    onSettingsChange(settings.copy(experimentalLiveMicProfile = 2))
                },
            ),
            onDismiss = { showExperimentalLiveMicProfileDialog = false }
        )
    }

    if (showLiveVoiceDialog) {
        SimpleSelectionDialog(
            title = stringResource(R.string.live_voice),
            options = GeminiLiveVoice.entries.map { voice ->
                voice.displayLabel to {
                    onSettingsChange(settings.copy(liveVoiceName = voice.voiceName))
                }
            },
            onDismiss = { showLiveVoiceDialog = false }
        )
    }

    if (showLiveOutputDialog) {
        SimpleSelectionDialog(
            title = stringResource(R.string.live_output_target),
            options = listOf(
                stringResource(R.string.live_output_target_auto_label) to {
                    onSettingsChange(settings.copy(liveOutputTarget = LiveOutputTarget.AUTO))
                },
                stringResource(R.string.live_output_target_glasses_label) to {
                    onSettingsChange(settings.copy(liveOutputTarget = LiveOutputTarget.GLASSES))
                },
                stringResource(R.string.live_output_target_phone_label) to {
                    onSettingsChange(settings.copy(liveOutputTarget = LiveOutputTarget.PHONE))
                },
                stringResource(R.string.live_output_target_both_label) to {
                    onSettingsChange(settings.copy(liveOutputTarget = LiveOutputTarget.BOTH))
                },
            ),
            onDismiss = { showLiveOutputDialog = false }
        )
    }

    if (showLiveCameraModeDialog) {
        SimpleSelectionDialog(
            title = stringResource(R.string.live_camera_mode),
            options = listOf(
                "Off" to { onSettingsChange(settings.copy(liveCameraMode = LiveCameraMode.OFF)) },
                "Manual capture" to { onSettingsChange(settings.copy(liveCameraMode = LiveCameraMode.MANUAL)) },
                "Interval" to { onSettingsChange(settings.copy(liveCameraMode = LiveCameraMode.INTERVAL)) },
                "Realtime" to { onSettingsChange(settings.copy(liveCameraMode = LiveCameraMode.REALTIME)) },
            ),
            onDismiss = { showLiveCameraModeDialog = false }
        )
    }

    if (showLiveCameraIntervalDialog) {
        SimpleSelectionDialog(
            title = stringResource(R.string.live_camera_interval),
            options = listOf(1, 2, 5, 10, 30, 60).map { seconds ->
                "${seconds}s" to {
                    onSettingsChange(settings.copy(liveCameraIntervalSec = seconds))
                }
            },
            onDismiss = { showLiveCameraIntervalDialog = false }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SimpleSelectionDialog(
    title: String,
    options: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                options.forEach { (label, action) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                action()
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun KofiButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/liangtinglin"))
            context.startActivity(intent)
        },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFF5E5B)
        )
    ) {
        Icon(
            imageVector = Icons.Default.LocalCafe,
            contentDescription = null,
            tint = Color.White
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Support me on Ko-fi",
            color = Color.White
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
) {
    val rowOnClick: () -> Unit = {
        if (enabled) {
            onClick()
        }
    }

    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = rowOnClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 0.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column {
                    Text(
                        text = title, 
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun LiveModeLockCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Column {
                    Text(
                        text = stringResource(R.string.live_mode_active_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.live_mode_active_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiveModeInfoRow(
                        label = stringResource(R.string.live_mode_provider_label),
                        value = stringResource(R.string.provider_gemini_live)
                    )
                    LiveModeInfoRow(
                        label = stringResource(R.string.live_mode_model_label),
                        value = stringResource(R.string.live_native_audio_model)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveModeInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun SettingsRowWithSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun LiveRagDisplayModeSelector(
    selectedMode: LiveRagDisplayMode,
    onModeSelected: (LiveRagDisplayMode) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.live_rag_display_mode),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LiveRagDisplayMode.entries.forEach { mode ->
            val title = when (mode) {
                LiveRagDisplayMode.RAG_RESULT_ONLY ->
                    stringResource(R.string.live_rag_display_mode_rag_only)
                LiveRagDisplayMode.SPLIT_LIVE_AND_RAG ->
                    stringResource(R.string.live_rag_display_mode_split)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selectedMode == mode,
                        onClick = { onModeSelected(mode) }
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun LiveRagSplitScrollModeSelector(
    selectedMode: LiveRagSplitScrollMode,
    onModeSelected: (LiveRagSplitScrollMode) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.live_rag_split_scroll_mode),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LiveRagSplitScrollMode.entries.forEach { mode ->
            val title = when (mode) {
                LiveRagSplitScrollMode.AUTO ->
                    stringResource(R.string.live_rag_split_scroll_mode_auto)
                LiveRagSplitScrollMode.MANUAL ->
                    stringResource(R.string.live_rag_split_scroll_mode_manual)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selectedMode == mode,
                        onClick = { onModeSelected(mode) }
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun liveRagAutoScrollSpeedLabel(level: Int): String {
    return when (ApiSettings.clampLiveRagAutoScrollSpeedLevel(level)) {
        0 -> stringResource(R.string.live_rag_auto_scroll_speed_very_slow)
        1 -> stringResource(R.string.live_rag_auto_scroll_speed_slow)
        2 -> stringResource(R.string.live_rag_auto_scroll_speed_normal)
        3 -> stringResource(R.string.live_rag_auto_scroll_speed_fast)
        else -> stringResource(R.string.live_rag_auto_scroll_speed_very_fast)
    }
}

@Composable
fun SettingsSliderRow(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isActive: Boolean,
    allowMultiline: Boolean = false,
    helperText: String? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val inUseText = stringResource(R.string.in_use)
    val hideText = stringResource(R.string.hide)
    val showText = stringResource(R.string.show)
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = !allowMultiline,
        minLines = if (allowMultiline) 4 else 1,
        maxLines = if (allowMultiline) 6 else 1,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowMultiline) KeyboardType.Text else KeyboardType.Password
        ),
        trailingIcon = {
            Row {
                if (isActive && value.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = inUseText,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) hideText else showText
                    )
                }
            }
        },
        supportingText = helperText?.let { text ->
            { Text(text) }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
fun ProviderSelectionDialog(
    currentProvider: AiProvider,
    onSelect: (AiProvider) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_ai_provider)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                AiProvider.entries
                    .filter { it != AiProvider.GEMINI_LIVE }
                    .forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(provider) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = provider == currentProvider,
                            onClick = { onSelect(provider) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(provider.displayNameResId))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ModelSelectionDialog(
    currentModelId: String,
    models: List<ModelOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_model)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                models.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(model.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = model.id == currentModelId,
                            onClick = { onSelect(model.id) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.displayName)
                            Text(
                                text = model.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Show capability badges
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                if (model.isPreview) {
                                    PreviewBadge()
                                }
                                if (model.supportsAudio) {
                                    CapabilityBadge(
                                        icon = Icons.Default.Mic,
                                        text = stringResource(R.string.supports_audio),
                                        isSupported = true
                                    )
                                } else {
                                    CapabilityBadge(
                                        icon = Icons.Default.MicOff,
                                        text = stringResource(R.string.no_speech_support),
                                        isSupported = false
                                    )
                                }
                                if (model.supportsVision) {
                                    CapabilityBadge(
                                        icon = Icons.Default.Image,
                                        text = stringResource(R.string.supports_vision),
                                        isSupported = true
                                    )
                                } else {
                                    CapabilityBadge(
                                        icon = Icons.Default.HideImage,
                                        text = stringResource(R.string.no_vision_support),
                                        isSupported = false
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
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Badge to indicate a Preview / experimental model
 */
@Composable
private fun PreviewBadge() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            text = stringResource(R.string.preview_badge),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Badge to indicate model capability support
 */
@Composable
private fun CapabilityBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isSupported: Boolean
) {
    val color = if (isSupported) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(12.dp),
            tint = color
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun SttProviderSelectionDialog(
    currentProvider: SttProvider,
    onSelect: (SttProvider) -> Unit,
    onDismiss: () -> Unit
) {
    val implementedProviders = remember { SttServiceFactory.getImplementedProviders() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_speech_service)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                implementedProviders.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(provider) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = provider == currentProvider,
                            onClick = { onSelect(provider) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(provider.displayNameResId))
                            Text(
                                text = provider.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Dynamic credential input fields based on the selected STT provider
 */
@Composable
fun SttCredentialsFields(
    provider: SttProvider,
    settings: ApiSettings,
    onSettingsChange: (ApiSettings) -> Unit
) {
    // Providers that use main AI API keys (no additional credentials needed)
    val noCredentialsNeeded = listOf(
        SttProvider.GEMINI,
        SttProvider.OPENAI_WHISPER,
        SttProvider.GROQ_WHISPER
    )
    
    if (provider in noCredentialsNeeded) {
        // These providers use the main AI API key
        Text(
            text = stringResource(R.string.stt_uses_main_api_key),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }
    
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.stt_credentials_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        when (provider) {
            SttProvider.DEEPGRAM -> {
                ApiKeyInputField(
                    label = stringResource(R.string.stt_api_key),
                    value = settings.deepgramApiKey,
                    onValueChange = { onSettingsChange(settings.copy(deepgramApiKey = it)) }
                )
            }
            
            SttProvider.ASSEMBLYAI -> {
                ApiKeyInputField(
                    label = stringResource(R.string.stt_api_key),
                    value = settings.assemblyaiApiKey,
                    onValueChange = { onSettingsChange(settings.copy(assemblyaiApiKey = it)) }
                )
            }
            
            SttProvider.GOOGLE_CLOUD_STT -> {
                ApiKeyInputField(
                    label = stringResource(R.string.stt_project_id),
                    value = settings.gcpProjectId,
                    onValueChange = { onSettingsChange(settings.copy(gcpProjectId = it)) },
                    isPassword = false
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_api_key),
                    value = settings.gcpApiKey,
                    onValueChange = { onSettingsChange(settings.copy(gcpApiKey = it)) }
                )
            }
            
            SttProvider.AZURE_SPEECH -> {
                ApiKeyInputField(
                    label = stringResource(R.string.stt_subscription_key),
                    value = settings.azureSpeechKey,
                    onValueChange = { onSettingsChange(settings.copy(azureSpeechKey = it)) }
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_region),
                    value = settings.azureSpeechRegion,
                    onValueChange = { onSettingsChange(settings.copy(azureSpeechRegion = it)) },
                    isPassword = false,
                    placeholder = "eastus, westus2, etc."
                )
            }
            
            SttProvider.AWS_TRANSCRIBE -> {
                ApiKeyInputField(
                    label = stringResource(R.string.stt_access_key),
                    value = settings.awsAccessKeyId,
                    onValueChange = { onSettingsChange(settings.copy(awsAccessKeyId = it)) }
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_secret_key),
                    value = settings.awsSecretAccessKey,
                    onValueChange = { onSettingsChange(settings.copy(awsSecretAccessKey = it)) }
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_region),
                    value = settings.awsRegion,
                    onValueChange = { onSettingsChange(settings.copy(awsRegion = it)) },
                    isPassword = false,
                    placeholder = "us-east-1"
                )
            }
            
            SttProvider.IBM_WATSON -> {
                ApiKeyInputField(
                    label = stringResource(R.string.stt_api_key),
                    value = settings.ibmApiKey,
                    onValueChange = { onSettingsChange(settings.copy(ibmApiKey = it)) }
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_service_url),
                    value = settings.ibmServiceUrl,
                    onValueChange = { onSettingsChange(settings.copy(ibmServiceUrl = it)) },
                    isPassword = false,
                    placeholder = "https://api.us-south.speech-to-text.watson.cloud.ibm.com"
                )
            }
            
            SttProvider.IFLYTEK -> {
                ApiKeyInputField(
                    label = stringResource(R.string.stt_app_id),
                    value = settings.iflytekAppId,
                    onValueChange = { onSettingsChange(settings.copy(iflytekAppId = it)) },
                    isPassword = false
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_api_key),
                    value = settings.iflytekApiKey,
                    onValueChange = { onSettingsChange(settings.copy(iflytekApiKey = it)) }
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_api_secret),
                    value = settings.iflytekApiSecret,
                    onValueChange = { onSettingsChange(settings.copy(iflytekApiSecret = it)) }
                )
            }
            
            SttProvider.HUAWEI_SIS -> {
                ApiKeyInputField(
                    label = stringResource(R.string.stt_access_key),
                    value = settings.huaweiAk,
                    onValueChange = { onSettingsChange(settings.copy(huaweiAk = it)) }
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_secret_key),
                    value = settings.huaweiSk,
                    onValueChange = { onSettingsChange(settings.copy(huaweiSk = it)) }
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_project_id),
                    value = settings.huaweiProjectId,
                    onValueChange = { onSettingsChange(settings.copy(huaweiProjectId = it)) },
                    isPassword = false
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_region),
                    value = settings.huaweiRegion,
                    onValueChange = { onSettingsChange(settings.copy(huaweiRegion = it)) },
                    isPassword = false,
                    placeholder = "cn-north-4"
                )
            }
            
            SttProvider.VOLCENGINE -> {
                ApiKeyInputField(
                    label = stringResource(R.string.stt_access_key),
                    value = settings.volcengineAk,
                    onValueChange = { onSettingsChange(settings.copy(volcengineAk = it)) }
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_secret_key),
                    value = settings.volcangineSk,
                    onValueChange = { onSettingsChange(settings.copy(volcangineSk = it)) }
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_app_id),
                    value = settings.volcengineAppId,
                    onValueChange = { onSettingsChange(settings.copy(volcengineAppId = it)) },
                    isPassword = false
                )
            }
            
            SttProvider.ALIBABA_ASR -> {
                ApiKeyInputField(
                    label = stringResource(R.string.stt_access_key),
                    value = settings.aliyunAccessKeyId,
                    onValueChange = { onSettingsChange(settings.copy(aliyunAccessKeyId = it)) }
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_secret_key),
                    value = settings.aliyunAccessKeySecret,
                    onValueChange = { onSettingsChange(settings.copy(aliyunAccessKeySecret = it)) }
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_app_id),
                    value = settings.aliyunAppKey,
                    onValueChange = { onSettingsChange(settings.copy(aliyunAppKey = it)) },
                    isPassword = false,
                    placeholder = "NLS AppKey"
                )
            }
            
            SttProvider.TENCENT_ASR -> {
                ApiKeyInputField(
                    label = "Secret ID",
                    value = settings.tencentSecretId,
                    onValueChange = { onSettingsChange(settings.copy(tencentSecretId = it)) }
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_secret_key),
                    value = settings.tencentSecretKey,
                    onValueChange = { onSettingsChange(settings.copy(tencentSecretKey = it)) }
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_app_id),
                    value = settings.tencentAppId,
                    onValueChange = { onSettingsChange(settings.copy(tencentAppId = it)) },
                    isPassword = false
                )
            }
            
            SttProvider.BAIDU_ASR -> {
                ApiKeyInputField(
                    label = stringResource(R.string.stt_api_key),
                    value = settings.baiduAsrApiKey,
                    onValueChange = { onSettingsChange(settings.copy(baiduAsrApiKey = it)) }
                )
                ApiKeyInputField(
                    label = stringResource(R.string.stt_secret_key),
                    value = settings.baiduAsrSecretKey,
                    onValueChange = { onSettingsChange(settings.copy(baiduAsrSecretKey = it)) }
                )
            }
            
            SttProvider.REV_AI -> {
                ApiKeyInputField(
                    label = "Access Token",
                    value = settings.revaiAccessToken,
                    onValueChange = { onSettingsChange(settings.copy(revaiAccessToken = it)) }
                )
            }
            
            SttProvider.SPEECHMATICS -> {
                ApiKeyInputField(
                    label = stringResource(R.string.stt_api_key),
                    value = settings.speechmaticsApiKey,
                    onValueChange = { onSettingsChange(settings.copy(speechmaticsApiKey = it)) }
                )
            }
            
            SttProvider.OTTER_AI -> {
                ApiKeyInputField(
                    label = stringResource(R.string.stt_api_key),
                    value = settings.otteraiApiKey,
                    onValueChange = { onSettingsChange(settings.copy(otteraiApiKey = it)) }
                )
            }
            
            else -> {
                // Fallback for any unhandled providers
                Text(
                    text = "Configure credentials for ${provider.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ApiKeyInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = true,
    placeholder: String = ""
) {
    var passwordVisible by remember { mutableStateOf(false) }
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) {{ Text(placeholder) }} else null,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (isPassword && !passwordVisible) 
            PasswordVisualTransformation() 
        else 
            VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text),
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide" else "Show"
                    )
                }
            }
        } else null
    )
}

@Composable
fun SystemPromptDialog(
    currentPrompt: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var prompt by remember { mutableStateOf(currentPrompt) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.system_prompt)) },
        text = {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                placeholder = { Text(stringResource(R.string.enter_system_prompt)) }
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(prompt) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun LanguageSelectionDialog(
    currentLanguage: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_language)) },
        text = {
            LazyColumn {
                items(AppLanguage.entries.toList()) { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(language) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language == currentLanguage,
                            onClick = { onSelect(language) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = language.nativeName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = language.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Custom Provider Settings Section
 */
@Composable
fun CustomProviderSection(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    modelName: String,
    onModelNameChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onTestConnection: () -> Unit
) {
    var isValidUrl by remember(baseUrl) { 
        mutableStateOf(baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))
    }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf<Boolean?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.custom_provider_settings),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Base URL field
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { 
                    onBaseUrlChange(it)
                    isValidUrl = it.startsWith("http://") || it.startsWith("https://")
                },
                label = { Text(stringResource(R.string.base_url)) },
                placeholder = { Text(stringResource(R.string.base_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = baseUrl.isNotBlank() && !isValidUrl,
                supportingText = {
                    if (baseUrl.isNotBlank() && !isValidUrl) {
                        Text(
                            text = stringResource(R.string.invalid_url),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Model Name field
            OutlinedTextField(
                value = modelName,
                onValueChange = onModelNameChange,
                label = { Text(stringResource(R.string.model_name)) },
                placeholder = { Text(stringResource(R.string.model_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // API Key field (optional for local models)
            var passwordVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text(stringResource(R.string.custom_api_key)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) stringResource(R.string.hide) else stringResource(R.string.show)
                        )
                    }
                },
                supportingText = {
                    Text(
                        text = stringResource(R.string.api_key_optional),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Test Connection button
            Button(
                onClick = {
                    isTesting = true
                    testResult = null
                    testSuccess = null
                    coroutineScope.launch {
                        try {
                            val service = com.example.rokidphone.service.ai.OpenAiCompatibleService(
                                apiKey = apiKey,
                                baseUrl = baseUrl,
                                modelId = modelName.ifBlank { "llama4" },
                                providerType = AiProvider.CUSTOM
                            )
                            val result = service.testConnection()
                            testSuccess = result.isSuccess
                            testResult = result.getOrElse { it.message ?: "Unknown error" }
                        } catch (e: Exception) {
                            testSuccess = false
                            testResult = e.message ?: "Connection failed"
                        } finally {
                            isTesting = false
                        }
                    }
                },
                enabled = isValidUrl && !isTesting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.testing_connection))
                } else {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.test_connection))
                }
            }
            
            // Test result
            testResult?.let { result ->
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (testSuccess == true)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (testSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (testSuccess == true)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (testSuccess == true) 
                                stringResource(R.string.connection_success) 
                            else 
                                result,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom Model Input Dialog
 */
@Composable
fun CustomModelDialog(
    currentModelName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var modelName by remember { mutableStateOf(currentModelName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_model_name)) },
        text = {
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text(stringResource(R.string.model_name)) },
                placeholder = { Text(stringResource(R.string.model_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(modelName) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
