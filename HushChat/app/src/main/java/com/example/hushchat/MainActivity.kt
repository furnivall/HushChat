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
import android.util.Base64
import com.example.hushchat.MessagesApplication.Companion.activityName
import com.example.hushchat.MessagesApplication.Companion.privateKey
import com.example.hushchat.MessagesApplication.Companion.publicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {
    val connected_users = ArrayList<String>()
    private lateinit var senderPubKey:PublicKey
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

        receivePubKey(mSocket)
        recv_messages(mSocket)

        if (!existingUser(mSocket)) {
            set_username_button.visibility = View.VISIBLE
            text_input_username.visibility = View.VISIBLE
            set_username_button.setOnClickListener {




                val typedData = text_input_username.text.toString()
                SocketHandler.send_Username(typedData)
                text_input_username.text?.clear()
                closeKeyboard()
                set_username_button.visibility = View.GONE
                text_input_username.visibility = View.GONE
                send_pub_key()
                val intent = Intent(this, Contacts::class.java).apply {
                    putExtra("com.example.hushchat.message", "Message")
                }

                startActivity(intent)
            }
        }
    }
    private fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp521r1"))
        return keyPairGenerator.generateKeyPair()
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

    fun send_pub_key() {
        val keyPair = generateKeyPair()
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
        privateKey = keyPair.private
        publicKey = keyPair.public
        val encodedPubKey = Base64.encode(publicKey.encoded, Base64.DEFAULT)
        SocketHandler.mSocket.emit("pubKey", encodedPubKey)
        val decodedPubKey = Base64.decode(encodedPubKey, Base64.DEFAULT)
        Log.e("e", "Check here for decodedPubKey"+decodedPubKey.toString())
        val keyspec = X509EncodedKeySpec(decodedPubKey)
        val keyFactory = KeyFactory.getInstance("EC")
        val pubKeyReturned = keyFactory.generatePublic(keyspec)
        Log.e("e", "here is the initial public key"+publicKey.toString())
        Log.e("e", "here is the returned public key"+pubKeyReturned.toString())

    }

    fun send_pub_key_old() {
//        TODO write clearly about this:
//        https://stackoverflow.com/questions/64776709/kotlin-ecc-encryption
        val plaintext = "string"
//        // Generate Keys
        val keyPairA = generateKeyPair()
//        val keyPairB = generateKeyPair()
//
//// Generate shared secrets
//        val sharedSecretA = getSharedSecret(keyPairA.private, keyPairB.public)
//        val sharedSecretB = getSharedSecret(keyPairB.private, keyPairA.public)
//
//// Generate AES-keys
//        val aesKeyA = getAESKey(sharedSecretA)
//        val aesKeyB = getAESKey(sharedSecretB)
//
//// Encryption (WLOG by A)
//        val plaintextA = "The quick brown fox jumps over the lazy dog".toByteArray(StandardCharsets.UTF_8)
//        val ciphertextA = encrypt(aesKeyA, plaintextA)
//
//// Decryption (WLOG by B)
//        val plaintextB = decrypt(aesKeyB, ciphertextA)
//        Log.e("e", String(plaintextB, StandardCharsets.UTF_8))

        val bytePlainText = plaintext.toByteArray(StandardCharsets.UTF_8)
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
        val keyPairGenerator = KeyPairGenerator.getInstance("ECDH")
        keyPairGenerator.initialize(ECGenParameterSpec("secp521r1"))
        val pair = keyPairGenerator.genKeyPair()

        val encodedPubKey = Base64.encode(pair.public.encoded, Base64.DEFAULT)
        Log.e("e", "Encoded base64 input $encodedPubKey")
        Log.e("e", "Decoded base64 input ${Base64.decode(encodedPubKey, Base64.DEFAULT)}")
        Log.e("e", "Stringified base64 decoded : ${Base64.decode(encodedPubKey, Base64.DEFAULT).toString(
            Charset.defaultCharset())}")
        Log.e("e", "--" + encodedPubKey + "--")
        Log.e("e", pair.public.format)
        SocketHandler.mSocket.emit("pubKey", encodedPubKey)
        privateKey = pair.private
        publicKey = pair.public
        Log.e("e", "Public key transmitted to server: ${publicKey.encoded}")
