# SELinux Patching Guide

## Overview

This guide explains how to **merge** Battery Monitor SELinux policies dengan original vendor SELinux files dari ROM kamu. Patching lebih aman daripada replace langsung karena:

✅ **Preserves existing policies** - Tidak override ROM policies yang sudah ada  
✅ **Safe integration** - Battery Monitor policies di-append, bukan replace  
✅ **Easy rollback** - Backup original files untuk restore jika needed  
✅ **ROM compatibility** - Works dengan custom ROM atau OEM ROM  
✅ **Version control** - Track changes dengan git  

---

## Prerequisites

### 1. Extract Original SELinux Files

Kamu perlu extract original `vendor_sepolicy.cil` dan `vendor_file_contexts` dari ROM:

#### Option A: From Running Device
```bash
# Extract dari device yang running
$ adb pull /vendor/etc/selinux/vendor_sepolicy.cil prebuilt/
$ adb pull /vendor/etc/selinux/vendor_file_contexts prebuilt/
```

#### Option B: From ROM ZIP
```bash
# Extract dari ROM flashable ZIP
$ unzip -p rom.zip vendor/etc/selinux/vendor_sepolicy.cil > prebuilt/vendor_sepolicy.cil
$ unzip -p rom.zip vendor/etc/selinux/vendor_file_contexts > prebuilt/vendor_file_contexts
```

#### Option C: From ROM Source
```bash
# Copy dari ROM build output
$ cp $ROM/out/target/product/$DEVICE/vendor/etc/selinux/vendor_sepolicy.cil prebuilt/
$ cp $ROM/out/target/product/$DEVICE/vendor/etc/selinux/vendor_file_contexts prebuilt/
```

### 2. Verify Files
```bash
$ ls -lh prebuilt/
-rw-r--r-- 1 user user 1.3M vendor_sepolicy.cil
-rw-r--r-- 1 user user 100K vendor_file_contexts
```

---

## Automated Patching (Recommended)

### Using `patch_sepolicy.sh` Script

Script ini automatically merge original files dengan Battery Monitor policies:

```bash
# From repo root
$ bash selinux/patch_sepolicy.sh
```

### Script Features

✅ **Input Validation**
- Check semua required files exist
- Validate file sizes
- Verify Battery Monitor policies present

✅ **Safety**
- Auto backup original files (.bak)
- Preserve timestamps
- No modification ke prebuilt/ files

✅ **Merge Process**
- Append Battery Monitor policies ke original
- Add separator comments
- Preserve original structure

✅ **Validation**
- Check CIL syntax (balanced parentheses)
- Verify file contexts regex
- Count added lines/rules
- Generate checksums

✅ **Output**
- Create `selinux/patched/` directory
- Generate `.patched` files
- Show summary report

### Expected Output

```
================================================================================
  Battery Monitor - SELinux Policy Patcher
================================================================================

✓ Found original vendor_sepolicy.cil (1.2MiB)
✓ Found original vendor_file_contexts (100KiB)
✓ Found Battery Monitor policies (8.5KiB)
✓ Found Battery Monitor contexts (6.2KiB)

================================================================================
  Step 4: Merging vendor_sepolicy.cil
================================================================================

✓ Merged vendor_sepolicy.cil
ℹ  Original lines: 45230
ℹ  Added lines: 156
ℹ  Total lines: 45386

================================================================================
  Step 5: Merging vendor_file_contexts
================================================================================

✓ Merged vendor_file_contexts
ℹ  Original lines: 3421
ℹ  Added lines: 98
ℹ  Total lines: 3519

================================================================================
  Patching Complete!
================================================================================

✓ Patched files created successfully:

  📄 selinux/patched/vendor_sepolicy.cil.patched
     Size: 1.3MiB
     Lines: 45386 (+156)

  📄 selinux/patched/vendor_file_contexts.patched
     Size: 106KiB
     Lines: 3519 (+98)
```

---

## Manual Patching (Fallback)

Jika script tidak bisa dipakai, kamu bisa merge secara manual:

### 1. Merge `vendor_sepolicy.cil`

```bash
# Create output directory
$ mkdir -p selinux/patched

# Merge files
$ cat prebuilt/vendor_sepolicy.cil > selinux/patched/vendor_sepolicy.cil.patched

# Add separator
$ echo "" >> selinux/patched/vendor_sepolicy.cil.patched
$ echo "" >> selinux/patched/vendor_sepolicy.cil.patched
$ echo ";; ===============================================================================" >> selinux/patched/vendor_sepolicy.cil.patched
$ echo ";; BATTERY MONITOR - APPENDED POLICIES" >> selinux/patched/vendor_sepolicy.cil.patched
$ echo ";; ===============================================================================" >> selinux/patched/vendor_sepolicy.cil.patched
$ echo "" >> selinux/patched/vendor_sepolicy.cil.patched

# Append Battery Monitor policies (skip header - first 29 lines)
$ tail -n +30 selinux/vendor_sepolicy.cil >> selinux/patched/vendor_sepolicy.cil.patched
```

