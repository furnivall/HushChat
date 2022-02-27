package com.example.hushchat

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
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
import android.provider.Settings.Secure
import com.example.hushchat.MessagesApplication.Companion.activityName
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement

class MainActivity : AppCompatActivity() {
    val connected_users = ArrayList<String>()

    private val messageViewModel: MessageViewModel by viewModels {
        MessageViewModelFactory((application as MessagesApplication).repository)
    }
    var notificationNumber = 1

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel("1", name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onPause() {
        super.onPause()
        activityName = "//pause"
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        activityName = "mainactivity"
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)
        val text_reader = findViewById(R.id.text_reader) as TextView
        val text_input_username = findViewById(R.id.text_input_username) as TextInputEditText
        val set_username_button = findViewById(R.id.username_button) as Button
        createNotificationChannel()
        text_reader.movementMethod = ScrollingMovementMethod()


        SocketHandler.setSocket()
        SocketHandler.establishConnection()
        val mSocket = SocketHandler.mSocket
        text_reader.text = text_reader.text.toString() + "\n*** Connection established. ***\n"
        getUniqueId(mSocket)
        handle_initial_messages()
        handle_username_messages()
        handle_new_users()

        recv_messages(mSocket)

        if (!existingUser(mSocket)) {
            set_username_button.visibility = View.VISIBLE
            text_input_username.visibility = View.VISIBLE
            set_username_button.setOnClickListener {

//              TODO write clearly about this:
//               https://stackoverflow.com/questions/64776709/kotlin-ecc-encryption


                val plaintext = "string"
                val bytePlainText = plaintext.toByteArray()
                Security.removeProvider("BC")
                Security.addProvider(BouncyCastleProvider())
                val keyPairGenerator = KeyPairGenerator.getInstance("ECDH")
                keyPairGenerator.initialize(ECGenParameterSpec("secp521r1"))
                val pair = keyPairGenerator.genKeyPair()

                //encrypt
                val cipherEnc = Cipher.getInstance("ECIES")
                cipherEnc.init(Cipher.ENCRYPT_MODE, pair.public)
                val cipherText = cipherEnc.doFinal(bytePlainText)
                Log.e("e", "Encrypted text: ${cipherText.toString()}")

                //decrypt
                val cipherDec = Cipher.getInstance("ECIES")
                cipherDec.init(Cipher.DECRYPT_MODE, pair.private)
                val decryptedText = cipherDec.doFinal(cipherText)
                Log.e("e", "Decrypted text: ${decryptedText.decodeToString()}")







                val typedData = text_input_username.text.toString()
                SocketHandler.send_Username(typedData)
                text_input_username.text?.clear()
                closeKeyboard()
                set_username_button.visibility = View.GONE
                text_input_username.visibility = View.GONE
                val intent = Intent(this, Contacts::class.java).apply {
                    putExtra("com.example.hushchat.message", "Message")
                }
                startActivity(intent)
            }
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
            Log.e("e", message)
            messageViewModel.insert(
                ChatMessage(
                    message = message,
                    sender = sender,
                    recipient = "me",
                    timestamp = now
                )
            )
//            intent to allow us to immediately open the relevant chatWindow activity for the user who sent the message.
            var notifIntent = Intent(this, ChatWindow::class.java).apply {
                putExtra("ChatUser", sender)
//                this boolean is added so that if we open a chat window from a push notification we
//                can actually receive messages from the other user.
                putExtra("notif", true)
            }
//            the below code turns our intent into a "PendingIntent" and allows us
//            to embed it within a notification as appropriate.
            val resultPendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
                addNextIntentWithParentStack(notifIntent)
                getPendingIntent(
                    0,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
//            the below code is the builder object which allows us to send a notification on
//            the notification channel outlined at the beginning of this file.
            var builder = NotificationCompat.Builder(this, "1")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(sender)
                .setContentText(message)
                .setContentIntent(resultPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
//            actually send the notification.

            Log.e("e", "the current window is: $activityName")
            if (activityName != sender) {
                with(NotificationManagerCompat.from(this)) {
                    notify(notificationNumber, builder.build())
                }
                notificationNumber++
            }
        }
    }

    private fun getUniqueId(socket: Socket) {
        val uniqueId = Secure.getString(contentResolver, Secure.ANDROID_ID)
        Log.i("i", "UNIQUE ID = $uniqueId")
        socket.emit("uid", "$uniqueId")
    }

    private fun existingUser(socket: Socket): Boolean {
        socket.on("existingUserCheck") {
            Log.i("i", it[0].toString())
            if (it[0].equals("true")) {
                Log.i("i", "user has already got an existing account")
                val intent = Intent(this, Contacts::class.java).apply {
                    putExtra("com.example.hushchat.message", "Message")
                }
                startActivity(intent)
            }
        }
        return false
    }

    fun closeKeyboard() {
        val imm: InputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm.isActive)
            imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
    }
}