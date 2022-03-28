package com.example.hushchat

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.textfield.TextInputEditText
import io.socket.client.Socket
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.provider.Settings.Secure
import android.util.Base64
import android.widget.Button
import android.widget.RadioButton
import com.example.hushchat.MessagesApplication.Companion.activityName
import com.example.hushchat.MessagesApplication.Companion.privateKey
import com.example.hushchat.MessagesApplication.Companion.publicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.ArrayList
import kotlin.concurrent.fixedRateTimer

/**
 * Main activity class for the app. Handles all the startup complexity and also ongoing background
 * transactions with the server.
 */
class MainActivity : AppCompatActivity() {
//    list of currently connected users.
//    Updates whenever new contacts are broadcast from the server
    val connected_users = ArrayList<String>()

//    represents the public key of whoever sent the most recent message. Lateinit so it doesn't
//    require initialisation value.
    private lateinit var senderPubKey:PublicKey

//    initialises our viewModel, which is responsible for the storage and display of message data
    private val messageViewModel: MessageViewModel by viewModels {
        MessageViewModelFactory((application as MessagesApplication).repository)
    }

//    initialises notificationNumber at 0, and will be incremented on each notification generation.
    var notificationNumber = 1

//    initialises our delete/self destruct variable. This will be shepherded into a shared prefs
//    file to provide persistence of this choice.
    var deleteDuration = 0

//    shared prefs file mentioned above. Stores the self destruct duration.
    lateinit var sharedPref:SharedPreferences