### 2. Merge `vendor_file_contexts`

```bash
# Merge files
$ cat prebuilt/vendor_file_contexts > selinux/patched/vendor_file_contexts.patched

# Add separator
$ echo "" >> selinux/patched/vendor_file_contexts.patched
$ echo "" >> selinux/patched/vendor_file_contexts.patched
$ echo "################################################################################" >> selinux/patched/vendor_file_contexts.patched
$ echo "# BATTERY MONITOR - APPENDED FILE CONTEXTS" >> selinux/patched/vendor_file_contexts.patched
$ echo "################################################################################" >> selinux/patched/vendor_file_contexts.patched
$ echo "" >> selinux/patched/vendor_file_contexts.patched

# Append Battery Monitor contexts (skip header - first 19 lines)
$ tail -n +20 selinux/vendor_file_contexts >> selinux/patched/vendor_file_contexts.patched
```

### 3. Validate Manual Merge

```bash
# Check CIL syntax (balanced parentheses)
$ echo "Open parens: $(grep -o '(' selinux/patched/vendor_sepolicy.cil.patched | wc -l)"
$ echo "Close parens: $(grep -o ')' selinux/patched/vendor_sepolicy.cil.patched | wc -l)"

# Count added rules
$ echo "Original CIL lines: $(wc -l < prebuilt/vendor_sepolicy.cil)"
$ echo "Patched CIL lines: $(wc -l < selinux/patched/vendor_sepolicy.cil.patched)"

# Count added contexts
$ echo "Original contexts lines: $(wc -l < prebuilt/vendor_file_contexts)"
$ echo "Patched contexts lines: $(wc -l < selinux/patched/vendor_file_contexts.patched)"
```

---

## Verification

### 1. Check Patched Files Exist

```bash
$ ls -lh selinux/patched/
-rw-r--r-- 1 user user 1.3M vendor_sepolicy.cil.patched
-rw-r--r-- 1 user user 106K vendor_file_contexts.patched
```

### 2. Verify Battery Monitor Policies Added

```bash
# Check for sysfs_charger type definition
$ grep -A2 'type sysfs_charger' selinux/patched/vendor_sepolicy.cil.patched
(type sysfs_charger)
(typeattributeset sysfs_type (sysfs_charger))
(typeattributeset fs_type (sysfs_charger))

# Check for priv_app sysfs_batteryinfo rules
$ grep 'priv_app sysfs_batteryinfo' selinux/patched/vendor_sepolicy.cil.patched
(allow priv_app sysfs_batteryinfo (dir (search getattr)))
(allow priv_app sysfs_batteryinfo (file (read open getattr)))
...
```

### 3. Verify File Contexts Added

```bash
# Check for battery sysfs contexts
$ grep 'power_supply/battery' selinux/patched/vendor_file_contexts.patched
/sys/class/power_supply/battery(/.*)?                       u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/battery/capacity                    u:object_r:sysfs_batteryinfo:s0
...

# Check for charger contexts
$ grep 'sysfs_charger' selinux/patched/vendor_file_contexts.patched | head -5
/sys/class/power_supply/battery/charger_voltage             u:object_r:sysfs_charger:s0
/sys/devices/platform/charger(/.*)?                         u:object_r:sysfs_charger:s0
...
```

### 4. Count Added Rules

```bash
# Count new CIL rules
$ echo "Battery Monitor CIL rules: $(grep -c '^(allow priv_app' selinux/patched/vendor_sepolicy.cil.patched)"

# Count new file contexts
$ echo "Battery Monitor contexts: $(grep -c 'sysfs_batteryinfo\|sysfs_charger' selinux/patched/vendor_file_contexts.patched)"
```

---

## ROM Integration

### Step 1: Copy Patched Files to ROM Source

```bash
# Set ROM source directory
$ export ROM=/path/to/your/rom/source

# Copy patched vendor_sepolicy.cil
$ cp selinux/patched/vendor_sepolicy.cil.patched \
     $ROM/vendor/etc/selinux/vendor_sepolicy.cil

# Copy patched vendor_file_contexts
$ cp selinux/patched/vendor_file_contexts.patched \
     $ROM/vendor/etc/selinux/vendor_file_contexts
```

