# Build Instructions: BatteryMonitor Priv-App

## Untuk ROM Developers (AOSP/LineageOS Build)

### 1. Clone Repo ke ROM Source
```bash
cd $ROM_SOURCE_DIR
git clone https://github.com/hoshiyomiX/webview.git vendor/extra/BatteryMonitor
cd vendor/extra/BatteryMonitor
git checkout priv-app-system
```

### 2. Build APK (Android Studio atau Gradle)
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### 3. Integrate ke ROM Build System

#### Opsi A: Copy ke device tree
```bash
cd $ROM_SOURCE_DIR/device/VENDOR/CODENAME
mkdir -p proprietary/priv-app/BatteryMonitor
cp vendor/extra/BatteryMonitor/app/build/outputs/apk/release/app-release.apk \
   proprietary/priv-app/BatteryMonitor/BatteryMonitor.apk
```

Lalu tambahkan ke `device.mk`:
```makefile
PRODUCT_PACKAGES += \
    BatteryMonitor
```

#### Opsi B: Direct include di ROM build
Tambahkan ke `vendor/extra/extra.mk`:
```makefile
PRODUCT_PACKAGES += \
    BatteryMonitor

include vendor/extra/BatteryMonitor/Android.mk
```

### 4. Apply SELinux Policies

#### Method 1: Append ke vendor policy (Recommended)
```bash
cd $ROM_SOURCE_DIR/device/VENDOR/CODENAME/sepolicy/vendor

# Append CIL rules
cat $ROM_SOURCE_DIR/vendor/extra/BatteryMonitor/sepolicy/vendor_sepolicy.cil >> vendor_sepolicy.cil

# Append file contexts
cat $ROM_SOURCE_DIR/vendor/extra/BatteryMonitor/sepolicy/vendor_file_contexts >> file_contexts
```

#### Method 2: Create separate policy module
```bash
cp -r $ROM_SOURCE_DIR/vendor/extra/BatteryMonitor/sepolicy \
      $ROM_SOURCE_DIR/device/VENDOR/CODENAME/sepolicy/batterymonitor
```

Tambahkan ke `BoardConfig.mk`:
```makefile
BOARD_SEPOLICY_DIRS += \
    device/VENDOR/CODENAME/sepolicy/batterymonitor
```

### 5. Build ROM
```bash
cd $ROM_SOURCE_DIR
. build/envsetup.sh
lunch lineage_CODENAME-userdebug  # atau ROM flavor lain
mka bacon
```

---

## Untuk Flashable Zip (Tanpa ROM Build)

### 1. Build APK
```bash
./gradlew assembleRelease
```

### 2. Create Magisk Module (Alternative)
```bash
mkdir -p magisk_module/system/priv-app/BatteryMonitor
cp app/build/outputs/apk/release/app-release.apk \
   magisk_module/system/priv-app/BatteryMonitor/BatteryMonitor.apk
```

Create `module.prop`:
```ini
id=batterymonitor_privapp
name=BatteryMonitor Priv-App
version=1.0
versionCode=1
author=hoshiyomiX
description=Battery Monitor as privileged system app with sysfs access
```

### 3. SELinux Policy via Magisk
Create `service.sh`:
```bash
#!/system/bin/sh
MODDIR=${0%/*}

# Inject SELinux rules at boot
supolicy --live \
  "allow priv_app sysfs_battery_supply file { read write open getattr }" \
  "allow priv_app sysfs_leds file { read write open getattr }" \
  "allow priv_app sysfs_leds dir search"
```

---

## Verification

### Check App Installation
```bash
adb shell pm list packages | grep batterymonitor
adb shell dumpsys package com.deviant.batterymonitor | grep -i priv
```

### Check SELinux Context
```bash
adb shell ls -laZ /system/priv-app/BatteryMonitor
adb shell ps -AZ | grep batterymonitor
```

### Test sysfs Access
```bash
adb shell
su
running_app=$(pidof com.deviant.batterymonitor)
cat /proc/$running_app/status | grep Seccomp
```

---

## Sysfs Paths yang Diakses

Default paths (adjust sesuai device):
- `/sys/class/power_supply/battery/capacity`
- `/sys/class/power_supply/battery/temp`
- `/sys/class/leds/*/brightness`
- `/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq`

Check device-specific paths:
```bash
adb shell find /sys/class/power_supply -name "*battery*"
adb shell find /sys/class/leds -type f -name "brightness"
```

---

## Troubleshooting

### SELinux Denials
```bash
adb shell dmesg | grep avc | grep batterymonitor
adb logcat | grep -i selinux
```

### Permission Issues
```bash
adb shell getprop ro.build.selinux
adb shell getenforce  # Should be "Permissive" or "Enforcing"
```

Untuk debug, temporary set permissive:
```bash
adb shell su -c setenforce 0
```

### App Crash Logs
```bash
adb logcat -b crash | grep batterymonitor
adb logcat BatteryMonitor:V *:S
```
