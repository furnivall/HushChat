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
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ChatWindow : AppCompatActivity() {

    private val newMessageActivityRequestCode = 1
    private val messageViewModel: MessageViewModel by viewModels {
        MessageViewModelFactory((application as MessagesApplication).repository)
    }
    private lateinit var recipientPubKey:PublicKey
    private lateinit var sharedSecret:ByteArray
    private lateinit var aesKey:ByteArray


    override fun onPause() {
        super.onPause()
        activityName = "//pause"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        SocketHandler.establishConnection()
        val mSocket = SocketHandler.getSocket()
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        val ChatUser = intent.getStringExtra("ChatUser")
        val notif = intent.getBooleanExtra("notif", false)
        activityName = ChatUser.toString()
        setContentView(R.layout.activity_chat_window)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
        val adapter = MessageListAdapter()
        val header = findViewById<TextView>(R.id.chatUserHeading)
        val textInput = findViewById<TextInputEditText>(R.id.new_message_text)
        val sendMsgButton = findViewById<Button>(R.id.send_message_button)

        header.text = ChatUser
        requestPubKey(ChatUser.toString())
        receivePubKey()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        if (notif){
    //      this is an ugly hack to deal with the fact that I don't fully understand intents.
    //      The issue is that I need to attach a backstack but can't seem to get it working.
            recv_messages(mSocket)
        }
        if (ChatUser != null) {
            messageViewModel.relevantMessages(ChatUser).observe(this, Observer { messages ->
                messages?.let { adapter.submitList(it) }
            })
        }

        sendMsgButton.setOnClickListener {
            send_message(textInput.text.toString(), ChatUser.toString())
            messageViewModel.insert(
                ChatMessage(
                    message = textInput.text.toString(),
                    sender = "me",
                    recipient = ChatUser.toString(),
                    timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                        .toString()
                )
            )
            closeKeyboard()
            textInput.text?.clear()
        }
        // add an observer on the LiveData returned by getAlphabetizedMessages.
        // The onChanged method fires when the observed data changes and the activity is in
        // the foreground.

    }

    fun recv_messages(socket: Socket) {
        socket.on("chatmessage") {
            Security.removeProvider("BC")
            Security.addProvider(BouncyCastleProvider())
            Log.i("I", it[0].toString())
            val data = it[0] as JSONObject
            val now: String =
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")).toString()
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
                    timestamp = now
                )
            )
        }
    }

    fun requestPubKey(user: String) {
        SocketHandler.mSocket.emit("getPubKey", user)
    }

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

    private fun getSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

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

    private fun decrypt(aesKey: ByteArray, ivCiphertext: ByteArray): ByteArray {
        val secretKeySpec = SecretKeySpec(aesKey, "AES")
        val iv = ivCiphertext.copyOfRange(0, 12) // Separate IV
        val ciphertext = ivCiphertext.copyOfRange(12, ivCiphertext.size) // Separate ciphertext (the MAC is implicitly separated from the ciphertext)
        val gCMParameterSpec = GCMParameterSpec(128, iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gCMParameterSpec)
        return cipher.doFinal(ciphertext)
    }

    fun send_message(message: String, recipient: String) {
        val encryptedMessage = encrypt(aesKey, message.toByteArray(StandardCharsets.UTF_8))
        val base64encryptedMessage = Base64.encodeToString(encryptedMessage, Base64.DEFAULT)
        val outputArray = arrayOf(recipient, base64encryptedMessage)
        Log.i("e", "The message below is the sent message:")
        Log.i("e", "--$base64encryptedMessage--")
        val decryptedMessage = decrypt(aesKey, encryptedMessage)
        Log.e("e", "The decrypted debug version of the string is here:$decryptedMessage")
        SocketHandler.mSocket.emit("send_to_user", JSONArray(outputArray))
        runOnUiThread {
            var textReader = findViewById<TextView>(R.id.new_message_text)
            val old_text = textReader.text.toString()
        }
    }

    fun closeKeyboard() {
        val imm: InputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm.isActive)
            imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
    }
}
