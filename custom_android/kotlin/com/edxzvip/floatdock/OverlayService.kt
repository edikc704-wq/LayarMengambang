package com.edxzvip.floatdock

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs

/**
 * Service ini yang bikin:
 * 1. Garis putih kecil di tepi kanan ATAS layar (handle).
 * 2. Panel daftar semua app saat handle digeser ke kiri / di-tap.
 * 3. Buka app yang dipilih di window mengambang (freeform), kalau device-nya support.
 *
 * CATATAN PENTING:
 * - Resize & tombol close (X) pada window mengambang itu disediakan otomatis oleh
 *   Android sendiri (mekanisme freeform window), bukan oleh kode di file ini.
 * - Kalau device tidak mendukung freeform window, app yang dipilih akan tetap
 *   terbuka, tapi full-screen biasa (fallback otomatis, tidak crash).
 * - Untuk freeform aktif: Developer Options → Enable freeform windows = ON
 */
class OverlayService : Service() {

    companion object {
        @Volatile
        var isRunning: Boolean = false
        private const val CHANNEL_ID = "floatdock_channel"
        private const val NOTIF_ID = 1
    }

    private lateinit var windowManager: WindowManager
    private var handleView: View? = null
    private var panelView: View? = null

    private var screenWidth = 0
    private var screenHeight = 0

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        startForeground(NOTIF_ID, buildNotification())
        showHandle()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        removeHandle()
        removePanel()
        super.onDestroy()
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    // Helper: konversi dp ke pixel
    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    // ---------------------------------------------------------------------
    // HANDLE: garis putih kecil di tepi kanan ATAS
    // ---------------------------------------------------------------------

    private fun showHandle() {
        if (handleView != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_handle, null)

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        // ✅ BUG FIX: sebelumnya params.x = screenWidth (100% off-screen ke kanan!)
        // Handle view 28dp lebar; geser 20dp dari kanan supaya bar putih (6dp) kelihatan
        // Bar putih posisinya 11dp–17dp dari kiri view → muncul 3dp–9dp dari tepi kanan layar
        params.x = screenWidth - dpToPx(20)

        // ✅ FIX POSISI: sebelumnya screenHeight/2 (tengah). Sekarang kanan atas (~80dp dari atas)
        params.y = dpToPx(80)

        var startY = 0f
        var startTouchX = 0f
        var startTouchY = 0f
        var dragged = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = params.y.toFloat()
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startTouchX
                    val dy = event.rawY - startTouchY
                    if (abs(dx) > 8 || abs(dy) > 8) dragged = true

                    // geser cukup jauh ke kiri → buka panel daftar app
                    if (dx < -60) {
                        openPanel()
                        return@setOnTouchListener true
                    }

                    // seret naik/turun
                    params.y = (startY + dy).toInt().coerceIn(0, screenHeight)
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // tap singkat (gak digeser) → buka panel juga
                    if (!dragged) openPanel()
                    true
                }
                else -> false
            }
        }

        handleView = view
        try {
            windowManager.addView(view, params)
        } catch (_: Exception) {
        }
    }

    private fun removeHandle() {
        handleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        handleView = null
    }

    // ---------------------------------------------------------------------
    // PANEL: daftar semua app, tap kosong buat nutup
    // ---------------------------------------------------------------------

    private fun openPanel() {
        if (panelView != null) return
        removeHandle()

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_app_grid, null)
        val grid = view.findViewById<GridView>(R.id.appGrid)

        val apps = loadLaunchableApps()
        grid.adapter = AppAdapter(apps)

        grid.setOnItemClickListener { _, _, position, _ ->
            launchFloating(apps[position])
            closePanel()
        }

        // tap di background (di luar item grid) → tutup panel
        view.setOnClickListener { closePanel() }
        grid.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val pos = grid.pointToPosition(event.x.toInt(), event.y.toInt())
                if (pos == AdapterView.INVALID_POSITION) closePanel()
            }
            false // tetap lanjut diproses GridView buat klik item
        }

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            overlayType(),
            0,
            PixelFormat.TRANSLUCENT
        )

        panelView = view
        try {
            windowManager.addView(view, params)
        } catch (_: Exception) {
        }
    }

    private fun closePanel() {
        removePanel()
        showHandle()
    }

    private fun removePanel() {
        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        panelView = null
    }

    private fun loadLaunchableApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

        return resolveInfos
            .filter { it.activityInfo.packageName != packageName }
            .map {
                AppInfo(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    className = it.activityInfo.name,
                    icon = it.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    // ---------------------------------------------------------------------
    // LAUNCH: buka app terpilih di window mengambang (freeform)
    // CATATAN: Butuh "Enable freeform windows" aktif di Developer Options
    // Kalau belum aktif, app tetap buka tapi fullscreen (fallback)
    // ---------------------------------------------------------------------

    private fun launchFloating(app: AppInfo) {
        val intent = Intent().apply {
            component = ComponentName(app.packageName, app.className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }

        val w = (screenWidth * 0.7).toInt()
        val h = (screenHeight * 0.6).toInt()
        val left = (screenWidth - w) / 2
        val top = (screenHeight - h) / 4
        val bounds = Rect(left, top, left + w, top + h)

        try {
            val options = ActivityOptions.makeBasic()
            options.launchBounds = bounds
            startActivity(intent, options.toBundle())
        } catch (_: Exception) {
            // device gak support freeform launch bounds → fallback buka biasa
            try {
                startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    // ---------------------------------------------------------------------
    // NOTIFIKASI (wajib buat foreground service)
    // ---------------------------------------------------------------------

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "FloatDock", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder.setContentTitle("FloatDock aktif")
        builder.setContentText("Geser garis putih di kanan atas buat buka daftar aplikasi")
        builder.setSmallIcon(android.R.drawable.ic_menu_view)
        builder.setOngoing(true)
        return builder.build()
    }

    // ---------------------------------------------------------------------
    // DATA & ADAPTER
    // ---------------------------------------------------------------------

    data class AppInfo(
        val label: String,
        val packageName: String,
        val className: String,
        val icon: Drawable
    )

    private inner class AppAdapter(private val apps: List<AppInfo>) : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int): AppInfo = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView
                ?: LayoutInflater.from(this@OverlayService).inflate(R.layout.item_app, parent, false)
            val app = apps[position]
            view.findViewById<ImageView>(R.id.appIcon).setImageDrawable(app.icon)
            view.findViewById<TextView>(R.id.appLabel).text = app.label
            return view
        }
    }
}
