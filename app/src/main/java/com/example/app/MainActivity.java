package com.example.app;

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
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    
    private static final String DEBUG_FILE = "/sdcard/battery_debug.txt";
    private FileWriter debugWriter;
    private BatteryManager batteryManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize BatteryManager
        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        
        WebView webView = new WebView(this);
        setContentView(webView);
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        
        webView.addJavascriptInterface(new BatteryBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
        
        initDebugFile();
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
            logDebug("\n--- Detection Strategy ---");
            logDebug("1. Try sysfs paths (requires root or system app)");
            logDebug("2. Fallback to BatteryManager + Intent API");
            logDebug("3. Estimate charger voltage from power calculation\n");
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
                boolean usedSysfs = false;
                
                logDebug("\n[" + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()) + "] Reading battery data...");
                logDebug("Android SDK: " + Build.VERSION.SDK_INT);
                
                // Try sysfs first
                String sysfsCapacity = readSysFile("/sys/class/power_supply/battery/capacity", null, "Capacity");
                String sysfsStatus = readSysFile("/sys/class/power_supply/battery/status", null, "Status");
                String sysfsCurrent = readSysFile("/sys/class/power_supply/battery/current_now", null, "Current");
                String sysfsTemp = readSysFile("/sys/class/power_supply/battery/temp", null, "Temperature");
                String sysfsVoltage = tryReadVoltageSysfs();
                
                // Check if sysfs worked
                if (sysfsCapacity != null && !sysfsCapacity.equals("0")) {
                    logDebug("✓ Using sysfs data");
                    usedSysfs = true;
                    data.put("capacity", sysfsCapacity);
                    data.put("status", sysfsStatus != null ? sysfsStatus : "Unknown");
                    data.put("current_now", sysfsCurrent != null ? sysfsCurrent : "0");
                    data.put("temp", sysfsTemp != null ? sysfsTemp : "0");
                    data.put("voltage", sysfsVoltage);
                    data.put("source", "sysfs");
                    
                    // Try get charger voltage from sysfs
                    String chargerVoltage = readSysFile("/sys/devices/platform/charger/ADC_Charger_Voltage", null, "Charger_Voltage");
                    if (chargerVoltage != null && !chargerVoltage.equals("0")) {
                        data.put("charger_voltage", chargerVoltage);
                        logDebug("Charger Voltage (sysfs): " + chargerVoltage + " mV");
                    } else {
                        data.put("charger_voltage", "0");
                    }
                } else {
                    logDebug("✗ Sysfs access failed, using BatteryManager + Intent API");
                    
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
                    // Voltage is in millivolts
                    int voltageMv = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                    int voltageUv = voltageMv * 1000; // Convert mV to µV
                    
                    // Temperature is in tenths of degree Celsius
                    int tempDeci = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                    
                    // Get plugged type
                    int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                    
                    logDebug("BatteryManager - Capacity: " + capacity + "%");
                    logDebug("Intent - Status: " + statusStr);
                    logDebug("Intent - Voltage: " + voltageMv + " mV (" + voltageUv + " µV)");
                    logDebug("BatteryManager - Current: " + currentUa + " µA");
                    logDebug("Intent - Temp: " + tempDeci + " deci°C (" + (tempDeci / 10.0) + " °C)");
                    
                    // Estimate charger voltage from power
                    ChargerEstimation estimation = estimateChargerVoltage(currentUa, voltageMv, status, plugged);
                    
                    logDebug("Charger Estimation:");
                    logDebug("  Power: " + estimation.powerW + " W");
                    logDebug("  Estimated Voltage: " + estimation.estimatedVoltageMv + " mV");
                    logDebug("  Charging Mode: " + estimation.chargingMode);
                    logDebug("  Confidence: " + estimation.confidence);
                    
                    // Convert to same format as sysfs
                    data.put("capacity", String.valueOf(capacity));
                    data.put("status", statusStr);
                    data.put("voltage", String.valueOf(voltageMv)); // Keep in mV like sysfs
                    data.put("current_now", String.valueOf(currentUa)); // Already in µA like sysfs
                    data.put("temp", String.valueOf(tempDeci)); // Already in deci°C like sysfs
                    data.put("source", "hybrid");
                    
                    // Add charger voltage estimation
                    data.put("charger_voltage", String.valueOf(estimation.estimatedVoltageMv));
                    data.put("charger_power", String.format("%.1f", estimation.powerW));
                    data.put("charging_mode", estimation.chargingMode);
                    data.put("estimation_confidence", estimation.confidence);
                }
                
                logDebug("Final JSON: " + data.toString());
                
                return data.toString();
            } catch (Exception e) {
                logDebug("ERROR in getBatteryData: " + e.getMessage());
                e.printStackTrace();
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }
        
        private class ChargerEstimation {
            float powerW;
            int estimatedVoltageMv;
            String chargingMode;
            String confidence;
        }
        
        private ChargerEstimation estimateChargerVoltage(int currentUa, int voltageMv, int status, int plugged) {
            ChargerEstimation result = new ChargerEstimation();
            
            // Calculate power (W = V × I)
            // currentUa is negative when charging
            float currentA = Math.abs(currentUa) / 1000000.0f;
            float voltageV = voltageMv / 1000.0f;
            result.powerW = voltageV * currentA;
            
            if (status != BatteryManager.BATTERY_STATUS_CHARGING) {
                result.estimatedVoltageMv = 0;
                result.chargingMode = "Not Charging";
                result.confidence = "N/A";
                return result;
            }
            
            // Infinix GT 30 Pro specs: 45W max (9V/5A or 12V/3.75A)
            // Typical efficiency: ~85-90%
            
            if (result.powerW > 35) {
                // 45W fast charging detected
                // Reverse calculate: ChargerVoltage = Power / Current (with efficiency factor)
                // Assume 85% efficiency: ActualPower = 45W, BatteryPower = 38W
                float estimatedChargerCurrent = currentA / 0.85f; // Account for losses
                result.estimatedVoltageMv = (int)(45000 / estimatedChargerCurrent); // 45W in mW
                
                if (result.estimatedVoltageMv >= 8000 && result.estimatedVoltageMv <= 10000) {
                    result.estimatedVoltageMv = 9000; // Snap to 9V
                    result.chargingMode = "45W Fast Charge (9V/5A)";
                    result.confidence = "High";
                } else if (result.estimatedVoltageMv >= 11000 && result.estimatedVoltageMv <= 13000) {
                    result.estimatedVoltageMv = 12000; // Snap to 12V
                    result.chargingMode = "45W Fast Charge (12V/3.75A)";
                    result.confidence = "High";
                } else {
                    result.chargingMode = "Fast Charge (Unknown Protocol)";
                    result.confidence = "Medium";
                }
            } else if (result.powerW > 15) {
                // ~18W charging (9V/2A or similar)
                result.estimatedVoltageMv = 9000;
                result.chargingMode = "18W Fast Charge (9V)";
                result.confidence = "Medium";
            } else if (result.powerW > 8) {
                // Standard 10W charging (5V/2A)
                result.estimatedVoltageMv = 5000;
                result.chargingMode = "10W Standard Charge (5V/2A)";
                result.confidence = "High";
            } else if (result.powerW > 3) {
                // Slow charging (5V/1A or less)
                result.estimatedVoltageMv = 5000;
                result.chargingMode = "Slow Charge (5V/<2A)";
                result.confidence = "High";
            } else {
                // Trickle charge or USB
                result.estimatedVoltageMv = 5000;
                if (plugged == BatteryManager.BATTERY_PLUGGED_USB) {
                    result.chargingMode = "USB Charge (5V/0.5A)";
                } else {
                    result.chargingMode = "Trickle Charge";
                }
                result.confidence = "Low";
            }
            
            return result;
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
            info.append("Android: ").append(android.os.Build.VERSION.RELEASE).append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n");
            info.append("=== SYSFS SCAN ===\n\n");
            
            String[] allPaths = {
                "/sys/class/power_supply/battery/capacity",
                "/sys/class/power_supply/battery/status",
                "/sys/class/power_supply/battery/voltage_now",
                "/sys/class/power_supply/battery/current_now",
                "/sys/class/power_supply/battery/temp",
                "/sys/class/power_supply/battery/batt_vol",
                "/sys/class/power_supply/usb/voltage_now",
                "/sys/devices/platform/charger/ADC_Charger_Voltage"
            };
            
            boolean anyAccessible = false;
            for (String path : allPaths) {
                File file = new File(path);
                if (file.exists()) {
                    if (file.canRead()) {
                        String value = readSysFileDebug(path);
                        info.append("✓ ").append(path).append("\n  ").append(value).append("\n\n");
                        anyAccessible = true;
                    } else {
                        info.append("✗ ").append(path).append("\n  (exists but not readable - SELinux)\n\n");
                    }
                } else {
                    info.append("✗ ").append(path).append("\n  (not found)\n\n");
                }
            }
            
            if (!anyAccessible) {
                info.append("⚠️  No sysfs access (expected on non-root Android 10+)\n\n");
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
                int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                
                info.append("✓ Capacity: ").append(capacity).append("% (BatteryManager)\n");
                info.append("✓ Status: ").append(getStatusString(status)).append(" (Intent)\n");
                info.append("✓ Voltage: ").append(voltageMv).append(" mV (").append(String.format("%.2f", voltageMv / 1000.0)).append(" V) (Intent)\n");
                info.append("✓ Current: ").append(current).append(" µA (").append(current / 1000).append(" mA) (BatteryManager)\n");
                info.append("✓ Temperature: ").append(tempDeci).append(" deci°C (").append(String.format("%.1f", tempDeci / 10.0)).append(" °C) (Intent)\n\n");
                
                // Charger voltage estimation
                ChargerEstimation estimation = estimateChargerVoltage(current, voltageMv, status, plugged);
                
                info.append("=== CHARGER VOLTAGE ESTIMATION ===\n\n");
                info.append("Calculated Power: ").append(String.format("%.2f", estimation.powerW)).append(" W\n");
                info.append("Estimated Charger Voltage: ").append(estimation.estimatedVoltageMv).append(" mV (");
                info.append(String.format("%.1f", estimation.estimatedVoltageMv / 1000.0)).append(" V)\n");
                info.append("Charging Mode: ").append(estimation.chargingMode).append("\n");
                info.append("Confidence: ").append(estimation.confidence).append("\n\n");
                
                info.append("⚡ Formula: Power = Battery_Voltage × |Current|\n");
                info.append("   ").append(String.format("%.2f", estimation.powerW)).append("W = ");
                info.append(String.format("%.2f", voltageMv / 1000.0)).append("V × ");
                info.append(String.format("%.2f", Math.abs(current) / 1000000.0)).append("A\n\n");
                
                info.append("✅ Using Hybrid API (works on all Android versions)");
            } catch (Exception e) {
                info.append("❌ API failed: ").append(e.getMessage());
            }
            
            return info.toString();
        }
        
        private String tryReadVoltageSysfs() {
            String[] paths = {
                "/sys/devices/platform/charger/ADC_Charger_Voltage",
                "/sys/class/power_supply/battery/voltage_now",
                "/sys/class/power_supply/usb/voltage_now",
                "/sys/class/power_supply/battery/batt_vol"
            };
            
            for (String path : paths) {
                String value = readSysFile(path, null, "Voltage[" + path.substring(path.lastIndexOf('/') + 1) + "]");
                if (value != null && !value.equals("0")) {
                    return value;
                }
            }
            return "0";
        }
        
        private String readSysFile(String path, String defaultValue, String label) {
            BufferedReader reader = null;
            try {
                File file = new File(path);
                if (!file.exists()) return defaultValue;
                if (!file.canRead()) return defaultValue;
                
                reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();
                
                if (line != null) {
                    String trimmed = line.trim();
                    logDebug(label + ": " + trimmed);
                    return trimmed;
                }
                return defaultValue;
            } catch (Exception e) {
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