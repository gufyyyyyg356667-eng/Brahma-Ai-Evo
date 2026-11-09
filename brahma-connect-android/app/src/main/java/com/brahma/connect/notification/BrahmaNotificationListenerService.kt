package com.brahma.connect.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.ArrayDeque

data class NotificationEntry(
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
)

/**
 * Keeps a short in-memory history of recent notifications so the desktop
 * assistant can read them on request. Nothing is written to disk.
 * The user must enable notification access for Brahma Connect in Android settings.
 */
class BrahmaNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
    }

    override fun onListenerDisconnected() {
        connected = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName == packageName) return
        val extras = notification.notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        synchronized(lock) {
            buffer.addFirst(NotificationEntry(notification.packageName, title, text, notification.postTime))
            while (buffer.size > MAX_HISTORY) {
                buffer.removeLast()
            }
        }
    }

    companion object {
        private const val MAX_HISTORY = 50
        private val lock = Any()
        private val buffer = ArrayDeque<NotificationEntry>()

        @Volatile
        private var connected = false

        fun isConnected(): Boolean = connected

        fun recent(limit: Int): List<NotificationEntry> = synchronized(lock) {
            buffer.take(limit)
        }
    }
}
