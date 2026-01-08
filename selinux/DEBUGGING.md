# SELinux Debugging Guide

## Overview

Battery Monitor app sudah dilengkapi dengan **enhanced debug logging** yang fokus ke SELinux troubleshooting. Debug log ini akan automatically capture:

- ✅ SELinux enforcing status
- ✅ App SELinux context (domain verification)
- ✅ File contexts untuk battery sysfs paths
- ✅ AVC denials dari dmesg (dengan suggested fixes)
- ✅ Detailed error logging untuk sysfs reads
- ✅ Permission denied root cause analysis

---

## Debug Log Location

```bash
# View debug log
$ adb shell cat /sdcard/battery_debug.txt

# Monitor real-time
$ adb shell tail -f /sdcard/battery_debug.txt

# Pull to PC
$ adb pull /sdcard/battery_debug.txt .
```

---

## Debug Log Structure

Debug log terbagi jadi beberapa section:

### Section 1: System Information
```
Timestamp: 2026-01-08 17:25:34
Android Version: 13 (SDK 33)
Device: Xiaomi Redmi Note 12
Board: mt6781
Hardware: mt6781
```

### Section 2: SELinux Status
```
[1] SELinux Enforcing Status:
    Enforcing
    ✓ SELinux is enforcing - policies will be enforced

[2] Application SELinux Context:
    u:r:priv_app:s0:c512,c768
    ✓ Running as priv_app domain (correct)
```

### Section 3: File Contexts Verification
```
[3] Battery Sysfs File Contexts:
    /sys/class/power_supply/battery/capacity
      → u:object_r:sysfs_batteryinfo:s0
      ✓ Correct label
```

### Section 4: AVC Denials
```
[4] Recent AVC Denials:
    ✓ No AVC denials found (permissions OK)
    
    OR (if there are denials):
    
    ✗ Found 2 AVC denial(s):
    avc: denied { read } for scontext=u:r:priv_app:s0 ...
    → Suggested fix: (allow priv_app sysfs (file (read)))
```

### Section 5: Sysfs Read Attempts
```
[Battery Capacity]
  ✓ Read success: /sys/class/power_supply/battery/capacity = 85
  
  OR (if failed):
  
  ✗ Permission denied (canRead=false)
    → Check SELinux context: ls -lZ /path/to/file
    → Expected label: u:object_r:sysfs_batteryinfo:s0
```

---

## Common Issues & Solutions

### Issue 1: Running as untrusted_app

**Symptom:**
```
[2] Application SELinux Context:
    u:r:untrusted_app:s0
    ✗ ERROR: Running as untrusted_app (needs priv_app)
```

**Cause:** App installed ke wrong location (bukan priv-app directory)

**Solution:**
1. Uninstall APK yang existing:
   ```bash
   $ adb uninstall com.deviant.batterymonitor
   ```

2. Copy ke ROM source:
   ```bash
   $ cp BatteryMonitor.apk $ROM/product/priv-app/BatteryMonitor/
   $ cp privapp-permissions-batterymonitor.xml $ROM/product/etc/permissions/
   ```

3. Rebuild ROM:
   ```bash
   $ cd $ROM
   $ make -j$(nproc)
   ```

4. Flash & verify:
   ```bash
   $ fastboot flash product product.img
   $ adb shell ps -Z | grep batterymonitor
   # Expected: u:r:priv_app:s0
   ```

---

### Issue 2: AVC Denials

**Symptom:**
```
[4] Recent AVC Denials:
    ✗ Found AVC denial:
    avc: denied { read } for scontext=u:r:priv_app:s0 
    tcontext=u:object_r:sysfs_batteryinfo:s0 tclass=file
```

**Cause:** Missing sepolicy rule di `vendor_sepolicy.cil`

**Solution:**
1. Debug log sudah suggest fix:
   ```cil
   (allow priv_app sysfs_batteryinfo (file (read)))
   ```

2. Add rule ke `vendor_sepolicy.cil`:
   ```bash
   $ echo "(allow priv_app sysfs_batteryinfo (file (read open getattr)))" >> \
       $ROM/vendor/etc/selinux/vendor_sepolicy.cil
   ```

3. Rebuild ROM dan flash

4. Verify no more denials:
   ```bash
   $ adb shell dmesg | grep avc | grep priv_app
   # Expected: (no output)
   ```

---

### Issue 3: Wrong File Context

**Symptom:**
```
[3] Battery Sysfs File Contexts:
    /sys/class/power_supply/battery/capacity
      → u:object_r:sysfs:s0
      ⚠ Generic sysfs label (may need vendor_file_contexts)
```

**Cause:** `vendor_file_contexts` tidak applied atau incorrect

**Solution:**
1. Add correct label ke `vendor_file_contexts`:
   ```bash
   $ cat >> $ROM/vendor/etc/selinux/vendor_file_contexts << 'EOF'
   /sys/class/power_supply/battery(/.*)?  u:object_r:sysfs_batteryinfo:s0
   EOF
   ```

