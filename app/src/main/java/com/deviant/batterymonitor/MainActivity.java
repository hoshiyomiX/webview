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
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    
    private static final String DEBUG_FILE = "/sdcard/battery_debug.txt";
    private FileWriter debugWriter;
    private boolean hasPrivilegedAccess = true; // Privileged app has direct sysfs access
    private WebView webView;
    private SELinuxDebugger selinuxDebugger;
    
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
        
        selinuxDebugger = new SELinuxDebugger();
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
            
            logDebug("================================================================================");
            logDebug("        BATTERY MONITOR - SELINUX DEBUG LOG");
            logDebug("================================================================================\n");
            
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            logDebug("Timestamp: " + timestamp);
            logDebug("Android Version: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
            logDebug("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            logDebug("Board: " + Build.BOARD);
            logDebug("Hardware: " + Build.HARDWARE);
            
            int uiMode = getResources().getConfiguration().uiMode;
            boolean isDark = (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            logDebug("Initial Theme: " + (isDark ? "dark" : "light"));
            
            logDebug("\n--- Application Mode ---");
            logDebug("Mode: Pure kernel sysfs reading");
            logDebug("APIs: NO BatteryManager, NO Intent");
            logDebug("Expected Domain: u:r:priv_app:s0");
            logDebug("Install Path: /product/priv-app/BatteryMonitor/");
            logDebug("Data Source: /sys/class/power_supply/");
            
            // SELinux Diagnostics
            logDebug("\n================================================================================");
            logDebug("        SELINUX TROUBLESHOOTING");
            logDebug("================================================================================\n");
            
            // 1. SELinux Mode
            logDebug("[1] SELinux Enforcing Status:");
            String enforceStatus = selinuxDebugger.getSELinuxStatus();
            logDebug("    " + enforceStatus);
            if (enforceStatus.contains("Enforcing")) {
                logDebug("    ✓ SELinux is enforcing - policies will be enforced");
            } else if (enforceStatus.contains("Permissive")) {
                logDebug("    ⚠ SELinux is permissive - denials logged but not enforced");
            } else {
                logDebug("    ✗ Cannot determine SELinux status");
            }
            
            // 2. App SELinux Context
            logDebug("\n[2] Application SELinux Context:");
            String appContext = selinuxDebugger.getProcessContext();
            logDebug("    " + appContext);
            if (appContext.contains("priv_app")) {
                logDebug("    ✓ Running as priv_app domain (correct)");
            } else if (appContext.contains("untrusted_app")) {
                logDebug("    ✗ ERROR: Running as untrusted_app (needs priv_app)");
                logDebug("    → Install to /product/priv-app/ or /system/priv-app/");
            } else {
                logDebug("    ⚠ Unknown domain: " + appContext);
            }
            
            // 3. Sysfs File Contexts
            logDebug("\n[3] Battery Sysfs File Contexts:");
            String[] checkPaths = {
                "/sys/class/power_supply/battery/capacity",
                "/sys/class/power_supply/battery/voltage_now",
                "/sys/class/power_supply/battery/temp",
                "/sys/devices/platform/charger/ADC_Charger_Voltage"
            };
            
            for (String path : checkPaths) {
                String fileContext = selinuxDebugger.getFileContext(path);
                logDebug("    " + path);
                logDebug("      → " + fileContext);
                
                if (fileContext.contains("sysfs_batteryinfo") || fileContext.contains("sysfs_charger")) {
                    logDebug("      ✓ Correct label");
                } else if (fileContext.contains("No such file")) {
                    logDebug("      ⚠ File doesn't exist on this device");
                } else if (fileContext.contains("sysfs")) {
                    logDebug("      ⚠ Generic sysfs label (may need vendor_file_contexts)");
                } else {
                    logDebug("      ✗ Unknown label: " + fileContext);
                }
            }
            
            // 4. AVC Denials
            logDebug("\n[4] Recent AVC Denials (filtered for batterymonitor):");
            List<String> avcDenials = selinuxDebugger.captureAVCDenials();
            if (avcDenials.isEmpty()) {
                logDebug("    ✓ No AVC denials found (permissions OK)");
            } else {
                logDebug("    ✗ Found " + avcDenials.size() + " AVC denial(s):\n");
                for (String denial : avcDenials) {
                    logDebug("    " + denial);
                    
                    // Parse and suggest fix
                    String suggestion = selinuxDebugger.suggestFixForDenial(denial);
                    if (suggestion != null) {
                        logDebug("    → Suggested fix: " + suggestion);
                    }
                    logDebug("");
                }
                
                logDebug("    📝 Action Required:");
                logDebug("    1. Add missing rules to vendor_sepolicy.cil");
                logDebug("    2. Rebuild ROM with updated policies");
                logDebug("    3. Verify file_contexts are applied (ls -lZ)");
            }
            
            // 5. Permission Test
            logDebug("\n[5] Sysfs Permission Test:");
            logDebug("    Testing read access to battery metrics...\n");
            
            logDebug("\n================================================================================\n");
            
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
     * Enhanced sysfs file reader with detailed error logging
     */
    private String readSysfsFile(String path) {
        File file = new File(path);
        
        // Check file existence
        if (!file.exists()) {
            logDebug("    ✗ File not found: " + path);
            return null;
        }
        
        // Check readability
        if (!file.canRead()) {
            logDebug("    ✗ Permission denied (canRead=false): " + path);
            logDebug("      → Check SELinux context: ls -lZ " + path);
            logDebug("      → Expected label: u:object_r:sysfs_batteryinfo:s0");
            return null;
        }
        
        // Attempt to read
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String value = reader.readLine();
            reader.close();
            
            if (value != null && !value.trim().isEmpty()) {
                logDebug("    ✓ Read success: " + path + " = " + value.trim());
                return value.trim();
            } else {
                logDebug("    ⚠ Empty value: " + path);
                return null;
            }
            
        } catch (FileNotFoundException e) {
            logDebug("    ✗ FileNotFoundException: " + path);
            logDebug("      Error: " + e.getMessage());
            logDebug("      → File may have been removed or path incorrect");
            return null;
            
        } catch (SecurityException e) {
            logDebug("    ✗ SecurityException: " + path);
            logDebug("      Error: " + e.getMessage());
            logDebug("      → SELinux denial - check vendor_sepolicy.cil");
            logDebug("      → Run: adb shell dmesg | grep avc | grep " + file.getName());
            return null;
            
        } catch (IOException e) {
            logDebug("    ✗ IOException: " + path);
            logDebug("      Error: " + e.getMessage());
            logDebug("      → Check file permissions: ls -l " + path);
            return null;
            
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // Ignore close error
                }
            }
        }
    }
    
    /**
     * Try multiple paths and return first successful read
     */
    private String readSysfsMultiPath(String[] paths, String metric) {
        logDebug("\n  [" + metric + "]");
        
        for (String path : paths) {
            String value = readSysfsFile(path);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        
        logDebug("    ✗ All paths failed for " + metric);
        logDebug("      → Device may use non-standard sysfs paths");
        logDebug("      → Run: find /sys -name \"*" + metric.toLowerCase().replace(" ", "*") + "*\" 2>/dev/null");
        
        return "0";
    }
    
    /**
     * SELinux Debugger - Capture AVC denials and SELinux status
     */
    private class SELinuxDebugger {
        
        /**
         * Execute shell command and return output
         */
        private String executeCommand(String command) {
            try {
                Process process = Runtime.getRuntime().exec(command);
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
                );
                
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                
                process.waitFor();
                reader.close();
                
                return output.toString().trim();
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }
        
        /**
         * Get SELinux enforcing status
         */
        public String getSELinuxStatus() {
            String status = executeCommand("getenforce");
            if (status.isEmpty() || status.startsWith("Error")) {
                // Try alternative method
                String enforce = executeCommand("cat /sys/fs/selinux/enforce");
                if (enforce.equals("1")) {
                    return "Enforcing";
                } else if (enforce.equals("0")) {
                    return "Permissive";
                }
                return "Unknown";
            }
            return status;
        }
        
        /**
         * Get app's SELinux context
         */
        public String getProcessContext() {
            // Try ps -Z
            String psOutput = executeCommand("ps -Z | grep batterymonitor");
            if (!psOutput.isEmpty() && !psOutput.startsWith("Error")) {
                // Extract context from ps output
                String[] parts = psOutput.split("\\s+");
                if (parts.length > 0) {
                    return parts[0]; // First column is SELinux context
                }
            }
            
            // Try reading /proc/self/attr/current
            String selfContext = executeCommand("cat /proc/self/attr/current");
            if (!selfContext.isEmpty() && !selfContext.startsWith("Error")) {
                return selfContext;
            }
            
            return "Cannot determine context";
        }
        
        /**
         * Get file's SELinux context
         */
        public String getFileContext(String path) {
            String lsOutput = executeCommand("ls -lZ " + path);
            if (lsOutput.startsWith("Error") || lsOutput.contains("No such file")) {
                return "No such file or directory";
            }
            
            // Parse ls -lZ output
            // Format: -rw-r--r-- u:object_r:sysfs_batteryinfo:s0 root root 4096 ...
            String[] parts = lsOutput.split("\\s+");
            if (parts.length >= 2) {
                return parts[1]; // Second column is SELinux context
            }
            
            return "Unknown context";
        }
        
        /**
         * Capture recent AVC denials from dmesg
         */
        public List<String> captureAVCDenials() {
            List<String> denials = new ArrayList<>();
            
            // Get dmesg output with AVC denials
            String dmesg = executeCommand("dmesg | grep avc | tail -20");
            
            if (dmesg.isEmpty() || dmesg.startsWith("Error")) {
                // Try logcat as fallback
                dmesg = executeCommand("logcat -d -b kernel | grep avc | tail -20");
            }
            
            if (!dmesg.isEmpty() && !dmesg.startsWith("Error")) {
                String[] lines = dmesg.split("\n");
                for (String line : lines) {
                    // Filter for denials related to priv_app and sysfs
                    if (line.contains("priv_app") || 
                        line.contains("sysfs") ||
                        line.contains("batterymonitor")) {
                        denials.add(line.trim());
                    }
                }
            }
            
            return denials;
        }
        
        /**
         * Parse AVC denial and suggest sepolicy fix
         */
        public String suggestFixForDenial(String avcDenial) {
            // Example AVC: avc: denied { read } for scontext=u:r:priv_app:s0 tcontext=u:object_r:sysfs:s0 tclass=file
            
            try {
                String suggestion = null;
                
                // Extract components
                String perm = extractBetween(avcDenial, "{ ", " }");
                String scontext = extractBetween(avcDenial, "scontext=u:r:", ":s0");
                String tcontext = extractBetween(avcDenial, "tcontext=u:object_r:", ":s0");
                String tclass = extractBetween(avcDenial, "tclass=", " ");
                
                if (tclass == null || tclass.isEmpty()) {
                    // tclass might be at end of line
                    int tclassIdx = avcDenial.indexOf("tclass=");
                    if (tclassIdx != -1) {
                        tclass = avcDenial.substring(tclassIdx + 7).trim();
                    }
                }
                
                if (scontext != null && tcontext != null && perm != null && tclass != null) {
                    suggestion = "(allow " + scontext + " " + tcontext + " (" + tclass + " (" + perm + ")))";
                }
                
                return suggestion;
            } catch (Exception e) {
                return null;
            }
        }
        
        private String extractBetween(String text, String start, String end) {
            try {
                int startIdx = text.indexOf(start);
                if (startIdx == -1) return null;
                
                startIdx += start.length();
                int endIdx = text.indexOf(end, startIdx);
                if (endIdx == -1) return null;
                
                return text.substring(startIdx, endIdx);
            } catch (Exception e) {
                return null;
            }
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
                
                String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                logDebug("\n================================================================================");
                logDebug("[" + timestamp + "] BATTERY DATA READ ATTEMPT");
                logDebug("================================================================================");
                
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
                
                logDebug("\n[SUMMARY]");
                logDebug("  Capacity: " + capacity + "%");
                logDebug("  Voltage: " + voltage + " mV");
                logDebug("  Current: " + current + " µA");
                logDebug("  Temperature: " + temperature + " deci°C");
                logDebug("  Status: " + status);
                logDebug("  Charger Voltage: " + chargerVoltage + " mV");
                logDebug("  Source: Direct sysfs");
                logDebug("================================================================================\n");
                
                return data.toString();
            } catch (Exception e) {
                logDebug("✗ EXCEPTION in getBatteryData: " + e.getClass().getName());
                logDebug("  Message: " + e.getMessage());
                logDebug("  Stack trace:");
                for (StackTraceElement elem : e.getStackTrace()) {
                    logDebug("    " + elem.toString());
                }
                e.printStackTrace();
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }
        
        /**
         * Read battery capacity (0-100%)
         */
        private String readBatteryCapacity() {
            String[] paths = {
                "/sys/class/power_supply/battery/capacity",
                "/sys/class/power_supply/bms/capacity",
                "/sys/class/power_supply/BAT0/capacity"
            };
            return readSysfsMultiPath(paths, "Battery Capacity");
        }
        
        /**
         * Read battery voltage (millivolts)
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
                if (microvolts > 100000) {
                    long millivolts = microvolts / 1000;
                    logDebug("    → Converted " + microvolts + " µV to " + millivolts + " mV");
                    return String.valueOf(millivolts);
                }
            } catch (NumberFormatException e) {
                logDebug("    ⚠ Cannot parse voltage value: " + value);
            }
            
            return value;
        }
        
        /**
         * Read battery current (microamperes)
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
         * Read battery temperature (deci-degrees Celsius)
         */
        private String readBatteryTemperature() {
            String[] paths = {
                "/sys/class/power_supply/battery/temp",
                "/sys/class/power_supply/bms/temp",
                "/sys/class/power_supply/BAT0/temp",
                "/sys/class/thermal/thermal_zone0/temp"
            };
            
            String value = readSysfsMultiPath(paths, "Battery Temperature");
            
            // Convert millidegrees to decidegrees if needed
            try {
                long temp = Long.parseLong(value);
                if (temp > 10000) {
                    long decidegrees = temp / 100;
                    logDebug("    → Converted " + temp + " milli°C to " + decidegrees + " deci°C");
                    return String.valueOf(decidegrees);
                }
            } catch (NumberFormatException e) {
                logDebug("    ⚠ Cannot parse temperature value: " + value);
            }
            
            return value;
        }
        
        /**
         * Read battery status
         */
        private String readBatteryStatus() {
            String[] paths = {
                "/sys/class/power_supply/battery/status",
                "/sys/class/power_supply/bms/status",
                "/sys/class/power_supply/BAT0/status"
            };
            
            String status = readSysfsMultiPath(paths, "Battery Status");
            
            // Normalize status string
            if (status.equalsIgnoreCase("CHARGING")) return "Charging";
            if (status.equalsIgnoreCase("DISCHARGING")) return "Discharging";
            if (status.equalsIgnoreCase("FULL")) return "Full";
            if (status.equalsIgnoreCase("NOT_CHARGING")) return "Not charging";
            if (status.equalsIgnoreCase("NOT CHARGING")) return "Not charging";
            
            return status.isEmpty() ? "Unknown" : status;
        }
        
        /**
         * Read charger voltage (millivolts)
         */
        private String readChargerVoltage() {
            String[] paths = {
                // MediaTek specific
                "/sys/devices/platform/charger/ADC_Charger_Voltage",
                "/sys/devices/platform/charger/Charger_Voltage",
                
                // Generic power_supply
                "/sys/class/power_supply/charger/voltage_now",
                "/sys/class/power_supply/usb/voltage_now",
                "/sys/class/power_supply/ac/voltage_now",
                
                // Battery-reported charger voltage
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
                if (microvolts > 100000) {
                    long millivolts = microvolts / 1000;
                    logDebug("    → Converted " + microvolts + " µV to " + millivolts + " mV");
                    return String.valueOf(millivolts);
                }
            } catch (NumberFormatException e) {
                logDebug("    ⚠ Cannot parse charger voltage: " + value);
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
            info.append("Access: Direct kernel sysfs\n\n");
            
            // SELinux Status
            info.append("=== SELINUX STATUS ===\n");
            info.append("Enforcing: ").append(selinuxDebugger.getSELinuxStatus()).append("\n");
            info.append("App Context: ").append(selinuxDebugger.getProcessContext()).append("\n\n");
            
            int uiMode = getResources().getConfiguration().uiMode;
            boolean isDark = (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            info.append("System Theme: ").append(isDark ? "Dark" : "Light").append("\n\n");
            
            info.append("=== BATTERY METRICS ===\n\n");
            
            // Read all metrics
            String capacity = readBatteryCapacity();
            String voltage = readBatteryVoltage();
            String current = readBatteryCurrent();
            String temperature = readBatteryTemperature();
            String status = readBatteryStatus();
            String chargerVoltage = readChargerVoltage();
            
            // Display results
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
            info.append("\n");
            info.append("📍 APK: /product/priv-app/BatteryMonitor/\n");
            info.append("🔒 Expected Context: u:r:priv_app:s0\n");
            info.append("\n");
            info.append("📝 Detailed logs: ").append(DEBUG_FILE).append("\n");
            info.append("   View with: adb shell cat ").append(DEBUG_FILE).append("\n");
            
            return info.toString();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (debugWriter != null) {
                logDebug("\n================================================================================");
                logDebug("Debug session ended at " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                logDebug("================================================================================");
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