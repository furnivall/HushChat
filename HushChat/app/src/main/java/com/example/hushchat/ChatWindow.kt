package com.example.hushchat

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.example.hushchat.MessagesApplication.Companion.activityName
import com.example.hushchat.MessagesApplication.Companion.privateKey
import com.example.hushchat.MessagesApplication.Companion.publicKey
import io.socket.client.Socket
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ChatWindow : AppCompatActivity() {

//    messageViewmodel is responsible for storage and display of messages.
    private val messageViewModel: MessageViewModel by viewModels {
        MessageViewModelFactory((application as MessagesApplication).repository)
    }

//    stores the recipient's (i.e. the other user) public key
    private lateinit var recipientPubKey:PublicKey

//    shared secret variable to assist with creation of aeskey
    private lateinit var sharedSecret:ByteArray

//    aeskey generated from shared secret
    private lateinit var aesKey:ByteArray

    /**
     * tells the app that our current activity is minimised so notifications will still be sent.
     * If the chat window is opened we will not receive a notification.
     */
    override fun onPause() {
        super.onPause()
        activityName = "//pause"
    }

    /**
     * Init method for activity. Initialises socket connection to the server. Gets recipient
     * metadata from the intent used to enter the activity.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        SocketHandler.establishConnection()
        val mSocket = SocketHandler.getSocket()
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        val ChatUser = intent.getStringExtra("ChatUser")
        val notif = intent.getBooleanExtra("notif", false)

//        ensures that notifications are not sent if the message is received from the current
//        recipient. If msg comes from another recipient, then the notification will still be sent.
        activityName = ChatUser.toString()

//        creates content view and initialises views.
        setContentView(R.layout.activity_chat_window)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
        val adapter = MessageListAdapter()
        val header = findViewById<TextView>(R.id.chatUserHeading)
        val textInput = findViewById<TextInputEditText>(R.id.new_message_text)
        val sendMsgButton = findViewById<Button>(R.id.send_message_button)
        header.text = ChatUser
//        get recipient pub key from server
        requestPubKey(ChatUser.toString())
        receivePubKey()
//        init recyclerview to show messages (and live update them as appropriate)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        if (notif){
    //      this is an ugly hack to deal with the fact that I don't fully understand intents.
    //      The issue is that I need to attach a backstack but can't seem to get it working.
            recv_messages(mSocket)
        }

//        check that target user exists and populate recycler based on content of messageViewModel
        if (ChatUser != null) {
            messageViewModel.relevantMessages(ChatUser).observe(this, Observer { messages ->
                messages?.let { adapter.submitList(it) }
            })
        }

//        handle the process of sending messages
        sendMsgButton.setOnClickListener {
            send_message(textInput.text.toString(), ChatUser.toString())
            messageViewModel.insert(
                ChatMessage(
                    message = textInput.text.toString(),
                    sender = "me",
                    recipient = ChatUser.toString(),
                    timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                        .toString(),
                    messageTime = Date().time
                )
            )

//            clean up after sent message
            closeKeyboard()
            textInput.text?.clear()
        }
    }

    /**
     * Almost exactly the same as the recv_messages function within MainActivity.kt, but without the
     * handling of notifications. This method exists so that if the user has reached this activity
     * from the notification intent then receiving messages will still work fluidly.
     */
    fun recv_messages(socket: Socket) {
        socket.on("chatmessage") {
            Security.removeProvider("BC")
            Security.addProvider(BouncyCastleProvider())
            Log.i("I", it[0].toString())
            val data = it[0] as JSONObject
            val now: String =
                LocalDateTime.now().toString()
            Log.e("e", "Our localdatetime format is: $now")
            val sender = data.getString("sender")
            val message = data.getString("message")
            val pubkeyBase64 = data.getString("pubkey")
            Log.e("e", "Public key in base 64 from message sender: $pubkeyBase64")
            val decodeBase64pubKey = Base64.decode(pubkeyBase64, Base64.DEFAULT)
            Log.e("e", "initialb64: "+decodeBase64pubKey.toString())
            Log.e("e", "our base64 key is of type: "+decodeBase64pubKey.javaClass.name)
            val keyspec = X509EncodedKeySpec(decodeBase64pubKey)
            val keyFactory = KeyFactory.getInstance("EC")
            val pubKeyReturned = keyFactory.generatePublic(keyspec)
            Log.e("e", "The message below is the received message")
            Log.e("e", "--"+message+"--")
            assert(pubKeyReturned.equals(recipientPubKey))
            val base64DecodedMessage = Base64.decode(message, Base64.DEFAULT)
            val otherSharedSecret = getSharedSecret(privateKey,pubKeyReturned)
            val responseAesKey = getAESKey(otherSharedSecret)
            val decryptedMessage = decrypt(responseAesKey, base64DecodedMessage)
            messageViewModel.insert(
                ChatMessage(
                    message = String(decryptedMessage, StandardCharsets.UTF_8),
                    sender = sender,
                    recipient = "me",
                    timestamp = now,
                    messageTime = Date().time
                )
            )
        }
    }

//    ask server for the public key of the target user
    fun requestPubKey(user: String) {
        SocketHandler.mSocket.emit("getPubKey", user)
    }

//    handle receipt of pub key from server. Almost exactly the same as the method in MainActivity
    fun receivePubKey() {
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
        SocketHandler.mSocket.on("pubKeyResponse") {
            Log.e("e", "Received pubKeyResponse...")
            Log.e("e", it[0].toString())
            val byteArrayPubKey:ByteArray = it[0] as ByteArray
            var returnedBase64Decoded = Base64.decode(byteArrayPubKey, Base64.DEFAULT)
            val keyspec = X509EncodedKeySpec(returnedBase64Decoded)
            val keyFactory = KeyFactory.getInstance("EC")
            val pubKeyReturned = keyFactory.generatePublic(keyspec)
            Log.e("e", "here is the initial public key"+publicKey.toString())
            Log.e("e", "here is the returned public key"+pubKeyReturned.toString())
            recipientPubKey = pubKeyReturned
            sharedSecret = getSharedSecret(privateKey, recipientPubKey)
            aesKey = getAESKey(sharedSecret)
        }
    }

    /**
     * Same as method in MainActivity
     */
    private fun getSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

    /**
     * Same as method in MainActivity
     */
    private fun getAESKey(sharedSecret: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-512")
        return digest.digest(sharedSecret).copyOfRange(0, 32)
    }

    private fun encrypt(aesKey: ByteArray, plaintext: ByteArray): ByteArray {
        val secretKeySpec = SecretKeySpec(aesKey, "AES")
        val iv = ByteArray(12) // Create random IV, 12 bytes for GCM
        SecureRandom().nextBytes(iv)
        val gCMParameterSpec = GCMParameterSpec(128, iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gCMParameterSpec)
        val ciphertext = cipher.doFinal(plaintext)
        val ivCiphertext = ByteArray(iv.size + ciphertext.size) // Concatenate IV and ciphertext (the MAC is implicitly appended to the ciphertext)
        System.arraycopy(iv, 0, ivCiphertext, 0, iv.size)
        System.arraycopy(ciphertext, 0, ivCiphertext, iv.size, ciphertext.size)
        return ivCiphertext
    }

    /**
     * Same as method in MainActivity
     */
    private fun decrypt(aesKey: ByteArray, ivCiphertext: ByteArray): ByteArray {
        val secretKeySpec = SecretKeySpec(aesKey, "AES")
        val iv = ivCiphertext.copyOfRange(0, 12) // Separate IV
        val ciphertext = ivCiphertext.copyOfRange(12, ivCiphertext.size) // Separate ciphertext (the MAC is implicitly separated from the ciphertext)
        val gCMParameterSpec = GCMParameterSpec(128, iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gCMParameterSpec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * This method is the big one. It uses client private key and target user's public key to
     * generate a shared secret and then AES key. After this, our plain text message is encrypted
     * using the AES key, then encoded to base64.
     */
    fun send_message(message: String, recipient: String) {
        val encryptedMessage = encrypt(aesKey, message.toByteArray(StandardCharsets.UTF_8))
        val base64encryptedMessage = Base64.encodeToString(encryptedMessage, Base64.DEFAULT)
//        generate an array (which will be translated to json) for the server to work with.
        val outputArray = arrayOf(recipient, base64encryptedMessage)
        Log.i("e", "The message below is the sent message:")
        Log.i("e", "--$base64encryptedMessage--")

//        this debug function allows us to verify that the decrypted message is being correctly
//        deserialised from base 64.
        val decryptedMessage = decrypt(aesKey, encryptedMessage)
        Log.e("e", "The decrypted debug version of the string is here:$decryptedMessage")

//        Send the event to the server.
        SocketHandler.mSocket.emit("send_to_user", JSONArray(outputArray))
    }

    /**
     * helper method for closing keyboard
     */
    fun closeKeyboard() {
        val imm: InputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm.isActive)
            imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
    }
}
