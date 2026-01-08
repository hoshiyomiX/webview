# Battery Monitor - SELinux Privileged App Deployment

## Overview

This branch contains modifications for deploying Battery Monitor as a **privileged system app** at `/product/priv-app/BatteryMonitor/`. The app can directly access sysfs without requiring root/Magisk.

## Key Changes from `checkpoint` Branch

### Removed (Root/Su Logic)
- `checkRootAccess()` - Root availability check
- `isRootAvailable()` - Su binary detection
- `showRootDialog()` - User root prompt dialog
- `requestRootAccess()` - Su permission request
- Root prompt dialog UI
- Runtime.exec("su") calls

### Added/Modified
- `hasPrivilegedAccess` - Always true for priv-app
- `readChargerVoltageDirect()` - Direct FileReader access
- Multi-path sysfs support (4 common charger paths)
- Automatic microvolt to millivolt conversion
- Updated debug logging for privileged app mode
- SELinux-aware status reporting

## Installation Requirements

### 1. SELinux Policy Files

You need to modify your ROM's vendor partition:

#### `/vendor/etc/selinux/vendor_sepolicy.cil`

Append these rules:

```cil
;; Battery Monitor - Privileged App Rules
(type sysfs_charger)
(typeattributeset sysfs_type (sysfs_charger))
(typeattributeset fs_type (sysfs_charger))

(allow priv_app sysfs_charger (dir (search getattr)))
(allow priv_app sysfs_charger (file (read open getattr ioctl)))
(allow priv_app sysfs_charger (lnk_file (read getattr)))

(allow priv_app sysfsbatteryinfo300 (dir (search getattr)))
(allow priv_app sysfsbatteryinfo300 (file (read open getattr)))

(allow priv_app sdcardtype (dir (write add_name remove_name search)))
(allow priv_app sdcardtype (file (create write append unlink open getattr setattr)))

(allow priv_app proc (dir (search)))
(allow priv_app proc (file (read open getattr)))
(allow priv_app procmeminfo300 (file (read open getattr)))

(allow priv_app storagefile300 (lnk_file (read getattr)))
(allow priv_app mntuserfile300 (dir (search getattr)))
(allow priv_app mntuserfile300 (lnk_file (read getattr)))
(allow priv_app tmpfs300 (dir (search)))
(allow priv_app vendordefaultprop300 (file (read open getattr map)))

(allow priv_app system_server (binder (call transfer)))
(allow priv_app servicemanager300 (binder (call transfer)))
(allow system_server priv_app (binder (call transfer)))
```

#### `/vendor/etc/selinux/vendor_file_contexts`

Append these contexts:

```
# Battery Monitor App
/product/priv-app/BatteryMonitor(/.*)?                      u:object_r:privapp_data_file:s0
/data/data/com\.deviant\.batterymonitor(/.*)?               u:object_r:privapp_data_file:s0

# Charger sysfs paths
/sys/devices/platform/charger(/.*)?                         u:object_r:sysfs_charger:s0
/sys/devices/platform/charger/ADC_Charger_Voltage           u:object_r:sysfs_charger:s0
/sys/class/power_supply/charger(/.*)?                       u:object_r:sysfs_charger:s0
/sys/class/power_supply/usb/voltage_now                     u:object_r:sysfs_charger:s0
```

### 2. Privileged App Permissions

Create `/product/etc/permissions/privapp-permissions-batterymonitor.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.deviant.batterymonitor">
        <permission name="android.permission.INTERNET"/>
        <permission name="android.permission.WRITE_EXTERNAL_STORAGE"/>
        <permission name="android.permission.READ_EXTERNAL_STORAGE"/>
        <permission name="android.permission.ACCESS_NETWORK_STATE"/>
        <permission name="android.permission.DUMP"/>
        <permission name="android.permission.READ_LOGS"/>
        <permission name="android.permission.BATTERY_STATS"/>
        <permission name="android.permission.WAKE_LOCK"/>
    </privapp-permissions>
</permissions>
```

### 3. APK Installation

Place the compiled APK at:
```
/product/priv-app/BatteryMonitor/BatteryMonitor.apk
```

Set permissions:
```bash
chmod 644 /product/priv-app/BatteryMonitor/BatteryMonitor.apk
chown root:root /product/priv-app/BatteryMonitor/BatteryMonitor.apk
```

