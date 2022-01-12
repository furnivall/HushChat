package com.example.hushchat

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {
    val connected_users = ArrayList<String>()
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)
        val text_reader = findViewById(R.id.text_reader) as TextView
        val text_input_username = findViewById(R.id.text_input_username) as TextInputEditText
        val set_username_button = findViewById(R.id.username_button) as Button
        val send_msg_btn = findViewById(R.id.send_message_button) as Button

        text_reader.movementMethod = ScrollingMovementMethod()


        SocketHandler.setSocket()
        SocketHandler.establishConnection()
        text_reader.text = text_reader.text.toString() + "\n*** Connection established. ***\n"

        handle_initial_messages()
        handle_username_messages()
        handle_new_users()

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

    fun handle_new_users(){
        SocketHandler.mSocket.on("new_user") { args ->
            if (args[0] != null) {
                Log.i("I", "New user: "+args[0] as String)
                runOnUiThread {
                    connected_users.add(args[0] as String)
                }
            }
        }
    }

    fun handle_initial_messages (){
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

    fun handle_username_messages () {
        SocketHandler.mSocket.on("username_message") {args ->
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

    fun closeKeyboard() {
        val imm: InputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm.isActive)
            imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
    }
}