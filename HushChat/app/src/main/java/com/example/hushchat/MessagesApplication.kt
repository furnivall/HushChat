package com.example.hushchat

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.security.PrivateKey
import java.security.PublicKey

class MessagesApplication : Application() {
    // uses by lazy so the db and repo are only created when they're needed rather than at app
    // start.
    companion object {
        var activityName = "mainactivity"
        lateinit var privateKey: PrivateKey
        lateinit var publicKey: PublicKey
    }
    val applicationScope = CoroutineScope(SupervisorJob())
    val database by lazy {MessageRoomDatabase.getDatabase(this, applicationScope)}
    val repository by lazy { MessageRepository(database.chatMessageDao())}
}