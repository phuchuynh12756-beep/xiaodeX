package com.example.densitytaskbar

object Root {
    /** Chạy lệnh bằng quyền root. Gọi từ thread nền. */
    fun run(cmd: String): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        p.waitFor() == 0
    } catch (e: Exception) {
        false
    }

    /** Bắt đầu: density 200 + khóa cứng ngang + bật freeform */
    fun start(): Boolean = run(
        "settings put global enable_freeform_support 1; " +
        "settings put global force_resizable_activities 1; " +
        "settings put system accelerometer_rotation 0; " +
        "settings put system user_rotation 1; " +   // 1 = landscape
        "wm density 200"
    )

    /** Kết thúc: reset density + mở khóa xoay */
    fun stop(): Boolean = run(
        "wm density reset; " +
        "settings put system user_rotation 0; " +
        "settings put system accelerometer_rotation 1"
    )

    /** Mở app trong cửa sổ nổi (freeform, windowingMode 5) */
    fun launchFreeform(pkg: String, cls: String) {
        Thread {
            // thử kèm bounds trước, nếu máy không hỗ trợ thì mở không bounds
            val ok = run("am start --windowingMode 5 -n $pkg/$cls")
            if (!ok) run("am start -n $pkg/$cls")
        }.start()
    }
}
