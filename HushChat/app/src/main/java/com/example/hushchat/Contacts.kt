package com.example.hushchat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.viewModels
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hushchat.SocketHandler.mSocket
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Contacts : AppCompatActivity() {
    private val messageViewModel: MessageViewModel by viewModels {
        MessageViewModelFactory((application as MessagesApplication).repository)
    }
    val data = ArrayList<ItemsViewModel>()

//    this below is where we'll define the behaviour to get to the correct chat window.


//    private fun createNotificationChannel() {
//        // Create the NotificationChannel, but only on API 26+ because
//        // the NotificationChannel class is new and not in the support library
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val name = getString(R.string.channel_name)
//            val descriptionText = getString(R.string.channel_description)
//            val importance = NotificationManager.IMPORTANCE_DEFAULT
//            val channel = NotificationChannel("1", name, importance).apply {
//                description = descriptionText
//            }
//            // Register the channel with the system
//            val notificationManager: NotificationManager =
//                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            notificationManager.createNotificationChannel(channel)
//        }
//    }




    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()
//        createNotificationChannel()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = CustomAdapter(data)
        recyclerView.adapter = adapter
        val users:String
        val mSocket = SocketHandler.mSocket
        val refreshUsersButton = findViewById<Button>(R.id.refresh_users_button)
//        recv_messages(mSocket)
        handleUsers()
        getContacts()
        refreshUsersButton.setOnClickListener {
            getContacts()
        }

    }

    fun redrawUsers(arrayOfUser:JSONArray, data:ArrayList<ItemsViewModel>){
        data.clear()
        for(i in 0 until arrayOfUser.length()) {
            data.add(ItemsViewModel(R.mipmap.ic_launcher_round, arrayOfUser.getString(i)))
        }
        runOnUiThread {
            val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
            recyclerView.layoutManager = LinearLayoutManager(this)
            val adapter = CustomAdapter(data)
            recyclerView.adapter = adapter
            adapter.onItemClick = {
                Log.i("I", it.text)
                val intent = Intent(this, ChatWindow::class.java).apply {
                    putExtra("ChatUser", it.text)
                }
                Log.i("I", "Starting activity now")
                startActivity(intent)
            }
        }

        Log.i("I", data.toString())

    }


    fun handleUsers() {
        mSocket.on("users") { args ->
                Log.i("I", (args[0] as JSONArray).toString())
                val arrayOfUsers = args[0] as JSONArray
                redrawUsers(arrayOfUsers, data)
            }
    }

    fun getContacts() {
        mSocket.emit("getUsers", "")
    }
}