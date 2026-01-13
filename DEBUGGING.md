# Debugging Guide - Priv-App Sysfs Access

## Overview

Branch ini include 2 debug utilities untuk troubleshoot SELinux policy dan sysfs access:

1. **SELinuxDebugger** - Auto-detect AVC denials dan generate policy suggestions
2. **SysfsAccessLogger** - Wrapper untuk sysfs read/write dengan detailed logging

## Setup

### 1. Enable Debug Logging

Debug logging **sudah enabled by default** di `build.gradle`:

```gradle
buildConfigField "boolean", "ENABLE_SYSFS_LOGGING", "true"
```

### 2. Filter Logcat

```bash
# Monitor semua BatteryMonitor logs
adb logcat BatteryMonitor:* *:S

# Specific tags
adb logcat SELinuxDebugger:D SysfsAccess:D *:S

# Dengan timestamp
adb logcat -v time SELinuxDebugger:* SysfsAccess:* *:S
```

## Usage Examples

### Basic Sysfs Access Logging

```java
import com.deviant.batterymonitor.SysfsAccessLogger;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Read battery capacity dengan auto-logging
        String capacity = SysfsAccessLogger.readSysfs(
            SysfsAccessLogger.BATTERY_CAPACITY
        );
        
        if (capacity != null) {
            Log.i(TAG, "Battery: " + capacity + "%");
        } else {
            Log.e(TAG, "Failed to read battery capacity");
        }
        
        // Write LED brightness
        boolean success = SysfsAccessLogger.writeSysfs(
            "/sys/class/leds/red/brightness",
            "255"
        );
        
        if (!success) {
            Log.e(TAG, "Failed to control LED");
        }
    }
}
```

**Output Log:**
```
D/SysfsAccess: [✓] READ /sys/class/power_supply/battery/capacity - 85 (125.30µs)
E/SysfsAccess: [✗] WRITE /sys/class/leds/red/brightness - SecurityException: Permission denied (value=255) (89.21µs)
E/SysfsAccess: App context: u:r:priv_app:s0:c512,c768
E/SysfsAccess: SELinux mode: Enforcing
```

### SELinux Denial Monitoring

```java
import com.deviant.batterymonitor.SELinuxDebugger;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onResume() {
        super.onResume();
        
        // Run SELinux diagnostics
        SELinuxDebugger.logDebugInfo();
        
        // Check SELinux mode
        String mode = SELinuxDebugger.getSELinuxMode();
        Log.i(TAG, "SELinux: " + mode);
        
        // Get app context
        String context = SELinuxDebugger.getAppContext();
        Log.i(TAG, "Context: " + context);
    }
}
```

**Output Log:**
```
D/SELinuxDebugger: === SELinux Debug Info ===
D/SELinuxDebugger: SELinux Mode: Enforcing
D/SELinuxDebugger: App Context: u:r:priv_app:s0:c512,c768
D/SELinuxDebugger: Recent Denials: 2
W/SELinuxDebugger: ⚠️ AVC Denials Detected:
W/SELinuxDebugger:   - Denied: priv_app -> sysfs_leds (class=file, perm=write)
W/SELinuxDebugger:   - Denied: priv_app -> sysfs_battery_supply (class=file, perm=read)
I/SELinuxDebugger: 
Policy Suggestion:
;; Generated policy suggestions
;; Add to vendor_sepolicy.cil

(allow priv_app sysfs_leds (file (write)))
(allow priv_app sysfs_battery_supply (file (read)))

I/SELinuxDebugger: ==========================
```

### Comprehensive Diagnostics

```java
public class DiagnosticActivity extends AppCompatActivity {
    private static final String TAG = "Diagnostics";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        runFullDiagnostics();
    }
    
    private void runFullDiagnostics() {
        Log.i(TAG, "=== Starting Diagnostics ===");
        
        // 1. Check SELinux status
        Log.i(TAG, "[1/4] SELinux Status");
        SELinuxDebugger.logDebugInfo();
        
        // 2. Test common sysfs paths
        Log.i(TAG, "[2/4] Testing Sysfs Paths");
        testSysfsPaths();
        
        // 3. Search for device-specific paths
        Log.i(TAG, "[3/4] Searching Device Paths");
        searchDevicePaths();
        
        // 4. Print statistics
        Log.i(TAG, "[4/4] Access Statistics");
        SysfsAccessLogger.printStats();
        
        Log.i(TAG, "=== Diagnostics Complete ===");
    }
    
    private void testSysfsPaths() {
        String[] testPaths = {
            SysfsAccessLogger.BATTERY_CAPACITY,
            SysfsAccessLogger.BATTERY_VOLTAGE,
            SysfsAccessLogger.BATTERY_TEMP,
            SysfsAccessLogger.BATTERY_STATUS,
            "/sys/class/leds/red/brightness",
            "/sys/class/leds/green/brightness",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
        };
        
        for (String path : testPaths) {
            String value = SysfsAccessLogger.readSysfs(path);
            if (value != null) {
                Log.i(TAG, "  ✓ " + path + " = " + value);
            } else {
                Log.e(TAG, "  ✗ " + path + " (failed)");
            }
        }
    }
    
    private void searchDevicePaths() {
        // Find battery nodes
        String[] batteryPaths = SysfsAccessLogger.findSysfsPaths(
            "/sys/class/power_supply",
            "capacity"
        );
        Log.i(TAG, "  Found " + batteryPaths.length + " battery capacity nodes:");
        for (String path : batteryPaths) {
            Log.i(TAG, "    - " + path);
        }
        
        // Find LED nodes
        String[] ledPaths = SysfsAccessLogger.findSysfsPaths(
            "/sys/class/leds",
            "brightness"
        );
        Log.i(TAG, "  Found " + ledPaths.length + " LED brightness nodes:");
        for (String path : ledPaths) {
            Log.i(TAG, "    - " + path);
        }
    }
}
```

