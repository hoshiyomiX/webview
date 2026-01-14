#!/bin/bash
# Build Magisk module untuk safer installation
# Module bisa di-uninstall tanpa touch system partition

set -e

APP_NAME="BatteryMonitor"
MODULE_ID="batterymonitor_privapp"
VERSION="1.0"
APK_PATH="app/build/outputs/apk/privapp/release/app-privapp-release.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "APK not found. Building..."
    ./gradlew assemblePrivappRelease
fi

echo "Building Magisk Module..."

# Create module structure
MODULE_DIR="magisk_module"
rm -rf "$MODULE_DIR"
mkdir -p "$MODULE_DIR/system/priv-app/$APP_NAME"
mkdir -p "$MODULE_DIR/sepolicy"

# Copy APK
cp "$APK_PATH" "$MODULE_DIR/system/priv-app/$APP_NAME/$APP_NAME.apk"

# Create module.prop
cat > "$MODULE_DIR/module.prop" << EOF
id=$MODULE_ID
name=$APP_NAME Priv-App
version=$VERSION
versionCode=1
author=hoshiyomiX
description=Install $APP_NAME as privileged system app with sysfs access
EOF

# Create install script
cat > "$MODULE_DIR/customize.sh" << 'EOFSCRIPT'
#!/system/bin/sh

ui_print "- Installing BatteryMonitor as priv-app"
ui_print "- Setting permissions..."

set_perm_recursive $MODPATH/system/priv-app 0 0 0755 0644

ui_print "- Applying SELinux policy..."

# Copy SELinux policies ke module
if [ -d "$MODPATH/sepolicy" ]; then
    ui_print "  Note: SELinux policies detected"
    ui_print "  Manual vendor patch may be required for sysfs access"
fi

ui_print "- Installation complete"
ui_print "- Reboot to apply changes"
ui_print ""
ui_print "To uninstall: Magisk Manager -> Modules -> Remove"
EOFSCRIPT

chmod +x "$MODULE_DIR/customize.sh"

# Create post-fs-data script untuk SELinux policy injection
cat > "$MODULE_DIR/post-fs-data.sh" << 'EOFSCRIPT'
#!/system/bin/sh

# Wait for boot
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done

# Try to inject SELinux policy via supolicy (if available)
if command -v supolicy &>/dev/null; then
    supolicy --live \
      "allow priv_app sysfs_battery_supply file { read open getattr }" \
      "allow priv_app sysfs_leds file { read write open getattr }" \
      "allow priv_app sysfs_leds dir search" \
      "allow priv_app sysfs_devices_system_cpu file { read open }" \
      "allow priv_app sysfs_power file { read open getattr }" &>/dev/null
fi
EOFSCRIPT

chmod +x "$MODULE_DIR/post-fs-data.sh"

# Copy SELinux policy files (for reference)
if [ -d "sepolicy" ]; then
    cp sepolicy/vendor_sepolicy.cil "$MODULE_DIR/sepolicy/" 2>/dev/null || true
    cp sepolicy/vendor_file_contexts "$MODULE_DIR/sepolicy/" 2>/dev/null || true
    
    cat > "$MODULE_DIR/sepolicy/README.txt" << 'EOF'
SELinux Policy Files (Reference Only)

These policies are NOT automatically applied by Magisk module.

For full sysfs access, you need to:
1. Patch vendor.img dengan policies ini, ATAU
2. Use Magisk supolicy (post-fs-data.sh script), ATAU
3. Set setenforce 0 (permissive mode - not recommended)

Manual vendor patch:
  1. Extract vendor.img
  2. Append vendor_sepolicy.cil to /vendor/etc/selinux/vendor_sepolicy.cil
  3. Append vendor_file_contexts to /vendor/etc/selinux/vendor_file_contexts
  4. Repack and flash vendor.img

See BUILD_INSTRUCTIONS.md for detailed steps.
EOF
fi

