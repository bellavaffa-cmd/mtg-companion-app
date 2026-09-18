package com.mtgcompanion.app.data.social

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mtgcompanion.app.BuildConfig
import com.mtgcompanion.app.MainActivity
import com.mtgcompanion.app.MtgCompanionApplication
import com.mtgcompanion.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Phone notifications for friend requests and trades. The server's push function sends a data
 * message through Firebase Cloud Messaging; [PushService] turns it into a notification, and a tap
 * opens Friends or Trades. Each signed-in device registers its FCM token with the server; signing
 * out deletes the token, so the device stops getting that account's notifications.
 *
 * Needs google-services.json at build time (see app/build.gradle); without it this is all off.
 */
object PushNotifications {
    /** Extra on MainActivity's intent: the screen a tapped notification opens ("friends" / "trades"). */
    const val EXTRA_OPEN = "open"

    private const val PREFS = "push_notifications"
    private const val KEY_ENABLED = "enabled"
    private const val CHANNEL_FRIENDS = "friends"
    private const val CHANNEL_TRADES = "trades"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Whether this build can get notifications at all (it has a Firebase project). */
    val available: Boolean get() = BuildConfig.FIREBASE_APP_ID.isNotBlank()

    private val _enabled = MutableStateFlow(false)
    /** The user's switch for this device (on unless they turned it off). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Sets up Firebase and the notification channels; called once from the Application. */
    fun init(context: Context) {
        _enabled.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)
        if (!available) return
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build()
            )
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_FRIENDS, "Friend requests", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Someone asks to be friends, or says yes"
        })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_TRADES, "Trades", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A trade arrives, or yours is answered"
        })
    }

    /** Android 13+ asks before an app may notify; earlier versions always may. */
    fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Turns this device's notifications on or off for the signed-in account. */
    fun setEnabled(context: Context, on: Boolean, social: SocialRepository) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
        if (on) syncToken(context, social) else scope.launch { forgetToken(social) }
    }

    /** Signed in (or just allowed): make sure the server knows where to reach this device. */
    fun syncToken(context: Context, social: SocialRepository) {
        if (!available || !_enabled.value || !permissionGranted(context) || social.userId == null) return
        scope.launch {
            runCatching { social.api.registerPushToken(FirebaseMessaging.getInstance().token.await()) }
        }
    }

    /** Firebase handed this device a new token. */
    fun onNewToken(context: Context, token: String) {
        val social = (context.applicationContext as MtgCompanionApplication).socialRepository
        if (!_enabled.value || !permissionGranted(context) || social.userId == null) return
        scope.launch { runCatching { social.api.registerPushToken(token) } }
    }

    /** Switched off: the server forgets this device, and the token itself is thrown away. */
    private suspend fun forgetToken(social: SocialRepository) {
        if (!available) return
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            if (social.userId != null) social.api.unregisterPushToken(token)
            FirebaseMessaging.getInstance().deleteToken().await()
        }
    }

    /**
     * Signed out: the token is thrown away, so this device stops getting the old account's
     * notifications (the server drops the dead token the next time it tries it). A new one is made
     * when someone signs in.
     */
    fun onSignedOut() {
        if (!available) return
        scope.launch { runCatching { FirebaseMessaging.getInstance().deleteToken().await() } }
    }

    /** Shows one notification from a push message's data. */
    fun show(context: Context, data: Map<String, String>) {
        if (!permissionGranted(context) || !_enabled.value) return
        val open = if (data["open"] == "trades") "trades" else "friends"
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, open)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tag = data["tag"] ?: open
        val tap = PendingIntent.getActivity(context, tag.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val body = data["body"].orEmpty()
        val notification = NotificationCompat.Builder(context, if (open == "trades") CHANNEL_TRADES else CHANNEL_FRIENDS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFE6B45E.toInt())
            .setContentTitle(data["title"] ?: "MTG Companion")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag, 0, notification)
        } catch (e: SecurityException) {
            // Permission withdrawn between the check and here: nothing to show.
        }
        // The badge on Home/Friends catches up too.
        (context.applicationContext as MtgCompanionApplication).socialRepository.refreshInboxInBackground()
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) cont.resume(task.result) else cont.resumeWithException(task.exception ?: IllegalStateException("Firebase task failed"))
    }
}

/** Receives Firebase messages for this app. */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        PushNotifications.show(applicationContext, message.data)
    }

    override fun onNewToken(token: String) {
        PushNotifications.onNewToken(applicationContext, token)
    }
}
