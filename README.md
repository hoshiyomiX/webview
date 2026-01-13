# BatteryMonitor - WebView App dengan Priv-App Sysfs Access

Android WebView template yang modified untuk run sebagai privileged system app dengan full sysfs access (battery, LED, CPU info, etc).

## Features

- ✅ WebView-based UI
- ✅ Priv-app build untuk ROM integration
- ✅ SELinux policy untuk sysfs access
- ✅ Auto-detect AVC denials dengan logging
- ✅ Debug utilities untuk troubleshoot permissions
- ✅ Standalone build untuk development tanpa root

## Quick Start

### Development (Standalone Build)

Untuk testing UI tanpa ROM integration:

```bash
# Build & install
./gradlew installStandaloneDebug

# Monitor logs
adb logcat BatteryMonitor:* *:S
```

**Features:**
- ✅ Install via `adb install` biasa
- ✅ WebView functionality works
- ✅ Debug logging enabled
- ⚠️ Sysfs access limited (expected)

---

### Production (Priv-App Build)

Untuk ROM integration dengan full sysfs access:

```bash
# Build APK
./gradlew assemblePrivappRelease

# Integrate ke device tree
./integrate_to_device.sh ~/android/lineage/device/vendor/codename

# Build ROM
cd $ROM_SOURCE
. build/envsetup.sh
lunch lineage_codename-userdebug
mka bacon
```

**Output:** APK di-install ke `/system/priv-app/BatteryMonitor/` dengan:
- ✅ Platform signature
- ✅ SELinux context: `u:r:priv_app:s0`
- ✅ Full sysfs access

---

## Build Variants

### 1. Standalone Flavor (Development)
- **Package:** `com.deviant.batterymonitor.standalone`
- **Install:** Via `adb install`
- **Permissions:** Standard app permissions
- **Use Case:** UI development, testing WebView logic

```bash
./gradlew assembleStandaloneDebug
adb install -r app/build/outputs/apk/standalone/debug/app-standalone-debug.apk
```

### 2. Privapp Flavor (Production)
- **Package:** `com.deviant.batterymonitor`
- **Install:** Flash via ROM atau push ke `/system`
- **Permissions:** System UID + sysfs access via SELinux
- **Use Case:** ROM integration, production builds

```bash
./gradlew assemblePrivappRelease
# Cannot install via adb - requires ROM build atau root push
```

**Details:** See [BUILD_VARIANTS.md](BUILD_VARIANTS.md)

---

## SELinux Policy

App include CIL policies untuk access:
- `/sys/class/power_supply/battery/*` - Battery info (capacity, voltage, temp)
- `/sys/class/leds/*/brightness` - LED control
- `/sys/devices/system/cpu/*` - CPU frequency info
- `/sys/power/*` - Power management

**Policy Files:**
- [`sepolicy/vendor_sepolicy.cil`](sepolicy/vendor_sepolicy.cil) - SELinux rules
- [`sepolicy/vendor_file_contexts`](sepolicy/vendor_file_contexts) - File labeling
- [`sepolicy/README.md`](sepolicy/README.md) - Policy documentation

**Auto-apply via:**
```bash
./integrate_to_device.sh <device_tree_path>
```

---

## Debug Utilities

App include built-in debugging tools:

### SELinuxDebugger
Auto-detect AVC denials dan generate policy suggestions:

```java
SELinuxDebugger.logDebugInfo();
// Output:
// D/SELinuxDebugger: SELinux Mode: Enforcing
// W/SELinuxDebugger: ⚠️ AVC Denials Detected:
// I/SELinuxDebugger: Policy Suggestion:
// (allow priv_app sysfs_leds (file (write)))
```

### SysfsAccessLogger
Wrapper untuk sysfs I/O dengan detailed logging:

```java
String capacity = SysfsAccessLogger.readSysfs(
    SysfsAccessLogger.BATTERY_CAPACITY
);
// Output:
// D/SysfsAccess: [✓] READ /sys/class/power_supply/battery/capacity - 85 (125.30µs)
```

**Usage Guide:** [DEBUGGING.md](DEBUGGING.md)

