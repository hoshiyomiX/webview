#!/bin/bash
# Test script untuk verify sysfs access dari BatteryMonitor
# Jalankan setelah flash ROM dengan app ter-install

set -e

PACKAGE="com.deviant.batterymonitor"
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "======================================"
echo "  BatteryMonitor Sysfs Access Test"
echo "======================================"
echo ""

# Check if device connected
if ! adb get-state &>/dev/null; then
    echo -e "${RED}[ERROR]${NC} No device connected via ADB"
    exit 1
fi

# Check if app installed
echo -n "[1/7] Checking app installation... "
if adb shell pm list packages | grep -q "$PACKAGE"; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
    echo "Package $PACKAGE not found. Install the app first."
    exit 1
fi

# Check if installed as priv-app
echo -n "[2/7] Checking priv-app status... "
PRIV_CHECK=$(adb shell dumpsys package $PACKAGE | grep -i "path:" | grep -i priv-app || echo "")
if [ -n "$PRIV_CHECK" ]; then
    echo -e "${GREEN}✓${NC}"
    echo "      Path: $(echo $PRIV_CHECK | awk '{print $2}')"
else
    echo -e "${YELLOW}⚠${NC}"
    echo "      App not installed as priv-app. May have limited permissions."
fi

# Check SELinux mode
echo -n "[3/7] Checking SELinux mode... "
SELINUX_MODE=$(adb shell getenforce)
if [ "$SELINUX_MODE" = "Enforcing" ]; then
    echo -e "${GREEN}✓${NC} Enforcing"
elif [ "$SELINUX_MODE" = "Permissive" ]; then
    echo -e "${YELLOW}⚠${NC} Permissive (all access allowed, policy tidak tested)"
else
    echo -e "${RED}✗${NC} Unknown mode: $SELINUX_MODE"
fi

# Check app SELinux context
echo -n "[4/7] Checking app SELinux context... "
APP_PID=$(adb shell pidof $PACKAGE 2>/dev/null || echo "")
if [ -z "$APP_PID" ]; then
    echo -e "${YELLOW}⚠${NC} App not running. Start the app first."
    echo "      Attempting to start..."
    adb shell monkey -p $PACKAGE -c android.intent.category.LAUNCHER 1 &>/dev/null
    sleep 3
    APP_PID=$(adb shell pidof $PACKAGE 2>/dev/null || echo "")
fi

if [ -n "$APP_PID" ]; then
    CONTEXT=$(adb shell ps -AZ | grep $PACKAGE | awk '{print $1}')
    if echo "$CONTEXT" | grep -q "priv_app"; then
        echo -e "${GREEN}✓${NC} $CONTEXT"
    else
        echo -e "${RED}✗${NC} $CONTEXT"
        echo "      Expected context containing 'priv_app'"
    fi
else
    echo -e "${RED}✗${NC} Cannot determine (app not running)"
fi

# Test battery sysfs access
echo "[5/7] Testing battery sysfs access..."
BATTERY_PATHS=(
    "/sys/class/power_supply/battery/capacity"
    "/sys/class/power_supply/battery/voltage_now"
    "/sys/class/power_supply/battery/temp"
    "/sys/class/power_supply/battery/status"
)

for path in "${BATTERY_PATHS[@]}"; do
    echo -n "      $path ... "
    if adb shell "[ -f $path ]" 2>/dev/null; then
        VALUE=$(adb shell cat $path 2>/dev/null || echo "Permission Denied")
        if [ "$VALUE" != "Permission Denied" ]; then
            echo -e "${GREEN}✓${NC} ($VALUE)"
        else
            echo -e "${RED}✗${NC} (permission denied)"
        fi
    else
        echo -e "${YELLOW}⚠${NC} (file not found)"
    fi
done

# Test LED sysfs access
echo "[6/7] Testing LED sysfs access..."
LED_PATH=$(adb shell find /sys/class/leds -name "*red*" -o -name "*green*" -o -name "*blue*" 2>/dev/null | head -1)
if [ -n "$LED_PATH" ]; then
    BRIGHTNESS_PATH="$LED_PATH/brightness"
    echo -n "      $BRIGHTNESS_PATH ... "
    if adb shell "[ -f $BRIGHTNESS_PATH ]" 2>/dev/null; then
        VALUE=$(adb shell cat $BRIGHTNESS_PATH 2>/dev/null || echo "Permission Denied")
        if [ "$VALUE" != "Permission Denied" ]; then
            echo -e "${GREEN}✓${NC} (current: $VALUE)"
        else
            echo -e "${RED}✗${NC} (permission denied)"
        fi
    else
        echo -e "${YELLOW}⚠${NC} (file not found)"
    fi
else
    echo -e "      ${YELLOW}⚠${NC} No LED nodes found"
fi

# Check SELinux denials
echo "[7/7] Checking recent SELinux denials..."
DENIALS=$(adb shell su -c "dmesg | grep avc | grep $PACKAGE" 2>/dev/null | tail -5 || echo "")
if [ -z "$DENIALS" ]; then
    echo -e "      ${GREEN}✓${NC} No denials found"
else
    echo -e "      ${YELLOW}⚠${NC} Recent denials detected:"
    echo "$DENIALS" | while read -r line; do
        echo "      - $line"
    done
    echo ""
    echo "      Run 'adb shell su -c dmesg | audit2allow' untuk generate missing policy"
fi

echo ""
echo "======================================"
echo "  Test Summary"
echo "======================================"
echo ""
echo "If all checks pass (${GREEN}✓${NC}), app sudah configured dengan benar."
echo ""
echo "Troubleshooting:"
echo "  - ${RED}✗${NC} Permission denials: Check SELinux policy di vendor_sepolicy.cil"
echo "  - ${YELLOW}⚠${NC} File not found: Adjust paths untuk device kamu"
echo "  - ${YELLOW}⚠${NC} App not priv-app: Re-flash ROM dengan proper integration"
echo ""
echo "Detailed logs:"
echo "  adb logcat | grep BatteryMonitor"
echo "  adb shell su -c dmesg | grep avc"
echo ""
