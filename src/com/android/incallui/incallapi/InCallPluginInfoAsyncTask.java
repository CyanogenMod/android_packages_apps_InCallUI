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
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;
import android.util.Log;
import com.android.phone.common.ambient.AmbientConnection;
import com.android.phone.common.incall.CallMethodHelper;
import com.android.phone.common.incall.CallMethodInfo;
import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.incall.InCallApi;
import com.cyanogen.ambient.incall.InCallServices;
import com.cyanogen.ambient.incall.extension.InCallContactInfo;
import com.cyanogen.ambient.incall.results.MimeTypeListResult;
import com.cyanogen.ambient.incall.results.PendingIntentResult;
import com.cyanogen.ambient.incall.results.PluginStatusResult;
import com.cyanogen.ambient.incall.results.InCallProviderInfoResult;
import com.cyanogen.ambient.incall.results.MimeTypeResult;
import com.cyanogen.ambient.plugin.PluginStatus;
import com.google.common.base.Joiner;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements a Loader class to asynchronously load InCall plugin Info.
 */
public class InCallPluginInfoAsyncTask extends AsyncTask<Void, Void, List<InCallPluginInfo>> {
    private static final String TAG = InCallPluginInfoAsyncTask.class.getSimpleName();
    private static final boolean DEBUG = false;
    private final Context mContext;
    private InCallContactInfo mContactInfo;
    private WeakReference<IInCallPostExecute> mPostExecute;

    public interface IInCallPostExecute {
        void onPostExecuteTask(List<InCallPluginInfo> inCallPluginInfoList);
    }

    private static final String[] CONTACT_PROJECTION = new String[] {
            Phone.NUMBER,                   // 0
            Phone.MIMETYPE,                 // 1
    };

    public InCallPluginInfoAsyncTask(Context context, InCallContactInfo contactInfo,
            IInCallPostExecute postExecute) {
        mContext = context.getApplicationContext();
        mContactInfo = contactInfo;
        mPostExecute = new WeakReference<IInCallPostExecute>(postExecute);
    }

