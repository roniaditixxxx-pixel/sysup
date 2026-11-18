package com.venice.rat.core

import rikka.shizuku.Shizuku
import java.io.BufferedReader

object ShizukuSh {
    fun available(): Boolean = runCatching {
        Shizuku.pingBinder() && !Shizuku.isPreV11()
    }.getOrDefault(false)

    fun run(cmd: String): String? = runCatching {
        val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
        val sb = StringBuilder()
        BufferedReader(p.inputStream.reader()).useLines { l -> l.forEach { sb.appendLine(it) } }
        BufferedReader(p.errorStream.reader()).useLines { l -> l.forEach { sb.appendLine(it) } }
        p.waitFor()
        sb.toString().ifBlank { "(ok)" }
    }.getOrNull()
}
