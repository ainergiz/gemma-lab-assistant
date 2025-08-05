package com.example.mediapipeapp.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.example.mediapipeapp.data.dao.ConversationDao
import com.example.mediapipeapp.data.dao.MessageDao
import com.example.mediapipeapp.data.entities.Conversation
import com.example.mediapipeapp.data.entities.Message

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    fun getAllConversations(): Flow<List<Conversation>> =
        conversationDao.getAllConversations()

    fun getConversationsWithMessages(): Flow<List<Conversation>> =
        conversationDao.getConversationsWithMessages()

    fun getMessagesForConversation(conversationId: Long): Flow<List<Message>> =
        messageDao.getMessagesForConversation(conversationId)

    suspend fun createNewConversation(title: String): Long {
        val conversation = Conversation(
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return conversationDao.insertConversation(conversation)
    }

    suspend fun addMessage(
        conversationId: Long,
        text: String,
        isUser: Boolean,
        modelSource: String,
        responseTime: Long? = null,
        tokensPerSecond: Double? = null,
        modelMode: String? = null
    ): Long {
        val message = Message(
            conversationId = conversationId,
            text = text,
            isUser = isUser,
            modelSource = modelSource,
            timestamp = System.currentTimeMillis(),
            responseTime = responseTime,
            tokensPerSecond = tokensPerSecond,
            modelMode = modelMode
        )

        val messageId = messageDao.insertMessage(message)

        // Update conversation timestamp and message count
        conversationDao.getConversationById(conversationId)?.let { conversation ->
            conversationDao.updateConversation(
                conversation.copy(
                    updatedAt = System.currentTimeMillis(),
                    messageCount = conversation.messageCount + 1
                )
            )
        }

        return messageId
    }

    suspend fun updateConversationTitle(conversationId: Long, newTitle: String) {
        conversationDao.getConversationById(conversationId)?.let { conversation ->
            conversationDao.updateConversation(
                conversation.copy(
                    title = newTitle,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteConversation(conversationId: Long) {
        conversationDao.deleteConversationById(conversationId)
    }

    suspend fun generateConversationTitle(conversationId: Long): String {
        // Get first few messages and create a smart title
        val messages = messageDao.getMessagesForConversation(conversationId).first()
        return if (messages.isNotEmpty()) {
            messages.first().text.take(30) + "..."
        } else {
            "New Conversation"
        }
    }
}