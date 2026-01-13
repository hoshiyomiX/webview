package com.deviant.batterymonitor;

import android.util.Log;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Sysfs Access Logger
 * Wrapper untuk sysfs read/write dengan auto-logging
 */
public class SysfsAccessLogger {
    private static final String TAG = "SysfsAccess";
    
    // Common sysfs paths
    public static final String BATTERY_CAPACITY = "/sys/class/power_supply/battery/capacity";
    public static final String BATTERY_VOLTAGE = "/sys/class/power_supply/battery/voltage_now";
    public static final String BATTERY_CURRENT = "/sys/class/power_supply/battery/current_now";
    public static final String BATTERY_TEMP = "/sys/class/power_supply/battery/temp";
    public static final String BATTERY_STATUS = "/sys/class/power_supply/battery/status";
    
    // Track access success/failure
    private static final Map<String, AccessStats> statsMap = new HashMap<>();
    
    /**
     * Read sysfs file dengan logging
     */
    public static String readSysfs(String path) {
        long startTime = System.nanoTime();
        String result = null;
        boolean success = false;
        String error = null;
        
        try {
            File file = new File(path);
            
            // Check file exists
            if (!file.exists()) {
                error = "File not found";
                logAccess(path, "READ", false, error, 0);
                return null;
            }
            
            // Check readable
            if (!file.canRead()) {
                error = "Permission denied (canRead=false)";
                logAccess(path, "READ", false, error, 0);
                return null;
            }
            
            // Try read
            BufferedReader reader = new BufferedReader(new FileReader(file));
            result = reader.readLine();
            reader.close();
            
            success = true;
            long elapsed = System.nanoTime() - startTime;
            logAccess(path, "READ", true, result, elapsed);
            
        } catch (SecurityException e) {
            error = "SecurityException: " + e.getMessage();
            logAccess(path, "READ", false, error, System.nanoTime() - startTime);
            
            // Log SELinux context untuk debugging
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Security violation reading " + path, e);
                Log.e(TAG, "App context: " + SELinuxDebugger.getAppContext());
                Log.e(TAG, "SELinux mode: " + SELinuxDebugger.getSELinuxMode());
            }
            
        } catch (IOException e) {
            error = "IOException: " + e.getMessage();
            logAccess(path, "READ", false, error, System.nanoTime() - startTime);
        } catch (Exception e) {
            error = "Exception: " + e.getMessage();
            logAccess(path, "READ", false, error, System.nanoTime() - startTime);
        }
        
