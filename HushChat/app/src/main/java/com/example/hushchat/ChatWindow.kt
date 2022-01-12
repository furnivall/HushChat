package com.example.hushchat

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.socket.client.Socket
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class ChatWindow : AppCompatActivity() {

    private val newMessageActivityRequestCode = 1
    private val messageViewModel: MessageViewModel by viewModels {
        MessageViewModelFactory((application as MessagesApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        SocketHandler.establishConnection()
        val mSocket = SocketHandler.getSocket()


        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        val ChatUser = intent.getStringExtra("ChatUser")
        setContentView(R.layout.activity_chat_window)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
        val adapter = MessageListAdapter()
        var header = findViewById<TextView>(R.id.chatUserHeading)

        header.text = ChatUser
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        recv_messages(mSocket)

        // add an observer on the LiveData returned by getAlphabetizedMessages.
        // The onChanged method fires when the observed data changes and the activity is in
        // the foreground.
        messageViewModel.allMessages.observe(this, Observer { messages ->
            messages?.let { adapter.submitList(it) }
        })

    }
    fun recv_messages(socket: Socket) {
        socket.on("chatmessage") {
            Log.i("I", it[0].toString())
            val data = it[0] as JSONObject
            Log.i("i",data.getString("message"))

            messageViewModel.insert(ChatMessage(message=data.getString("message"),
                sender = data.getString("sender"),
                timestamp = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).toString()))
        }
    }
}