# Create update-binary (standard Magisk installer)
mkdir -p "$MODULE_DIR/META-INF/com/google/android"
cat > "$MODULE_DIR/META-INF/com/google/android/updater-script" << 'EOF'
#MAGISK
EOF

cat > "$MODULE_DIR/META-INF/com/google/android/update-binary" << 'EOFBIN'
#!/sbin/sh

#################
# Initialization
#################

umask 022

# Global vars
TMPDIR=/dev/tmp
INSTALLER=$TMPDIR/install
MODPATH=/data/adb/modules/batterymonitor_privapp

rm -rf $TMPDIR 2>/dev/null
mkdir -p $INSTALLER

##############
# Environment
##############

OUTFD=$2
ZIPFILE=$3

mount /data 2>/dev/null

# Load utility functions
if [ -f /data/adb/magisk/util_functions.sh ]; then
  . /data/adb/magisk/util_functions.sh
  NVBASE=/data/adb
else
  ui_print "! Unable to find Magisk"
  exit 1
fi

# Preperation for flashable zips
setup_flashable

# Mount partitions
mount_partitions

# Detect version and architecture
api_level_arch_detect

# Setup busybox and binaries
$BOOTMODE && boot_actions || recovery_actions

##############
# Preparation
##############

# Extract files
ui_print "- Extracting module files"
unzip -o "$ZIPFILE" -d $INSTALLER >&2

##############
# Install
##############

ui_print "- Installing $APP_NAME"

# Create module path
rm -rf $MODPATH 2>/dev/null
mkdir -p $MODPATH

# Copy files
cp -af $INSTALLER/* $MODPATH/

# Run customize script
if [ -f "$MODPATH/customize.sh" ]; then
  ui_print "- Running installation script"
  . $MODPATH/customize.sh
fi

##############
# Finalizing
##############

ui_print "- Finalizing installation"

# Remove placeholder files
rm -rf \
$MODPATH/system/placeholder $MODPATH/customize.sh \
$MODPATH/README.md $MODPATH/.git* 2>/dev/null

# Cleanup
rm -rf $TMPDIR

ui_print "- Done"
ui_print " "
exit 0
EOFBIN

chmod +x "$MODULE_DIR/META-INF/com/google/android/update-binary"

# Create README
cat > "$MODULE_DIR/README.md" << 'EOF'
# BatteryMonitor Magisk Module

## Installation

1. Flash via Magisk Manager:
   - Modules → Install from storage → Select ZIP
   
2. Reboot

3. Verify:
   ```
   adb shell pm list packages | grep batterymonitor
   adb shell dumpsys package com.deviant.batterymonitor | grep priv
   ```

## Uninstallation

Magisk Manager → Modules → BatteryMonitor → Remove → Reboot

**Safe:** Tidak modify system partition, easily reversible.

## SELinux Policy

Module include post-fs-data script untuk inject policy via supolicy.

Jika sysfs access tetap denied:
1. Check SELinux mode: `getenforce`
2. Manual vendor patch required (see sepolicy/README.txt)

## Troubleshooting

Jika bootloop:
1. Boot ke recovery
2. Mount data: `mount /data`
3. Remove module: `rm -rf /data/adb/modules/batterymonitor_privapp`
4. Reboot
EOF

# Package module
OUTPUT_ZIP="${APP_NAME}_Magisk_v${VERSION}.zip"
cd "$MODULE_DIR"
zip -r9 "../$OUTPUT_ZIP" . -x '*.git*'
cd ..

echo ""
echo "✓ Magisk module created: $OUTPUT_ZIP"
echo ""
echo "Installation:"
echo "  adb push $OUTPUT_ZIP /sdcard/"
echo "  # Then: Magisk Manager -> Modules -> Install from storage"
echo ""
echo "Or flash via recovery:"
echo "  adb push $OUTPUT_ZIP /sdcard/"
echo "  adb reboot recovery"
echo "  # Install ZIP from /sdcard/$OUTPUT_ZIP"
echo ""
