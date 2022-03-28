package com.example.hushchat

import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.Flow

/**
 * Repository class which performs db operations with the Room db we use to store messages.
 * uses a DAO (data access object) as private property in the constructor so don't need to pass
 * in whole db.
  */
class MessageRepository(private val messageDao: ChatMessageDao) {

    /**
     * sends a query to the db to get the messages relevant to the particular chat window we are
     * looking at (e.g. if we're talking to Dave then we only want to see messages between Dave and
     * ourselves).
     */
    fun getRelevantMessages(sender:String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesFromSender(sender)
    }

    /**
     * Sends a delete query representing our self-destruct logic for messages. This is run by a
     * daemon thread on a schedule we define on first login
     */
    fun deleteAccordingToTimeFrame(timeFrame: Long) {
        messageDao.deleteMessagesAccordingToTimeFrame(timeFrame)
    }

    /**
     * Insert function to add new items to our db.
     */
    @Suppress("RedundantSuspendModifier")
//    room runs suspend queries off main thread so no need to do anything to protect main thread
    @WorkerThread
    suspend fun insert(message: ChatMessage) {
        messageDao.insert(message)
    }

}