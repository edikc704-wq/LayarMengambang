package com.edxzvip.floatdock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "com.edxzvip.floatdock/overlay"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "checkOverlayPermission" ->
                        result.success(Settings.canDrawOverlays(this))

                    "requestOverlayPermission" -> {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                        result.success(null)
                    }

                    "checkBatteryOptimizationIgnored" -> {
                        val pm = getSystemService(POWER_SERVICE) as PowerManager
                        result.success(pm.isIgnoringBatteryOptimizations(packageName))
                    }

                    "requestIgnoreBatteryOptimization" -> {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                        result.success(null)
                    }

                    // ✅ Cek status freeform window.
                    // PENTING (khusus ColorOS/Realme & beberapa OEM lain):
                    // key Settings.Global yang dipakai Android AOSP ("enable_freeform_support")
                    // SERINGKALI TIDAK ADA / TIDAK DIPAKAI di ColorOS, karena Realme/Oppo
                    // punya implementasi multi-window sendiri yang gak selalu nulis ke key itu.
                    // Jadi nilai "false" dari device Realme BUKAN berarti pasti freeform mati —
                    // bisa juga cuma key-nya gak kebaca. Makanya kita coba beberapa key yang
                    // dikenal dipakai berbagai OEM, dan kalau semua gagal, anggap "tidak diketahui"
                    // (dikembalikan null) supaya UI bisa kasih instruksi manual, bukan ngeklaim
                    // pasti mati.
                    "checkFreeformEnabled" -> {
                        val knownKeys = listOf(
                            "enable_freeform_support",       // AOSP standar
                            "force_resizable_activities",     // beberapa custom ROM
                            "oplus_freeform_window_support"   // varian ColorOS/Oplus di sebagian build
                        )
                        var found: Boolean? = null
                        for (key in knownKeys) {
                            try {
                                val value = Settings.Global.getInt(contentResolver, key, -1)
                                if (value != -1) {
                                    found = (value == 1)
                                    break
                                }
                            } catch (_: Exception) {
                                // key gak ada di ROM ini, lanjut coba key berikutnya
                            }
                        }
                        // null = status gak bisa dipastikan lewat Settings.Global di ROM ini
                        result.success(found)
                    }

                    // ✅ BARU: Buka halaman Developer Options
                    // User perlu aktifkan "Enable freeform windows" di sini
                    "openDeveloperSettings" -> {
                        try {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                        } catch (e: Exception) {
                            // Fallback ke halaman Settings utama kalau Developer Options belum dibuka
                            // (Developer Options perlu diaktifkan dulu: Settings → Tentang Ponsel →
                            //  tap "Nomor Build" 7x)
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                        result.success(null)
                    }

                    "startFloatService" -> {
                        val intent = Intent(this, OverlayService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        result.success(null)
                    }

                    "stopFloatService" -> {
                        stopService(Intent(this, OverlayService::class.java))
                        result.success(null)
                    }

                    "isServiceRunning" -> result.success(OverlayService.isRunning)

                    else -> result.notImplemented()
                }
            }
    }
}