## Building the APK

```bash
# From repository root
./gradlew assembleRelease

# Output will be at:
# app/build/outputs/apk/release/app-release.apk
```

## Verification After Installation

### Check App Domain
```bash
adb shell ps -Z | grep batterymonitor
# Expected: u:r:priv_app:s0
```

### Check Sysfs Context
```bash
adb shell ls -laZ /sys/devices/platform/charger/ADC_Charger_Voltage
# Expected: u:object_r:sysfs_charger:s0
```

### Check for SELinux Denials
```bash
adb shell dmesg | grep avc | grep batterymonitor
# Expected: No output (no denials)
```

### Test App Functionality
```bash
adb shell am start -n com.deviant.batterymonitor/.MainActivity
adb logcat | grep -i battery
```

### Check Debug Log
```bash
adb shell cat /sdcard/battery_debug.txt
```

## Supported Charger Voltage Paths

The app will try these paths in order:

1. `/sys/devices/platform/charger/ADC_Charger_Voltage` (MediaTek)
2. `/sys/class/power_supply/charger/voltage_now` (Generic)
3. `/sys/class/power_supply/usb/voltage_now` (USB charger)
4. `/sys/class/power_supply/battery/charger_voltage` (Alternative)

The first readable path will be used.

## Troubleshooting

### App shows "Permission denied"

1. Check SELinux context:
   ```bash
   adb shell ls -laZ /sys/devices/platform/charger/
   ```

2. If wrong context, relabel:
   ```bash
   adb shell su -c "restorecon -R /sys/devices/platform/charger"
   ```

3. Check for denials in kernel log:
   ```bash
   adb shell dmesg | grep avc
   ```

### App not in priv_app domain

- Verify file_contexts was properly applied
- Ensure app is in `/product/priv-app/` not `/system/app/`
- Check permissions XML exists in `/product/etc/permissions/`

### Charger voltage always shows 0

- Device might not be charging
- Your device may use a different sysfs path
- Check available paths:
   ```bash
   adb shell find /sys -name "*voltage*" | grep -i charger
   ```

## Comparison with Root/Magisk Approach

| Feature | Root/Magisk | Priv-App (This Branch) |
|---------|-------------|------------------------|
| Requires root | ✅ Yes | ❌ No |
| Runtime permission prompt | ✅ Yes | ❌ No |
| Works on locked bootloader | ❌ No | ✅ Yes |
| Survives OTA updates | ❌ No | ✅ Yes (if in ROM) |
| SELinux enforcing | ⚠️ May need permissive | ✅ Fully enforcing |
| Installation complexity | 🟢 Easy (install APK) | 🟡 Medium (ROM modification) |
| Security | ⚠️ Requires su access | ✅ Principle of least privilege |

## Technical Details

### Direct Sysfs Access

Instead of:
```java
Process process = Runtime.getRuntime().exec("su");
DataOutputStream os = new DataOutputStream(process.getOutputStream());
os.writeBytes("cat /sys/devices/platform/charger/ADC_Charger_Voltage\n");
```

We now use:
```java
File voltageFile = new File("/sys/devices/platform/charger/ADC_Charger_Voltage");
BufferedReader reader = new BufferedReader(new FileReader(voltageFile));
String voltage = reader.readLine();
```

This works because:
1. App runs in `priv_app` SELinux domain
2. Custom `sysfs_charger` type defined in policy
3. Policy allows `priv_app` → `sysfs_charger` read access

### SELinux Context Flow

```
zygote (u:r:zygote:s0)
  └─> BatteryMonitor app launched
      └─> Context transition to u:r:priv_app:s0
          └─> Can read u:object_r:sysfs_charger:s0 files
```

## Additional Resources

- [Android SELinux Documentation](https://source.android.com/docs/security/features/selinux)
- [Privileged App Permissions](https://source.android.com/docs/core/permissions/perms-allowlist)
- [CIL Policy Language](https://github.com/SELinuxProject/selinux-notebook/blob/main/src/cil_overview.md)

## License

Same as parent repository.

## Credits

- Original webview template: [slymax/webview](https://github.com/slymax/webview)
- SELinux configuration: Custom implementation for battery monitoring
