package com.hoshiyomi.devbattery

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var themeManager: ThemeManager
    private var hasRootAccess = false
    private var rootCheckDone = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            webView.post {
                webView.evaluateJavascript("if(typeof updateBattery === 'function') updateBattery();", null)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        themeManager = ThemeManager(this)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupImmersiveMode()
        
        webView = WebView(this)
        setContentView(webView)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            
            cacheMode = WebSettings.LOAD_DEFAULT
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }
        
        webView.addJavascriptInterface(AndroidBridge(), "Android")
        WebView.setWebContentsDebuggingEnabled(true)
        
        webView.loadUrl("file:///android_asset/index.html")
        
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        checkRootAccess()
    }

    private fun setupImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val theme = themeManager.getCurrentTheme()
        webView.post {
            webView.evaluateJavascript(
                """if(typeof applyTheme === 'function') applyTheme('$theme');""",
                null
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
        }
        webView.destroy()
    }

    private fun checkRootAccess() {
        Thread {
            hasRootAccess = isDeviceRooted()
            rootCheckDone = true
        }.start()
    }

    private fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        
        for (path in paths) {
            if (java.io.File(path).exists()) return true
        }
        
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            output?.contains("uid=0") == true
        } catch (e: Exception) {
            false
        }
    }

    inner class AndroidBridge {
        
        @JavascriptInterface
        fun getSystemTheme(): String {
            return themeManager.getCurrentTheme()
        }
        
        @JavascriptInterface
        fun getBatteryData(): String {
            val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            
            return try {
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val capacity = if (level >= 0 && scale > 0) {
                    (level * 100 / scale.toFloat()).toInt()
                } else 0
                
                val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val statusText = when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                    else -> "Unknown"
                }
                
                val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
                val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                
                val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val currentNow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                } else 0
                
                val chargerVoltage = if (hasRootAccess) {
                    readSysfsFile("/sys/class/power_supply/usb/voltage_now")
                } else 0
                
                val powerNow = if (hasRootAccess) {
                    readSysfsFile("/sys/class/power_supply/battery/power_now")
                } else 0
                
                JSONObject().apply {
                    put("capacity", capacity)
                    put("status", statusText)
                    put("voltage", voltage)
                    put("current_now", currentNow)
                    put("temp", temp)
                    put("charger_voltage", chargerVoltage)
                    put("power_now", powerNow)
                }.toString()
                
            } catch (e: Exception) {
                JSONObject().apply {
                    put("error", e.message ?: "Unknown error")
                }.toString()
            }
        }
        
        @JavascriptInterface
        fun getDebugInfo(): String {
            return buildString {
                append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                append("Root Access: ${if (hasRootAccess) "YES" else "NO"}\n")
                append("Current Theme: ${themeManager.getCurrentTheme()}\n")
            }
        }
        
        @JavascriptInterface
        fun getRootStatus(): String {
            return JSONObject().apply {
                put("hasRoot", hasRootAccess)
                put("checked", rootCheckDone)
            }.toString()
        }
        
        @JavascriptInterface
        fun requestRootAccess() {
            Thread {
                try {
                    val process = Runtime.getRuntime().exec("su")
                    process.outputStream.write("id\n".toByteArray())
                    process.outputStream.flush()
                    process.outputStream.close()
                    process.waitFor()
                    hasRootAccess = process.exitValue() == 0
                    rootCheckDone = true
                } catch (e: Exception) {
                    hasRootAccess = false
                }
            }.start()
        }
        
        private fun readSysfsFile(path: String): Int {
            if (!hasRootAccess) return 0
            
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $path"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val line = reader.readLine()
                process.waitFor()
                line?.trim()?.toIntOrNull() ?: 0
            } catch (e: Exception) {
                0
            }
        }
    }
}