        return result;
    }
    
    /**
     * Write sysfs file dengan logging
     */
    public static boolean writeSysfs(String path, String value) {
        long startTime = System.nanoTime();
        boolean success = false;
        String error = null;
        
        try {
            File file = new File(path);
            
            // Check file exists
            if (!file.exists()) {
                error = "File not found";
                logAccess(path, "WRITE", false, error + " (value=" + value + ")", 0);
                return false;
            }
            
            // Check writable
            if (!file.canWrite()) {
                error = "Permission denied (canWrite=false)";
                logAccess(path, "WRITE", false, error + " (value=" + value + ")", 0);
                return false;
            }
            
            // Try write
            FileWriter writer = new FileWriter(file);
            writer.write(value);
            writer.flush();
            writer.close();
            
            success = true;
            long elapsed = System.nanoTime() - startTime;
            logAccess(path, "WRITE", true, "Wrote: " + value, elapsed);
            
        } catch (SecurityException e) {
            error = "SecurityException: " + e.getMessage();
            logAccess(path, "WRITE", false, error + " (value=" + value + ")", 
                     System.nanoTime() - startTime);
            
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Security violation writing to " + path, e);
                Log.e(TAG, "App context: " + SELinuxDebugger.getAppContext());
            }
            
        } catch (IOException e) {
            error = "IOException: " + e.getMessage();
            logAccess(path, "WRITE", false, error + " (value=" + value + ")", 
                     System.nanoTime() - startTime);
        } catch (Exception e) {
            error = "Exception: " + e.getMessage();
            logAccess(path, "WRITE", false, error + " (value=" + value + ")", 
                     System.nanoTime() - startTime);
        }
        
        return success;
    }
    
    /**
     * Check if sysfs path exists dan accessible
     */
    public static boolean checkAccess(String path) {
        File file = new File(path);
        boolean exists = file.exists();
        boolean readable = exists && file.canRead();
        boolean writable = exists && file.canWrite();
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, String.format(
                "[CHECK] %s - exists:%b read:%b write:%b",
                path, exists, readable, writable
            ));
        }
        
        return readable;
    }
    
    /**
     * Log access attempt
     */
    private static void logAccess(String path, String operation, boolean success, 
                                  String message, long elapsedNanos) {
        // Update stats
        AccessStats stats = statsMap.get(path);
        if (stats == null) {
            stats = new AccessStats(path);
            statsMap.put(path, stats);
        }
        
        if (success) {
            stats.successCount++;
        } else {
            stats.failureCount++;
        }
        stats.totalTimeNanos += elapsedNanos;
        
        // Log to logcat
        String logMsg = String.format(
            "[%s] %s %s - %s (%.2fµs)",
            success ? "✓" : "✗",
            operation,
            path,
            message,
            elapsedNanos / 1000.0
        );
        
        if (success) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, logMsg);
            }
        } else {
            Log.e(TAG, logMsg);
            
            // Trigger SELinux debug pada first failure
            if (stats.failureCount == 1 && BuildConfig.DEBUG) {
                Log.w(TAG, "First failure detected for " + path + ", running SELinux diagnostics...");
                SELinuxDebugger.logDebugInfo();
            }
        }
    }
    
    /**
     * Print access statistics
     */
    public static void printStats() {
        if (!BuildConfig.DEBUG) return;
        
        Log.d(TAG, "=== Sysfs Access Statistics ===");
        for (Map.Entry<String, AccessStats> entry : statsMap.entrySet()) {
            AccessStats stats = entry.getValue();
            Log.d(TAG, stats.toString());
        }
        Log.d(TAG, "===============================");
    }
    
    /**
     * Access statistics per path
     */
    private static class AccessStats {
        String path;
        int successCount = 0;
        int failureCount = 0;
        long totalTimeNanos = 0;
        
        AccessStats(String path) {
            this.path = path;
        }
        
        @Override
        public String toString() {
            int total = successCount + failureCount;
            double avgMicros = total > 0 ? (totalTimeNanos / 1000.0) / total : 0;
            double successRate = total > 0 ? (successCount * 100.0) / total : 0;
            
            return String.format(
                "%s: %d/%d success (%.1f%%), avg %.2fµs",
                path, successCount, total, successRate, avgMicros
            );
        }
    }
    
    /**
     * Find available sysfs paths untuk specific subsystem
     */
    public static String[] findSysfsPaths(String basePath, String filename) {
        java.util.List<String> foundPaths = new java.util.ArrayList<>();
        
        try {
            File baseDir = new File(basePath);
            if (!baseDir.exists() || !baseDir.isDirectory()) {
                return new String[0];
            }
            
            searchRecursive(baseDir, filename, foundPaths, 3); // Max 3 levels deep
            
        } catch (Exception e) {
            Log.e(TAG, "Error searching sysfs paths", e);
        }
        
        return foundPaths.toArray(new String[0]);
    }
    
    private static void searchRecursive(File dir, String filename, 
                                       java.util.List<String> results, int maxDepth) {
        if (maxDepth <= 0) return;
        
        try {
            File[] files = dir.listFiles();
            if (files == null) return;
            
            for (File file : files) {
                if (file.isFile() && file.getName().equals(filename)) {
                    results.add(file.getAbsolutePath());
                } else if (file.isDirectory()) {
                    searchRecursive(file, filename, results, maxDepth - 1);
                }
            }
        } catch (SecurityException e) {
            // Permission denied, skip
        }
    }
}
