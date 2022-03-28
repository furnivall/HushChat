package com.example.hushchat

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Application class - stores global variables for use throughout the program.
 */
class MessagesApplication : Application() {

    companion object {
//        activityname variable stores our current position - this is used to ensure notifications
//        are not sent when we're already focused on the user we've received the message from.
        var activityName = "mainactivity"

//        pub and priv keys for the client - used for encryption and decryption. pub key is sent to
//        server.
        lateinit var privateKey: PrivateKey
        lateinit var publicKey: PublicKey
    }

    val applicationScope = CoroutineScope(SupervisorJob())

    // uses by lazy so the db and repo are only created when they're needed rather than at app
    // start.
    val database by lazy {MessageRoomDatabase.getDatabase(this, applicationScope)}
    val repository by lazy { MessageRepository(database.chatMessageDao())}
}