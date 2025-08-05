package com.example.mediapipeapp

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.mediapipeapp.data.ChatDatabase
import com.example.mediapipeapp.repository.ChatRepository

class LlmChatViewModelFactory(
    private val context: Context,
    private val promptName: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LlmChatViewModel::class.java)) {
            val preferencesRepository = PreferencesRepository(context)
            val database = ChatDatabase.getDatabase(context)
            val chatRepository = ChatRepository(
                conversationDao = database.conversationDao(),
                messageDao = database.messageDao()
            )
            return LlmChatViewModel(preferencesRepository, chatRepository, promptName) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}