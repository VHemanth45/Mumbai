package com.citymemory.util

import android.Manifest
import android.app.Notification
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
import com.citymemory.MainActivity
import com.citymemory.R

/**
 * The one interruption this app is allowed to make.
 *
 * Behind an interface for the same reason [LocationSource] and
 * [NavigationLauncher] are: it keeps `NotificationManager` out of everything
 * that decides *whether* to notify, so that decision can be tested on the JVM
 * with a notifier that records rather than posts.
 */
interface VisitNotifier {

    /** Whether a notification posted right now would actually be shown. */
    fun canNotify(context: Context): Boolean

    /**
     * Asks whether the user was at [placeName].
     *
     * One notification, replaced rather than stacked — see the fixed id in the
     * implementation. Somebody who has been out all day should come back to one
     * question, not to nine.
     */
    fun askAboutVisit(context: Context, placeName: String, pendingCount: Int)

    /** Takes the question down, once it has been answered somewhere else. */
    fun clear(context: Context)
}

class AndroidVisitNotifier : VisitNotifier {

    override fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    override fun askAboutVisit(context: Context, placeName: String, pendingCount: Int) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val others = pendingCount - 1
        val text = if (others > 0) {
            context.resources.getQuantityString(
                R.plurals.suggestion_notification_more,
                others,
                others,
            )
        } else {
            context.getString(R.string.suggestion_notification_body)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_city_memory)
            .setContentTitle(context.getString(R.string.suggestion_notification_title, placeName))
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            // Quiet on purpose. This is a question about something that already
            // happened; nothing about it is urgent, and a feature that buzzes
            // in someone's pocket every time they sit down for lunch gets
            // switched off within a week.
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        // Guarded: `canNotify` has already checked the permission, but the
        // linter cannot see through the interface.
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    override fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * Opens the app on Explore, where the card is.
     *
     * There is deliberately no deep link to a dedicated confirmation screen.
     * The suggestion is answered on a card in the place the user already looks
     * — over the map that is about to light up — so tapping the notification
     * only has to bring the app forward. That also means a suggestion answered
     * from the card and one answered from the notification arrive at the same
     * screen, rather than being two flows that can drift apart.
     */
    private fun openApp(context: Context): PendingIntent {
        // NEW_TASK and nothing else. CLEAR_TOP would destroy the running
        // Activity and rebuild it — with `singleTop` in the manifest, a bare
        // NEW_TASK is delivered to the live instance instead, which is the
        // whole point of pairing the two.
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.suggestion_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.suggestion_channel_description)
                setShowBadge(true)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "visit_suggestions"

        /**
         * Fixed, so a second question replaces the first rather than stacking.
         * The count in the body carries the rest.
         */
        const val NOTIFICATION_ID = 1
    }
}

/** Records instead of posting, for tests and for builds with no notifier. */
object NoVisitNotifier : VisitNotifier {
    override fun canNotify(context: Context): Boolean = false
    override fun askAboutVisit(context: Context, placeName: String, pendingCount: Int) = Unit
    override fun clear(context: Context) = Unit
}
