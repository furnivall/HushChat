package com.example.hushchat

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hushchat.SocketHandler.mSocket
import org.json.JSONArray
import kotlin.concurrent.fixedRateTimer
import com.example.hushchat.MessagesApplication.Companion.activityName


class Contacts : AppCompatActivity() {

//    Represents a list of users, each one represented by an ItemsViewModel.
    val listOfUsers = ArrayList<ItemsViewModel>()

    /**
     * Init method for contacts activity. Displays a list of users, updated every couple of seconds.
     * On clicking a username, launch a chat activity window with said user.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()
        activityName = "contacts"
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
        val emptyContacts = findViewById<TextView>(R.id.empty_contacts_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = ContactsAdapter(listOfUsers)
        recyclerView.adapter = adapter
        handleUsers()
        getContacts()
        val mytimer = fixedRateTimer("timer", true, 0, 2000){
            SocketHandler.mSocket.emit("getUsers", "")
        }
    }

    /**
     * Redraws the list of users. A getUsers event is sent to the server, which returns a "users"
     * event holding a json object with the list of current users, which then triggers a redraw of
     * the view. This is run every couple of seconds on receipt of the users event.
     */
    fun redrawUsers(arrayOfUser:JSONArray, data:ArrayList<ItemsViewModel>){
        data.clear()
        val emptyContacts = findViewById<TextView>(R.id.empty_contacts_list)
        for(i in 0 until arrayOfUser.length()) {
            data.add(ItemsViewModel(R.mipmap.ic_launcher_round, arrayOfUser.getString(i)))
        }
        runOnUiThread {
//            display specific message if no users online
            if(arrayOfUser.length() == 0){
                val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
                recyclerView.visibility = GONE
                emptyContacts.visibility = VISIBLE
            }
//            display user list
            else{
                emptyContacts.visibility = GONE
                val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
                recyclerView.visibility = VISIBLE
                recyclerView.layoutManager = LinearLayoutManager(this)
                val adapter = ContactsAdapter(data)
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
        }
    }

    /**
     * Handler for users event from the server. Triggers a redraw of the contacts page
     * as appropriate.
     */
    fun handleUsers() {
        mSocket.on("users") { args ->
                Log.i("I", (args[0] as JSONArray).toString())
                val arrayOfUsers = args[0] as JSONArray
                redrawUsers(arrayOfUsers, listOfUsers)
            }
    }

    /**
     * Sends a request for users to the server, which responds with a "users" event (handled above).
     */
    fun getContacts() {
        mSocket.emit("getUsers", "")
    }
}