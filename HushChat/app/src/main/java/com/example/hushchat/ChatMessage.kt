package com.example.hushchat

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "chat_messages")
class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int? = null,
    @ColumnInfo(name = "Message") val message: String,
    @ColumnInfo(name = "Sender") val sender: String,
    @ColumnInfo(name = "Timestamp") val timestamp: String
    ) {
    override
    fun toString(): String {
        return this.message
    }
}

