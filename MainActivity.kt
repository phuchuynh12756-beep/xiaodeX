package com.example.densitytaskbar

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var btnTaskbar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        status = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            text = "Sẵn sàng"
        }

        val btnStart = Button(this).apply {
            text = "BẮT ĐẦU (density 200 + khóa ngang)"
            setOnClickListener { doAsync("Đang áp dụng...", Root::start, "Đã đặt density 200, khóa ngang") }
        }
        val btnStop = Button(this).apply {
            text = "KẾT THÚC (reset density + bỏ khóa)"
            setOnClickListener { doAsync("Đang reset...", Root::stop, "Đã reset density, bỏ khóa ngang") }
        }
        btnTaskbar = Button(this).apply {
            setOnClickListener { toggleTaskbar() }
        }

        root.addView(btnStart)
        root.addView(btnStop)
        root.addView(btnTaskbar)
        root.addView(status)
        setContentView(root)
        refreshTaskbarButton()
    }

    override fun onResume() {
        super.onResume()
        refreshTaskbarButton()
    }

    private fun doAsync(busy: String, action: () -> Boolean, okMsg: String) {
        status.text = busy
        Thread {
            val ok = action()
            runOnUiThread {
                status.text = if (ok) okMsg else "Lỗi: máy chưa cấp quyền root (su)"
            }
        }.start()
    }

    private fun toggleTaskbar() {
        if (TaskbarService.running) {
            stopService(Intent(this, TaskbarService::class.java))
            btnTaskbar.postDelayed({ refreshTaskbarButton() }, 300)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Hãy cấp quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        val i = Intent(this, TaskbarService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        btnTaskbar.postDelayed({ refreshTaskbarButton() }, 300)
    }

    private fun refreshTaskbarButton() {
        btnTaskbar.text = if (TaskbarService.running) "TẮT TASKBAR" else "BẬT TASKBAR"
    }
}
