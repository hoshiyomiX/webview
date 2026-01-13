package com.deviant.batterymonitor;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SELinux AVC Denial Monitor
 * Auto-detect dan log SELinux denials related to app
 */
public class SELinuxDebugger {
    private static final String TAG = "SELinuxDebugger";
    private static final String PACKAGE_NAME = "com.deviant.batterymonitor";
    
    // Pattern untuk parse AVC denial dari dmesg
    private static final Pattern AVC_PATTERN = Pattern.compile(
        "avc:\\s+denied.*scontext=([^\\s]+).*tcontext=([^\\s]+).*tclass=([^\\s]+)"
    );
    
    /**
     * Check current SELinux mode
     */
    public static String getSELinuxMode() {
        try {
            Process process = Runtime.getRuntime().exec("getenforce");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            String mode = reader.readLine();
            reader.close();
            return mode != null ? mode.trim() : "Unknown";
        } catch (Exception e) {
            Log.e(TAG, "Failed to get SELinux mode", e);
            return "Error";
        }
    }
    
    /**
     * Get app's SELinux context
     */
    public static String getAppContext() {
        try {
            int pid = android.os.Process.myPid();
            Process process = Runtime.getRuntime().exec("cat /proc/" + pid + "/attr/current");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            String context = reader.readLine();
            reader.close();
            return context != null ? context.trim() : "Unknown";
        } catch (Exception e) {
            Log.e(TAG, "Failed to get app context", e);
            return "Error";
        }
    }
    
    /**
     * Scan dmesg untuk AVC denials related to app
     * Requires: su access atau priv-app dengan DUMP permission
     */
    public static List<AVCDenial> scanRecentDenials() {
        List<AVCDenial> denials = new ArrayList<>();
        
        try {
            // Try dengan su first, fallback ke dmesg direct
            Process process;
            try {
                process = Runtime.getRuntime().exec(new String[]{"su", "-c", "dmesg | grep avc"});
            } catch (Exception e) {
                // Fallback: dmesg tanpa su (bisa gagal di enforcing mode)
                process = Runtime.getRuntime().exec("dmesg");
            }
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("avc") && line.contains("denied")) {
                    // Check if related to app (by package name atau priv_app context)
                    if (line.contains(PACKAGE_NAME) || line.contains("priv_app")) {
                        AVCDenial denial = parseAVCLine(line);
                        if (denial != null) {
                            denials.add(denial);
                        }
                    }
                }
            }
            
            reader.close();
            process.waitFor();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to scan AVC denials", e);
        }
        
        return denials;
    }
    
    /**
     * Parse AVC denial line dari dmesg
     */
    private static AVCDenial parseAVCLine(String line) {
        try {
            Matcher matcher = AVC_PATTERN.matcher(line);
            if (matcher.find()) {
                AVCDenial denial = new AVCDenial();
                denial.rawLine = line;
                denial.sourceContext = matcher.group(1);
                denial.targetContext = matcher.group(2);
                denial.targetClass = matcher.group(3);
                
                // Extract permission dari line
                Pattern permPattern = Pattern.compile("\\{\\s*([^}]+)\\s*\\}");
                Matcher permMatcher = permPattern.matcher(line);
                if (permMatcher.find()) {
                    denial.permission = permMatcher.group(1).trim();
                }
                
                return denial;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse AVC line: " + line, e);
        }
        return null;
    }
    
    /**
     * Generate audit2allow-style policy suggestion
     */
    public static String generatePolicySuggestion(List<AVCDenial> denials) {
        if (denials.isEmpty()) {
            return "# No denials found";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(";; Generated policy suggestions\n");
        sb.append(";; Add to vendor_sepolicy.cil\n\n");
        
        for (AVCDenial denial : denials) {
            if (denial.sourceContext.contains("priv_app") && 
                denial.targetContext != null && 
                denial.targetClass != null) {
                
                sb.append(String.format(
                    "(allow priv_app %s (%s (%s)))\n",
                    extractType(denial.targetContext),
                    denial.targetClass,
                    denial.permission != null ? denial.permission : "*"
                ));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Extract type dari SELinux context (u:r:TYPE:s0)
     */
    private static String extractType(String context) {
        if (context == null) return "unknown";
        String[] parts = context.split(":");
        return parts.length >= 3 ? parts[2] : "unknown";
    }
    
    /**
     * Log full SELinux debug info
     */
    public static void logDebugInfo() {
        if (!BuildConfig.DEBUG) return;
        
        Log.d(TAG, "=== SELinux Debug Info ===");
        Log.d(TAG, "SELinux Mode: " + getSELinuxMode());
        Log.d(TAG, "App Context: " + getAppContext());
        
        List<AVCDenial> denials = scanRecentDenials();
        Log.d(TAG, "Recent Denials: " + denials.size());
        
        if (!denials.isEmpty()) {
            Log.w(TAG, "⚠️ AVC Denials Detected:");
            for (AVCDenial denial : denials) {
                Log.w(TAG, "  - " + denial.toString());
            }
            
            Log.i(TAG, "\nPolicy Suggestion:\n" + generatePolicySuggestion(denials));
        } else {
            Log.i(TAG, "✓ No AVC denials found");
        }
        
        Log.d(TAG, "==========================");
    }
    
    /**
     * AVC Denial data class
     */
    public static class AVCDenial {
        public String rawLine;
        public String sourceContext;
        public String targetContext;
        public String targetClass;
        public String permission;
        
        @Override
        public String toString() {
            return String.format(
                "Denied: %s -> %s (class=%s, perm=%s)",
                extractType(sourceContext),
                extractType(targetContext),
                targetClass,
                permission
            );
        }
    }
}