//
//        //encrypt with second pub key
//        val pair2 = keyPairGenerator.genKeyPair()
//        val cipherOwn = Cipher.getInstance("ECIES")
//        cipherOwn.init(Cipher.ENCRYPT_MODE, pair2.public)
//        val cipherTextOwn = cipherOwn.doFinal(bytePlainText)
//
//        //decrypt with first priv key
//        val decCipher = Cipher.getInstance("ECIES")
//        decCipher.init(Cipher.DECRYPT_MODE, pair2.private)
//        val decrypted_text = decCipher.doFinal(cipherTextOwn).toString(StandardCharsets.UTF_8)
//        Log.i("e",decrypted_text)


//        //encrypt
//        val cipherEnc = Cipher.getInstance("ECIES")
//        cipherEnc.init(Cipher.ENCRYPT_MODE, pair.public)
//        val cipherText = cipherEnc.doFinal(bytePlainText)
//        Log.e("e", "Encrypted text: ${cipherText.toString()}")
//
//        //decrypt
//        val cipherDec = Cipher.getInstance("ECIES")
//        cipherDec.init(Cipher.DECRYPT_MODE, pair.private)
//        val decryptedText = cipherDec.doFinal(cipherText)
//        Log.e("e", "Decrypted text: ${decryptedText.decodeToString()}")

    }

    fun getPubKey(user: String) {
        SocketHandler.mSocket.emit("getPubKey", user)
        SocketHandler.mSocket.on("pubKeyResponse") {
            Log.e("e", it[0].toString())
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

    fun requestPubKey(user: String) {
        SocketHandler.mSocket.emit("getPubKey", user)
    }

    fun receivePubKey(socket: Socket) {
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
        socket.on("pubKeyResponse") {
            Log.e("e", "Received pubKeyResponse...")
            Log.e("e", it[0].toString())
            val byteArrayPubKey:ByteArray = it[0] as ByteArray
            val returnedBase64Decoded = Base64.decode(byteArrayPubKey, Base64.DEFAULT)
            val keyspec = X509EncodedKeySpec(returnedBase64Decoded)
            val keyFactory = KeyFactory.getInstance("EC")
            val pubKeyReturned = keyFactory.generatePublic(keyspec)
            Log.e("e", "here is the initial public key"+publicKey.toString())
            Log.e("e", "here is the returned public key within mainactivity"+pubKeyReturned.toString())
            senderPubKey = pubKeyReturned
        }
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
            val pubkeyBase64 = data.getString("pubkey")
            Log.e("e", "Public key in base 64 from message sender: $pubkeyBase64")
            val decodeBase64pubKey = Base64.decode(pubkeyBase64, Base64.DEFAULT)
            Log.e("e", "initialb64: "+decodeBase64pubKey.toString())
            Log.e("e", "our base64 key is of type: "+decodeBase64pubKey.javaClass.name)
            val keyspec = X509EncodedKeySpec(decodeBase64pubKey)
            val keyFactory = KeyFactory.getInstance("EC")
            val pubKeyReturned = keyFactory.generatePublic(keyspec)
            Log.e("e", "Our generated public key for the other user is:$pubKeyReturned")
            requestPubKey(sender)
            val message = data.getString("message").trim()
            Log.e("e", "The message below is the received message")
            Log.e("e", "--"+message+"--")
            val base64DecodedMessage = Base64.decode(message, Base64.DEFAULT)


            val sharedSecret = getSharedSecret(privateKey,pubKeyReturned)
            val responseAesKey = getAESKey(sharedSecret)
            val decryptedMessage = decrypt(responseAesKey, base64DecodedMessage).toString(StandardCharsets.UTF_8)
            Log.e("e", "Encrypted message: $message")
            Log.e("e", "Decrypted text: $decryptedMessage")
            messageViewModel.insert(
                ChatMessage(
                    message = decryptedMessage,
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
                .setContentText(decryptedMessage)
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