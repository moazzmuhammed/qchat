package com.example.qchat.fcmservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.qchat.R
import com.example.qchat.model.User
import com.example.qchat.ui.main.MainActivity
import com.example.qchat.utils.AesUtils
import com.example.qchat.utils.Constant
import com.example.qchat.utils.CryptoUtils
import com.example.qchat.utils.decodeBase64
import kotlin.random.Random

class MessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "onNewToken: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "onMessageReceived: ${remoteMessage.notification?.body}")

        val encryptedMessage = remoteMessage.data[Constant.KEY_MESSAGE] ?: ""
        val user = getUser(remoteMessage)
        val publicKeyString = remoteMessage.data["public_key"]?.toByteArray()
        val signatureString = remoteMessage.data["signature"]?.toByteArray()
        val aesKeyBase64 = remoteMessage.data["aes_key"] ?: ""
        val aesKey = AesUtils.base64ToKey(aesKeyBase64)

        val decryptedMessage = AesUtils.decryptMessage(encryptedMessage, aesKey)

        Log.d("AES", "Encrypted Message: $encryptedMessage")
        Log.d("AES", "Decrypted Message: $decryptedMessage")

        if (publicKeyString != null && signatureString != null && aesKeyBase64.isNotEmpty()) {
            val aesKey = AesUtils.base64ToKey(aesKeyBase64)

            val decryptedMessage = AesUtils.decryptMessage(encryptedMessage, aesKey)

            val isVerified = CryptoUtils.verifySignature(publicKeyString, decryptedMessage.toByteArray(), signatureString)

            if (isVerified) {
                sendNotification(user, decryptedMessage, getUserPendingIntent(user))
            } else {
                Log.e("FCM", "Invalid signature. Message verification failed.")
            }
        } else {
            Log.e("FCM", "Missing public key, signature, or AES key in message data.")
        }
    }

    private fun getUser(remoteMessage: RemoteMessage) =
        User(
            name = remoteMessage.data[Constant.KEY_NAME] ?: "",
            id = remoteMessage.data[Constant.KEY_USER_ID] ?: "",
            token = remoteMessage.data[Constant.KEY_FCM_TOKEN]
        )

    private fun getUserPendingIntent(user: User): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).also {
                it.action = Constant.ACTION_SHOW_CHAT_FRAGMENT
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK and Intent.FLAG_ACTIVITY_CLEAR_TASK
                it.putExtra(Constant.KEY_USER, user)

            },
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        )

    private fun sendNotification(user: User, message: String, contentIntent: PendingIntent) {

        val notificationId = Random.nextInt()

        val notificationBuilder = NotificationCompat.Builder(this, Constant.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(user.name)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }

        notificationManager.notify(notificationId,notificationBuilder.build())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            Constant.NOTIFICATION_CHANNEL_ID,
            Constant.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }
}