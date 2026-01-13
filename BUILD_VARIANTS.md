# Build Variants Guide

## Overview

Repo ini support 2 build flavors untuk handle deployment scenarios:

### 1. **Standalone Flavor** (Development/Testing)
- **Purpose**: Local testing tanpa ROM integration
- **Installation**: Via `adb install` biasa
- **Certificate**: Debug/release signing key
- **Permissions**: Limited (standard app permissions)
- **Sysfs Access**: ❌ Gagal di enforcing mode (tidak ada SELinux policy)
- **Use Case**: Development, UI testing, WebView functionality

### 2. **Privapp Flavor** (Production ROM Build)
- **Purpose**: System priv-app dengan full sysfs access
- **Installation**: Flash via ROM atau Magisk module
- **Certificate**: Platform certificate (system signature)
- **Permissions**: Full system permissions + sysfs access via SELinux
- **Sysfs Access**: ✅ Full access dengan proper policy
- **Use Case**: Final ROM integration, production builds

---

## Build Commands

### Standalone Debug (Recommended untuk Testing)
```bash
./gradlew assembleStandaloneDebug

# Output: app/build/outputs/apk/standalone/debug/app-standalone-debug.apk
# Install:
adb install -r app/build/outputs/apk/standalone/debug/app-standalone-debug.apk
```

**Features:**
- ✅ Install langsung via adb
- ✅ Debug logging enabled
- ✅ WebView functionality works
- ❌ Sysfs access limited (permission denied di enforcing)
- ❌ System permissions tidak aktif

**Package ID:** `com.deviant.batterymonitor.standalone`

---

### Standalone Release
```bash
./gradlew assembleStandaloneRelease

# Output: app/build/outputs/apk/standalone/release/app-standalone-release.apk
```

**Features:**
- Code minified dengan ProGuard
- Debug logs stripped
- Signed dengan release key
- Bisa upload ke Play Store (jika needed)

---

### Privapp Debug
```bash
./gradlew assemblePrivappDebug

# Output: app/build/outputs/apk/privapp/debug/app-privapp-debug.apk
```

**Warning:** ⚠️ Tidak bisa install via `adb install` karena `sharedUserId=system`

```bash
# Install akan FAIL:
adb install app-privapp-debug.apk
# Error: INSTALL_FAILED_SHARED_USER_INCOMPATIBLE
```

**Correct Installation:**
```bash
# Push ke system partition (requires root)
adb root
adb remount
adb push app-privapp-debug.apk /system/priv-app/BatteryMonitor/BatteryMonitor.apk
adb shell chmod 644 /system/priv-app/BatteryMonitor/BatteryMonitor.apk
adb reboot
```

**Package ID:** `com.deviant.batterymonitor`

---

### Privapp Release (ROM Integration)
```bash
./gradlew assemblePrivappRelease

# Output: app/build/outputs/apk/privapp/release/app-privapp-release.apk
```

**Features:**
- ✅ `android:sharedUserId="android.uid.system"`
- ✅ `android:persistent="true"`
- ✅ All system permissions enabled
- ✅ SELinux context: `u:r:priv_app:s0`
- ✅ Full sysfs access (dengan proper policy)
- ⚠️ **MUST** be signed dengan platform certificate di ROM build

**Integration ke ROM:**
```bash
# Copy APK ke device tree
cp app/build/outputs/apk/privapp/release/app-privapp-release.apk \
   device/vendor/codename/proprietary/priv-app/BatteryMonitor/BatteryMonitor.apk

# Build ROM - akan auto re-sign dengan platform key
mka bacon
```

---

## BuildConfig Differences

### Standalone Flavor
```java
BuildConfig.APPLICATION_ID = "com.deviant.batterymonitor.standalone"
BuildConfig.IS_PRIV_APP = false
BuildConfig.ENABLE_SYSFS_LOGGING = true
```

### Privapp Flavor
```java
BuildConfig.APPLICATION_ID = "com.deviant.batterymonitor"
BuildConfig.IS_PRIV_APP = true
BuildConfig.ENABLE_SYSFS_LOGGING = true
```

---

## Runtime Detection

App bisa detect flavor via BuildConfig:

```java
if (BuildConfig.IS_PRIV_APP) {
    Log.i(TAG, "Running as privileged system app");
    // Enable sysfs features
} else {
    Log.w(TAG, "Running as standalone app - limited permissions");
    // Fallback to standard Android APIs
}
```

---

## Testing Workflow

### Phase 1: UI Development (Standalone)
```bash
# Build & install
./gradlew installStandaloneDebug

# Hot reload changes
./gradlew assembleStandaloneDebug
adb install -r app/build/outputs/apk/standalone/debug/app-standalone-debug.apk

# Monitor logs
adb logcat BatteryMonitor:* *:S
```

