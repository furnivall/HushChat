package com.example.hushchat

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity represents the serialisable class which is stored within the room db
 */
@Entity(tableName = "chat_messages")
class ChatMessage(
//    Unique identifier
    @PrimaryKey(autoGenerate = true) val id: Int? = null,
//    Message string - this is a base 64 encoded string which decodes to a byte array of encrypted
//    data which can only be decrypted by the relevant user.
    @ColumnInfo(name = "Message") val message: String,
//    Metadata showing who the sender of the message is. Used to ensure only relevant messages are
//    displayed to the user
    @ColumnInfo(name = "Sender") val sender: String,
//    Metadata showing the intended recipient of the message. The server uses this to determine who
//    to pass the information on to.
    @ColumnInfo(name="Recipient") val recipient: String,
//    timestamp representing the current time for displaying message dates to the user.
    @ColumnInfo(name = "Timestamp") val timestamp: String,
//    time representing actual time in epoch seconds - this allows us to compare dates so the self-
//    destruct function can be used to delete messages after our selected duration.
    @ColumnInfo(name = "messageTime") val messageTime: Long
    ) {
    override
    fun toString(): String {
        return this.message
    }
}

