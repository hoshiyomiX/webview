package com.example.app;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.app.Activity;
import android.os.Environment;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    
    private static final String DEBUG_FILE = "/sdcard/battery_debug.txt";
    private FileWriter debugWriter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        WebView webView = new WebView(this);
        setContentView(webView);
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        
        webView.addJavascriptInterface(new BatteryBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
        
        // Initialize debug file
        initDebugFile();
    }
    
    private void initDebugFile() {
        try {
            File debugFile = new File(DEBUG_FILE);
            debugWriter = new FileWriter(debugFile, false);
            logDebug("=== DevBattery Monitor Debug Log ===");
            logDebug("Timestamp: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            logDebug("Android Version: " + android.os.Build.VERSION.RELEASE);
            logDebug("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            logDebug("\n--- Starting sysfs scan ---\n");
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
        public String getBatteryData() {
            try {
                JSONObject data = new JSONObject();
                
                logDebug("\n[" + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()) + "] Reading battery data...");
                
                data.put("capacity", readSysFile("/sys/class/power_supply/battery/capacity", "0", "Capacity"));
                data.put("status", readSysFile("/sys/class/power_supply/battery/status", "Unknown", "Status"));
                data.put("current_now", readSysFile("/sys/class/power_supply/battery/current_now", "0", "Current"));
                data.put("temp", readSysFile("/sys/class/power_supply/battery/temp", "0", "Temperature"));
                data.put("voltage", tryReadVoltage());
                
                logDebug("Final JSON: " + data.toString());
                
                return data.toString();
            } catch (Exception e) {
                logDebug("ERROR in getBatteryData: " + e.getMessage());
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }
        
        @JavascriptInterface
        public String getDebugInfo() {
            StringBuilder info = new StringBuilder();
            info.append("Debug file location: ").append(DEBUG_FILE).append("\n\n");
            info.append("Scanning all battery-related sysfs paths...\n\n");
            
            // Scan all possible battery paths
            String[] allPaths = {
                "/sys/class/power_supply/battery/capacity",
                "/sys/class/power_supply/battery/status",
                "/sys/class/power_supply/battery/voltage_now",
                "/sys/class/power_supply/battery/current_now",
                "/sys/class/power_supply/battery/temp",
                "/sys/class/power_supply/battery/batt_vol",
                "/sys/class/power_supply/usb/voltage_now",
                "/sys/devices/platform/charger/ADC_Charger_Voltage",
                "/sys/class/power_supply/battery/voltage_max",
                "/sys/class/power_supply/battery/voltage_min"
            };
            
            for (String path : allPaths) {
                File file = new File(path);
                if (file.exists()) {
                    if (file.canRead()) {
                        String value = readSysFileDebug(path);
                        info.append("✓ ").append(path).append("\n  Value: ").append(value).append("\n\n");
                    } else {
                        info.append("✗ ").append(path).append(" (exists but not readable)\n\n");
                    }
                } else {
                    info.append("✗ ").append(path).append(" (not found)\n\n");
                }
            }
            
            return info.toString();
        }
        
        private String tryReadVoltage() {
            logDebug("\n--- Scanning voltage paths ---");
            
            String[] paths = {
                "/sys/devices/platform/charger/ADC_Charger_Voltage",
                "/sys/class/power_supply/battery/voltage_now",
                "/sys/class/power_supply/usb/voltage_now",
                "/sys/class/power_supply/battery/batt_vol",
                "/sys/class/power_supply/battery/voltage_max"
            };
            
            for (String path : paths) {
                String value = readSysFile(path, null, "Voltage[" + path.substring(path.lastIndexOf('/') + 1) + "]");
                if (value != null && !value.equals("0")) {
                    logDebug("✓ Using voltage from: " + path);
                    return value;
                }
            }
            
            logDebug("✗ No valid voltage path found!");
            return "0";
        }
        
        private String readSysFile(String path, String defaultValue, String label) {
            BufferedReader reader = null;
            try {
                File file = new File(path);
                
                if (!file.exists()) {
                    logDebug(label + " [" + path + "]: FILE NOT FOUND");
                    return defaultValue;
                }
                
                if (!file.canRead()) {
                    logDebug(label + " [" + path + "]: NO READ PERMISSION");
                    return defaultValue;
                }
                
                reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();
                
                if (line != null) {
                    String trimmed = line.trim();
                    logDebug(label + " [" + path + "]: " + trimmed);
                    return trimmed;
                } else {
                    logDebug(label + " [" + path + "]: EMPTY FILE");
                    return defaultValue;
                }
            } catch (Exception e) {
                logDebug(label + " [" + path + "]: ERROR - " + e.getMessage());
                return defaultValue;
            } finally {
                if (reader != null) {
                    try { reader.close(); } catch (IOException ignored) {}
                }
            }
        }
        
        private String readSysFileDebug(String path) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(path));
                String line = reader.readLine();
                reader.close();
                return (line != null) ? line.trim() : "(empty)";
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
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