### Step 2: Install Battery Monitor APK

```bash
# Create priv-app directory
$ mkdir -p $ROM/product/priv-app/BatteryMonitor

# Copy APK
$ cp BatteryMonitor.apk $ROM/product/priv-app/BatteryMonitor/

# Create permissions directory
$ mkdir -p $ROM/product/etc/permissions

# Copy privapp permissions
$ cp privapp-permissions-batterymonitor.xml \
     $ROM/product/etc/permissions/
```

### Step 3: Build ROM

```bash
$ cd $ROM

# Clean (optional, tapi recommended untuk SELinux changes)
$ make clean

# Build
$ . build/envsetup.sh
$ lunch lineage_$DEVICE-userdebug
$ make -j$(nproc)
```

### Step 4: Flash ROM

```bash
# Flash via fastboot
$ fastboot flash vendor out/target/product/$DEVICE/vendor.img
$ fastboot flash product out/target/product/$DEVICE/product.img
$ fastboot flash system out/target/product/$DEVICE/system.img
$ fastboot reboot
```

Or flash full ROM ZIP:
```bash
$ adb reboot recovery
# In recovery: Install from ADB
$ adb sideload lineage-*.zip
```

---

## Post-Flash Verification

### 1. Check App Domain

```bash
$ adb shell ps -Z | grep batterymonitor
u:r:priv_app:s0:c512,c768 ... com.deviant.batterymonitor
```

✅ **Expected:** `u:r:priv_app:s0`  
❌ **Wrong:** `u:r:untrusted_app:s0` (not installed as priv-app)

### 2. Check File Contexts

```bash
$ adb shell ls -lZ /sys/class/power_supply/battery/capacity
-r--r--r-- 1 root root u:object_r:sysfs_batteryinfo:s0 ... capacity
```

✅ **Expected:** `u:object_r:sysfs_batteryinfo:s0`  
❌ **Wrong:** `u:object_r:sysfs:s0` (generic label)

### 3. Check for AVC Denials

```bash
$ adb shell dmesg | grep avc | grep batterymonitor
```

✅ **Expected:** No output (no denials)  
❌ **Wrong:** AVC denials present (missing rules)

### 4. Check App Works

```bash
# View debug log
$ adb shell cat /sdcard/battery_debug.txt

# Check for success reads
$ adb shell cat /sdcard/battery_debug.txt | grep "✓ Read success"
✓ Read success: /sys/class/power_supply/battery/capacity = 85
✓ Read success: /sys/class/power_supply/battery/voltage_now = 3892000
...
```

---

## Troubleshooting

### Issue 1: Script Fails - Files Not Found

**Error:**
```
✗ Original vendor_sepolicy.cil not found: prebuilt/vendor_sepolicy.cil
```

**Solution:**
```bash
# Extract files dari device atau ROM
$ adb pull /vendor/etc/selinux/vendor_sepolicy.cil prebuilt/
$ adb pull /vendor/etc/selinux/vendor_file_contexts prebuilt/
```

---

### Issue 2: CIL Syntax Error - Unbalanced Parentheses

**Error:**
```
✗ CIL syntax error: Unbalanced parentheses!
  Opening '(': 12345
  Closing ')': 12344
```

**Solution:**
```bash
# Check original file integrity
$ grep -o '(' prebuilt/vendor_sepolicy.cil | wc -l
$ grep -o ')' prebuilt/vendor_sepolicy.cil | wc -l

# If original is corrupt, re-extract
$ adb pull /vendor/etc/selinux/vendor_sepolicy.cil prebuilt/vendor_sepolicy.cil

# Re-run script
$ bash selinux/patch_sepolicy.sh
```

---

### Issue 3: Duplicate Rules Warning

**Warning:**
```
ℹ Rule already exists in original: (allow priv_app sysfs (file (read)))
```

**Solution:**  
Ini biasanya safe - duplicate rules akan ignored oleh SELinux compiler. Tapi jika mau clean:

```bash
# Check for duplicates
$ grep -n 'allow priv_app sysfs_batteryinfo' selinux/patched/vendor_sepolicy.cil.patched

# Manually remove duplicates (edit file)
$ nano selinux/patched/vendor_sepolicy.cil.patched
```

---

### Issue 4: Patched Files Too Large

**Error:**
```
Build error: vendor_sepolicy.cil exceeds partition size
```

**Solution:**

Battery Monitor policies add ~150 lines (< 10KB). Jika partition size issue:

1. **Option A:** Increase partition size di `BoardConfig.mk`:
   ```make
   BOARD_VENDORIMAGE_PARTITION_SIZE := 1073741824  # 1GB
   ```

