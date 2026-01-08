# SELinux Policy Files for Battery Monitor

This directory contains SELinux policy files required to deploy Battery Monitor as a privileged system app with direct sysfs access.

## 📁 Files in This Directory

### 1. `vendor_sepolicy.cil`
**Purpose:** SELinux policy rules in CIL (Common Intermediate Language) format  
**Target Location:** `/vendor/etc/selinux/vendor_sepolicy.cil`  
**Action:** APPEND to existing file

**What it does:**
- Defines custom `sysfs_charger` type for charger voltage files
- Grants `priv_app` domain read access to charger sysfs
- Allows debug log writing to SD card
- Permits system service communication via Binder IPC

### 2. `vendor_file_contexts`
**Purpose:** File context labels for filesystem objects  
**Target Location:** `/vendor/etc/selinux/vendor_file_contexts`  
**Action:** APPEND to existing file

**What it does:**
- Labels `/product/priv-app/BatteryMonitor/` as `privapp_data_file`
- Labels charger sysfs paths as `sysfs_charger`
- Covers multiple device-specific charger paths (MTK, Qualcomm, etc.)

### 3. `privapp-permissions-batterymonitor.xml`
**Purpose:** Privileged permissions whitelist  
**Target Location:** `/product/etc/permissions/privapp-permissions-batterymonitor.xml`  
**Action:** CREATE as new file

**What it does:**
- Whitelists `BATTERY_STATS`, `DUMP`, `READ_LOGS` permissions
- Grants storage access for debug logging
- Prevents "Privileged permission not declared" warnings

## 🚀 Quick Installation

### Step 1: Unpack ROM

```bash
# For dynamic partitions (super.img)
lpunpack super.img
simg2img vendor.img vendor_raw.img
simg2img product.img product_raw.img

# Mount partitions
mkdir vendor product
sudo mount -o loop vendor_raw.img vendor/
sudo mount -o loop product_raw.img product/
```

### Step 2: Apply SELinux Policies

```bash
# Backup original files
sudo cp vendor/etc/selinux/vendor_sepolicy.cil vendor/etc/selinux/vendor_sepolicy.cil.bak
sudo cp vendor/etc/selinux/vendor_file_contexts vendor/etc/selinux/vendor_file_contexts.bak

# Append new rules
sudo cat vendor_sepolicy.cil >> vendor/etc/selinux/vendor_sepolicy.cil
sudo cat vendor_file_contexts >> vendor/etc/selinux/vendor_file_contexts
```

### Step 3: Install Permissions File

```bash
# Create directory if not exists
sudo mkdir -p product/etc/permissions/

# Copy permissions XML
sudo cp privapp-permissions-batterymonitor.xml product/etc/permissions/

# Set permissions
sudo chmod 644 product/etc/permissions/privapp-permissions-batterymonitor.xml
sudo chown root:root product/etc/permissions/privapp-permissions-batterymonitor.xml
```

### Step 4: Install APK

```bash
# Create app directory
sudo mkdir -p product/priv-app/BatteryMonitor/

# Copy APK (build first with ./gradlew assembleRelease)
sudo cp ../app/build/outputs/apk/release/app-release.apk \
        product/priv-app/BatteryMonitor/BatteryMonitor.apk

# Set permissions
sudo chmod 644 product/priv-app/BatteryMonitor/BatteryMonitor.apk
sudo chown root:root product/priv-app/BatteryMonitor/BatteryMonitor.apk
```

### Step 5: Repack ROM

```bash
# Unmount
sudo umount vendor/
sudo umount product/

# Convert back to sparse images
img2simg vendor_raw.img vendor_new.img
img2simg product_raw.img product_new.img

# For dynamic partitions, rebuild super.img
lpmake --metadata-size 65536 \
       --super-name super \
       --metadata-slots 2 \
       --device super:9126805504 \
       --group main:9126805504 \
       --partition vendor:readonly:VENDOR_SIZE:main \
       --image vendor=vendor_new.img \
       --partition product:readonly:PRODUCT_SIZE:main \
       --image product=product_new.img \
       --sparse \
       --output super_new.img

# Flash
fastboot flash super super_new.img
fastboot reboot
```

## ✅ Verification After Installation

### Check App Process Domain

```bash
adb shell ps -Z | grep batterymonitor
```

**Expected output:**
```
u:r:priv_app:s0:c512,c768     u0_a123  12345  456  1234567  67890  0 S com.deviant.batterymonitor
```

### Check Sysfs File Context

```bash
adb shell ls -laZ /sys/devices/platform/charger/ADC_Charger_Voltage
```

**Expected output:**
```
-r--r--r-- 1 root root u:object_r:sysfs_charger:s0 4096 2026-01-08 15:00 ADC_Charger_Voltage
```

### Check for SELinux Denials

```bash
adb shell dmesg | grep avc | grep batterymonitor
```

**Expected:** No output (no denials)

### Test App Functionality

```bash
# Launch app
adb shell am start -n com.deviant.batterymonitor/.MainActivity

# Check logs
adb logcat -s BatteryMonitor

# View debug file
adb shell cat /sdcard/battery_debug.txt
```

## 🔧 Troubleshooting

### Permission Denied Reading Sysfs

**Symptom:** App shows "0" for charger voltage  
**Cause:** SELinux context not applied