2. Rebuild ROM dan flash

3. Verify after boot:
   ```bash
   $ adb shell ls -lZ /sys/class/power_supply/battery/capacity
   # Expected: u:object_r:sysfs_batteryinfo:s0
   ```

---

### Issue 4: Permission Denied

**Symptom:**
```
[Battery Capacity]
  ✗ Permission denied (canRead=false)
  ✗ SecurityException: Permission denied
```

**Cause:** SELinux blocking access

**Debugging Steps:**

1. Check file exists:
   ```bash
   $ adb shell ls -l /sys/class/power_supply/battery/capacity
   ```

2. Check file context:
   ```bash
   $ adb shell ls -lZ /sys/class/power_supply/battery/capacity
   ```

3. Capture AVC denials:
   ```bash
   $ adb shell dmesg | grep avc | grep capacity
   ```

4. Check if context is correct:
   - If context is `sysfs:s0` → Need to update `vendor_file_contexts`
   - If context is `sysfs_batteryinfo:s0` → Need to add sepolicy rule

---

### Issue 5: File Not Found

**Symptom:**
```
[Charger Voltage]
  ✗ File not found: /sys/devices/platform/charger/ADC_Charger_Voltage
  ✗ All paths failed for Charger Voltage
```

**Cause:** Device pakai non-standard sysfs paths

**Solution:**

1. Find device-specific paths:
   ```bash
   # Find capacity file
   $ adb shell find /sys -name "*capacity*" 2>/dev/null
   
   # Find voltage files
   $ adb shell find /sys -name "*voltage*" 2>/dev/null | grep -i batt
   
   # Find charger paths
   $ adb shell find /sys -name "*charger*" 2>/dev/null
   ```

2. Update `MainActivity.java` dengan custom paths:
   ```java
   private String readBatteryCapacity() {
       String[] paths = {
           "/sys/your/custom/path/capacity",  // Add your path
           "/sys/class/power_supply/battery/capacity",
           "/sys/class/power_supply/bms/capacity"
       };
       return readSysfsMultiPath(paths, "Battery Capacity");
   }
   ```

3. Rebuild APK dan reinstall

---

### Issue 6: SELinux Permissive Mode

**Symptom:**
```
[1] SELinux Enforcing Status:
    Permissive
    ⚠ SELinux is permissive - denials logged but not enforced
```

**Impact:** AVC denials are logged tapi tidak enforced (app masih bisa baca sysfs)

**Action:**
- Fix policies even di permissive mode untuk production readiness
- Check dmesg untuk denials yang akan terjadi di enforcing mode

**Switch to Enforcing (temporary):**
```bash
$ adb shell su -c "setenforce 1"
$ adb shell getenforce
# Expected: Enforcing
```

**Permanent Enforcing:** Rebuild ROM dengan `SELINUX=enforcing` di kernel config

---

## Advanced Debugging

### Real-Time AVC Capture
```bash
# Watch AVC denials live
$ adb shell dmesg -w | grep avc

# Filter for specific app
$ adb shell dmesg -w | grep avc | grep priv_app

# Filter for sysfs denials
$ adb shell dmesg -w | grep avc | grep sysfs
```

### Search Specific Denials
```bash
# Last 50 AVC denials
$ adb shell dmesg | grep avc | tail -50

# Denials involving priv_app
$ adb shell dmesg | grep avc | grep priv_app | grep sysfs

# Denials for specific file
$ adb shell dmesg | grep avc | grep capacity
```

### Check App Context
```bash
# Via ps
$ adb shell ps -Z | grep batterymonitor

# Via /proc
$ adb shell cat /proc/$(pidof com.deviant.batterymonitor)/attr/current
```

### Verify Policy Loaded
```bash
# Check policy file
$ adb shell ls -l /sys/fs/selinux/policy

# Check policy version
$ adb shell cat /sys/fs/selinux/policyvers

# Check if enforcing
$ adb shell cat /sys/fs/selinux/enforce
# 1 = Enforcing, 0 = Permissive
```

### Find Files by Context
```bash
# Find all sysfs_batteryinfo files
$ adb shell find /sys -context "*sysfs_batteryinfo*" 2>/dev/null

# Find all sysfs_charger files
$ adb shell find /sys -context "*sysfs_charger*" 2>/dev/null
```

### Test Direct Read (Requires Root)
```bash
# Test as root (bypasses SELinux)
$ adb shell su -c "cat /sys/class/power_supply/battery/capacity"

# Compare with app context (may be denied)
$ adb shell run-as com.deviant.batterymonitor \
    cat /sys/class/power_supply/battery/capacity
```

---

## AVC Denial Parsing

### Example AVC Denial
```
avc: denied { read } for pid=12345 comm="droid.batterymon" 
name="capacity" dev="sysfs" ino=98765 
scontext=u:r:priv_app:s0 
tcontext=u:object_r:sysfs_batteryinfo:s0 
tclass=file permissive=0
```

