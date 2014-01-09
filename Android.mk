LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

# Need to specify an invalid resoure path to avoid including resource
LOCAL_RESOURCE_DIR := res_none

LOCAL_PACKAGE_NAME := InCallUI
LOCAL_CERTIFICATE := shared
LOCAL_PRIVELEGED_MODULE := false

include $(BUILD_PACKAGE)

# Build the test package
include $(call all-makefiles-under,$(LOCAL_PATH))
