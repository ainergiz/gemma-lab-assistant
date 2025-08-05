package com.example.mediapipeapp.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.mediapipeapp.data.entities.Conversation

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Query("""
        SELECT DISTINCT c.* FROM conversations c 
        INNER JOIN messages m ON c.id = m.conversation_id 
        ORDER BY c.updated_at DESC
    """)
    fun getConversationsWithMessages(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): Conversation?

    @Insert
    suspend fun insertConversation(conversation: Conversation): Long

    @Update
    suspend fun updateConversation(conversation: Conversation)

    @Delete
    suspend fun deleteConversation(conversation: Conversation)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: Long)
}