    /**
     * Create the NotificationChannel - I think this only works on API version 26+, so might
     * potentially cause issues on older android versions
     */
    private fun createNotificationChannel() {
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
//        ensures that the notifications will actually pop up for all users by default unless
//        they've messed with their device settings.
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel("1", name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * This allows the notification handler to work out when the user has minimised the app and send
     * notifications as appropriate.
     */
    override fun onPause() {
        super.onPause()
        activityName = "//pause"
    }

    /**
     * Activity create method. If a new installation, prompts the user to select a message self-destruct
     * duration. After this, or if there's already a shared preference indicating a duration has
     * been selected, move to either the username selection content view or the contacts list if an
     * existing user.
     */
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        activityName = "mainactivity"
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        deleteDuration = sharedPref.getInt("deleteDuration", 0)
        Log.e("e", "Imported shared preference for delete duration: "+sharedPref.getInt("deleteDuration", 0).toString())
        if(sharedPref.getInt("deleteDuration", 0) != 0){
            setContentView(R.layout.activity_main)
            afterDeleteDuration()
        }else {
            setContentView(R.layout.activity_get_duration)
        }
    }

    /**
     * Handles the process after the deletion duration has been picked (or if a duration already
     * exists in shared preferences. Initialises notification channel, creates the daemon process
     * which deletes messages according to preferences.
     */
    fun afterDeleteDuration(){
        createNotificationChannel()
//        create daemon process which uses delete duration to automatically delete messages at
//        appropriate time. Refreshes every second.
        fixedRateTimer("timer", true, 0, 1000){
            deleteAccordingToTimeFrame(Date().time - deleteDuration * 1000)
        }
//        initialise socket connection
        SocketHandler.setSocket()
        SocketHandler.establishConnection()
        val mSocket = SocketHandler.mSocket
//        dirty sleep call to make things work a bit smoother
        Thread.sleep(50)
//        send unique id to server
        getUniqueId(mSocket)
//        manage new users when they log in
        handle_new_users()
//        receive and handle sender pub key when appropriate
        receivePubKey(mSocket)
//        handler for message receipt
        recv_messages(mSocket)

//        deal with creation of user name, broadcast to server and move to contacts screen
        if (!existingUser(mSocket)) {
            val textInputUsername = findViewById<TextInputEditText>(R.id.text_input_username)
            val setUsernameButton = findViewById<Button>(R.id.username_button)
            setUsernameButton.setOnClickListener {
                val typedData = textInputUsername.text.toString()
                SocketHandler.send_Username(typedData)
                textInputUsername.text?.clear()
                closeKeyboard()
                setUsernameButton.visibility = View.GONE
                textInputUsername.visibility = View.GONE
                send_pub_key()
                val intent = Intent(this, Contacts::class.java).apply {
                    putExtra("com.example.hushchat.message", "Message")
                }
                startActivity(intent)
            }
        }
        else{
            update_pub_key()
        }
    }

    fun onRadioButtonClicked(view:View){
        if(view is RadioButton){
            val chosenOption = view.isChecked

            when (view.getId()){
                R.id.onemin ->
                    if(chosenOption){
                        deleteDuration = 60
                    }
                R.id.fivemins ->
                    if(chosenOption){
                        deleteDuration = 300
                    }
                R.id.thirtymins ->
                    if(chosenOption){
                        deleteDuration = 1800
                    }
                R.id.oneday ->
                    if(chosenOption){
                        deleteDuration = 86400
                    }
            }
        }
        Log.e("e", deleteDuration.toString())
        with(sharedPref.edit()){
            putInt("deleteDuration", deleteDuration)
            commit()
        }
        setContentView(R.layout.activity_main)
        afterDeleteDuration()
    }

    /**
     * manage self destruct message mechanism.
     */
    private fun deleteAccordingToTimeFrame(dateTime: Long){
        messageViewModel.deleteAccordingToTimeFrame(dateTime)
    }

    /**
     * generates a new keypair when required
     */
    private fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp521r1"))
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Generates a shared secret using elliptic curve diffie hellman algorithm from sender pub key
     * and recipient priv key. This is the asymmetric component of the ECIES approach I'm using.
     */
    private fun getSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

    /**
     * Generates an AES key from a given shared secret. This is the symmetric component of the ECIES
     * approach. The generated AES key is used to both encrypt and decrypt the message.
     * The decrypt component is in the function below, and the encrypt is within ChatWindow.kt
     */
    private fun getAESKey(sharedSecret: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-512")
        return digest.digest(sharedSecret).copyOfRange(0, 32)
    }


    /**
     * Takes in an aes key and the byte array containing the encrypted plain text. Decrypts the msg
     * and returns a bytearray containing the final decrypted text.
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
     * Generates a new keypair for a user that is reconnecting. This was a deliberate choice
     * rather than storing public/private key locally. There is a means of storing keys securely in
     * android (i.e. AndroidKeyStore) but it does not support ECDH keys. Generating a new pair on
     * demand requires propagation throughout the system to the server and other clients, but allows
     * greater flexibility.
     */
    fun update_pub_key(){
        val keyPair = generateKeyPair()
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
        privateKey = keyPair.private
        publicKey = keyPair.public
        val encodedPubKey = Base64.encode(publicKey.encoded, Base64.DEFAULT)
        SocketHandler.mSocket.emit("update_pub_key", encodedPubKey)
    }

    /**
     * Generates a public/private keypair, then encodes in base 64 for sending to the server
     */
    fun send_pub_key() {
        val keyPair = generateKeyPair()
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
        privateKey = keyPair.private
        publicKey = keyPair.public
        val encodedPubKey = Base64.encode(publicKey.encoded, Base64.DEFAULT)
        SocketHandler.mSocket.emit("pubKey", encodedPubKey)
    }


    /**
     * handle the connection of new users
     */
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

    /**
     * helper to request sender pub key
     */
    fun requestPubKey(user: String) {
        SocketHandler.mSocket.emit("getPubKey", user)
    }

    /**
     * Gets the sender's serialised pub key for a given message and deserialises the content,
     * reconstructs the key from the X509 key spec.
     */
    fun receivePubKey(socket: Socket) {

//    BouncyCastleProvider needs to be replaced with an improved version as Stock Android
//    BouncyCastle provider does not support Elliptic Curve encryption. This is reflected in
//    build.gradle.
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
//    event handler for receiving a public key
        socket.on("pubKeyResponse") {
            Log.e("e", "Received pubKeyResponse...")
            Log.e("e", it[0].toString())
//            decode base 64
            val byteArrayPubKey:ByteArray = it[0] as ByteArray
            val returnedBase64Decoded = Base64.decode(byteArrayPubKey, Base64.DEFAULT)
//            deserialise and reconstruct the key from keyspec
            val keyspec = X509EncodedKeySpec(returnedBase64Decoded)
            val keyFactory = KeyFactory.getInstance("EC")
            val pubKeyReturned = keyFactory.generatePublic(keyspec)
            Log.e("e", "here is the initial public key"+publicKey.toString())
            Log.e("e", "here is the returned public key within mainactivity"+pubKeyReturned.toString())
            senderPubKey = pubKeyReturned
        }
    }

    /**
     * This is a fairly monstrous function, so I apologise to the reader.
     * It handles the receipt of messages sent to the client from other users (via the server).
     * There is a similar method within ChatWindow.kt which handles the situation when the user
     * reaches this intent via a notification. This function takes an encrypted message from the
     * server, and decrypts it locally using this device's private key.
     */
    fun recv_messages(socket: Socket) {
//            Event handler for receiving chat messages
        socket.on("chatmessage") {
//            BouncyCastleProvider needs to be replaced with an improved version as Stock Android
//            BouncyCastle provider does not support Elliptic Curve encryption. This is reflected in
//            build.gradle
            Security.removeProvider("BC")
            Security.addProvider(BouncyCastleProvider())

//            generate timestamp
            val now: String =
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")).toString()

//            deconstruct the json object sent by the server containing message & metadata
            Log.i("I", it[0].toString())
            val data = it[0] as JSONObject
            val sender = data.getString("sender")
            val pubkeyBase64 = data.getString("pubkey")
            Log.i("i", "Public key in base 64 from message sender: $pubkeyBase64")
            val decodeBase64pubKey = Base64.decode(pubkeyBase64, Base64.DEFAULT)
            Log.i("i", "initialb64: "+decodeBase64pubKey.toString())
            Log.i("i", "our base64 key is of type: "+decodeBase64pubKey.javaClass.name)

//            this is to deserialize the b64 encoded public key sent from the server
            val keyspec = X509EncodedKeySpec(decodeBase64pubKey)
            val keyFactory = KeyFactory.getInstance("EC")
            val pubKeyReturned = keyFactory.generatePublic(keyspec)
            Log.v("v", "Our generated public key for the other user is:$pubKeyReturned")

//            get the public key of the sender from the server
            requestPubKey(sender)

//            remove trailing newline that python loves to helpfully introduce
            val message = data.getString("message").trim()
            Log.v("v", "The message below is the received message")
            Log.v("v", "--"+message+"--")

//            initial base 64 decoding to leave us with ECIES encrypted version of message
            val base64DecodedMessage = Base64.decode(message, Base64.DEFAULT)

//            generate shared secret with sender's public key and recipient's priv key.
//            This is then used to generate an AES key which can decrypt the message to plain text
            val sharedSecret = getSharedSecret(privateKey,pubKeyReturned)
            val responseAesKey = getAESKey(sharedSecret)

//            success! we've got our plain text response
            val decryptedMessage = decrypt(responseAesKey, base64DecodedMessage)
                .toString(StandardCharsets.UTF_8)
            Log.i("i", "Encrypted message: $message")
            Log.i("i", "Decrypted text: $decryptedMessage")

//            add message to viewmodel
            messageViewModel.insert(
                ChatMessage(
                    message = decryptedMessage,
                    sender = sender,
                    recipient = "me",
                    timestamp = now,
                    messageTime = Date().time
                )
            )
//            intent to allow us to immediately open the relevant chatWindow activity for the user who sent the message.
            var notifIntent = Intent(this, ChatWindow::class.java).apply {
                putExtra("ChatUser", sender)
//                this boolean is added so that if we open a chat window from a push notification we
//                can actually receive messages from the other user.
                putExtra("notif", true)
            }

//            the below code turns the above intent into a "PendingIntent" and allows us
//            to embed it within a notification as appropriate.

            val resultPendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
                addNextIntentWithParentStack(notifIntent)
                getPendingIntent(
                    0,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
//
//            the below code is the builder object which allows us to send a notification on
//            the notification channel outlined at the beginning of this file.
            var builder = NotificationCompat.Builder(this, "1")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(sender)
                .setContentText(decryptedMessage)
                .setContentIntent(resultPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

//            actually send the notification.
            Log.e("e", "the current window is: $activityName")
            if (activityName != sender) {
                with(NotificationManagerCompat.from(this)) {
                    notify(notificationNumber, builder.build())
                }
//                counter needs to be incremented so we can use more than one
//                notification if multiple messages come in from different users.
                notificationNumber++
            }
        }
    }

    /**
     * gets a unique identifier for our device which is sent to server to allow distinction between
     * users.
     */
    private fun getUniqueId(socket: Socket) {
        val uniqueId = Secure.getString(contentResolver, Secure.ANDROID_ID)
        Log.i("i", "UNIQUE ID = $uniqueId")
        socket.emit("uid", "$uniqueId")
    }

    /**
     * event handler for managing client behaviour when server specifies whether this client has
     * connected previously. If user has previously connected then we need to generate a new keypair
     * and broadcast the public key to the server to allow communication
     */
    private fun existingUser(socket: Socket): Boolean {
        socket.on("existingUserCheck") {
            Log.i("i", it[0].toString())
            if (it[0].equals("true")) {
                Log.i("i", "user has already got an existing account")
                update_pub_key()
                val intent = Intent(this, Contacts::class.java).apply {
                    putExtra("com.example.hushchat.message", "Message")
                }
                startActivity(intent)
            }
        }
        return false
    }

    /**
     * helper function to close keyboard - imm.toggleSoftInput is soon to be deprecated
     * functionality but the alternative didn't work quite as nicely.
     */
    fun closeKeyboard() {
        val imm: InputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imm.isActive)
            imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
    }
}