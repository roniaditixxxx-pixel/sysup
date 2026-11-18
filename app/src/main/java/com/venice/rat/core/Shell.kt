package com.venice.rat.core

import java.io.BufferedReader

object Shell {
    @Volatile private var root: Boolean? = null

    fun isRoot(): Boolean {
        root?.let { return it }
        val r = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            p.waitFor() == 0 && p.inputStream.bufferedReader().readText().contains("uid=0")
        }.getOrDefault(false)
        root = r
        return r
    }

    fun run(cmd: String, forceRoot: Boolean = false, timeout: Long = 20000): String {
        val useRoot = forceRoot && isRoot()
        if (!useRoot && ShizukuSh.available()) ShizukuSh.run(cmd)?.let { return it }
        val argv = if (useRoot) arrayOf("su", "-c", cmd) else arrayOf("/system/bin/sh", "-c", cmd)
        return runCatching {
            val p = Runtime.getRuntime().exec(argv)
            val sb = StringBuilder()
            val t = Thread {
                BufferedReader(p.inputStream.reader()).useLines { l -> l.forEach { sb.appendLine(it) } }
            }.apply { start() }
            Thread {
                BufferedReader(p.errorStream.reader()).useLines { l -> l.forEach { sb.appendLine(it) } }
            }.start()
            p.waitFor()
            t.join(3000)
            sb.toString().ifBlank { "(no output)" }
        }.getOrElse { "ERR: ${it.message}" }
    }
}