**Output:**
```
I/Diagnostics: === Starting Diagnostics ===
I/Diagnostics: [1/4] SELinux Status
D/SELinuxDebugger: === SELinux Debug Info ===
D/SELinuxDebugger: SELinux Mode: Enforcing
D/SELinuxDebugger: App Context: u:r:priv_app:s0:c512,c768
I/SELinuxDebugger: ✓ No AVC denials found

I/Diagnostics: [2/4] Testing Sysfs Paths
D/SysfsAccess: [✓] READ /sys/class/power_supply/battery/capacity - 85 (120.45µs)
I/Diagnostics:   ✓ /sys/class/power_supply/battery/capacity = 85
D/SysfsAccess: [✓] READ /sys/class/power_supply/battery/voltage_now - 4186000 (98.32µs)
I/Diagnostics:   ✓ /sys/class/power_supply/battery/voltage_now = 4186000
E/SysfsAccess: [✗] READ /sys/class/leds/red/brightness - Permission denied (canRead=false) (0.00µs)
E/Diagnostics:   ✗ /sys/class/leds/red/brightness (failed)

I/Diagnostics: [3/4] Searching Device Paths
I/Diagnostics:   Found 3 battery capacity nodes:
I/Diagnostics:     - /sys/class/power_supply/battery/capacity
I/Diagnostics:     - /sys/class/power_supply/usb/capacity
I/Diagnostics:     - /sys/class/power_supply/bms/capacity
I/Diagnostics:   Found 5 LED brightness nodes:
I/Diagnostics:     - /sys/class/leds/red/brightness
I/Diagnostics:     - /sys/class/leds/green/brightness
I/Diagnostics:     - /sys/class/leds/blue/brightness
I/Diagnostics:     - /sys/class/leds/lcd-backlight/brightness
I/Diagnostics:     - /sys/class/leds/keyboard-backlight/brightness

I/Diagnostics: [4/4] Access Statistics
D/SysfsAccess: === Sysfs Access Statistics ===
D/SysfsAccess: /sys/class/power_supply/battery/capacity: 5/5 success (100.0%), avg 115.23µs
D/SysfsAccess: /sys/class/power_supply/battery/voltage_now: 3/3 success (100.0%), avg 102.45µs
D/SysfsAccess: /sys/class/leds/red/brightness: 0/2 success (0.0%), avg 0.00µs
D/SysfsAccess: ===============================

I/Diagnostics: === Diagnostics Complete ===
```

## Auto-Detection Features

### Trigger SELinux Diagnostics on First Failure

`SysfsAccessLogger` automatically runs `SELinuxDebugger.logDebugInfo()` pada **first failure** untuk setiap path:

```java
// First attempt
String value = SysfsAccessLogger.readSysfs("/sys/class/leds/red/brightness");
// Output:
// E/SysfsAccess: [✗] READ /sys/class/leds/red/brightness - Permission denied
// W/SysfsAccess: First failure detected, running SELinux diagnostics...
// D/SELinuxDebugger: === SELinux Debug Info ===
// ...

// Subsequent attempts (tidak trigger diagnostics lagi)
value = SysfsAccessLogger.readSysfs("/sys/class/leds/red/brightness");
// Output:
// E/SysfsAccess: [✗] READ /sys/class/leds/red/brightness - Permission denied
```

### Device-Specific Path Discovery

```java
// Auto-find battery paths
String[] batteryNodes = SysfsAccessLogger.findSysfsPaths(
    "/sys/class/power_supply",
    "capacity"
);

// Test semua found paths
for (String path : batteryNodes) {
    String value = SysfsAccessLogger.readSysfs(path);
    if (value != null) {
        Log.i(TAG, "Working battery node: " + path);
        break;  // Use this path
    }
}
```

## Workflow: Debugging SELinux Denials

