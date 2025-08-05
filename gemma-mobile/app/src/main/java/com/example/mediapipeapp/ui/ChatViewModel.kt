package com.example.mediapipeapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediapipeapp.DesktopApiService
import com.example.mediapipeapp.StreamingToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val desktopApiService: DesktopApiService,
    private val promptName: String
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    fun sendMessage(prompt: String, imageBase64: String? = null, audioBase64: String? = null) {
        if (prompt.isBlank()) return

        // Add user message to the list
        val userMessage = Message(text = prompt, author = "user", imageBase64 = imageBase64)
        _messages.value += userMessage

        // Start generation
        _isGenerating.value = true
        val assistantMessage = Message(text = "", author = "assistant")
        _messages.value += assistantMessage

        viewModelScope.launch {
            try {
                desktopApiService.generateTextStreaming(
                    prompt = prompt,
                    promptName = promptName, // Pass the prompt name to the API
                    imageBase64 = imageBase64,
                    audioBase64 = audioBase64
                ).collect { streamingToken ->
                    if (streamingToken.error != null) {
                        updateLastMessage("Error: ${streamingToken.error}")
                    } else if (!streamingToken.isComplete) {
                        appendTokenToLastMessage(streamingToken.token)
                    }

                    if (streamingToken.isComplete) {
                        _isGenerating.value = false
                    }
                }
            } catch (e: Exception) {
                updateLastMessage("Error: ${e.message}")
                _isGenerating.value = false
            }
        }
    }

    private fun appendTokenToLastMessage(token: String) {
        val lastMessage = _messages.value.lastOrNull()
        if (lastMessage != null && lastMessage.author == "assistant") {
            val updatedMessage = lastMessage.copy(text = lastMessage.text + token)
            _messages.value = _messages.value.dropLast(1) + updatedMessage
        }
    }

    private fun updateLastMessage(text: String) {
        val lastMessage = _messages.value.lastOrNull()
        if (lastMessage != null && lastMessage.author == "assistant") {
            val updatedMessage = lastMessage.copy(text = text)
            _messages.value = _messages.value.dropLast(1) + updatedMessage
        }
    }
}

data class Message(
    val text: String,
    val author: String, // "user" or "assistant"
    val imageBase64: String? = null
)
