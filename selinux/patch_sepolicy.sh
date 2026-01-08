#!/bin/bash
################################################################################
# patch_sepolicy.sh - Merge Original SELinux Files with Battery Monitor Policies
################################################################################
#
# Description:
#   This script merges original vendor SELinux files from prebuilt/ directory
#   with Battery Monitor policies from selinux/ directory.
#
# Usage:
#   $ bash selinux/patch_sepolicy.sh
#
# Input:
#   - prebuilt/vendor_sepolicy.cil (original ROM file)
#   - prebuilt/vendor_file_contexts (original ROM file)
#   - selinux/vendor_sepolicy.cil (Battery Monitor policies)
#   - selinux/vendor_file_contexts (Battery Monitor contexts)
#
# Output:
#   - selinux/patched/vendor_sepolicy.cil.patched
#   - selinux/patched/vendor_file_contexts.patched
#
################################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Directories
PREBUILT_DIR="prebuilt"
SELINUX_DIR="selinux"
PATCHED_DIR="${SELINUX_DIR}/patched"

# Input files
ORIGINAL_CIL="${PREBUILT_DIR}/vendor_sepolicy.cil"
ORIGINAL_CONTEXTS="${PREBUILT_DIR}/vendor_file_contexts"
BATTERY_CIL="${SELINUX_DIR}/vendor_sepolicy.cil"
BATTERY_CONTEXTS="${SELINUX_DIR}/vendor_file_contexts"

# Output files
PATCHED_CIL="${PATCHED_DIR}/vendor_sepolicy.cil.patched"
PATCHED_CONTEXTS="${PATCHED_DIR}/vendor_file_contexts.patched"

################################################################################
# Functions
################################################################################

