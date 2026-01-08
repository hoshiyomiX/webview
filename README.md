# Battery Monitor - Android WebView Application

A battery monitoring application for Android that displays real-time battery statistics including charger voltage, current, temperature, and power consumption. Built on the [slymax/webview](https://github.com/slymax/webview) template.

## Features

- 🔋 Real-time battery monitoring
- ⚡ Charger voltage detection (direct sysfs access)
- 🌡️ Temperature monitoring
- 🔌 Current and power consumption tracking
- 🎨 Dark/Light theme support (follows system)
- 📊 HTML5-based responsive UI
- 📝 Debug logging to /sdcard/battery_debug.txt

## Branches

### `master`
Original webview template from [slymax/webview](https://github.com/slymax/webview).

### `checkpoint`
Battery Monitor with **root/Magisk support**. Uses `su` command to read charger voltage from sysfs.

**Features:**
- Root prompt dialog on first launch
- Runtime `su` execution for privileged sysfs access
- Fallback to non-root mode if su unavailable

**Installation:**
```bash
adb install app-release.apk
```

### `selinux` (Current Branch)
Battery Monitor as **privileged system app** without root/Magisk.

**Features:**
- Direct sysfs access via priv_app SELinux domain
- No root prompts or su commands
- Multi-path charger voltage detection
- Automatic microvolt to millivolt conversion
- Fully enforcing SELinux compatible

**Installation:**
Requires ROM modification. See [SELINUX_DEPLOYMENT.md](./SELINUX_DEPLOYMENT.md) for complete guide.

**Target location:** `/product/priv-app/BatteryMonitor/`

### `battery-monitor`
Earlier battery monitoring implementation.

### `battery-monitor-css-enhanced`
Enhanced CSS styling for battery monitor UI.

### `build-logs`
Build logging and debugging utilities.

## Quick Start (SELinux/Priv-App Branch)

### Prerequisites

- Android Studio or Gradle
- Custom ROM with unlocked bootloader
- Ability to modify `/vendor` and `/product` partitions

### Building

```bash
git clone https://github.com/hoshiyomiX/webview.git
cd webview
git checkout selinux
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### Deployment

**This branch requires system-level installation!** See detailed instructions in [SELINUX_DEPLOYMENT.md](./SELINUX_DEPLOYMENT.md).

#### Quick Summary

1. **Modify SELinux policies** in `/vendor/etc/selinux/`
   - Add custom `sysfs_charger` type
   - Allow `priv_app` access to charger sysfs

2. **Install APK** to `/product/priv-app/BatteryMonitor/`

3. **Add permissions whitelist** in `/product/etc/permissions/`

4. **Flash modified ROM**

## Supported Devices

Tested on:
- MediaTek devices with `/sys/devices/platform/charger/ADC_Charger_Voltage`

Should work on any Android device with:
- Charger voltage exposed in sysfs
- Android 5.0+ (API 21+)
- SELinux enforcing mode

### Common Charger Sysfs Paths

```
/sys/devices/platform/charger/ADC_Charger_Voltage  (MediaTek)
/sys/class/power_supply/charger/voltage_now        (Generic)
/sys/class/power_supply/usb/voltage_now            (USB)
/sys/class/power_supply/battery/charger_voltage    (Alternative)
```

The app automatically tries all known paths.

## Technical Details

### Architecture

```
┌────────────────────┐
│   WebView (HTML5)   │
│   JavaScript UI     │
└──────┬─────────────┘
       │ JavascriptInterface
┌──────┴─────────────┐
│  BatteryBridge     │
│  (Java/Android)    │
└──────┬─────────────┘
       │ Direct FileReader
┌──────┴─────────────┐
│ sysfs (Kernel)    │
│ /sys/devices/...  │
└────────────────────┘
```

### SELinux Context

**Process domain:** `u:r:priv_app:s0`

**File contexts:**
- APK: `u:object_r:privapp_data_file:s0`
- Sysfs: `u:object_r:sysfs_charger:s0`

### Data Sources

| Metric | Source | API Level |
|--------|--------|----------|
| Capacity (%) | BatteryManager | 21+ |
| Current (µA) | BatteryManager | 21+ |
| Voltage (mV) | Intent (BATTERY_CHANGED) | 1+ |
| Temperature | Intent (BATTERY_CHANGED) | 1+ |
| Status | Intent (BATTERY_CHANGED) | 1+ |
| Charger Voltage | Direct sysfs read | N/A |

## Verification Commands

After installation, verify functionality:

```bash
# Check app process domain
adb shell ps -Z | grep batterymonitor
# Expected: u:r:priv_app:s0

# Check sysfs context
adb shell ls -laZ /sys/devices/platform/charger/
# Expected: u:object_r:sysfs_charger:s0

# Check for SELinux denials
adb shell dmesg | grep avc | grep batterymonitor
# Expected: No output

# View debug log
adb shell cat /sdcard/battery_debug.txt
```

## Troubleshooting

### Permission Denied on Sysfs Read

```bash
# Check file context
adb shell ls -laZ /sys/devices/platform/charger/ADC_Charger_Voltage

# Relabel if needed
adb shell su -c "restorecon -R /sys/devices/platform/charger"

# Check for denials
adb shell dmesg | grep avc
```

### App Not in Priv-App Domain

- Verify APK location is `/product/priv-app/BatteryMonitor/`
- Check permissions XML exists in `/product/etc/permissions/`
- Verify file_contexts was applied correctly

### Charger Voltage Always 0

- Device may not be charging
- Your device uses different sysfs path
- Find available paths:
  ```bash
  adb shell find /sys -name "*voltage*" | grep -i charger
  ```

## Branch Comparison

| Feature | checkpoint | selinux (this) |
|---------|------------|----------------|
| Installation | User app | System priv-app |
| Root required | ✅ Yes | ❌ No |
| su command | ✅ Used | ❌ Not used |
| SELinux mode | Any | Enforcing |
| ROM modification | ❌ Not needed | ✅ Required |
| OTA survival | ❌ No | ✅ Yes |
| Bootloader | Can be locked | Must unlock once |
| Security | Lower (su access) | Higher (least privilege) |

## Development

### Project Structure

```
webview/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/deviant/batterymonitor/
│   │   │   ├── MainActivity.java       (Modified for priv-app)
│   │   │   └── MyWebViewClient.java
│   │   ├── assets/
│   │   │   └── index.html             (Battery UI)
│   │   └── res/
│   └── build.gradle
├── SELINUX_DEPLOYMENT.md          (Installation guide)
└── README.md                      (This file)
```

### Key Code Changes (selinux branch)

**Before (checkpoint):**
```java
Process process = Runtime.getRuntime().exec("su");
DataOutputStream os = new DataOutputStream(process.getOutputStream());
os.writeBytes("cat /sys/devices/platform/charger/ADC_Charger_Voltage\n");
```

**After (selinux):**
```java
File voltageFile = new File("/sys/devices/platform/charger/ADC_Charger_Voltage");
BufferedReader reader = new BufferedReader(new FileReader(voltageFile));
String voltage = reader.readLine();
```

## Contributing

Contributions welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is based on [slymax/webview](https://github.com/slymax/webview) template.

## Credits

- **Original Template:** [slymax/webview](https://github.com/slymax/webview)
- **Battery Monitor Implementation:** hoshiyomiX
- **SELinux Integration:** Custom implementation

## Related Documentation

- [SELINUX_DEPLOYMENT.md](./SELINUX_DEPLOYMENT.md) - Complete deployment guide
- [Android SELinux](https://source.android.com/docs/security/features/selinux)
- [Privileged App Permissions](https://source.android.com/docs/core/permissions/perms-allowlist)

## Changelog

### selinux branch (2026-01-08)
- Removed root/su logic
- Added direct sysfs access for priv-app
- Multi-path charger voltage support
- SELinux policy documentation
- Automatic unit conversion

### checkpoint branch
- Battery monitoring with root access
- Dynamic theme switching
- HTML5 UI with real-time updates

### master branch
- Original webview template
