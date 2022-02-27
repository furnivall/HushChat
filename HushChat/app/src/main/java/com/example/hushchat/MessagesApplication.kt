package com.example.hushchat

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class MessagesApplication : Application() {
    // uses by lazy so the db and repo are only created when they're needed rather than at app
    // start.
    companion object {
        var activityName = "mainactivity"
        var privateKey = ""
        var publicKey = ""
    }
    val applicationScope = CoroutineScope(SupervisorJob())
    val database by lazy {MessageRoomDatabase.getDatabase(this, applicationScope)}
    val repository by lazy { MessageRepository(database.chatMessageDao())}
}