/*
 * Copyright (c) 2014 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.android.incallui;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;
import com.suntek.mway.rcs.client.api.autoconfig.RcsAccountApi;
import com.suntek.mway.rcs.client.api.capability.impl.CapabilityApi;
import com.suntek.mway.rcs.client.api.impl.groupchat.ConfApi;
import com.suntek.mway.rcs.client.api.support.RcsSupportApi;
import com.suntek.mway.rcs.client.api.RCSServiceListener;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.voip.impl.RichScreenApi;

public class RcsApiManager {
    private static boolean mIsRcsServiceInstalled;
    private static String TAG = "InCallUI_RcsApiManager";
    private static ConfApi mConfApi = new ConfApi();
    private static RcsAccountApi mRcsAccountApi = new RcsAccountApi();
    private static CapabilityApi mCapabilityApi = new CapabilityApi();
    private static RichScreenApi mRichScreenApi = new RichScreenApi(null);
    public static Context mContext;
    public static void init(Context context) {
        mContext = context;
        Log.d(TAG, "RCS init");
        mIsRcsServiceInstalled = RcsSupportApi.isRcsServiceInstalled(context);
        if (!mIsRcsServiceInstalled) {
            Log.d(TAG, "mIsRcsServiceInstalled" + mIsRcsServiceInstalled);
            return;
        }

        mRcsAccountApi.init(context, new RCSServiceListener() {
            @Override
            public void onServiceDisconnected() throws RemoteException {
                Log.d(TAG, "RcsAccountApi disconnected");
            }

            @Override
            public void onServiceConnected() throws RemoteException {
                Log.d(TAG, "RcsAccountApi connected");
            }
        });

        mConfApi.init(context, new RCSServiceListener() {
            public void onServiceDisconnected() throws RemoteException {
                Log.d(TAG, "ConfApi connected");
            }

            public void onServiceConnected() throws RemoteException {
                Log.d(TAG, "ConfApi connected");
            }
        });
        mRichScreenApi.init(context, new RCSServiceListener() {
            public void onServiceDisconnected() throws RemoteException {
                Log.d(TAG, "RichScreenApi connected");
            }

            public void onServiceConnected() throws RemoteException {
                Log.d(TAG, "RichScreenApi connected");
            }
        });
        mCapabilityApi.init(context, null);
    }

    public static RcsAccountApi getRcsAccountApi() {
        return mRcsAccountApi;
    }

    public static ConfApi getConfApi() {
        return mConfApi;
    }

    public static boolean isRcsServiceInstalled() {
        return mIsRcsServiceInstalled;
    }

    public static RichScreenApi getRichScreenApi() {
        return mRichScreenApi;
    }

    public static boolean isRcsOnline() {
        try {
            return mRcsAccountApi.isOnline();
        } catch (ServiceDisconnectedException e) {
            return false;
        }
    }

    public static CapabilityApi getCapabilityApi() {
        return mCapabilityApi;
    }
}
