LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# Module name (harus match dengan folder di priv-app/)
LOCAL_MODULE := BatteryMonitor
LOCAL_MODULE_TAGS := optional

# Certificate untuk signing (platform = system signature)
LOCAL_CERTIFICATE := platform

# Source APK path
LOCAL_SRC_FILES := app/build/outputs/apk/release/app-release.apk

# Module class
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

# Install sebagai privileged app
LOCAL_PRIVILEGED_MODULE := true

# Allow missing dependencies (optional)
LOCAL_ENFORCE_USES_LIBRARIES := false

# Overrides (jika perlu replace app lain)
# LOCAL_OVERRIDES_PACKAGES := 

include $(BUILD_PREBUILT)
