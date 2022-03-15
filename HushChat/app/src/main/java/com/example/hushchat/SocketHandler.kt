package com.example.hushchat

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import java.net.URISyntaxException

object SocketHandler {
    lateinit var mSocket: Socket

    @Synchronized
    fun setSocket() {
        try{
            mSocket = IO.socket("http://20.126.74.125:8080")
//            mSocket = IO.socket("http://10.0.2.2:8080")
        }catch (e: Exception) {

            Log.e("e", "Error connecting to server.")
        }
    }

    @Synchronized
    fun getSocket(): Socket {
        return mSocket
    }


    @Synchronized
    fun establishConnection() {
        mSocket.connect()
    }

    @Synchronized
    fun closeConnection() {
        mSocket.disconnect()
    }

    @Synchronized
    fun send_Username(username: String) {
        mSocket.emit("newUsername", username)
    }
}