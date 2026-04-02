package com.example.rokidphone.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var conversationDao: ConversationDao
    private lateinit var messageDao: MessageDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        conversationDao = database.conversationDao()
        messageDao = database.messageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `deleteAllConversations removes every conversation and cascades messages`() = runTest {
        val activeConversation = ConversationEntity(
            id = "active-conversation",
            title = "Active",
            providerId = "GEMINI",
            modelId = "gemini-3.1-flash-lite-preview",
            isArchived = false
        )
        val archivedConversation = ConversationEntity(
            id = "archived-conversation",
            title = "Archived",
            providerId = "GEMINI",
            modelId = "gemini-3.1-flash-lite-preview",
            isArchived = true
        )

        conversationDao.insertConversation(activeConversation)
        conversationDao.insertConversation(archivedConversation)

        messageDao.insertMessages(
            listOf(
                MessageEntity(
                    id = "active-message",
                    conversationId = activeConversation.id,
                    role = "user",
                    content = "keep me?"
                ),
                MessageEntity(
                    id = "archived-message",
                    conversationId = archivedConversation.id,
                    role = "user",
                    content = "archive survives"
                )
            )
        )

        conversationDao.deleteAllConversations()

        val remainingConversations = conversationDao.getAllConversationsSync()
        val archivedConversations = conversationDao.getArchivedConversations().first()
        val remainingArchivedMessages = messageDao.getMessagesForConversationSync(archivedConversation.id)
        val remainingActiveMessages = messageDao.getMessagesForConversationSync(activeConversation.id)

        assertThat(remainingConversations).isEmpty()
        assertThat(archivedConversations).isEmpty()
        assertThat(remainingArchivedMessages).isEmpty()
        assertThat(remainingActiveMessages).isEmpty()
    }
}
