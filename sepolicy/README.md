# SELinux Policy untuk BatteryMonitor Priv-App

## File Structure

```
sepolicy/
├── vendor_sepolicy.cil      # Common Intermediate Language policy
└── vendor_file_contexts     # File labeling rules
```

## Policy Breakdown

### vendor_sepolicy.cil

**Allowed Operations:**

1. **Battery Sysfs Access**
   ```cil
   (allow priv_app sysfs_battery_supply (file (read write open getattr)))
   ```
   - Path: `/sys/class/power_supply/battery/*`
   - Actions: Read battery capacity, voltage, temp, status

2. **LED Control**
   ```cil
   (allow priv_app sysfs_leds (file (read write open getattr)))
   (allow priv_app sysfs_leds (dir (search)))
   ```
   - Path: `/sys/class/leds/*/brightness`
   - Actions: Control notification LED, keyboard backlight

3. **CPU Info**
   ```cil
   (allow priv_app sysfs_devices_system_cpu (file (read write open)))
   ```
   - Path: `/sys/devices/system/cpu/cpu*/cpufreq/*`
   - Actions: Read frequency, governor, scaling info

4. **Power Management**
   ```cil
   (allow priv_app sysfs_power (file (read write open getattr)))
   ```
   - Path: `/sys/power/*`
   - Actions: Read wakelock stats, suspend info

### vendor_file_contexts

**Labeling Rules:**

1. **App Binary**
   ```
   /system/priv-app/BatteryMonitor(/.*)? u:object_r:system_app_data_file:s0
   ```
   - Labels app directory as system app

2. **App Data**
   ```
   /data/data/com\.deviant\.batterymonitor(/.*)? u:object_r:app_data_file:s0
   ```
   - Standard app data label

3. **Sysfs Nodes**
   - Battery: `sysfs_battery_supply`
   - LEDs: `sysfs_leds`
   - CPU: `sysfs_devices_system_cpu`

## Device-Specific Adjustments

### Custom Sysfs Paths

Jika device kamu pakai custom paths, tambahkan di `vendor_file_contexts`:

```bash
# Example: Xiaomi Redmi Note 11 Pro (pissarro)
/sys/class/power_supply/bms(/.*)?          u:object_r:sysfs_battery_supply:s0
/sys/class/qcom-battery(/.*)?              u:object_r:sysfs_battery_supply:s0
```

### Additional Hardware Access

**Vibrator:**
```cil
(allow priv_app sysfs_vibrator (file (read write open)))
```

**Display Brightness:**
```cil
(allow priv_app sysfs_backlight (file (read write open getattr)))
```

**Thermal Zones:**
```cil
(allow priv_app sysfs_thermal (file (read open getattr)))
(allow priv_app sysfs_thermal (dir (search)))
```

## Testing Policy

### 1. Check Current Denials
```bash
adb shell su -c dmesg | grep avc | grep priv_app
```

### 2. Generate Policy from Denials
```bash
adb shell su -c 'dmesg | audit2allow -p /sys/fs/selinux/policy'
```

### 3. Test Permissive Mode (Debug Only)
```bash
adb shell su -c setenforce 0  # Set permissive
# Test app functionality
adb shell su -c setenforce 1  # Restore enforcing
```

### 4. Live Policy Injection (Magisk)
```bash
adb shell su -c 'supolicy --live "allow priv_app sysfs_battery_supply file *"'
```

## Integration Methods

### Method 1: Device Tree (Compile-time)

**Preferred for ROM builds**

```bash
# Copy policies
cp sepolicy/vendor_sepolicy.cil device/vendor/codename/sepolicy/vendor/
cp sepolicy/vendor_file_contexts device/vendor/codename/sepolicy/vendor/

# Update BoardConfig.mk
BOARD_SEPOLICY_DIRS += device/vendor/codename/sepolicy/vendor
```

### Method 2: Vendor Image Patch (Runtime)

**For pre-built ROMs**

```bash
# Extract vendor.img
simg2img vendor.img vendor.img.ext4
mkdir vendor_mount
sudo mount -o loop vendor.img.ext4 vendor_mount

# Append policies
sudo sh -c 'cat sepolicy/vendor_sepolicy.cil >> vendor_mount/etc/selinux/vendor_sepolicy.cil'
sudo sh -c 'cat sepolicy/vendor_file_contexts >> vendor_mount/etc/selinux/vendor_file_contexts'

# Repack
sudo umount vendor_mount
img2simg vendor.img.ext4 vendor_patched.img
```

### Method 3: Magisk Module

**No recompile needed**

Create `service.sh`:
```bash
#!/system/bin/sh
MODDIR=${0%/*}

# Wait for boot
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 1
done

# Inject policy
supolicy --live \
  "allow priv_app sysfs_battery_supply file { read write open getattr }" \
  "allow priv_app sysfs_leds file { read write open getattr }" \
  "allow priv_app sysfs_leds dir search" \
  "allow priv_app sysfs_devices_system_cpu file { read write open }" \
  "allow priv_app sysfs_power file { read write open getattr }"
```

## Troubleshooting

### App Crashes on Sysfs Access

1. **Check SELinux mode:**
   ```bash
   adb shell getenforce  # Should be "Enforcing"
   ```

2. **Check app context:**
   ```bash
   adb shell ps -AZ | grep batterymonitor
   # Expected: u:r:priv_app:s0:c512,c768
   ```

3. **Test sysfs access manually:**
   ```bash
   adb shell
   su
   run-as com.deviant.batterymonitor
   cat /sys/class/power_supply/battery/capacity
   ```

### Denials Still Appearing

**Check policy compilation:**
```bash
adb shell su -c sesearch -A -s priv_app -t sysfs_battery_supply /sys/fs/selinux/policy
```

**If empty, policy tidak ter-compile. Re-flash vendor atau rebuild ROM.**

### Permission Denied Despite Policy

**Kernel-level restrictions (SafetyNet/Knox):**
- Some kernels block priv-app sysfs access via LSM hooks
- Check kernel config: `CONFIG_SECURITY_SELINUX_DEVELOP`
- Workaround: Patch kernel atau gunakan custom kernel

## Security Notes

⚠️ **Warning:**
- Policy ini memberikan akses luas ke sysfs
- Hanya untuk personal builds, **JANGAN distribute ke public**
- Potential security risk jika app vulnerable

✅ **Best Practices:**
- Validasi input sebelum write ke sysfs
- Implement rate limiting untuk prevent hardware damage
- Add bounds checking untuk brightness/voltage values
- Log sysfs operations untuk audit trail

## References

- [Android SELinux Concepts](https://source.android.com/docs/security/features/selinux/concepts)
- [CIL Language Reference](https://github.com/SELinuxProject/selinux/blob/main/secilc/docs/README.md)
- [audit2allow Tool](https://source.android.com/docs/security/features/selinux/validate)
