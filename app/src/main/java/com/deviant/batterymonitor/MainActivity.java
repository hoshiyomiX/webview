package com.deviant.batterymonitor;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.app.Activity;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    
    private static final String DEBUG_FILE = "/sdcard/battery_debug.txt";
    private FileWriter debugWriter;
    private BatteryManager batteryManager;
    private boolean hasPrivilegedAccess = true; // Privileged app has direct sysfs access
    private WebView webView; // Store WebView reference for theme changes
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize BatteryManager
        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        
        webView = new WebView(this);
        setContentView(webView);
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        
        webView.addJavascriptInterface(new BatteryBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
        
        initDebugFile();
    }
    
    // Detect system theme changes and notify JavaScript
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Detect theme change
        boolean isDark = (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        String theme = isDark ? "dark" : "light";
        
        logDebug("[THEME] System theme changed to: " + theme);
        
        // Notify JavaScript
        if (webView != null) {
            runOnUiThread(() -> {
                webView.evaluateJavascript(
                    "if (typeof applyTheme === 'function') { applyTheme('" + theme + "'); }",
                    null
                );
            });
        }
    }
    
    private void initDebugFile() {
        try {
            File debugFile = new File(DEBUG_FILE);
            debugWriter = new FileWriter(debugFile, false);
            logDebug("=== DevBattery Monitor Debug Log ===");
            logDebug("Timestamp: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            logDebug("Android Version: " + android.os.Build.VERSION.RELEASE + " (SDK " + android.os.Build.VERSION.SDK_INT + ")");
            logDebug("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            logDebug("Board: " + android.os.Build.BOARD);
            
            // Log initial theme
            int uiMode = getResources().getConfiguration().uiMode;
            boolean isDark = (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            logDebug("Initial Theme: " + (isDark ? "dark" : "light"));
            
            logDebug("\n--- Running as Privileged System App ---");
            logDebug("Mode: Direct sysfs access (no root/su required)");
            logDebug("SELinux Domain: priv_app");
            logDebug("Detection Strategy:");
            logDebug("1. Direct sysfs read for charger voltage");
            logDebug("2. BatteryManager + Intent API for other data\n");
            debugWriter.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void logDebug(String message) {
        try {
            if (debugWriter != null) {
                debugWriter.write(message + "\n");
                debugWriter.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public class BatteryBridge {
        
        @JavascriptInterface
        public String getSystemTheme() {
            try {
                int uiMode = getResources().getConfiguration().uiMode;
                boolean isDark = (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                String theme = isDark ? "dark" : "light";
                logDebug("[THEME] JavaScript requested theme: " + theme);
                return theme;
            } catch (Exception e) {
                logDebug("[THEME] Error getting theme: " + e.getMessage());
                return "dark"; // Default to dark on error
            }
        }
        
        @JavascriptInterface
        public String getRootStatus() {
            try {
                JSONObject status = new JSONObject();
                status.put("checked", true);
                status.put("hasRoot", hasPrivilegedAccess);
                status.put("mode", "privileged_app");
                return status.toString();
            } catch (Exception e) {
                return "{\"checked\":true,\"hasRoot\":true,\"mode\":\"privileged_app\"}";
            }
        }
        
        @JavascriptInterface
        public String getBatteryData() {
            try {
                JSONObject data = new JSONObject();
                
                logDebug("\n[" + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()) + "] Reading battery data...");
                logDebug("Android SDK: " + Build.VERSION.SDK_INT);
                logDebug("Privileged Access: " + hasPrivilegedAccess);
                
                // Get battery status from Intent
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);
                
                if (batteryStatus == null) {
                    logDebug("ERROR: Battery Intent is null!");
                    data.put("error", "Battery Intent unavailable");
                    return data.toString();
                }
                
                // Get capacity from BatteryManager (API 21+)
                int capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                
                // Get status from Intent
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                String statusStr = getStatusString(status);
                
                // Get current from BatteryManager (API 21+)
                int currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                
                // Get voltage & temperature from Intent (API 1+)
                int voltageMv = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                int tempDeci = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                
                logDebug("BatteryManager - Capacity: " + capacity + "%");
                logDebug("Intent - Status: " + statusStr);
                logDebug("Intent - Voltage: " + voltageMv + " mV");
                logDebug("BatteryManager - Current: " + currentUa + " µA");
                logDebug("Intent - Temp: " + tempDeci + " deci°C");
                
                // Build response
                data.put("capacity", String.valueOf(capacity));
                data.put("status", statusStr);
                data.put("voltage", String.valueOf(voltageMv));
                data.put("current_now", String.valueOf(currentUa));
                data.put("temp", String.valueOf(tempDeci));
                data.put("source", "privileged_app_sysfs");
                
                // Try to get charger voltage with direct sysfs access
                if (hasPrivilegedAccess && status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    String chargerVoltage = readChargerVoltageDirect();
                    if (chargerVoltage != null && !chargerVoltage.equals("0")) {
                        data.put("charger_voltage", chargerVoltage);
                        logDebug("Charger Voltage (direct sysfs): " + chargerVoltage + " mV");
                    } else {
                        data.put("charger_voltage", "0");
                        logDebug("Charger Voltage: Not available or not charging");
                    }
                } else {
                    data.put("charger_voltage", "0");
                }
                
                logDebug("Final JSON: " + data.toString());
                
                return data.toString();
            } catch (Exception e) {
                logDebug("ERROR in getBatteryData: " + e.getMessage());
                e.printStackTrace();
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }
        
        /**
         * Read charger voltage directly from sysfs without root/su
         * This works because app runs as priv_app with proper SELinux context
         */
        private String readChargerVoltageDirect() {
            // Try multiple possible charger voltage paths
            String[] paths = {
                "/sys/devices/platform/charger/ADC_Charger_Voltage",
                "/sys/class/power_supply/charger/voltage_now",
                "/sys/class/power_supply/usb/voltage_now",
                "/sys/class/power_supply/battery/charger_voltage"
            };
            
            for (String path : paths) {
                try {
                    File voltageFile = new File(path);
                    if (!voltageFile.exists()) {
                        logDebug("Path not found: " + path);
                        continue;
                    }
                    
                    BufferedReader reader = new BufferedReader(new FileReader(voltageFile));
                    String voltage = reader.readLine();
                    reader.close();
                    
                    if (voltage != null && !voltage.isEmpty()) {
                        voltage = voltage.trim();
                        logDebug("Successfully read from: " + path + " = " + voltage);
                        
                        // Some paths return microvolts, convert to millivolts if needed
                        try {
                            long voltageValue = Long.parseLong(voltage);
                            if (voltageValue > 100000) { // Likely in microvolts
                                voltage = String.valueOf(voltageValue / 1000);
                            }
                        } catch (NumberFormatException e) {
                            // Keep original value if not a number
                        }
                        
                        return voltage;
                    }
                } catch (IOException e) {
                    logDebug("Failed to read " + path + ": " + e.getMessage());
                }
            }
            
            logDebug("All charger voltage paths failed");
            return "0";
        }
        
        private String getStatusString(int status) {
            switch (status) {
                case BatteryManager.BATTERY_STATUS_CHARGING:
                    return "Charging";
                case BatteryManager.BATTERY_STATUS_DISCHARGING:
                    return "Discharging";
                case BatteryManager.BATTERY_STATUS_FULL:
                    return "Full";
                case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                    return "Not charging";
                default:
                    return "Unknown";
            }
        }
        
        @JavascriptInterface
        public String getDebugInfo() {
            StringBuilder info = new StringBuilder();
            info.append("Debug file: ").append(DEBUG_FILE).append("\n\n");
            info.append("Device: ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n");
            info.append("Android: ").append(android.os.Build.VERSION.RELEASE).append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
            info.append("Mode: Privileged System App\n");
            info.append("Access: Direct sysfs (no root/su)\n");
            
            // Show current theme
            int uiMode = getResources().getConfiguration().uiMode;
            boolean isDark = (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            info.append("System Theme: ").append(isDark ? "Dark" : "Light").append("\n\n");
            
            info.append("=== PRIVILEGED APP MODE - DIRECT SYSFS ACCESS ===\n\n");
            
            // Try read charger voltage
            String chargerVoltage = readChargerVoltageDirect();
            if (!chargerVoltage.equals("0")) {
                info.append("✓ Charger Voltage: ").append(chargerVoltage).append(" mV\n");
                info.append("  Source: Direct sysfs read\n");
                info.append("  SELinux: Allowed via priv_app → sysfs_charger\n\n");
            } else {
                info.append("✗ Charger Voltage: Not available\n");
                info.append("  Possible reasons:\n");
                info.append("  - Device not charging\n");
                info.append("  - Charger path not in supported list\n");
                info.append("  - SELinux denial (check dmesg)\n\n");
            }
            
            info.append("=== HYBRID API (BatteryManager + Intent) ===\n\n");
            
            try {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);
                
                // From BatteryManager
                int capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                int current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                
                // From Intent
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int voltageMv = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                int tempDeci = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                
                info.append("✓ Capacity: ").append(capacity).append("% (BatteryManager)\n");
                info.append("✓ Status: ").append(getStatusString(status)).append(" (Intent)\n");
                info.append("✓ Voltage: ").append(voltageMv).append(" mV (").append(String.format("%.2f", voltageMv / 1000.0)).append(" V) (Intent)\n");
                info.append("✓ Current: ").append(current).append(" µA (").append(current / 1000).append(" mA) (BatteryManager)\n");
                info.append("✓ Temperature: ").append(tempDeci).append(" deci°C (").append(String.format("%.1f", tempDeci / 10.0)).append(" °C) (Intent)\n\n");
                
                // Calculate power
                float power = Math.abs((voltageMv / 1000.0f) * (current / 1000000.0f));
                info.append("✓ Power: ").append(String.format("%.2f", power)).append(" W\n\n");
                
                info.append("✅ All standard APIs working correctly\n");
                info.append("📍 Location: /product/priv-app/BatteryMonitor/\n");
                info.append("🔒 SELinux Domain: priv_app\n");
            } catch (Exception e) {
                info.append("❌ API failed: ").append(e.getMessage());
            }
            
            return info.toString();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (debugWriter != null) {
                logDebug("\n=== Debug session ended ===");
                debugWriter.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onBackPressed() {
        finishAffinity();
    }
}