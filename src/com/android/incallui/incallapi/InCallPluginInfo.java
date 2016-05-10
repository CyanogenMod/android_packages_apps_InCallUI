/*
 * Copyright (C) 2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.incallui.incallapi;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.graphics.drawable.Drawable;

public class InCallPluginInfo {
    private ComponentName mPluginComponent;
    private String mPluginTitle;
    private String mUserId;
    private String mMimeType;
    /* Plugin's simple brand icon (24dp x 24dp)
       Expected format: Vector Drawable (.xml)
       2 colors allowed. */
    private Drawable mPluginBrandIcon;
    /* Plugin's video call action icon (24dp x 24dp)
       Expected format: Vector Drawable (.xml)
       1 color allowed. */
    private Drawable mPluginVideoIcon;

    private InCallPluginInfo() {
    }

    public ComponentName getPluginComponent() {
        return mPluginComponent;
    }

    public String getPluginTitle() {
        return mPluginTitle;
    }

    public String getUserId() {
        return mUserId;
    }

    public String getMimeType() {
        return mMimeType;
    }

    /* Plugin's simple brand icon (24dp x 24dp)
       Expected format: Vector Drawable (.xml)
       2 colors allowed. */
    public Drawable getPluginBrandIcon() {
        return mPluginBrandIcon;
    }

    /* Plugin's video call action icon (24dp x 24dp)
       Expected format: Vector Drawable (.xml)
       1 color allowed. */
    public Drawable getPluginVideoIcon() {
        return mPluginVideoIcon;
    }

    public static class Builder {
        private ComponentName mPluginComponent;
        private String mPluginTitle;
        private String mUserId;
        private String mMimeType;
        private Drawable mPluginBrandIcon;
        private Drawable mPluginVideoIcon;

        public Builder() {
        }

        public Builder setPluginComponent(ComponentName pluginComponent) {
            this.mPluginComponent = pluginComponent;
            return this;
        }

        public Builder setPluginTitle(String pluginTitle) {
            this.mPluginTitle = pluginTitle;
            return this;
        }

        public Builder setUserId(String userId) {
            this.mUserId = userId;
            return this;
        }

        public Builder setMimeType(String mimeType) {
            this.mMimeType = mimeType;
            return this;
        }

        public Builder setPluginBrandIcon(Drawable pluginBrandIcon) {
            this.mPluginBrandIcon = pluginBrandIcon;
            return this;
        }

        public Builder setPluginVideoIcon(Drawable pluginVideoIcon) {
            this.mPluginVideoIcon = pluginVideoIcon;
            return this;
        }

        // TODO: Check if we want to require an invite intent or not
        public InCallPluginInfo build() throws IllegalStateException{
            if (mPluginComponent == null || mPluginTitle == null || mMimeType == null
                    || mPluginBrandIcon == null || mPluginVideoIcon == null) {
                throw new IllegalStateException();
            }
            InCallPluginInfo info = new InCallPluginInfo();
            info.mPluginComponent = mPluginComponent;
            info.mPluginTitle = mPluginTitle;
            info.mUserId = mUserId;
            info.mMimeType = mMimeType;
            info.mPluginBrandIcon = mPluginBrandIcon;
            info.mPluginVideoIcon = mPluginVideoIcon;
            return info;
        }
    }
}