### Parse Components

| Component | Value | Meaning |
|-----------|-------|----------|
| **Action** | `{ read }` | Permission being denied |
| **scontext** | `u:r:priv_app:s0` | Source domain (app) |
| **tcontext** | `u:object_r:sysfs_batteryinfo:s0` | Target type (file label) |
| **tclass** | `file` | Object class |
| **permissive** | `0` | 0=enforcing, 1=permissive |

### Convert to CIL Rule

**Format:**
```cil
(allow <source_domain> <target_type> (<class> (<permissions>)))
```

**Example:**
```cil
(allow priv_app sysfs_batteryinfo (file (read)))
```

**Complete rule dengan common permissions:**
```cil
(allow priv_app sysfs_batteryinfo (file (read open getattr)))
```

### Common Permission Sets

**File read:**
```cil
(file (read open getattr))
```

**Directory traverse:**
```cil
(dir (search getattr))
```

**Symbolic link:**
```cil
(lnk_file (read getattr))
```

**File write:**
```cil
(file (write append create unlink open getattr setattr))
```

**Directory write:**
```cil
(dir (write add_name remove_name search))
```

---

## Troubleshooting Workflow

### Step 1: Get Debug Log
```bash
$ adb shell cat /sdcard/battery_debug.txt > battery_debug.txt
```

### Step 2: Check SELinux Status
Look for:
- `✓ SELinux is enforcing` → Good
- `✓ Running as priv_app domain` → Good
- `✗ ERROR: Running as untrusted_app` → Fix installation

### Step 3: Check File Contexts
Look for:
- `✓ Correct label` → Good
- `⚠ Generic sysfs label` → Need to fix vendor_file_contexts
- `✗ Unknown label` → Check labeling

### Step 4: Check AVC Denials
Look for:
- `✓ No AVC denials found` → All permissions OK
- `✗ Found AVC denial(s)` → Add suggested rules

### Step 5: Check Sysfs Reads
Look for:
- `✓ Read success` → Path works
- `✗ File not found` → Try alternative paths
- `✗ Permission denied` → SELinux issue
- `✗ SecurityException` → Add sepolicy rule

### Step 6: Apply Fixes
1. Add missing rules ke `vendor_sepolicy.cil`
2. Fix labels di `vendor_file_contexts`
3. Fix install location (priv-app)
4. Rebuild ROM
5. Flash & test

### Step 7: Verify
```bash
# Check no more denials
$ adb shell dmesg | grep avc | grep batterymonitor

# Check app works
$ adb shell cat /sdcard/battery_debug.txt | grep "Read success"
```

---

## Quick Reference

### Debug Commands Cheat Sheet

```bash
# View debug log
adb shell cat /sdcard/battery_debug.txt

# Check SELinux mode
adb shell getenforce

# Check app domain
adb shell ps -Z | grep batterymonitor

# Check file context
adb shell ls -lZ /sys/class/power_supply/battery/capacity

# Check AVC denials
adb shell dmesg | grep avc | tail -20

# Find battery sysfs paths
adb shell find /sys -name "*capacity*" 2>/dev/null

# Test direct read (root)
adb shell su -c "cat /sys/class/power_supply/battery/capacity"
```

---

## Support

### Report Issues

**Before reporting:**
1. Attach `/sdcard/battery_debug.txt`
2. Include output dari:
   ```bash
   $ adb shell getenforce
   $ adb shell ps -Z | grep batterymonitor
   $ adb shell ls -lZ /sys/class/power_supply/battery/
   $ adb shell dmesg | grep avc | grep priv_app | tail -20
   ```

### Repository
- **Main:** [github.com/hoshiyomiX/webview](https://github.com/hoshiyomiX/webview)
- **Branch:** `selinux`
- **Issues:** [github.com/hoshiyomiX/webview/issues](https://github.com/hoshiyomiX/webview/issues)

### Files
- **Debug Log:** `/sdcard/battery_debug.txt`
- **SELinux Rules:** `selinux/vendor_sepolicy.cil`
- **File Contexts:** `selinux/vendor_file_contexts`
- **Main Code:** `app/src/main/java/com/deviant/batterymonitor/MainActivity.java`

---

## Conclusion

Enhanced debug logging di Battery Monitor app sudah dilengkapi dengan comprehensive SELinux troubleshooting. Debug log akan automatically:

✅ Detect SELinux misconfigurations  
✅ Capture & parse AVC denials  
✅ Suggest fixes untuk common issues  
✅ Verify file contexts labeling  
✅ Track detailed permission errors  

Use this guide untuk troubleshoot issues secara mandiri. Kalau masih stuck, attach debug log pas report issue!

---

**Last Updated:** 2026-01-08  
**Version:** 1.0  
**Author:** hoshiyomiX
