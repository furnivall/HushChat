package com.example.hushchat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao

interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getTimeSortedMessages() : Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE sender = :sender OR recipient = :sender ORDER BY timestamp ASC")
    fun getMessagesFromSender(sender: String) : Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}