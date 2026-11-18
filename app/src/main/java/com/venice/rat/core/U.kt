package com.venice.rat.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object U {
    fun dir(ctx: Context, name: String): File =
        File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, name).apply { mkdirs() }

    fun size(b: Long): String = when {
        b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> "%.2f MB".format(b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> "%.1f KB".format(b / 1024.0)
        else -> "$b B"
    }

    fun ts(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    fun uri(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fp", f)

    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun batteryOpt(ctx: Context) {
        runCatching {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun sd(): File = Environment.getExternalStorageDirectory()

    fun isAllFiles(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true
}