2. **Option B:** Remove unused policies dari original file (advanced)

3. **Option C:** Use dynamic partitions (Android 10+)

---

### Issue 5: App Still Can't Read Sysfs

**Symptom:**
```
✗ Permission denied (canRead=false): /sys/class/power_supply/battery/capacity
```

**Debug:**

1. Check app context:
   ```bash
   $ adb shell ps -Z | grep batterymonitor
   ```
   Must be `u:r:priv_app:s0`

2. Check file context:
   ```bash
   $ adb shell ls -lZ /sys/class/power_supply/battery/capacity
   ```
   Must be `u:object_r:sysfs_batteryinfo:s0`

3. Check AVC denials:
   ```bash
   $ adb shell dmesg | grep avc | tail -20
   ```

4. If denials present, add missing rules:
   ```bash
   # Add to vendor_sepolicy.cil in ROM source
   $ echo "(allow priv_app sysfs_batteryinfo (file (read open getattr)))" >> \
       $ROM/vendor/etc/selinux/vendor_sepolicy.cil
   
   # Rebuild ROM
   ```

---

## Alternative Methods

### Method A: Magisk Module (Systemless)

Untuk testing tanpa rebuild ROM:

```bash
# Create Magisk module structure
$ mkdir -p magisk-batterymonitor/vendor/etc/selinux

# Copy patched files
$ cp selinux/patched/vendor_sepolicy.cil.patched \
     magisk-batterymonitor/vendor/etc/selinux/vendor_sepolicy.cil

$ cp selinux/patched/vendor_file_contexts.patched \
     magisk-batterymonitor/vendor/etc/selinux/vendor_file_contexts

# Create module.prop
$ cat > magisk-batterymonitor/module.prop << 'EOF'
id=batterymonitor-selinux
name=Battery Monitor SELinux
version=1.0
authorid=hoshiyomiX
description=SELinux policies for Battery Monitor app
EOF

# Zip and flash
$ cd magisk-batterymonitor && zip -r9 ../batterymonitor-magisk.zip . && cd ..
$ adb push batterymonitor-magisk.zip /sdcard/
# Flash in Magisk Manager
```

### Method B: Overlay Partitions

Use overlay filesystem untuk patch tanpa modify vendor:

```bash
# Create overlay
$ adb shell su -c "mkdir -p /data/overlay/vendor/etc/selinux"

# Push patched files
$ adb push selinux/patched/vendor_sepolicy.cil.patched \
     /data/overlay/vendor/etc/selinux/vendor_sepolicy.cil

$ adb push selinux/patched/vendor_file_contexts.patched \
     /data/overlay/vendor/etc/selinux/vendor_file_contexts

# Mount overlay (requires init script or Magisk)
```

---

## Rollback

Jika ada issues, restore original files:

```bash
# Restore from backup
$ cp prebuilt/vendor_sepolicy.cil.bak $ROM/vendor/etc/selinux/vendor_sepolicy.cil
$ cp prebuilt/vendor_file_contexts.bak $ROM/vendor/etc/selinux/vendor_file_contexts

# Rebuild ROM
$ cd $ROM && make -j$(nproc)

# Flash
$ fastboot flash vendor vendor.img
$ fastboot reboot
```

---

## Summary

| Method | Pros | Cons | Best For |
|--------|------|------|----------|
| **Automated Script** | Fast, validated, safe | Requires bash | Most users |
| **Manual Merge** | Full control, portable | Error-prone | Advanced users |
| **Magisk Module** | Systemless, easy rollback | Requires Magisk | Testing |
| **Overlay** | Non-destructive | Complex setup | Development |

---

## Additional Resources

- **Debugging Guide:** [`DEBUGGING.md`](DEBUGGING.md)
- **SELinux Policies:** [`vendor_sepolicy.cil`](vendor_sepolicy.cil)
- **File Contexts:** [`vendor_file_contexts`](vendor_file_contexts)
- **Patching Script:** [`patch_sepolicy.sh`](patch_sepolicy.sh)

---

## Support

**Repository:** [github.com/hoshiyomiX/webview](https://github.com/hoshiyomiX/webview)  
**Branch:** `selinux`  
**Issues:** [github.com/hoshiyomiX/webview/issues](https://github.com/hoshiyomiX/webview/issues)

For patching issues, attach:
1. `prebuilt/` files (original)
2. `selinux/patched/` files (patched)
3. Script output
4. Build errors (if any)

---

**Last Updated:** 2026-01-08  
**Version:** 1.0  
**Author:** hoshiyomiX
