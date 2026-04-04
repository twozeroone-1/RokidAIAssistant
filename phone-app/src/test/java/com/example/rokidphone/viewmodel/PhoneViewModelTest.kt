package com.example.rokidphone.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.rokidcommon.protocol.Message
import com.example.rokidcommon.protocol.LiveTranscriptPayload
import com.example.rokidcommon.protocol.LiveTranscriptRole
import com.example.rokidcommon.protocol.MessageType
import com.example.rokidphone.data.LiveInputSource
import com.example.rokidphone.data.LiveOutputTarget
import com.example.rokidphone.data.LiveThinkingLevel
import com.example.rokidphone.service.BluetoothConnectionState
import com.example.rokidphone.service.ServiceBridge
import com.example.rokidphone.service.ai.LiveSessionStatusSnapshot
import com.example.rokidphone.data.db.RecordingState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var viewModelStore: ViewModelStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        viewModelStore = ViewModelStore()
        ServiceBridge.updateBluetoothState(BluetoothConnectionState.DISCONNECTED)
        ServiceBridge.updateConnectedDeviceName(null)
        ServiceBridge.updateServiceState(false)
        ServiceBridge.updateLiveSessionStatus(null)
        ServiceBridge.updateLiveUsageSummary(null)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        ServiceBridge.updateBluetoothState(BluetoothConnectionState.DISCONNECTED)
        ServiceBridge.updateConnectedDeviceName(null)
        ServiceBridge.updateServiceState(false)
        ServiceBridge.updateLiveSessionStatus(null)
        ServiceBridge.updateLiveUsageSummary(null)
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PhoneViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PhoneViewModel(
                    application = application,
                    recordingStateOverride = MutableStateFlow<RecordingState>(RecordingState.Idle)
                ) as T
            }
        }
        return ViewModelProvider(viewModelStore, factory)[PhoneViewModel::class.java]
    }

    private suspend fun TestScope.withPhoneViewModel(
        block: suspend TestScope.(PhoneViewModel) -> Unit
    ) {
        viewModelStore = ViewModelStore()
        val viewModel = createViewModel()
        try {
            advanceUntilIdle()
            block(viewModel)
        } finally {
            viewModelStore.clear()
            advanceUntilIdle()
        }
    }

    @Test
    fun `clears processing status when ai response arrives`() = runTest {
        withPhoneViewModel { viewModel ->
            ServiceBridge.emitConversation(
                Message(type = MessageType.AI_PROCESSING, payload = "안경 녹음 중...")
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.processingStatus).isEqualTo("안경 녹음 중...")

            ServiceBridge.emitConversation(
                Message(type = MessageType.AI_RESPONSE_TEXT, payload = "응답 완료")
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.processingStatus).isNull()
        }
    }

    @Test
    fun `clears processing status when ai error arrives`() = runTest {
        withPhoneViewModel { viewModel ->
            ServiceBridge.emitConversation(
                Message(type = MessageType.AI_PROCESSING, payload = "안경 녹음 중...")
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.processingStatus).isEqualTo("안경 녹음 중...")

            ServiceBridge.emitConversation(
                Message(type = MessageType.AI_ERROR, payload = "실패")
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.processingStatus).isNull()
        }
    }

    @Test
    fun `clears processing status when bluetooth disconnects back to idle`() = runTest {
        withPhoneViewModel { viewModel ->
            ServiceBridge.updateBluetoothState(BluetoothConnectionState.CONNECTED)
            advanceUntilIdle()

            ServiceBridge.emitConversation(
                Message(type = MessageType.AI_PROCESSING, payload = "안경 녹음 중...")
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.processingStatus).isEqualTo("안경 녹음 중...")

            ServiceBridge.updateBluetoothState(BluetoothConnectionState.DISCONNECTED)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.processingStatus).isNull()
        }
    }

    @Test
    fun `clears processing status when bluetooth returns to listening idle`() = runTest {
        withPhoneViewModel { viewModel ->
            ServiceBridge.emitConversation(
                Message(type = MessageType.AI_PROCESSING, payload = "안경 녹음 중...")
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.processingStatus).isEqualTo("안경 녹음 중...")

            ServiceBridge.updateBluetoothState(BluetoothConnectionState.LISTENING)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.processingStatus).isNull()
        }
    }

    @Test
    fun `live assistant transcription replaces last assistant conversation instead of appending`() = runTest {
        withPhoneViewModel { viewModel ->
            ServiceBridge.emitConversation(
                Message(
                    type = MessageType.LIVE_TRANSCRIPTION,
                    payload = LiveTranscriptPayload(
                        role = LiveTranscriptRole.ASSISTANT,
                        text = "삼만 팔천",
                    ).toPayloadString()
                )
            )
            advanceUntilIdle()

            ServiceBridge.emitConversation(
                Message(
                    type = MessageType.LIVE_TRANSCRIPTION,
                    payload = LiveTranscriptPayload(
                        role = LiveTranscriptRole.ASSISTANT,
                        text = "삼만 팔천 사백십육이에요.",
                    ).toPayloadString()
                )
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.conversations).hasSize(1)
            assertThat(viewModel.uiState.value.conversations.single().content)
                .isEqualTo("삼만 팔천 사백십육이에요.")
        }
    }

    @Test
    fun `final ai response does not duplicate live assistant transcript`() = runTest {
        withPhoneViewModel { viewModel ->
            val finalText = "삼만 팔천 사백십육이에요."
            ServiceBridge.emitConversation(
                Message(
                    type = MessageType.LIVE_TRANSCRIPTION,
                    payload = LiveTranscriptPayload(
                        role = LiveTranscriptRole.ASSISTANT,
                        text = finalText,
                    ).toPayloadString()
                )
            )
            advanceUntilIdle()

            ServiceBridge.emitConversation(
                Message(type = MessageType.AI_RESPONSE_TEXT, payload = finalText)
            )
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.conversations).hasSize(1)
            assertThat(viewModel.uiState.value.conversations.single().content).isEqualTo(finalText)
        }
    }

    @Test
    fun `live usage summary updates footer state without touching conversations`() = runTest {
        withPhoneViewModel { viewModel ->
            ServiceBridge.updateLiveUsageSummary("입력 1,200 · 출력 240 · 총 1,440")
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.liveUsageSummary)
                .isEqualTo("입력 1,200 · 출력 240 · 총 1,440")
            assertThat(viewModel.uiState.value.conversations).isEmpty()

            ServiceBridge.updateLiveUsageSummary(null)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.liveUsageSummary).isNull()
        }
    }

    @Test
    fun `live session status updates footer state without touching conversations`() = runTest {
        withPhoneViewModel { viewModel ->
            val snapshot = LiveSessionStatusSnapshot(
                inputSource = LiveInputSource.PHONE,
                outputTarget = LiveOutputTarget.GLASSES,
                liveRagEnabled = true,
                googleSearchEnabledInSettings = true,
                googleSearchAvailableForSession = false,
                thinkingLevel = LiveThinkingLevel.HIGH,
            )

            ServiceBridge.updateLiveSessionStatus(snapshot)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.liveSessionStatus).isEqualTo(snapshot)
            assertThat(viewModel.uiState.value.conversations).isEmpty()

            ServiceBridge.updateLiveSessionStatus(null)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.liveSessionStatus).isEqualTo(null)
        }
    }
}
