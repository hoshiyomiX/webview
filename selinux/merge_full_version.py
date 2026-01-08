#!/usr/bin/env python3
"""
Merge Original ROM SELinux Policies with Battery Monitor Rules
===============================================================

This script merges your ROM's original SELinux policy files with
Battery Monitor-specific rules to create full versions ready for
deployment.

Usage:
    1. Place your ROM files in the same directory:
       - vendor_sepolicy_original.cil (your ROM's original policy)
       - vendor_file_contexts_original (your ROM's original contexts)
    
    2. Run: python3 merge_full_version.py
    
    3. Output files will be created:
       - vendor_sepolicy_FULL.cil (ready to deploy)
       - vendor_file_contexts_FULL (ready to deploy)

Author: Battery Monitor SELinux Integration
"""

import os
import sys

# Battery Monitor SELinux CIL Rules
BATTERY_MONITOR_CIL = '''
################################################################################
# Battery Monitor - SELinux Policy (CIL Format)
################################################################################
#
# Pure Sysfs Mode - Direct Kernel Interface
# NO BatteryManager API, NO Intent API, PURE /sys path reading
#
# Package: com.deviant.batterymonitor
# Install: /product/priv-app/BatteryMonitor/
# Domain: priv_app
#
################################################################################

# ------------------------------------------------------------------------------
# BATTERY SYSFS ACCESS - PRIMARY
# ------------------------------------------------------------------------------

# Battery capacity, voltage, current, temperature, status
# Direct read from /sys/class/power_supply/battery/*
(allow priv_app sysfs_batteryinfo (file (ioctl read getattr lock map open watch watch_reads)))
(allow priv_app sysfs_batteryinfo (dir (ioctl read getattr lock open watch watch_reads search)))

# Generic sysfs fallback (if sysfs_batteryinfo context missing)
(allow priv_app sysfs (file (ioctl read getattr lock map open watch watch_reads)))
(allow priv_app sysfs (dir (ioctl read getattr lock open watch watch_reads search)))

# ------------------------------------------------------------------------------
# CHARGER SYSFS ACCESS
# ------------------------------------------------------------------------------

# MediaTek charger voltage (ADC_Charger_Voltage)
(allow priv_app sysfs_charger (file (ioctl read getattr lock map open watch watch_reads)))
(allow priv_app sysfs_charger (dir (ioctl read getattr lock open watch watch_reads search)))

# ------------------------------------------------------------------------------
# THERMAL ZONE ACCESS (TEMPERATURE FALLBACK)
# ------------------------------------------------------------------------------

# Thermal zone temperature reading (/sys/class/thermal/thermal_zone*/temp)
(allow priv_app sysfs_thermal (file (ioctl read getattr lock map open watch watch_reads)))
(allow priv_app sysfs_thermal (dir (ioctl read getattr lock open watch watch_reads search)))

################################################################################
# END OF BATTERY MONITOR SELINUX POLICY
################################################################################
'''