**Solution:**
```bash
# Check context
adb shell ls -laZ /sys/devices/platform/charger/

# If showing wrong context (sysfs:s0), relabel:
adb shell su -c "restorecon -Rv /sys/devices/platform/charger"
```

### App Not in priv_app Domain

**Symptom:** `ps -Z` shows `u:r:untrusted_app:s0`  
**Cause:** File contexts not applied or APK in wrong location

**Solution:**
1. Verify APK is in `/product/priv-app/BatteryMonitor/` (not `/system/app/`)
2. Verify permissions XML exists in `/product/etc/permissions/`
3. Check vendor_file_contexts was applied correctly

### SELinux Denials in dmesg

**Symptom:** `avc: denied` messages in dmesg  
**Cause:** Missing policy rule

**Solution:**
```bash
# View denials
adb shell dmesg | grep avc

# Example denial:
# avc: denied { read } for comm="batterymonitor" name="voltage_now" 
#      scontext=u:r:priv_app:s0 tcontext=u:object_r:sysfs:s0 tclass=file

# Add rule to vendor_sepolicy.cil:
# (allow priv_app sysfs (file (read)))
```

### Charger Voltage Always 0

**Cause 1:** Device not charging  
**Solution:** Connect charger

**Cause 2:** Different sysfs path  
**Solution:** Find your device's path:
```bash
adb shell find /sys -name "*voltage*" 2>/dev/null | grep -E "(charger|power_supply)"

# Add found path to vendor_file_contexts:
# /sys/path/to/your/voltage_now    u:object_r:sysfs_charger:s0
```

## 📚 Understanding the Files

### CIL Policy Syntax

```cil
;; Define a type
(type sysfs_charger)

;; Assign to attribute
(typeattributeset sysfs_type (sysfs_charger))

;; Allow rule
(allow source_domain target_type (object_class (permission1 permission2)))
```

**Example:**
```cil
(allow priv_app sysfs_charger (file (read open getattr)))
```
Means: Allow `priv_app` to read, open, and getattr on files labeled `sysfs_charger`

### File Contexts Syntax

```
/path/to/file    u:object_r:type:s0
```

- `u:` = user (usually `u` for all files)
- `object_r` = role (always `object_r` for files)
- `type` = SELinux type (e.g., `sysfs_charger`)
- `s0` = MLS level (usually `s0`)

**Regex support:**
```
/path/to/directory(/.*)?    u:object_r:type:s0
                   ^^^^^^ Matches all files under directory
```

## 🔍 Device-Specific Paths

### Finding Charger Voltage Path

```bash
# Method 1: Search for voltage files
adb shell find /sys -name "*voltage*" 2>/dev/null | grep -i charger

# Method 2: Check power_supply class
adb shell ls -la /sys/class/power_supply/
adb shell cat /sys/class/power_supply/*/voltage_now

# Method 3: Monitor while charging
adb shell "while true; do find /sys -name voltage_now -exec echo {} \; -exec cat {} \; 2>/dev/null; sleep 2; done"
```

### Common Paths by Manufacturer

| Manufacturer | Charger Path |
|--------------|-------------|
| MediaTek | `/sys/devices/platform/charger/ADC_Charger_Voltage` |
| Qualcomm | `/sys/class/power_supply/usb/voltage_now` |
| Samsung | `/sys/class/power_supply/max77705-charger/voltage_now` |
| OnePlus | `/sys/class/power_supply/bq25890/voltage_now` |
| Xiaomi | `/sys/class/power_supply/bq2597x-master/voltage_now` |
| OPPO/Realme | `/sys/class/power_supply/bms/voltage_now` |

## 📖 Additional Resources

- [Android SELinux Documentation](https://source.android.com/docs/security/features/selinux)
- [CIL Language Reference](https://github.com/SELinuxProject/selinux-notebook/blob/main/src/cil_overview.md)
- [Privileged App Permissions](https://source.android.com/docs/core/permissions/perms-allowlist)
- [SELinux Policy Analysis](https://source.android.com/docs/security/features/selinux/implement)

## 📝 Notes

1. **SELinux Mode:** These policies are designed for **enforcing mode**. Permissive mode is not required.

2. **Context Application:** File contexts are applied at boot time, not runtime. After modifying `vendor_file_contexts`, you must:
   - Reboot device, OR
   - Run `restorecon -R /sys/devices/platform/charger`

3. **Policy Compilation:** CIL files are compiled at boot. Syntax errors will prevent boot. Always backup original files.

4. **Compatibility:** These policies target Android 10+ (API 29+) but should work on Android 8.0+ with minor modifications.

5. **Security:** Custom policies should follow principle of least privilege. Only grant necessary permissions.

## 🆘 Need Help?

If you encounter issues:

1. **Check dmesg for denials:**
   ```bash
   adb shell dmesg | grep avc | grep -i battery
   ```

2. **Verify file contexts:**
   ```bash
   adb shell ls -laZ /product/priv-app/BatteryMonitor/
   adb shell ls -laZ /sys/devices/platform/charger/
   ```

3. **Check app permissions:**
   ```bash
   adb shell dumpsys package com.deviant.batterymonitor | grep permission
   ```

4. **Review debug log:**
   ```bash
   adb shell cat /sdcard/battery_debug.txt
   ```

## ⚖️ License

Same as parent repository.
