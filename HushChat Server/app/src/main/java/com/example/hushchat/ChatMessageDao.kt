package com.example.hushchat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao

interface ChatMessageDao {

    /**
     * get time sorted messages. Not actually used anywhere except for debugging.
     */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getTimeSortedMessages() : Flow<List<ChatMessage>>

    /**
     * Gets the relevant messages for the target user (i.e. when a chat window to that user is open)
     */
    @Query("SELECT * FROM chat_messages WHERE sender = :sender OR recipient = :sender ORDER BY timestamp ASC")
    fun getMessagesFromSender(sender: String) : Flow<List<ChatMessage>>

    /**
     * insert function.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: ChatMessage)

    /**
     * deleteAll function - not used except for debug
     */
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()

    /**
     * Deletion query - used for our self-destruct logic.
     */
    @Query("DELETE FROM chat_messages WHERE messageTime < :deleteTimeFrame")
    fun deleteMessagesAccordingToTimeFrame(deleteTimeFrame: Long)
}