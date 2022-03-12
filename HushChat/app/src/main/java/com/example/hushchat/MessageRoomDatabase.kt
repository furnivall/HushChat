package com.example.hushchat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// annotates class to be a room db with a table of chat messages
@Database(entities = arrayOf(ChatMessage::class), version = 3, exportSchema = false)
abstract class MessageRoomDatabase : RoomDatabase() {

    abstract fun chatMessageDao() : ChatMessageDao

    private class MessageDatabaseCallback(
        private val scope:CoroutineScope) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch {
                    var messageDao = database.chatMessageDao()
                    // delete all content here
                    messageDao.deleteAll()

                }
            }
        }
        }

    companion object {
        // singleton prevents multiple instances
        @Volatile
        private var INSTANCE: MessageRoomDatabase? = null

        fun getDatabase(
            context: Context,
            scope:CoroutineScope):
                MessageRoomDatabase {
            // if instance not null then return
            // if null, create db
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MessageRoomDatabase::class.java,
                    "chat_message_database"
                ).addCallback(MessageDatabaseCallback(scope)).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}