    /**
     * Loads the CallMethods in background.
     * @return List of available (authenticated and enabled) incall plugins associated with the
     * specified contact.
     */
    @Override
    protected List<InCallPluginInfo> doInBackground(Void... params) {
        List<InCallPluginInfo> inCallPluginList = new ArrayList<InCallPluginInfo>();
        List<InCallPluginInfo.Builder> inCallPluginInfoBuilderList =
                new ArrayList<InCallPluginInfo.Builder>();
        Map<String, Integer> pluginIndex = new HashMap<String, Integer>();
        HashMap<ComponentName, CallMethodInfo> plugins = CallMethodHelper.getAllEnabledCallMethods();
        String mimeTypes = CallMethodHelper.getAllEnabledVideoCallableMimeTypes();

        if (mContactInfo == null) {
            return inCallPluginList;
        }

        if (mContactInfo.mLookupUri != null &&
                !TextUtils.isEmpty(mContactInfo.mLookupUri.toString())) {
            // Query contact info with these mimetypes
            final Uri queryUri;
            final String inputUriAsString = mContactInfo.mLookupUri.toString();
            if (inputUriAsString.startsWith(Contacts.CONTENT_URI.toString())) {
                if (!inputUriAsString.endsWith(Contacts.Data.CONTENT_DIRECTORY)) {
                    queryUri = Uri.withAppendedPath(mContactInfo.mLookupUri,
                            Contacts.Data.CONTENT_DIRECTORY);
                } else {
                    queryUri = mContactInfo.mLookupUri;
                }
            } else if (inputUriAsString.startsWith(Data.CONTENT_URI.toString())) {
                queryUri = mContactInfo.mLookupUri;
            } else {
                throw new UnsupportedOperationException(
                        "Input Uri must be contact Uri or data Uri (input: \"" +
                                mContactInfo.mLookupUri + "\")");
            }

            if (!TextUtils.isEmpty(mimeTypes) && queryUri != null) {
                Cursor cursor = mContext.getContentResolver().query(
                        queryUri,
                        CONTACT_PROJECTION,
                        constructSelection(mimeTypes),
                        null,
                        null);
                if (cursor != null) {
                    try {
                        final Context context = mContext;
                        while (cursor.moveToNext()) {
                            int cursorIndex = cursor.getColumnIndex(Phone.NUMBER);
                            final String id = cursorIndex == -1 ?
                                    null : cursor.getString(cursorIndex);
                            cursorIndex = cursor.getColumnIndex(Phone.MIMETYPE);
                            final String mimeType =
                                    cursorIndex == -1 ? null : cursor.getString(cursorIndex);
                            InCallPluginInfo.Builder infoBuilder = new InCallPluginInfo.Builder()
                                    .setUserId(id).setMimeType(mimeType);
                            inCallPluginInfoBuilderList.add(infoBuilder);
                            pluginIndex.put(mimeType, inCallPluginInfoBuilderList.size() - 1);
                        }
                    } finally {
                        cursor.close();
                    }
                }
            } else {
                if (DEBUG) Log.i("InCall", "No InCall plugins found with video callable mimetypes");
                return null;
            }
        }

        // Fill in plugin Info.
        if (plugins != null && !plugins.isEmpty()) {
            InCallApi inCallServices = InCallServices.getInstance();
            for (CallMethodInfo callMethod : plugins.values()) {
                if (!pluginIndex.containsKey(callMethod.mVideoCallableMimeType)) {
                    if (DEBUG) {
                        Log.d(TAG, "Contact does not have account with this plugin, looking up" +
                                " invite for Component=" + callMethod.mComponent.flattenToString() +
                                " and Uri=" + mContactInfo.mLookupUri.toString());
                    }
                    PendingIntentResult inviteResult =
                            inCallServices.getInviteIntent(
                                    AmbientConnection.CLIENT.get(mContext.getApplicationContext()),
                                    callMethod.mComponent, mContactInfo).await();
                    InCallPluginInfo.Builder infoBuilder =
                            new InCallPluginInfo.Builder().setUserId(null)
                                    .setMimeType(callMethod.mVideoCallableMimeType)
                                    .setPluginInviteIntent(inviteResult == null ?
                                            null : inviteResult.intent);
                    inCallPluginInfoBuilderList.add(infoBuilder);
                    pluginIndex.put(callMethod.mVideoCallableMimeType,
                            inCallPluginInfoBuilderList.size() - 1);
                }

                int index = pluginIndex.get(callMethod.mVideoCallableMimeType);
                InCallPluginInfo.Builder infoBuilder = inCallPluginInfoBuilderList.get(index);
                infoBuilder.setPluginComponent(callMethod.mComponent)
                        .setPluginTitle(callMethod.mName)
                        .setPluginColorIcon(callMethod.mBrandIcon)
                        .setPluginSingleColorIcon(callMethod.mSingleColorBrandIcon);

                try {
                    inCallPluginList.add(infoBuilder.build());
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Failed to build InCallPluginInfo object.");
                    continue;
                }
            }
        }
        return inCallPluginList;
    }

    @Override
    protected void onPostExecute(List<InCallPluginInfo> inCallPluginInfoList) {
        if (mPostExecute != null) {
            final IInCallPostExecute postExecute = mPostExecute.get();
            if (postExecute != null) {
                postExecute.onPostExecuteTask(inCallPluginInfoList);
            }
        }
    }

    private String constructSelection(String mimeTypes) {
        StringBuilder selection = new StringBuilder();
        if (!TextUtils.isEmpty(mimeTypes)) {
            selection.append(Data.MIMETYPE + " IN ('");
            selection.append(mimeTypes);
            selection.append("') AND " + Data.DATA1 + " NOT NULL");
        }
        return selection.toString();
    }
}
