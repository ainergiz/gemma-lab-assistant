package com.example.mediapipeapp.data.entities

import com.example.mediapipeapp.ChatMessage

fun Message.toChatMessage(): ChatMessage {
    return ChatMessage(
        text = text,
        isUser = isUser,
        timestamp = timestamp,
        responseTime = responseTime,
        tokensPerSecond = tokensPerSecond,
        id = id,
        modelMode = modelSource
    )
}