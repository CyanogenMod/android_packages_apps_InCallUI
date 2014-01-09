LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

# Need to specify an invalid resoure path to avoid including resource
LOCAL_RESOURCE_DIR := res_none

# Without any resource, we don't depend on framework-res in the build
# system, but we actually do to compile AndroidManifest.xml. Avoid
# the issue by setting an SDK version to compile against a historical
# SDK.
LOCAL_SDK_VERSION := 21

LOCAL_PACKAGE_NAME := InCallUI
LOCAL_CERTIFICATE := shared
LOCAL_PRIVELEGED_MODULE := false

include $(BUILD_PACKAGE)
