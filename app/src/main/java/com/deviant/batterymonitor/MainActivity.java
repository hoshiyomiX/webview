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
import android.app.AlertDialog;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    
    private static final String DEBUG_FILE = "/sdcard/battery_debug.txt";
    private FileWriter debugWriter;
    private BatteryManager batteryManager;
    private boolean hasRootAccess = false;
    private boolean rootChecked = false;
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
        
        // Check and request root access
        checkRootAccess();
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
    
    private void checkRootAccess() {
        new Thread(() -> {
            boolean rootAvailable = isRootAvailable();
            
            runOnUiThread(() -> {
                if (rootAvailable) {
                    showRootDialog();
                } else {
                    rootChecked = true;
                    hasRootAccess = false;
                    Toast.makeText(this, "Running in non-root mode", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
    
    private boolean isRootAvailable() {
        try {
            Process process = Runtime.getRuntime().exec("su -v");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();
            return line != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void showRootDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Root Access")
            .setMessage("This app can use root access to read charger voltage from sysfs.\n\nGrant root access?")
            .setPositiveButton("Grant", (dialog, which) -> {
                new Thread(() -> {
                    boolean granted = requestRootAccess();
                    runOnUiThread(() -> {
                        hasRootAccess = granted;
                        rootChecked = true;
                        if (granted) {
                            Toast.makeText(this, "Root access granted", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Root access denied", Toast.LENGTH_SHORT).show();
                        }
                    });
                }).start();
            })
            .setNegativeButton("Deny", (dialog, which) -> {
                hasRootAccess = false;
                rootChecked = true;
                Toast.makeText(this, "Running in non-root mode", Toast.LENGTH_SHORT).show();
            })
            .setCancelable(false)
            .show();
    }
    
    private boolean requestRootAccess() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("id\n");
            os.writeBytes("exit\n");
            os.flush();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();
            
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
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
            
            logDebug("\n--- Detection Strategy ---");
            logDebug("1. Try sysfs paths with root access");
            logDebug("2. Fallback to BatteryManager + Intent API (official, no root needed)\n");
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
                status.put("checked", rootChecked);
                status.put("hasRoot", hasRootAccess);
                return status.toString();
            } catch (Exception e) {
                return "{\"checked\":false,\"hasRoot\":false}";
            }
        }
        
        @JavascriptInterface
        public String getBatteryData() {
            try {
                JSONObject data = new JSONObject();
                
                logDebug("\n[" + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()) + "] Reading battery data...");
                logDebug("Android SDK: " + Build.VERSION.SDK_INT);
                logDebug("Root access: " + hasRootAccess);
                
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
                data.put("source", hasRootAccess ? "root" : "hybrid");
                
                // Try to get charger voltage with root
                if (hasRootAccess && status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    String chargerVoltage = readChargerVoltageRoot();
                    if (chargerVoltage != null && !chargerVoltage.equals("0")) {
                        data.put("charger_voltage", chargerVoltage);
                        logDebug("Charger Voltage (root): " + chargerVoltage + " mV");
                    } else {
                        data.put("charger_voltage", "0");
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
        
        private String readChargerVoltageRoot() {
            try {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                
                os.writeBytes("cat /sys/devices/platform/charger/ADC_Charger_Voltage\n");
                os.writeBytes("exit\n");
                os.flush();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String voltage = reader.readLine();
                
                process.waitFor();
                os.close();
                reader.close();
                
                if (voltage != null && !voltage.isEmpty()) {
                    return voltage.trim();
                }
                return "0";
            } catch (Exception e) {
                logDebug("Root read failed: " + e.getMessage());
                return "0";
            }
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
            info.append("Root Access: ").append(hasRootAccess ? "YES" : "NO").append("\n");
            
            // Show current theme
            int uiMode = getResources().getConfiguration().uiMode;
            boolean isDark = (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            info.append("System Theme: ").append(isDark ? "Dark" : "Light").append("\n\n");
            
            if (hasRootAccess) {
                info.append("=== ROOT MODE - SYSFS ACCESS ===\n\n");
                
                // Try read charger voltage
                String chargerVoltage = readChargerVoltageRoot();
                if (!chargerVoltage.equals("0")) {
                    info.append("✓ Charger Voltage: ").append(chargerVoltage).append(" mV\n\n");
                } else {
                    info.append("✗ Charger Voltage: Not available\n\n");
                }
            } else {
                info.append("=== NON-ROOT MODE ===\n\n");
                info.append("⚠️ Cannot read charger voltage (requires root)\n\n");
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
                
                info.append("✅ Using Hybrid API (works on all Android versions)");
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