# Battery Monitor File Contexts
BATTERY_MONITOR_FILE_CONTEXTS = '''
################################################################################
# Battery Monitor - SELinux File Contexts
################################################################################
# 
# FILE: vendor_file_contexts
# LOCATION: /vendor/etc/selinux/vendor_file_contexts
# ACTION: APPEND these contexts to your existing vendor_file_contexts
# 
# Package: com.deviant.batterymonitor
# Install Path: /product/priv-app/BatteryMonitor/
# 
# Purpose:
#   Label battery monitor app files and ALL battery/charger sysfs paths
#   with appropriate SELinux contexts for pure sysfs reading mode.
# 
# Usage:
#   cat selinux/vendor_file_contexts >> /vendor/etc/selinux/vendor_file_contexts
# 
# Note:
#   File contexts are applied at boot time, not runtime.
#   After modifying, you must relabel filesystems or reboot.
# 
################################################################################

# ------------------------------------------------------------------------------
# APPLICATION DIRECTORIES
# ------------------------------------------------------------------------------

# Privileged app installation directory
/product/priv-app/BatteryMonitor(/.*)?                      u:object_r:privapp_data_file:s0

# App data directory
/data/data/com\\.deviant\\.batterymonitor(/.*)?               u:object_r:privapp_data_file:s0

# ------------------------------------------------------------------------------
# BATTERY SYSFS PATHS - PRIMARY (ALL METRICS)
# ------------------------------------------------------------------------------

# Main battery power_supply node - ALL battery metrics
# Includes: capacity, voltage_now, current_now, temp, status, etc.
/sys/class/power_supply/battery(/.*)?                       u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/battery/capacity                    u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/battery/voltage_now                 u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/battery/current_now                 u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/battery/temp                        u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/battery/status                      u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/battery/health                      u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/battery/present                     u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/battery/technology                  u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/battery/cycle_count                 u:object_r:sysfs_batteryinfo:s0

# Battery charger-related attributes
/sys/class/power_supply/battery/charger_voltage             u:object_r:sysfs_charger:s0
/sys/class/power_supply/battery/input_voltage               u:object_r:sysfs_charger:s0
/sys/class/power_supply/battery/input_current               u:object_r:sysfs_charger:s0

# ------------------------------------------------------------------------------
# BATTERY SYSFS PATHS - ALTERNATIVE (BMS)
# ------------------------------------------------------------------------------

# Battery Management System (BMS) - Alternative battery node
# Some devices (Xiaomi, OPPO, OnePlus) use bms instead of battery
/sys/class/power_supply/bms(/.*)?                           u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/bms/capacity                        u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/bms/voltage_now                     u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/bms/current_now                     u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/bms/temp                            u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/bms/status                          u:object_r:sysfs_batteryinfo:s0

# ------------------------------------------------------------------------------
# BATTERY SYSFS PATHS - ALTERNATIVE (BAT0)
# ------------------------------------------------------------------------------

# Legacy/ACPI battery interface (rare on Android)
/sys/class/power_supply/BAT0(/.*)?                          u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/BAT0/capacity                       u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/BAT0/voltage_now                    u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/BAT0/current_now                    u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/BAT0/temp                           u:object_r:sysfs_batteryinfo:s0
/sys/class/power_supply/BAT0/status                         u:object_r:sysfs_batteryinfo:s0

# ------------------------------------------------------------------------------
# CHARGER SYSFS PATHS - PRIMARY (MediaTek)
# ------------------------------------------------------------------------------

# MediaTek charger driver sysfs interface
/sys/devices/platform/charger(/.*)?                         u:object_r:sysfs_charger:s0
/sys/devices/platform/charger/ADC_Charger_Voltage           u:object_r:sysfs_charger:s0
/sys/devices/platform/charger/Charger_Voltage               u:object_r:sysfs_charger:s0
/sys/devices/platform/charger/Charger_Type                  u:object_r:sysfs_charger:s0
/sys/devices/platform/charger/Input_Current_Limit           u:object_r:sysfs_charger:s0
/sys/devices/platform/charger/Charging_Current              u:object_r:sysfs_charger:s0

# ------------------------------------------------------------------------------
# CHARGER SYSFS PATHS - GENERIC (power_supply class)
# ------------------------------------------------------------------------------

# Generic charger power_supply node
/sys/class/power_supply/charger(/.*)?                       u:object_r:sysfs_charger:s0
/sys/class/power_supply/charger/voltage_now                 u:object_r:sysfs_charger:s0
/sys/class/power_supply/charger/voltage_max                 u:object_r:sysfs_charger:s0
/sys/class/power_supply/charger/current_now                 u:object_r:sysfs_charger:s0
/sys/class/power_supply/charger/type                        u:object_r:sysfs_charger:s0

# USB power_supply node (charger via USB)
/sys/class/power_supply/usb(/.*)?                           u:object_r:sysfs_charger:s0
/sys/class/power_supply/usb/voltage_now                     u:object_r:sysfs_charger:s0
/sys/class/power_supply/usb/voltage_max                     u:object_r:sysfs_charger:s0
/sys/class/power_supply/usb/current_now                     u:object_r:sysfs_charger:s0

# AC power_supply node (wall charger)
/sys/class/power_supply/ac(/.*)?                            u:object_r:sysfs_charger:s0
/sys/class/power_supply/ac/voltage_now                      u:object_r:sysfs_charger:s0
/sys/class/power_supply/ac/current_now                      u:object_r:sysfs_charger:s0

# ------------------------------------------------------------------------------
# THERMAL ZONE SYSFS (TEMPERATURE FALLBACK)
# ------------------------------------------------------------------------------

# Thermal zones for temperature reading fallback
/sys/class/thermal/thermal_zone0(/.*)?                      u:object_r:sysfs_thermal:s0
/sys/class/thermal/thermal_zone0/temp                       u:object_r:sysfs_thermal:s0
/sys/class/thermal/thermal_zone1/temp                       u:object_r:sysfs_thermal:s0

# ------------------------------------------------------------------------------
# DEVICE-SPECIFIC CHARGER IC PATHS
# ------------------------------------------------------------------------------

# Qualcomm PMIC charger
/sys/class/power_supply/qpnp-charger(/.*)?                  u:object_r:sysfs_charger:s0
/sys/class/power_supply/qpnp-charger/voltage_now            u:object_r:sysfs_charger:s0

# Samsung charger IC (Maxim)
/sys/class/power_supply/max77705-charger(/.*)?              u:object_r:sysfs_charger:s0
/sys/class/power_supply/max77705-charger/voltage_now        u:object_r:sysfs_charger:s0

# Texas Instruments BQ series charger (OnePlus, Xiaomi)
/sys/class/power_supply/bq25890(/.*)?                       u:object_r:sysfs_charger:s0
/sys/class/power_supply/bq25890/voltage_now                 u:object_r:sysfs_charger:s0

# Texas Instruments BQ dual charger (Xiaomi fast charge)
/sys/class/power_supply/bq2597x-master(/.*)?                u:object_r:sysfs_charger:s0
/sys/class/power_supply/bq2597x-master/voltage_now          u:object_r:sysfs_charger:s0
/sys/class/power_supply/bq2597x-slave(/.*)?                 u:object_r:sysfs_charger:s0
/sys/class/power_supply/bq2597x-slave/voltage_now           u:object_r:sysfs_charger:s0

################################################################################
# END OF BATTERY MONITOR FILE CONTEXTS
################################################################################
'''

