package com.example.rokidphone.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.rokidphone.data.db.AppDatabase
import com.example.rokidphone.data.db.ConversationRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var repository: ConversationRepository
    private lateinit var database: AppDatabase
    private lateinit var viewModelStore: ViewModelStore

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        viewModelStore = ViewModelStore()
        repository = ConversationRepository.getInstance(application)
        database = AppDatabase.getInstance(application)
        repository.deleteAllConversations()
    }

    @After
    fun tearDown() = runTest {
        viewModelStore.clear()
        repository.deleteAllConversations()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ConversationViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConversationViewModel(application) as T
            }
        }
        return ViewModelProvider(viewModelStore, factory)[ConversationViewModel::class.java]
    }

    @Test
    fun `createNewConversation opens draft without creating persisted conversation`() = runTest {
        val viewModel = createViewModel()
        try {
            viewModel.createNewConversation()
            advanceUntilIdle()

            assertThat(viewModel.isDraftConversationOpen.value).isTrue()
            assertThat(viewModel.currentConversationId.value).isNull()
            assertThat(database.conversationDao().getAllConversationsSync()).isEmpty()
        } finally {
            viewModelStore.clear()
            advanceUntilIdle()
        }
    }
}
