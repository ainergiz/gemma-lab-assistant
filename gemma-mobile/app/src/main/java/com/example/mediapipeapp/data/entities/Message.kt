package com.example.mediapipeapp.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = Conversation::class,
        parentColumns = ["id"],
        childColumns = ["conversation_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,
    val text: String,
    @ColumnInfo(name = "is_user")
    val isUser: Boolean,
    @ColumnInfo(name = "model_source")
    val modelSource: String, // "mobile", "desktop", "streaming"
    val timestamp: Long,
    @ColumnInfo(name = "response_time")
    val responseTime: Long? = null,
    @ColumnInfo(name = "tokens_per_second")
    val tokensPerSecond: Double? = null,
    @ColumnInfo(name = "model_mode")
    val modelMode: String? = null
)