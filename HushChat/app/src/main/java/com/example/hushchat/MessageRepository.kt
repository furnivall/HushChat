package com.example.hushchat

import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.Flow

// uses DAO as priv property in the constructor so don't need to pass in whole db.
class MessageRepository(private val messageDao: ChatMessageDao) {

    fun getRelevantMessages(sender:String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesFromSender(sender)
    }

    fun deleteAccordingToTimeFrame(timeFrame: Long) {
        messageDao.deleteMessagesAccordingToTimeFrame(timeFrame)
    }

//   relevantMessages: Flow<List<ChatMessage>> = messageDao.getMessagesFromSender(sender)

    // room runs suspend queries off main thread so no need to do anything to protect main thread
    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insert(message: ChatMessage) {
        messageDao.insert(message)
    }

}