### Step 1: Run App dengan Logger Enabled

```bash
# Install debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Clear logcat
adb logcat -c

# Start logging
adb logcat SELinuxDebugger:* SysfsAccess:* *:S

# Launch app
adb shell monkey -p com.deviant.batterymonitor.debug 1
```

### Step 2: Analyze Output

Look for:
- ❌ `[✗]` markers = failed access
- `SecurityException` = SELinux denial
- `Permission denied (canRead=false)` = file permissions atau SELinux
- AVC denial details dari `SELinuxDebugger`

### Step 3: Generate Policy

Dari log output, copy policy suggestion:

```cil
;; From SELinuxDebugger output
(allow priv_app sysfs_leds (file (write open)))
(allow priv_app sysfs_battery_supply (file (read open)))
```

### Step 4: Apply Policy

```bash
# Append to vendor_sepolicy.cil
echo '(allow priv_app sysfs_leds (file (write open)))' >> device/vendor/codename/sepolicy/vendor/vendor_sepolicy.cil

# Rebuild ROM atau repack vendor.img
mka bacon
```

### Step 5: Verify

```bash
# Flash updated ROM/vendor
adb reboot bootloader
fastboot flash vendor vendor.img
fastboot reboot

# Re-test
adb logcat SELinuxDebugger:* SysfsAccess:* *:S
# Expected: [✓] markers untuk previously failed paths
```

## Advanced: Runtime Policy Testing

### Magisk supolicy (Temporary Testing)

```bash
# Set permissive untuk specific context (non-persistent)
adb shell su -c 'setenforce 0'

# Test app
# ...

# Restore enforcing
adb shell su -c 'setenforce 1'
```

### Live Policy Injection

```bash
# Inject policy without reboot (requires Magisk)
adb shell su -c 'supolicy --live "allow priv_app sysfs_leds file { read write open }"'

# Test immediately
# Policy active until reboot
```

## Troubleshooting

### No Logs Appearing

**Problem:** Logcat tidak show logs dari SELinuxDebugger/SysfsAccessLogger

**Solution:**
```bash
# Check if app running
adb shell ps | grep batterymonitor

# Check logcat buffer
adb logcat -b all -d | grep -i selinux

# Verify BuildConfig.DEBUG
adb shell dumpsys package com.deviant.batterymonitor | grep debuggable
```

### "Permission denied" for dmesg

**Problem:** `SELinuxDebugger.scanRecentDenials()` returns empty

**Cause:** Non-root app tidak bisa access dmesg di enforcing mode

**Solution:**
1. Add `DUMP` permission di manifest (sudah included)
2. Install sebagai priv-app (required)
3. Atau test dengan `setenforce 0` temporary

### Wrong SELinux Context

**Problem:** App context bukan `u:r:priv_app:s0`

**Check:**
```bash
adb shell ps -AZ | grep batterymonitor
```

**Expected:** `u:r:priv_app:s0:c512,c768 ... com.deviant.batterymonitor`

**If different:**
- App tidak installed di `/system/priv-app/`
- `file_contexts` tidak applied
- Manifest missing `android:sharedUserId="android.uid.system"`

## Performance Impact

### Logging Overhead

- Average logging overhead: **~50-100µs** per access
- Negligible untuk UI operations
- Production builds: Disable via `BuildConfig.ENABLE_SYSFS_LOGGING = false`

### Statistics Storage

- In-memory only (HashMap)
- Cleared on app restart
- ~200 bytes per unique path

## Best Practices

1. **Always test di debug build first**
   ```gradle
   buildConfigField "boolean", "ENABLE_SYSFS_LOGGING", "true"
   ```

2. **Check access sebelum repeated operations**
   ```java
   if (SysfsAccessLogger.checkAccess(path)) {
       // Path accessible, proceed
   }
   ```

3. **Print stats before app exit**
   ```java
   @Override
   protected void onDestroy() {
       SysfsAccessLogger.printStats();
       super.onDestroy();
   }
   ```

4. **Save policy suggestions**
   ```java
   List<AVCDenial> denials = SELinuxDebugger.scanRecentDenials();
   String policy = SELinuxDebugger.generatePolicySuggestion(denials);
   // Write to file atau display di UI
   ```

## Production Builds

Untuk release builds, disable verbose logging tapi keep error reporting:

```gradle
buildTypes {
    release {
        buildConfigField "boolean", "ENABLE_SYSFS_LOGGING", "false"
        
        // ProGuard akan remove Log.d/Log.v calls
        minifyEnabled true
    }
}
```

Error logs (`Log.e()`) tetap enabled untuk crash reporting.

## References

- [Android SELinux](https://source.android.com/docs/security/features/selinux)
- [audit2allow](https://linux.die.net/man/1/audit2allow)
- [Sysfs Documentation](https://www.kernel.org/doc/Documentation/filesystems/sysfs.txt)
