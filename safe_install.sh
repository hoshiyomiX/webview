#!/bin/bash
# Safe installation script untuk priv-app APK
# Include proper SELinux relabeling dan pre-flight checks

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

APK_PATH="$1"
APP_NAME="BatteryMonitor"
PACKAGE="com.deviant.batterymonitor"
DEST_DIR="/system/priv-app/$APP_NAME"

if [ -z "$APK_PATH" ]; then
    echo "Usage: $0 <path_to_apk>"
    echo "Example: $0 app/build/outputs/apk/privapp/release/app-privapp-release.apk"
    exit 1
fi

if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}[ERROR]${NC} APK not found: $APK_PATH"
    exit 1
fi

echo "======================================"
echo "  Safe Priv-App Installation"
echo "======================================"
echo ""
echo "APK: $APK_PATH"
echo "Destination: $DEST_DIR"
echo ""

# Check device connected
if ! adb get-state &>/dev/null; then
    echo -e "${RED}[ERROR]${NC} No device connected via ADB"
    exit 1
fi

# Pre-flight checks
echo "[1/8] Pre-flight checks..."

# Check root
if ! adb shell su -c "id" &>/dev/null; then
    echo -e "${RED}[ERROR]${NC} Root access required"
    echo "Please enable root in Developer Options"
    exit 1
fi
echo -e "  ${GREEN}✓${NC} Root access available"

# Check SELinux mode
SELINUX_MODE=$(adb shell getenforce)
if [ "$SELINUX_MODE" = "Enforcing" ]; then
    echo -e "  ${YELLOW}⚠${NC} SELinux is Enforcing (bootloop risk if policy not applied)"
    echo -n "  Continue? (y/N): "
    read -r CONTINUE
    if [ "$CONTINUE" != "y" ] && [ "$CONTINUE" != "Y" ]; then
        echo "Aborted."
        exit 1
    fi
else
    echo -e "  ${GREEN}✓${NC} SELinux is Permissive (safe for testing)"
fi

# Check if app already installed
if adb shell pm list packages | grep -q "$PACKAGE"; then
    echo -e "  ${YELLOW}⚠${NC} App already installed, will be replaced"
    echo -n "  Uninstall first? (y/N): "
    read -r UNINSTALL
    if [ "$UNINSTALL" = "y" ] || [ "$UNINSTALL" = "Y" ]; then
        adb shell pm uninstall $PACKAGE &>/dev/null || true
        echo -e "    ${GREEN}✓${NC} Old app uninstalled"
    fi
fi

# Remount system
echo "[2/8] Remounting system..."
adb root &>/dev/null
sleep 1
adb remount &>/dev/null
if ! adb shell mount | grep -q "on /system.*rw"; then
    echo -e "${RED}[ERROR]${NC} Failed to remount /system as rw"
    echo "Try: adb shell mount -o rw,remount /system"
    exit 1
fi
echo -e "  ${GREEN}✓${NC} System remounted as rw"

# Backup existing APK
echo "[3/8] Checking for existing installation..."
if adb shell "[ -d $DEST_DIR ]" 2>/dev/null; then
    BACKUP_DIR="/sdcard/batterymonitor_backup_$(date +%Y%m%d_%H%M%S)"
    echo -e "  ${YELLOW}⚠${NC} Found existing installation, backing up..."
    adb shell mkdir -p "$BACKUP_DIR"
    adb shell cp -r "$DEST_DIR" "$BACKUP_DIR/" 2>/dev/null || true
    echo -e "  ${GREEN}✓${NC} Backup saved to $BACKUP_DIR"
fi

# Remove old installation
echo "[4/8] Removing old installation..."
adb shell rm -rf "$DEST_DIR" 2>/dev/null || true
adb shell mkdir -p "$DEST_DIR"
echo -e "  ${GREEN}✓${NC} Directory prepared"

# Push APK
echo "[5/8] Pushing APK..."
adb push "$APK_PATH" "$DEST_DIR/$APP_NAME.apk"
echo -e "  ${GREEN}✓${NC} APK pushed"

# Set permissions
echo "[6/8] Setting permissions..."
adb shell chown root:root "$DEST_DIR/$APP_NAME.apk"
adb shell chmod 644 "$DEST_DIR/$APP_NAME.apk"
echo -e "  ${GREEN}✓${NC} Permissions set (644, root:root)"

# CRITICAL: Set SELinux context
echo "[7/8] Setting SELinux context..."
adb shell chcon u:object_r:system_app_data_file:s0 "$DEST_DIR/$APP_NAME.apk" 2>/dev/null || {
    echo -e "  ${YELLOW}⚠${NC} chcon failed, trying restorecon..."
    adb shell restorecon -R "$DEST_DIR" 2>/dev/null || {
        echo -e "  ${RED}⚠${NC} SELinux relabel failed - HIGH BOOTLOOP RISK!"
        echo "  Device may bootloop. Proceed? (y/N): "
        read -r PROCEED
        if [ "$PROCEED" != "y" ] && [ "$PROCEED" != "Y" ]; then
            echo "Removing APK..."
            adb shell rm -rf "$DEST_DIR"
            exit 1
        fi
    }
}
echo -e "  ${GREEN}✓${NC} SELinux context applied"

# Verify installation
echo "[8/8] Verifying installation..."
echo ""
echo "File details:"
adb shell ls -laZ "$DEST_DIR/$APP_NAME.apk"
echo ""

FILE_SIZE=$(adb shell stat -c %s "$DEST_DIR/$APP_NAME.apk")
LOCAL_SIZE=$(stat -c %s "$APK_PATH" 2>/dev/null || stat -f %z "$APK_PATH" 2>/dev/null)

if [ "$FILE_SIZE" != "$LOCAL_SIZE" ]; then
    echo -e "${RED}[ERROR]${NC} File size mismatch!"
    echo "Local: $LOCAL_SIZE bytes"
    echo "Device: $FILE_SIZE bytes"
    exit 1
fi

echo -e "${GREEN}✓${NC} Installation verified"
echo ""

# Reboot prompt
echo "======================================"
echo "  Installation Complete"
echo "======================================"
echo ""
echo "Next steps:"
echo "1. Reboot device: adb reboot"
echo "2. Monitor boot: adb logcat -c && adb wait-for-device && adb logcat"
echo "3. Check installation: adb shell pm list packages | grep $PACKAGE"
echo ""
echo -e "${YELLOW}⚠${NC} IMPORTANT:"
echo "- If bootloop occurs, boot to recovery and remove $DEST_DIR"
echo "- Keep USB cable connected during first boot"
echo "- Monitor logcat for crashes"
echo ""
echo -n "Reboot now? (y/N): "
read -r REBOOT

if [ "$REBOOT" = "y" ] || [ "$REBOOT" = "Y" ]; then
    echo "Rebooting..."
    adb reboot
    echo ""
    echo "Waiting for device..."
    adb wait-for-device
    sleep 5
    
    echo "Checking installation..."
    if adb shell pm list packages | grep -q "$PACKAGE"; then
        echo -e "${GREEN}✓${NC} App successfully installed!"
        echo ""
        echo "Verify priv-app status:"
        adb shell dumpsys package $PACKAGE | grep -i "path:\|priv\|context"
    else
        echo -e "${RED}✗${NC} App not found after reboot"
        echo "Check logcat for errors: adb logcat | grep -i batterymonitor"
    fi
else
    echo "Skipped reboot. Reboot manually when ready."
fi

echo ""
echo "Installation log saved to: install_$(date +%Y%m%d_%H%M%S).log"
