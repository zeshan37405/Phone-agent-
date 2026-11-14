package com.usman.agent

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList

class Item(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    val reply: Notification.Action?
)

class NotifService : NotificationListenerService() {

    companion object {
        val items = CopyOnWriteArrayList<Item>()
        @Volatile var connected = false

        // Sends a reply through the notification's own "Reply" button.
        fun sendReply(ctx: Context, item: Item, text: String): Boolean {
            val a = item.reply ?: return false
            val inputs = a.remoteInputs ?: return false
            val intent = Intent()
            val bundle = Bundle()
            for (r in inputs) bundle.putCharSequence(r.resultKey, text)
            RemoteInput.addResultsToIntent(inputs, intent, bundle)
            return try { a.actionIntent.send(ctx, 0, intent); true } catch (e: Exception) { false }
        }
    }

    override fun onListenerConnected() { connected = true }
    override fun onListenerDisconnected() { connected = false }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (sbn.packageName == packageName) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (Agent.isBlocked(sbn.packageName)) return   // financial apps are never read

        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return

        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) { sbn.packageName }
        if (Agent.isBlocked(label)) return

        var reply: Notification.Action? = null
        n.actions?.forEach { a ->
            if (a.remoteInputs?.isNotEmpty() == true) reply = a
        }

        items.removeAll { it.key == sbn.key }
        items.add(0, Item(sbn.key, sbn.packageName, label, title, text, System.currentTimeMillis(), reply))
        while (items.size > 100) items.removeAt(items.size - 1)
    }
}
