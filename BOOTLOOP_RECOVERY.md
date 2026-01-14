# Bootloop Recovery Guide

## Gejala

Setelah push APK ke `/system/priv-app/` dan reboot:
- Device stuck di boot animation
- Tidak sampai lock screen
- Logcat menunjukkan app crashes berulang

---

## Penyebab Umum

### 1. SELinux Context Salah

**Problem:** File tidak ter-label dengan context yang benar

```bash
# Wrong:
-rw-r--r-- 1 root root u:object_r:system_file:s0 BatteryMonitor.apk

# Expected:
-rw-r--r-- 1 root root u:object_r:system_app_data_file:s0 BatteryMonitor.apk
```

### 2. App Crash on Boot

**Problem:** App set `android:persistent="true"` tapi crash saat init
- System terus restart app
- App crash lagi
- Bootloop

### 3. Permission/Ownership Salah

**Problem:** File tidak readable oleh system_server

```bash
# Wrong:
-rw------- 1 shell shell ... BatteryMonitor.apk

# Expected:
-rw-r--r-- 1 root root ... BatteryMonitor.apk
```

---

## Recovery Steps

### Method 1: TWRP/Custom Recovery (Recommended)

#### Step 1: Boot ke Recovery

```bash
adb reboot recovery
# atau hold Vol Up + Power saat boot
```

#### Step 2: Mount System

Di TWRP:
- Mount → Check "System"

#### Step 3: Remove Bad APK

```bash
adb shell
mount -o rw,remount /system
rm -rf /system/priv-app/BatteryMonitor
sync
```

#### Step 4: Reboot

```bash
reboot
```

**Device seharusnya boot normal sekarang.**

---

### Method 2: Fastboot (Jika TWRP Tidak Ada)

#### Step 1: Boot ke Fastboot

```bash
adb reboot bootloader
# atau hold Vol Down + Power saat boot
```

#### Step 2: Flash Stock System/Vendor

```bash
# Backup dulu jika ada
fastboot getvar all  # Check slot (A/B)

# Flash system image (will erase your app)
fastboot flash system system.img
fastboot flash vendor vendor.img  # jika ada

# Reboot
fastboot reboot
```

⚠️ **Warning:** Method ini erase semua changes di system partition.

---

### Method 3: Safe Mode (Android 11+)

#### Step 1: Force Reboot

Hold Power button 30 detik sampai device restart.

#### Step 2: Enter Safe Mode

Saat boot animation muncul:
- Hold Vol Down sampai boot complete
- Device boot dengan "Safe Mode" watermark

#### Step 3: Uninstall App

```bash
adb shell pm uninstall com.deviant.batterymonitor
```

#### Step 4: Reboot Normal

```bash
adb reboot
```

---

### Method 4: ADB Sideload (TWRP)

Create flashable zip untuk remove app:

#### Step 1: Create `remove_app.zip`

```bash
mkdir -p flashable/META-INF/com/google/android
cat > flashable/META-INF/com/google/android/update-binary << 'EOF'
#!/sbin/sh
ui_print "Removing BatteryMonitor..."
rm -rf /system/priv-app/BatteryMonitor
ui_print "Done!"
exit 0
EOF

cat > flashable/META-INF/com/google/android/updater-script << 'EOF'
#DUMMY
EOF

cd flashable
zip -r ../remove_app.zip .
cd ..
```

#### Step 2: Flash via TWRP

```bash
adb reboot recovery
# Di TWRP: Install → ADB Sideload → Swipe
adb sideload remove_app.zip
```

---

## Prevention: Safe Installation

### ✅ Method 1: Test with Magisk Module (Recommended)

Lebih safe karena bisa uninstall tanpa touch system:

```bash
# Build Magisk module
./build_magisk_module.sh

# Install via Magisk Manager
adb push BatteryMonitor_Magisk.zip /sdcard/
# Magisk Manager → Modules → Install from storage

# Test reboot
adb reboot

# Jika bootloop: Boot ke recovery, remove /data/adb/modules/batterymonitor
```

**Recovery jika bootloop:**
```bash
adb shell
rm -rf /data/adb/modules/batterymonitor
reboot
```

### ✅ Method 2: Proper System Push dengan SELinux Relabel

