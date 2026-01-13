#!/bin/bash
# Script untuk integrate BatteryMonitor ke device tree
# Usage: ./integrate_to_device.sh /path/to/device/vendor/codename

set -e

DEVICE_TREE="$1"
APP_NAME="BatteryMonitor"
PACKAGE="com.deviant.batterymonitor"

if [ -z "$DEVICE_TREE" ]; then
    echo "Usage: $0 <device_tree_path>"
    echo "Example: $0 ~/android/lineage/device/xiaomi/gauguin"
    exit 1
fi

if [ ! -d "$DEVICE_TREE" ]; then
    echo "Error: Device tree not found at $DEVICE_TREE"
    exit 1
fi

echo "[*] Integrating $APP_NAME to $DEVICE_TREE"

# 1. Build APK
echo "[1/5] Building APK..."
if [ ! -f "app/build/outputs/apk/release/app-release.apk" ]; then
    echo "Building release APK..."
    ./gradlew assembleRelease
fi

# 2. Create priv-app directory
echo "[2/5] Creating priv-app structure..."
mkdir -p "$DEVICE_TREE/proprietary/priv-app/$APP_NAME"
cp app/build/outputs/apk/release/app-release.apk \
   "$DEVICE_TREE/proprietary/priv-app/$APP_NAME/$APP_NAME.apk"
echo "   ✓ APK copied to $DEVICE_TREE/proprietary/priv-app/$APP_NAME/"

# 3. Create Android.bp
echo "[3/5] Creating Android.bp..."
cat > "$DEVICE_TREE/proprietary/priv-app/$APP_NAME/Android.bp" << 'EOF'
android_app_import {
    name: "BatteryMonitor",
    owner: "deviant",
    apk: "BatteryMonitor.apk",
    certificate: "platform",
    dex_preopt: {
        enabled: false,
    },
    privileged: true,
}
EOF
echo "   ✓ Android.bp created"

# 4. Update device.mk
echo "[4/5] Updating device.mk..."
if ! grep -q "PRODUCT_PACKAGES.*$APP_NAME" "$DEVICE_TREE/device.mk" 2>/dev/null; then
    echo "" >> "$DEVICE_TREE/device.mk"
    echo "# Battery Monitor Priv-App" >> "$DEVICE_TREE/device.mk"
    echo "PRODUCT_PACKAGES += \\" >> "$DEVICE_TREE/device.mk"
    echo "    $APP_NAME" >> "$DEVICE_TREE/device.mk"
    echo "   ✓ Added to device.mk"
else
    echo "   ⚠ Already exists in device.mk, skipping"
fi

# 5. SELinux policies
echo "[5/5] Integrating SELinux policies..."
mkdir -p "$DEVICE_TREE/sepolicy/vendor"

# Append CIL rules
if [ -f "$DEVICE_TREE/sepolicy/vendor/vendor_sepolicy.cil" ]; then
    echo "" >> "$DEVICE_TREE/sepolicy/vendor/vendor_sepolicy.cil"
    cat sepolicy/vendor_sepolicy.cil >> "$DEVICE_TREE/sepolicy/vendor/vendor_sepolicy.cil"
    echo "   ✓ Appended to existing vendor_sepolicy.cil"
else
    cp sepolicy/vendor_sepolicy.cil "$DEVICE_TREE/sepolicy/vendor/"
    echo "   ✓ Created vendor_sepolicy.cil"
fi

# Append file contexts
if [ -f "$DEVICE_TREE/sepolicy/vendor/file_contexts" ]; then
    echo "" >> "$DEVICE_TREE/sepolicy/vendor/file_contexts"
    cat sepolicy/vendor_file_contexts >> "$DEVICE_TREE/sepolicy/vendor/file_contexts"
    echo "   ✓ Appended to existing file_contexts"
else
    cp sepolicy/vendor_file_contexts "$DEVICE_TREE/sepolicy/vendor/file_contexts"
    echo "   ✓ Created file_contexts"
fi

echo ""
echo "✅ Integration complete!"
echo ""
echo "Next steps:"
echo "1. cd \$ROM_SOURCE && . build/envsetup.sh"
echo "2. lunch lineage_<codename>-userdebug"
echo "3. mka bacon"
echo ""
echo "To verify after flashing:"
echo "  adb shell pm list packages | grep $PACKAGE"
echo "  adb shell ls -laZ /system/priv-app/$APP_NAME"
echo ""
