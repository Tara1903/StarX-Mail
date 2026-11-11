package app.starx.mail.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.starx.mail.MainActivity
import app.starx.mail.R
import app.starx.mail.core.model.Email
import app.starx.mail.core.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val emailChannel = NotificationChannel(
                Constants.CHANNEL_EMAILS_ID,
                context.getString(R.string.channel_emails_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_emails_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            val deliveryChannel = NotificationChannel(
                Constants.CHANNEL_DELIVERY_ID,
                context.getString(R.string.channel_delivery_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_delivery_desc)
            }

            val syncChannel = NotificationChannel(
                Constants.CHANNEL_SYNC_ID,
                context.getString(R.string.channel_sync_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_sync_desc)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannels(listOf(emailChannel, deliveryChannel, syncChannel))
        }
    }

    fun showNewEmailNotification(email: Email) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_EMAIL_ID", email.id)
            putExtra("EXTRA_THREAD_ID", email.threadId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            email.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_EMAILS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(email.from)
            .setContentText(email.subject)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(email.from)
                    .setSummaryText(email.subject)
                    .bigText(email.snippet.ifBlank { email.textBody })
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            notificationManager.notify(email.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Handled when notification permission is denied
        }
    }

    fun showDeliveryStatusNotification(emailId: String, subject: String, status: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("EXTRA_EMAIL_ID", emailId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            emailId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_DELIVERY_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Email $status")
            .setContentText(subject)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            notificationManager.notify(emailId.hashCode() + 1, notification)
        } catch (_: SecurityException) {
        }
    }
}
