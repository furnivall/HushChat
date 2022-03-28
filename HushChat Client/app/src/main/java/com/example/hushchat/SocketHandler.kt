package com.example.hushchat

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import java.net.URISyntaxException

/**
 * Socket handler method to allow us to work with socket.io
 */
object SocketHandler {
    lateinit var mSocket: Socket

    @Synchronized
    fun setSocket() {
        try{
//            hardcoded link to azure vm. should probably be an environment variable.
            mSocket = IO.socket("http://20.126.74.125:8080")
        }catch (e: Exception) {
            Log.e("e", "Error connecting to server.")
        }
    }

    /**
     * gets our socket object
     */
    @Synchronized
    fun getSocket(): Socket {
        return mSocket
    }


    /**
     * Establishes a connection with the server.
     */
    @Synchronized
    fun establishConnection() {
        mSocket.connect()
    }

    /**
     * Closes connection as appropriate
     */
    @Synchronized
    fun closeConnection() {
        mSocket.disconnect()
    }

    /**
     * Sends username to server.
     */
    @Synchronized
    fun send_Username(username: String) {
        mSocket.emit("newUsername", username)
    }
}