**Test:**
- ✅ WebView rendering
- ✅ UI functionality
- ✅ Network access
- ⚠️ Sysfs access (akan fail - expected)

### Phase 2: SELinux Policy Testing (Privapp)
```bash
# Build privapp variant
./gradlew assemblePrivappDebug

# Push ke system (root required)
adb root && adb remount
adb push app/build/outputs/apk/privapp/debug/app-privapp-debug.apk \
         /system/priv-app/BatteryMonitor/BatteryMonitor.apk
adb shell chmod 644 /system/priv-app/BatteryMonitor/BatteryMonitor.apk
adb reboot

# Monitor sysfs access
adb logcat SELinuxDebugger:* SysfsAccess:* *:S
```

**Test:**
- ✅ App context: `u:r:priv_app:s0`
- ✅ Sysfs read/write
- ✅ SELinux denials detection
- ✅ Policy suggestions

### Phase 3: ROM Integration (Privapp Release)
```bash
# Build release APK
./gradlew assemblePrivappRelease

# Integrate ke ROM
./integrate_to_device.sh ~/android/lineage/device/vendor/codename

# Build ROM
cd $ROM_SOURCE && mka bacon

# Flash & verify
fastboot flash system system.img
fastboot flash vendor vendor.img
fastboot reboot

# Verify installation
adb shell pm list packages | grep batterymonitor
adb shell dumpsys package com.deviant.batterymonitor | grep -i priv
```

---

## Troubleshooting

### Error: INSTALL_FAILED_SHARED_USER_INCOMPATIBLE

**Cause:** Trying to install privapp variant via `adb install`

**Solution:**
```bash
# Option 1: Use standalone flavor
./gradlew installStandaloneDebug

# Option 2: Push ke /system (root)
adb root && adb remount
adb push app-privapp-debug.apk /system/priv-app/BatteryMonitor/BatteryMonitor.apk
adb reboot
```

### Error: Package ... has no signatures that match

**Cause:** Privapp variant tidak di-sign dengan platform certificate

**Solution:** Build via ROM build system, bukan manual Gradle:
```bash
# Jangan:
./gradlew assemblePrivappRelease
adb install app-privapp-release.apk  # FAIL

# Lakukan:
cp app-privapp-release.apk device/vendor/codename/proprietary/priv-app/...
mka bacon  # ROM build akan re-sign dengan platform key
```

### Sysfs Access Denied (Standalone)

**Expected Behavior:** Standalone flavor tidak punya sysfs access

```bash
E/SysfsAccess: [✗] READ /sys/class/power_supply/battery/capacity - Permission denied
```

**Solution:** Gunakan privapp flavor atau set `setenforce 0` (temporary testing)

### Wrong SELinux Context

```bash
# Check app context
adb shell ps -AZ | grep batterymonitor

# Standalone: u:r:untrusted_app:s0 (correct)
# Privapp: u:r:priv_app:s0 (correct)
```

Jika privapp variant show `untrusted_app`, artinya:
- Tidak installed di `/system/priv-app/`
- `file_contexts` tidak applied

---

## Recommended Workflow

```
┌─────────────────────┐
│   Development       │
│ (Standalone Debug)  │
│                     │
│ - UI testing        │
│ - WebView logic     │
│ - Rapid iteration   │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  Policy Testing     │
│  (Privapp Debug)    │
│                     │
│ - Push to /system   │
│ - Test sysfs access │
│ - Fix SELinux denials│
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  ROM Integration    │
│ (Privapp Release)   │
│                     │
│ - Full ROM build    │
│ - Platform signing  │
│ - Production ready  │
└─────────────────────┘
```

---

## Quick Reference

| Build Variant | Command | Output APK | Install Method | sharedUserId | Sysfs Access |
|--------------|---------|------------|----------------|--------------|-------------|
| Standalone Debug | `assembleStandaloneDebug` | `app-standalone-debug.apk` | `adb install` | ❌ None | ❌ Limited |
| Standalone Release | `assembleStandaloneRelease` | `app-standalone-release.apk` | `adb install` | ❌ None | ❌ Limited |
| Privapp Debug | `assemblePrivappDebug` | `app-privapp-debug.apk` | Push to `/system` | ✅ system | ✅ Full |
| Privapp Release | `assemblePrivappRelease` | `app-privapp-release.apk` | ROM build | ✅ system | ✅ Full |

---

## Next Steps

1. **Start Development:**
   ```bash
   ./gradlew installStandaloneDebug
   ```

2. **Test Sysfs Access:**
   ```bash
   ./gradlew assemblePrivappDebug
   adb root && adb push ... /system/priv-app/
   ```

3. **ROM Integration:**
   ```bash
   ./gradlew assemblePrivappRelease
   ./integrate_to_device.sh <device_tree>
   mka bacon
   ```
