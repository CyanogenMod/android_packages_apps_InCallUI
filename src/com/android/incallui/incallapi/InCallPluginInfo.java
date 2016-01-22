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
    private Drawable mPluginColorIcon;
    private Drawable mPluginSingleColorIcon;
    private PendingIntent mInviteIntent;

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

    public Drawable getPluginColorIcon() {
        return mPluginColorIcon;
    }

    public Drawable getPluginSingleColorIcon() {
        return mPluginSingleColorIcon;
    }

    public PendingIntent getPluginInviteIntent() {
        return mInviteIntent;
    }

    public static class Builder {
        private ComponentName mPluginComponent;
        private String mPluginTitle;
        private String mUserId;
        private String mMimeType;
        private Drawable mPluginColorIcon;
        private Drawable mPluginSingleColorIcon;
        private PendingIntent mInviteIntent;

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

        public Builder setPluginColorIcon(Drawable pluginColorIcon) {
            this.mPluginColorIcon = pluginColorIcon;
            return this;
        }

        public Builder setPluginSingleColorIcon(Drawable pluginSingleColorIcon) {
            this.mPluginSingleColorIcon = pluginSingleColorIcon;
            return this;
        }

        public Builder setPluginInviteIntent(PendingIntent pluginInviteIntent) {
            this.mInviteIntent = pluginInviteIntent;
            return this;
        }

        // TODO: Check if we want to require an invite intent or not
        public InCallPluginInfo build() throws IllegalStateException{
            if (mPluginComponent == null || mPluginTitle == null || mMimeType == null
                    || mPluginColorIcon == null || mPluginSingleColorIcon == null) {
                throw new IllegalStateException();
            }
            InCallPluginInfo info = new InCallPluginInfo();
            info.mPluginComponent = mPluginComponent;
            info.mPluginTitle = mPluginTitle;
            info.mUserId = mUserId;
            info.mMimeType = mMimeType;
            info.mPluginColorIcon = mPluginColorIcon;
            info.mPluginSingleColorIcon = mPluginSingleColorIcon;
            info.mInviteIntent = mInviteIntent;
            return info;
        }
    }
}