package com.deviant.batterymonitor;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.app.Activity;
import android.os.Build;
import android.content.res.Configuration;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    
    private static final String DEBUG_FILE = "/sdcard/battery_debug.txt";
    private FileWriter debugWriter;
    private boolean hasPrivilegedAccess = true; // Privileged app has direct sysfs access
    private WebView webView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
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
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        boolean isDark = (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        String theme = isDark ? "dark" : "light";
        
        logDebug("[THEME] System theme changed to: " + theme);
        
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
            logDebug("=== Battery Monitor - Pure Sysfs Mode ===");
            logDebug("Timestamp: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            logDebug("Android Version: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
            logDebug("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            logDebug("Board: " + Build.BOARD);
            
            int uiMode = getResources().getConfiguration().uiMode;
            boolean isDark = (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            logDebug("Initial Theme: " + (isDark ? "dark" : "light"));
            
            logDebug("\n--- Direct Sysfs Access Mode ---");
            logDebug("Mode: Pure kernel sysfs reading");
            logDebug("APIs: NO BatteryManager, NO Intent");
            logDebug("SELinux Domain: priv_app");
            logDebug("All battery data read directly from /sys/class/power_supply/\n");
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
    
    /**
     * Read a single line from a sysfs file
     */
    private String readSysfsFile(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                return null;
            }
            
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String value = reader.readLine();
            reader.close();
            
            return (value != null) ? value.trim() : null;
        } catch (IOException e) {
            return null;
        }
    }
    
    /**
     * Try multiple paths and return first successful read
     */
    private String readSysfsMultiPath(String[] paths, String metric) {
        for (String path : paths) {
            String value = readSysfsFile(path);
            if (value != null && !value.isEmpty()) {
                logDebug(metric + " read from: " + path + " = " + value);
                return value;
            }
        }
        logDebug(metric + " - all paths failed");
        return "0";
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
                return "dark";
            }
        }
        
        @JavascriptInterface
        public String getRootStatus() {
            try {
                JSONObject status = new JSONObject();
                status.put("checked", true);
                status.put("hasRoot", hasPrivilegedAccess);
                status.put("mode", "pure_sysfs");
                return status.toString();
            } catch (Exception e) {
                return "{\"checked\":true,\"hasRoot\":true,\"mode\":\"pure_sysfs\"}";
            }
        }
        
        @JavascriptInterface
        public String getBatteryData() {
            try {
                JSONObject data = new JSONObject();
                
                logDebug("\n[" + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()) + "] Reading battery data from sysfs...");
                
                // Read all battery metrics from sysfs
                String capacity = readBatteryCapacity();
                String voltage = readBatteryVoltage();
                String current = readBatteryCurrent();
                String temperature = readBatteryTemperature();
                String status = readBatteryStatus();
                String chargerVoltage = readChargerVoltage();
                
                // Build response
                data.put("capacity", capacity);
                data.put("status", status);
                data.put("voltage", voltage);
                data.put("current_now", current);
                data.put("temp", temperature);
                data.put("charger_voltage", chargerVoltage);
                data.put("source", "pure_sysfs");
                
                logDebug("Battery Data Summary:");
                logDebug("  Capacity: " + capacity + "%");
                logDebug("  Voltage: " + voltage + " mV");
                logDebug("  Current: " + current + " µA");
                logDebug("  Temperature: " + temperature + " deci°C");
                logDebug("  Status: " + status);
                logDebug("  Charger Voltage: " + chargerVoltage + " mV");
                
                return data.toString();
            } catch (Exception e) {
                logDebug("ERROR in getBatteryData: " + e.getMessage());
                e.printStackTrace();
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }
        
        /**
         * Read battery capacity (0-100%)
         * Returns: percentage as string
         */
        private String readBatteryCapacity() {
            String[] paths = {
                "/sys/class/power_supply/battery/capacity",
                "/sys/class/power_supply/bms/capacity",
                "/sys/class/power_supply/BAT0/capacity"
            };
            return readSysfsMultiPath(paths, "Capacity");
        }
        
        /**
         * Read battery voltage
         * Returns: millivolts as string
         */
        private String readBatteryVoltage() {
            String[] paths = {
                "/sys/class/power_supply/battery/voltage_now",
                "/sys/class/power_supply/bms/voltage_now",
                "/sys/class/power_supply/BAT0/voltage_now"
            };
            
            String value = readSysfsMultiPath(paths, "Battery Voltage");
            
            // Convert from microvolts to millivolts if needed
            try {
                long microvolts = Long.parseLong(value);
                if (microvolts > 100000) { // Likely in microvolts
                    return String.valueOf(microvolts / 1000);
                }
            } catch (NumberFormatException e) {
                // Keep original value
            }
            
            return value;
        }
        
        /**
         * Read battery current
         * Returns: microamperes as string (negative = discharging, positive = charging)
         */
        private String readBatteryCurrent() {
            String[] paths = {
                "/sys/class/power_supply/battery/current_now",
                "/sys/class/power_supply/bms/current_now",
                "/sys/class/power_supply/BAT0/current_now"
            };
            return readSysfsMultiPath(paths, "Battery Current");
        }
        
        /**
         * Read battery temperature
         * Returns: deci-degrees Celsius as string (e.g., "250" = 25.0°C)
         */
        private String readBatteryTemperature() {
            String[] paths = {
                "/sys/class/power_supply/battery/temp",
                "/sys/class/power_supply/bms/temp",
                "/sys/class/power_supply/BAT0/temp",
                "/sys/class/thermal/thermal_zone0/temp" // Fallback to thermal zone
            };
            
            String value = readSysfsMultiPath(paths, "Battery Temperature");
            
            // Some devices report in millidegrees (e.g., 25000 = 25°C)
            // Convert to decidegrees (e.g., 250 = 25.0°C)
            try {
                long temp = Long.parseLong(value);
                if (temp > 10000) { // Likely in millidegrees
                    return String.valueOf(temp / 100);
                }
            } catch (NumberFormatException e) {
                // Keep original value
            }
            
            return value;
        }
        
        /**
         * Read battery charging status
         * Returns: "Charging", "Discharging", "Full", "Not charging", or "Unknown"
         */
        private String readBatteryStatus() {
            String[] paths = {
                "/sys/class/power_supply/battery/status",
                "/sys/class/power_supply/bms/status",
                "/sys/class/power_supply/BAT0/status"
            };
            
            String status = readSysfsMultiPath(paths, "Battery Status");
            
            // Normalize status string (kernel returns uppercase)
            if (status.equalsIgnoreCase("CHARGING")) return "Charging";
            if (status.equalsIgnoreCase("DISCHARGING")) return "Discharging";
            if (status.equalsIgnoreCase("FULL")) return "Full";
            if (status.equalsIgnoreCase("NOT_CHARGING")) return "Not charging";
            if (status.equalsIgnoreCase("NOT CHARGING")) return "Not charging";
            
            return status.isEmpty() ? "Unknown" : status;
        }
        
        /**
         * Read charger voltage
         * Returns: millivolts as string
         */
        private String readChargerVoltage() {
            String[] paths = {
                // MediaTek specific
                "/sys/devices/platform/charger/ADC_Charger_Voltage",
                "/sys/devices/platform/charger/Charger_Voltage",
                
                // Generic power_supply paths
                "/sys/class/power_supply/charger/voltage_now",
                "/sys/class/power_supply/usb/voltage_now",
                "/sys/class/power_supply/ac/voltage_now",
                
                // Alternative battery-reported charger voltage
                "/sys/class/power_supply/battery/charger_voltage",
                "/sys/class/power_supply/battery/input_voltage",
                
                // Device-specific charger ICs
                "/sys/class/power_supply/bq25890/voltage_now",
                "/sys/class/power_supply/bq2597x-master/voltage_now",
                "/sys/class/power_supply/max77705-charger/voltage_now"
            };
            
            String value = readSysfsMultiPath(paths, "Charger Voltage");
            
            // Convert from microvolts to millivolts if needed
            try {
                long microvolts = Long.parseLong(value);
                if (microvolts > 100000) { // Likely in microvolts
                    return String.valueOf(microvolts / 1000);
                }
            } catch (NumberFormatException e) {
                // Keep original value
            }
            
            return value;
        }
        
        @JavascriptInterface
        public String getDebugInfo() {
            StringBuilder info = new StringBuilder();
            info.append("Debug file: ").append(DEBUG_FILE).append("\n\n");
            info.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
            info.append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
            info.append("Mode: Pure Sysfs (NO Android API)\n");
            info.append("Access: Direct kernel sysfs\n");
            
            int uiMode = getResources().getConfiguration().uiMode;
            boolean isDark = (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            info.append("System Theme: ").append(isDark ? "Dark" : "Light").append("\n\n");
            
            info.append("=== PURE SYSFS MODE - DIRECT KERNEL ACCESS ===\n\n");
            
            // Read all metrics
            String capacity = readBatteryCapacity();
            String voltage = readBatteryVoltage();
            String current = readBatteryCurrent();
            String temperature = readBatteryTemperature();
            String status = readBatteryStatus();
            String chargerVoltage = readChargerVoltage();
            
            // Display results
            info.append("Battery Metrics:\n");
            info.append("  ✓ Capacity: ").append(capacity).append("%\n");
            info.append("  ✓ Voltage: ").append(voltage).append(" mV (");
            try {
                info.append(String.format("%.2f", Long.parseLong(voltage) / 1000.0)).append(" V)\n");
            } catch (Exception e) {
                info.append("? V)\n");
            }
            
            info.append("  ✓ Current: ").append(current).append(" µA (");
            try {
                info.append(Long.parseLong(current) / 1000).append(" mA)\n");
            } catch (Exception e) {
                info.append("? mA)\n");
            }
            
            info.append("  ✓ Temperature: ").append(temperature).append(" deci°C (");
            try {
                info.append(String.format("%.1f", Long.parseLong(temperature) / 10.0)).append(" °C)\n");
            } catch (Exception e) {
                info.append("? °C)\n");
            }
            
            info.append("  ✓ Status: ").append(status).append("\n");
            
            if (!chargerVoltage.equals("0")) {
                info.append("  ✓ Charger Voltage: ").append(chargerVoltage).append(" mV\n");
            } else {
                info.append("  ✗ Charger Voltage: Not available\n");
            }
            
            // Calculate power
            try {
                long voltageMv = Long.parseLong(voltage);
                long currentUa = Long.parseLong(current);
                float power = Math.abs((voltageMv / 1000.0f) * (currentUa / 1000000.0f));
                info.append("\n  ⚡ Power: ").append(String.format("%.2f", power)).append(" W\n");
            } catch (Exception e) {
                // Can't calculate power
            }
            
            info.append("\n");
            info.append("Data Source: Direct sysfs reads\n");
            info.append("Base Path: /sys/class/power_supply/\n");
            info.append("APIs Used: NONE (pure kernel interface)\n");
            info.append("SELinux: Enforcing (priv_app domain)\n");
            info.append("\n");
            info.append("📍 APK: /product/priv-app/BatteryMonitor/\n");
            info.append("🔒 Context: u:r:priv_app:s0\n");
            
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