def check_duplicate(content, marker):
    """Check if Battery Monitor rules already exist in file"""
    return marker in content

def merge_files():
    """Main merge function"""
    print("=" * 80)
    print("  Battery Monitor SELinux Policy Merger")
    print("=" * 80)
    print()
    
    # Check if original files exist
    cil_original = "vendor_sepolicy_original.cil"
    fc_original = "vendor_file_contexts_original"
    
    if not os.path.exists(cil_original):
        print(f"❌ ERROR: {cil_original} not found!")
        print(f"   Place your ROM's vendor_sepolicy.cil as '{cil_original}'")
        return False
    
    if not os.path.exists(fc_original):
        print(f"❌ ERROR: {fc_original} not found!")
        print(f"   Place your ROM's vendor_file_contexts as '{fc_original}'")
        return False
    
    print(f"✅ Found: {cil_original}")
    print(f"✅ Found: {fc_original}")
    print()
    
    # Read original files
    print("📖 Reading original ROM policies...")
    with open(cil_original, 'r', encoding='utf-8') as f:
        cil_content = f.read()
    
    with open(fc_original, 'r', encoding='utf-8') as f:
        fc_content = f.read()
    
    print(f"   vendor_sepolicy.cil: {len(cil_content):,} bytes")
    print(f"   vendor_file_contexts: {len(fc_content):,} bytes")
    print()
    
    # Check for duplicates
    print("🔍 Checking for existing Battery Monitor rules...")
    cil_has_bm = check_duplicate(cil_content, "Battery Monitor - SELinux Policy")
    fc_has_bm = check_duplicate(fc_content, "Battery Monitor - SELinux File Contexts")
    
    if cil_has_bm or fc_has_bm:
        print("⚠️  WARNING: Battery Monitor rules already exist!")
        if cil_has_bm:
            print("   - vendor_sepolicy.cil already has Battery Monitor policy")
        if fc_has_bm:
            print("   - vendor_file_contexts already has Battery Monitor contexts")
        print()
        response = input("   Continue anyway? (y/N): ")
        if response.lower() != 'y':
            print("❌ Aborted by user")
            return False
        print()
    else:
        print("✅ No existing Battery Monitor rules found")
        print()
    
    # Merge CIL
    print("🔧 Merging vendor_sepolicy.cil...")
    merged_cil = cil_content.rstrip() + "\n\n" + BATTERY_MONITOR_CIL
    output_cil = "vendor_sepolicy_FULL.cil"
    
    with open(output_cil, 'w', encoding='utf-8') as f:
        f.write(merged_cil)
    
    print(f"✅ Created: {output_cil} ({len(merged_cil):,} bytes)")
    print()
    
    # Merge File Contexts
    print("🔧 Merging vendor_file_contexts...")
    merged_fc = fc_content.rstrip() + "\n\n" + BATTERY_MONITOR_FILE_CONTEXTS
    output_fc = "vendor_file_contexts_FULL"
    
    with open(output_fc, 'w', encoding='utf-8') as f:
        f.write(merged_fc)
    
    print(f"✅ Created: {output_fc} ({len(merged_fc):,} bytes)")
    print()
    
    # Summary
    print("=" * 80)
    print("  ✨ MERGE COMPLETE!")
    print("=" * 80)
    print()
    print("📦 Output Files (Ready to Deploy):")
    print(f"   1. {output_cil}")
    print(f"   2. {output_fc}")
    print()
    print("📋 Next Steps:")
    print("   1. Copy to your ROM build:")
    print(f"      cp {output_cil} /vendor/etc/selinux/vendor_sepolicy.cil")
    print(f"      cp {output_fc} /vendor/etc/selinux/vendor_file_contexts")
    print()
    print("   2. Rebuild vendor.img:")
    print("      make vendorimage")
    print()
    print("   3. Flash and reboot")
    print()
    print("   4. Verify contexts:")
    print("      adb shell ls -laZ /sys/class/power_supply/battery/capacity")
    print("      adb shell ls -laZ /product/priv-app/BatteryMonitor/")
    print()
    
    return True

if __name__ == "__main__":
    try:
        success = merge_files()
        sys.exit(0 if success else 1)
    except Exception as e:
        print(f"\n❌ ERROR: {str(e)}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
