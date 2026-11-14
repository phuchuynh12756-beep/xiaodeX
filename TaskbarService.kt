package com.example.densitytaskbar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class TaskbarService : Service() {

    companion object {
        @Volatile var running = false
    }

    private lateinit var wm: WindowManager
    private lateinit var bubble: ImageView
    private var bar: View? = null          // dock giữa đáy màn hình
    private var drawer: View? = null       // lưới tất cả ứng dụng

    private data class AppItem(val label: String, val pkg: String, val cls: String)
    private var allApps: List<AppItem> = emptyList()

    private val prefs by lazy { getSharedPreferences("taskbar", Context.MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun overlayParams(w: Int, h: Int) =
        WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

    /** Nút lưới 9 chấm giống taskbar */
    private inner class GridDots(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            for (i in 1..3) for (j in 1..3) {
                c.drawCircle(w / 4f * i, h / 4f * j, w * 0.065f, p)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        loadApps()
        createBubble()
    }

    override fun onDestroy() {
        running = false
        runCatching { wm.removeView(bubble) }
        hideBar(); hideDrawer()
        super.onDestroy()
    }

    // ---------- foreground notification ----------
    private fun startAsForeground() {
        val id = "taskbar"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(id, "Taskbar", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, id)
            .setContentTitle("Taskbar đang chạy")
            .setSmallIcon(android.R.drawable.ic_menu_sort_by_size)
            .build()
        startForeground(1, n)
    }

    // ---------- danh sách ứng dụng ----------
    private fun loadApps() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        allApps = packageManager.queryIntentActivities(i, 0)
            .map { AppItem(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .filter { it.pkg != packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Lần đầu: ghim sẵn vài app quen thuộc nếu máy có */
    private fun pinned(): MutableSet<String> {
        if (!prefs.contains("pins")) {
            val defaults = listOf(
                "com.android.vending", "com.google.android.dialer", "com.android.dialer",
                "com.google.android.youtube", "com.android.chrome"
            ).filter { pkg -> allApps.any { it.pkg == pkg } }.toSet()
            prefs.edit().putStringSet("pins", defaults).apply()
            return defaults.toMutableSet()
        }
        return prefs.getStringSet("pins", emptySet())!!.toMutableSet()
    }

    private fun togglePin(pkg: String) {
        val p = pinned()
        if (!p.remove(pkg)) p.add(pkg)
        prefs.edit().putStringSet("pins", p).apply()
        // vẽ lại dock nếu đang mở
        if (bar != null) { hideBar(); showBar() }
    }

    private fun icon(pkg: String) = try {
        packageManager.getApplicationIcon(pkg)
    } catch (e: PackageManager.NameNotFoundException) {
        packageManager.defaultActivityIcon
    }

    private fun launch(a: AppItem) {
        Root.launchFreeform(a.pkg, a.cls)
        hideDrawer()          // dock vẫn giữ nguyên như taskbar thật
    }

    // ---------- bong bóng nổi trong suốt ----------
    private fun createBubble() {
        bubble = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            setColorFilter(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(90, 30, 30, 30))   // trong suốt
            }
        }
        val lp = overlayParams(dp(48), dp(48)).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = dp(200)
        }

        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                    lp.x = sx + dx; lp.y = sy + dy
                    wm.updateViewLayout(bubble, lp)
                }
                MotionEvent.ACTION_UP -> if (!moved) toggleBar()
            }
            true
        }
        wm.addView(bubble, lp)
    }

    // ---------- dock giữa đáy màn hình ----------
    private fun toggleBar() {
        if (bar != null) { hideBar(); hideDrawer() } else showBar()
    }

    private fun showBar() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        // nút tất cả ứng dụng (9 chấm) đứng đầu
        row.addView(GridDots(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                marginStart = dp(4); marginEnd = dp(12)
            }
            setOnClickListener { if (drawer == null) showDrawer() else hideDrawer() }
        })

        // các app đã ghim; nhấn giữ để bỏ ghim
        val pins = pinned()
        allApps.filter { it.pkg in pins }.forEach { a ->
            row.addView(ImageView(this).apply {
                setImageDrawable(icon(a.pkg))
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    marginStart = dp(6); marginEnd = dp(6)
                }
                setOnClickListener { launch(a) }
                setOnLongClickListener { togglePin(a.pkg); true }
            })
        }

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
            background = GradientDrawable().apply {
                cornerRadius = dp(36).toFloat()
                setColor(Color.argb(225, 43, 45, 49))
            }
        }
        val lp = overlayParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(16)
        }
        wm.addView(scroll, lp)
        bar = scroll
    }

    private fun hideBar() {
        bar?.let { runCatching { wm.removeView(it) } }
        bar = null
    }

    // ---------- lưới tất cả ứng dụng (hiện phía trên dock) ----------
    private fun showDrawer() {
        val grid = GridView(this).apply {
            numColumns = 5
            setPadding(dp(12), dp(12), dp(12), dp(12))
            adapter = object : BaseAdapter() {
                override fun getCount() = allApps.size
                override fun getItem(p: Int) = allApps[p]
                override fun getItemId(p: Int) = p.toLong()
                override fun getView(p: Int, v: View?, parent: ViewGroup): View {
                    val a = allApps[p]
                    val cell = LinearLayout(this@TaskbarService).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(dp(4), dp(8), dp(4), dp(8))
                    }
                    cell.addView(ImageView(this@TaskbarService).apply {
                        setImageDrawable(icon(a.pkg))
                        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                    })
                    cell.addView(TextView(this@TaskbarService).apply {
                        text = if (a.pkg in pinned()) "📌 ${a.label}" else a.label
                        setTextColor(Color.WHITE); textSize = 11f
                        maxLines = 1; gravity = Gravity.CENTER
                    })
                    cell.setOnClickListener { launch(a) }
                    // nhấn giữ = ghim / bỏ ghim lên dock
                    cell.setOnLongClickListener { togglePin(a.pkg); notifyDataSetChanged(); true }
                    return cell
                }
            }
        }
        val box = LinearLayout(this).apply {
            addView(grid, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(Color.argb(225, 43, 45, 49))
            }
        }
        val dm = resources.displayMetrics
        val lp = overlayParams(
            minOf(dm.widthPixels - dp(40), dp(460)),
            minOf(dm.heightPixels - dp(140), dp(420))
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(96)    // nằm ngay trên dock
        }
        wm.addView(box, lp)
        drawer = box
    }

    private fun hideDrawer() {
        drawer?.let { runCatching { wm.removeView(it) } }
        drawer = null
    }
}
