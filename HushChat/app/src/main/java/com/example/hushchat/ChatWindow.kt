package com.example.hushchat

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
        val header = findViewById<TextView>(R.id.chatUserHeading)
        val textInput = findViewById<TextInputEditText>(R.id.new_message_text)
        val sendMsgButton = findViewById<Button>(R.id.send_message_button)
        header.text = ChatUser
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        recv_messages(mSocket)
        sendMsgButton.setOnClickListener {
            send_message(textInput.text.toString(), ChatUser.toString())
            messageViewModel.insert(ChatMessage(message=textInput.text.toString(),
                sender = "me",
                timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")).toString()))
            closeKeyboard()
            textInput.text?.clear()
        }

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
            val now:String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")).toString()
            messageViewModel.insert(ChatMessage(message=data.getString("message"),
                sender = data.getString("sender"),
                timestamp = now))
        }
    }

    fun send_message(message:String, recipient:String) {
        val outputArray = arrayOf(recipient, message)
        SocketHandler.mSocket.emit("send_to_user", JSONArray(outputArray))
        runOnUiThread {
            var textReader = findViewById<TextView>(R.id.new_message_text)
            val old_text = textReader.text.toString()
        }
    }

    fun closeKeyboard() {
        val imm: InputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm.isActive)
            imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
    }
}