```bash
# DON'T:
adb push app.apk /system/priv-app/BatteryMonitor/
adb reboot  # BOOTLOOP RISK!

# DO:
adb root && adb remount
adb push app.apk /system/priv-app/BatteryMonitor/BatteryMonitor.apk
adb shell chown root:root /system/priv-app/BatteryMonitor/BatteryMonitor.apk
adb shell chmod 644 /system/priv-app/BatteryMonitor/BatteryMonitor.apk

# CRITICAL: Relabel SELinux context
adb shell chcon u:object_r:system_app_data_file:s0 /system/priv-app/BatteryMonitor/BatteryMonitor.apk

# Verify before reboot
adb shell ls -laZ /system/priv-app/BatteryMonitor/

# Safe reboot
adb reboot
```

### ✅ Method 3: ROM Integration (Safest)

Build via ROM build system - semua SELinux labeling auto-handled:

```bash
./integrate_to_device.sh ~/android/lineage/device/vendor/codename
cd $ROM_SOURCE && mka bacon
```

---

## Debugging Active Bootloop

### Get Logcat During Boot

Jika device masih respond ke adb saat bootloop:

```bash
# Clear previous logs
adb logcat -c

# Wait for bootloop
sleep 5

# Capture logs
adb logcat -d > bootloop.log

# Search for crashes
grep -i "batterymonitor" bootloop.log
grep -i "FATAL" bootloop.log
grep -i "avc.*denied" bootloop.log
```

**Common Errors:**

#### Error: `Permission denied` atau `SecurityException`

```
E/AndroidRuntime: FATAL EXCEPTION: main
E/AndroidRuntime: Process: com.deviant.batterymonitor, PID: 1234
E/AndroidRuntime: java.lang.SecurityException: Permission denied
```

**Cause:** SELinux context salah atau policy tidak applied

**Fix:** Relabel file atau flash updated vendor dengan policy

#### Error: `ClassNotFoundException` atau `VerifyError`

```
E/AndroidRuntime: java.lang.RuntimeException: Unable to instantiate application
E/AndroidRuntime: Caused by: java.lang.ClassNotFoundException
```

**Cause:** APK corrupted atau dependencies missing

**Fix:** Re-push APK atau check ProGuard rules

#### Error: AVC Denials Loop

```
avc: denied { read } for scontext=u:r:priv_app:s0 tcontext=u:r:sysfs_battery_supply:s0 tclass=file
```

**Cause:** SELinux policy tidak applied atau salah

**Fix:** Flash updated vendor dengan correct policy

---

## Post-Recovery Checklist

✅ **Before Next Attempt:**

1. **Test di emulator dulu** (jika possible)
   ```bash
   emulator -avd Pixel_5_API_30 -writable-system
   ```

2. **Use standalone build first** untuk verify app tidak crash
   ```bash
   ./gradlew installStandaloneDebug
   # Test all functionality
   ```

3. **Remove `android:persistent="true"`** untuk testing
   - Edit `app/src/privapp/AndroidManifest.xml`
   - Comment out persistent flag
   - Test install
   - Add back jika stable

4. **Test with Magisk module** sebelum permanent system push
   ```bash
   ./build_magisk_module.sh
   # Install, test, uninstall if needed
   ```

5. **Create TWRP backup** sebelum system modifications
   - TWRP → Backup → System + Vendor

---

## Emergency Contacts

### Full System Restore

Jika all recovery methods fail:

1. **Download stock ROM** untuk device kamu
2. **Flash via fastboot:**
   ```bash
   fastboot -w flashall
   ```
3. **Atau flash via TWRP** dari ROM zip

### Data Preservation

Jika bootloop tapi data penting:

```bash
# Boot ke TWRP
adb reboot recovery

# Pull data
adb pull /data/media/0 ~/backup/

# Then proceed dengan system restore
```

---

## Additional Resources

- [TWRP Official Docs](https://twrp.me)
- [Android Fastboot Reference](https://source.android.com/docs/core/architecture/bootloader/fastboot)
- [SELinux Troubleshooting](https://source.android.com/docs/security/features/selinux/validate)

---

## Need Help?

Jika masih bootloop:

1. Capture logcat (jika possible)
2. Note device model + ROM version
3. List exact steps yang dilakukan
4. Check XDA forums untuk device-specific solutions