print_header() {
    echo -e "${BLUE}================================================================================${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}================================================================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

################################################################################
# Main Script
################################################################################

print_header "Battery Monitor - SELinux Policy Patcher"

echo ""
print_info "This script merges original vendor SELinux files with Battery Monitor policies"
echo ""

# Step 1: Validate input files
print_header "Step 1: Validating Input Files"

if [ ! -f "${ORIGINAL_CIL}" ]; then
    print_error "Original vendor_sepolicy.cil not found: ${ORIGINAL_CIL}"
    print_info "Please place your ROM's vendor_sepolicy.cil in prebuilt/ directory"
    exit 1
fi
print_success "Found original vendor_sepolicy.cil ($(wc -c < "${ORIGINAL_CIL}" | numfmt --to=iec-i)B)"

if [ ! -f "${ORIGINAL_CONTEXTS}" ]; then
    print_error "Original vendor_file_contexts not found: ${ORIGINAL_CONTEXTS}"
    print_info "Please place your ROM's vendor_file_contexts in prebuilt/ directory"
    exit 1
fi
print_success "Found original vendor_file_contexts ($(wc -c < "${ORIGINAL_CONTEXTS}" | numfmt --to=iec-i)B)"

if [ ! -f "${BATTERY_CIL}" ]; then
    print_error "Battery Monitor policies not found: ${BATTERY_CIL}"
    exit 1
fi
print_success "Found Battery Monitor policies ($(wc -c < "${BATTERY_CIL}" | numfmt --to=iec-i)B)"

if [ ! -f "${BATTERY_CONTEXTS}" ]; then
    print_error "Battery Monitor contexts not found: ${BATTERY_CONTEXTS}"
    exit 1
fi
print_success "Found Battery Monitor contexts ($(wc -c < "${BATTERY_CONTEXTS}" | numfmt --to=iec-i)B)"

echo ""

# Step 2: Create output directory
print_header "Step 2: Preparing Output Directory"

mkdir -p "${PATCHED_DIR}"
print_success "Created output directory: ${PATCHED_DIR}"

echo ""

# Step 3: Backup original files
print_header "Step 3: Creating Backups"

cp -p "${ORIGINAL_CIL}" "${ORIGINAL_CIL}.bak"
print_success "Backed up vendor_sepolicy.cil to ${ORIGINAL_CIL}.bak"

cp -p "${ORIGINAL_CONTEXTS}" "${ORIGINAL_CONTEXTS}.bak"
print_success "Backed up vendor_file_contexts to ${ORIGINAL_CONTEXTS}.bak"

echo ""

# Step 4: Merge vendor_sepolicy.cil
print_header "Step 4: Merging vendor_sepolicy.cil"

print_info "Appending Battery Monitor policies to original file..."

{
    # Copy original file
    cat "${ORIGINAL_CIL}"
    
    # Add separator
    echo ""
    echo ""
    echo ";; ================================================================================"
    echo ";; BATTERY MONITOR - APPENDED POLICIES"
    echo ";; ================================================================================"
    echo ";; "
    echo ";; The following SELinux policies were automatically appended by patch_sepolicy.sh"
    echo ";; to enable Battery Monitor app to read battery metrics from sysfs."
    echo ";; "
    echo ";; Package: com.deviant.batterymonitor"
    echo ";; Install Path: /product/priv-app/BatteryMonitor/"
    echo ";; Domain: priv_app"
    echo ";; "
    echo ";; Generated: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ";; "
    echo ";; ================================================================================"
    echo ""
    
    # Skip header comments from Battery Monitor policies (first 29 lines)
    tail -n +30 "${BATTERY_CIL}"
    
} > "${PATCHED_CIL}"

ORIG_CIL_LINES=$(wc -l < "${ORIGINAL_CIL}")
PATCHED_CIL_LINES=$(wc -l < "${PATCHED_CIL}")
ADDED_CIL_LINES=$((PATCHED_CIL_LINES - ORIG_CIL_LINES))

print_success "Merged vendor_sepolicy.cil"
print_info "  Original lines: ${ORIG_CIL_LINES}"
print_info "  Added lines: ${ADDED_CIL_LINES}"
print_info "  Total lines: ${PATCHED_CIL_LINES}"

echo ""

# Step 5: Merge vendor_file_contexts
print_header "Step 5: Merging vendor_file_contexts"

print_info "Appending Battery Monitor contexts to original file..."

{
    # Copy original file
    cat "${ORIGINAL_CONTEXTS}"
    
    # Add separator
    echo ""
    echo ""
    echo "################################################################################"
    echo "# BATTERY MONITOR - APPENDED FILE CONTEXTS"
    echo "################################################################################"
    echo "#"
    echo "# The following file contexts were automatically appended by patch_sepolicy.sh"
    echo "# to label battery sysfs paths for Battery Monitor app access."
    echo "#"
    echo "# Package: com.deviant.batterymonitor"
    echo "# Install Path: /product/priv-app/BatteryMonitor/"
    echo "#"
    echo "# Generated: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "#"
    echo "################################################################################"
    echo ""
    
    # Skip header comments from Battery Monitor contexts (first 19 lines)
    tail -n +20 "${BATTERY_CONTEXTS}"
    
} > "${PATCHED_CONTEXTS}"

ORIG_CONTEXTS_LINES=$(wc -l < "${ORIGINAL_CONTEXTS}")
PATCHED_CONTEXTS_LINES=$(wc -l < "${PATCHED_CONTEXTS}")
ADDED_CONTEXTS_LINES=$((PATCHED_CONTEXTS_LINES - ORIG_CONTEXTS_LINES))

print_success "Merged vendor_file_contexts"
print_info "  Original lines: ${ORIG_CONTEXTS_LINES}"
print_info "  Added lines: ${ADDED_CONTEXTS_LINES}"
print_info "  Total lines: ${PATCHED_CONTEXTS_LINES}"

echo ""

# Step 6: Validate CIL syntax
print_header "Step 6: Validating CIL Syntax"

print_info "Checking parentheses balance..."

OPEN_PARENS=$(grep -o '(' "${PATCHED_CIL}" | wc -l)
CLOSE_PARENS=$(grep -o ')' "${PATCHED_CIL}" | wc -l)

if [ "${OPEN_PARENS}" -eq "${CLOSE_PARENS}" ]; then
    print_success "CIL syntax valid (${OPEN_PARENS} balanced parentheses)"
else
    print_error "CIL syntax error: Unbalanced parentheses!"
    print_info "  Opening '(': ${OPEN_PARENS}"
    print_info "  Closing ')': ${CLOSE_PARENS}"
    exit 1
fi

echo ""

# Step 7: Validate file_contexts
print_header "Step 7: Validating File Contexts"

print_info "Checking file_contexts regex patterns..."

# Count context rules (lines with u:object_r:)
CONTEXT_RULES=$(grep -c 'u:object_r:' "${PATCHED_CONTEXTS}" || true)
print_success "Found ${CONTEXT_RULES} file context rules"

# Check for Battery Monitor specific contexts
BATTERY_CONTEXTS_COUNT=$(grep -c 'batterymonitor' "${PATCHED_CONTEXTS}" || true)
SYSFS_BATTERYINFO_COUNT=$(grep -c 'sysfs_batteryinfo' "${PATCHED_CONTEXTS}" || true)
SYSFS_CHARGER_COUNT=$(grep -c 'sysfs_charger' "${PATCHED_CONTEXTS}" || true)

print_info "  Battery Monitor app contexts: ${BATTERY_CONTEXTS_COUNT}"
print_info "  sysfs_batteryinfo labels: ${SYSFS_BATTERYINFO_COUNT}"
print_info "  sysfs_charger labels: ${SYSFS_CHARGER_COUNT}"

echo ""

# Step 8: Generate checksums
print_header "Step 8: Generating Checksums"

print_info "Calculating file checksums..."

if command -v md5sum &> /dev/null; then
    MD5_CIL=$(md5sum "${PATCHED_CIL}" | awk '{print $1}')
    MD5_CONTEXTS=$(md5sum "${PATCHED_CONTEXTS}" | awk '{print $1}')
    print_success "MD5 checksums generated"
    print_info "  vendor_sepolicy.cil.patched: ${MD5_CIL}"
    print_info "  vendor_file_contexts.patched: ${MD5_CONTEXTS}"
else
    print_warning "md5sum not available, skipping checksum generation"
fi

echo ""

# Step 9: Summary
print_header "Patching Complete!"

echo ""
print_success "Patched files created successfully:"
echo ""
echo "  📄 ${PATCHED_CIL}"
echo "     Size: $(wc -c < "${PATCHED_CIL}" | numfmt --to=iec-i)B"
echo "     Lines: ${PATCHED_CIL_LINES} (+${ADDED_CIL_LINES})"
echo ""
echo "  📄 ${PATCHED_CONTEXTS}"
echo "     Size: $(wc -c < "${PATCHED_CONTEXTS}" | numfmt --to=iec-i)B"
echo "     Lines: ${PATCHED_CONTEXTS_LINES} (+${ADDED_CONTEXTS_LINES})"
echo ""

print_header "Next Steps"

echo ""
print_info "1. Copy patched files to ROM source:"
echo ""
echo "   $ cp ${PATCHED_CIL} \\" 
echo "        \$ROM/vendor/etc/selinux/vendor_sepolicy.cil"
echo ""
echo "   $ cp ${PATCHED_CONTEXTS} \\" 
echo "        \$ROM/vendor/etc/selinux/vendor_file_contexts"
echo ""

print_info "2. Install Battery Monitor app:"
echo ""
echo "   $ cp BatteryMonitor.apk \\" 
echo "        \$ROM/product/priv-app/BatteryMonitor/"
echo ""
echo "   $ cp privapp-permissions-batterymonitor.xml \\" 
echo "        \$ROM/product/etc/permissions/"
echo ""

print_info "3. Build ROM:"
echo ""
echo "   $ cd \$ROM"
echo "   $ make -j\$(nproc)"
echo ""

print_info "4. Flash and verify:"
echo ""
echo "   $ fastboot flash vendor vendor.img"
echo "   $ fastboot flash product product.img"
echo "   $ fastboot reboot"
echo ""
echo "   # After boot:"
echo "   $ adb shell ps -Z | grep batterymonitor"
echo "   $ adb shell cat /sdcard/battery_debug.txt"
echo ""

print_header "Backup Information"

echo ""
print_info "Original files backed up to:"
echo "   ${ORIGINAL_CIL}.bak"
echo "   ${ORIGINAL_CONTEXTS}.bak"
echo ""

print_success "Patching complete! Ready for ROM integration."
echo ""

exit 0