---

## Testing

### Verify Installation

```bash
# Check app installed
adb shell pm list packages | grep batterymonitor

# Check priv-app status
adb shell dumpsys package com.deviant.batterymonitor | grep -i priv

# Check SELinux context
adb shell ps -AZ | grep batterymonitor
# Expected: u:r:priv_app:s0:c512,c768
```

### Test Sysfs Access

```bash
# Run automated test script
./test_sysfs_access.sh

# Manual test
adb logcat SELinuxDebugger:* SysfsAccess:* *:S
# Launch app, check untuk [✓] atau [✗] markers
```

---

## Documentation

- [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) - ROM integration guide
- [BUILD_VARIANTS.md](BUILD_VARIANTS.md) - Detailed build flavors explanation
- [DEBUGGING.md](DEBUGGING.md) - Troubleshooting SELinux denials
- [sepolicy/README.md](sepolicy/README.md) - SELinux policy reference

---

## Requirements

### Development Build (Standalone)
- Android Studio Arctic Fox+
- Android SDK 21+
- Standard development environment

### Production Build (Privapp)
- AOSP/LineageOS build environment
- Device tree dengan SELinux policy support
- Platform signing keys

---

## File Structure

```
.
├── Android.mk                      # AOSP build integration
├── app/
│   ├── src/
│   │   ├── main/                   # Shared source
│   │   │   ├── java/.../
│   │   │   │   ├── SELinuxDebugger.java
│   │   │   │   └── SysfsAccessLogger.java
│   │   │   └── AndroidManifest.xml
│   │   └── privapp/                # Privapp-specific manifest
│   │       └── AndroidManifest.xml (sharedUserId injection)
│   └── build.gradle                # Product flavors config
├── sepolicy/
│   ├── vendor_sepolicy.cil         # SELinux CIL rules
│   ├── vendor_file_contexts        # File labeling
│   └── README.md
├── integrate_to_device.sh          # Device tree integration script
├── test_sysfs_access.sh           # Automated testing
├── BUILD_INSTRUCTIONS.md
├── BUILD_VARIANTS.md
└── DEBUGGING.md
```

---

## Troubleshooting

### INSTALL_FAILED_SHARED_USER_INCOMPATIBLE

**Problem:** Cannot install privapp variant via `adb install`

**Solution:**
```bash
# Use standalone build untuk development
./gradlew installStandaloneDebug

# Atau push privapp ke /system (root required)
adb root && adb remount
adb push app-privapp-debug.apk /system/priv-app/BatteryMonitor/BatteryMonitor.apk
adb reboot
```

### Sysfs Permission Denied

**Problem:** `E/SysfsAccess: [✗] Permission denied`

**Check:**
1. App installed di `/system/priv-app/`?
2. SELinux policy applied?
3. App context = `u:r:priv_app:s0`?

**Debug:**
```bash
# Run diagnostics
adb logcat SELinuxDebugger:D *:S
# Launch app, check output untuk policy suggestions

# Check denials
adb shell su -c "dmesg | grep avc | grep batterymonitor"
```

**Solution:** See [DEBUGGING.md](DEBUGGING.md#troubleshooting)

---

## Security Notes

⚠️ **Warning:**
- Policy ini memberikan priv-app access ke sysfs nodes
- **JANGAN distribute public builds** dengan policy ini
- Hanya untuk personal ROM builds
- Validate input sebelum write ke sysfs untuk prevent hardware damage

✅ **Best Practices:**
- Implement rate limiting untuk write operations
- Add bounds checking untuk brightness/voltage values
- Log all sysfs operations untuk audit
- Use standalone build untuk public releases

---

## Contributing

This is a personal ROM project. Fork freely untuk your own builds.

---

## License

Base template from [slymax/webview](https://github.com/slymax/webview)

Modifications (priv-app integration, SELinux policies, debug utils) by hoshiyomiX

---

## Credits

- Base WebView template: [slymax](https://github.com/slymax)
- SELinux policy references: [Android Source](https://source.android.com/docs/security/features/selinux)
- Debug utilities: Custom implementation
