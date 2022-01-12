package com.example.hushchat

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hushchat.SocketHandler.mSocket
import org.json.JSONArray

class Contacts : AppCompatActivity() {

    val data = ArrayList<ItemsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = CustomAdapter(data)
        recyclerView.adapter = adapter
        val users:String
        val mSocket = SocketHandler.mSocket
        val refreshUsersButton = findViewById<Button>(R.id.refresh_users_button)


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