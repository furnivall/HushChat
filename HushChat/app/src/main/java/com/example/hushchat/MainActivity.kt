package com.example.hushchat

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.textfield.TextInputEditText
import io.socket.client.Socket
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    val connected_users = ArrayList<String>()
    private val messageViewModel: MessageViewModel by viewModels {
        MessageViewModelFactory((application as MessagesApplication).repository)
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("1", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)
        val text_reader = findViewById(R.id.text_reader) as TextView
        val text_input_username = findViewById(R.id.text_input_username) as TextInputEditText
        val set_username_button = findViewById(R.id.username_button) as Button
        val send_msg_btn = findViewById(R.id.send_message_button) as Button
        createNotificationChannel()
        text_reader.movementMethod = ScrollingMovementMethod()


        SocketHandler.setSocket()
        SocketHandler.establishConnection()
        text_reader.text = text_reader.text.toString() + "\n*** Connection established. ***\n"

        handle_initial_messages()
        handle_username_messages()
        handle_new_users()
        val mSocket = SocketHandler.mSocket
        recv_messages(mSocket)
        set_username_button.setOnClickListener {
            val typedData = text_input_username.text.toString()
            SocketHandler.send_Username(typedData)
            text_input_username.text?.clear()
            closeKeyboard()
            set_username_button.visibility = View.GONE
            text_input_username.visibility = View.GONE
            text_reader.visibility = View.VISIBLE
            send_msg_btn.visibility = View.VISIBLE
            val intent = Intent(this, Contacts::class.java).apply {
                putExtra("com.example.hushchat.message", "Message")
            }
            startActivity(intent)
        }
    }

    fun handle_new_users() {
        SocketHandler.mSocket.on("new_user") { args ->
            if (args[0] != null) {
                Log.i("I", "New user: " + args[0] as String)
                runOnUiThread {
                    connected_users.add(args[0] as String)
                }
            }
        }
    }

    fun handle_initial_messages() {
        SocketHandler.mSocket.on("init_message") { args ->
            if (args[0] != null) {
                val counter = args[1].toString()
                Log.i("I", counter)
                runOnUiThread {
                    var text_reader = findViewById(R.id.text_reader) as TextView
                    val old_text = text_reader.text.toString()
                    text_reader.text = old_text + args[0].toString()
                }
            }
        }
    }

    fun handle_username_messages() {
        SocketHandler.mSocket.on("username_message") { args ->
            if (args.size > 0) {
                Log.i("I", args[0].toString())
                runOnUiThread {
                    var text_reader = findViewById(R.id.text_reader) as TextView
                    val old_text = text_reader.text.toString()
                    text_reader.text = old_text + args[0].toString()

                }

            }
        }
    }


    fun recv_messages(socket: Socket) {
        socket.on("chatmessage") {
            Log.i("I", it[0].toString())
            val data = it[0] as JSONObject
            Log.i("i", data.getString("message"))
            val now: String =
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")).toString()
            val sender = data.getString("sender")
            val message = data.getString("message")
            messageViewModel.insert(
                ChatMessage(
                    message = message,
                    sender = sender,
                    recipient = "me",
                    timestamp = now
                )
            )
            val notifIntent = Intent(this, ChatWindow::class.java).apply {
                putExtra("ChatUser", sender)
            }
            val resultPendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
                // Add the intent, which inflates the back stack
                addNextIntentWithParentStack(notifIntent)
                // Get the PendingIntent containing the entire back stack
                getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE)
            }
            var builder = NotificationCompat.Builder(this, "1")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(sender)
                .setContentText(message)
                .setContentIntent(resultPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            with(NotificationManagerCompat.from(this)) {
                notify(1, builder.build())
            }
        }
    }


    fun closeKeyboard() {
        val imm: InputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm.isActive)
            imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
    }
}