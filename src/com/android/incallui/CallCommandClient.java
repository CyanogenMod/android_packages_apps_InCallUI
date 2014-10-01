/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.incallui;

import android.os.RemoteException;
import android.os.SystemProperties;

import com.android.internal.telephony.MSimConstants;

import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.ICallCommandService;
import com.android.services.telephony.common.Call;

/**
 * Main interface for phone related commands.
 */
public class CallCommandClient {

    private static CallCommandClient sInstance;

    public static synchronized CallCommandClient getInstance() {
        if (sInstance == null) {
            sInstance = new CallCommandClient();
        }
        return sInstance;
    }

    private ICallCommandService mCommandService;

    private CallCommandClient() {
    }

    public void setService(ICallCommandService service) {
        mCommandService = service;
    }

    public void answerCall(int callId) {
        Log.i(this, "answerCall: " + callId);
        if (mCommandService == null) {
            Log.e(this, "Cannot answer call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.answerCall(callId);
        } catch (RemoteException e) {
            Log.e(this, "Error answering call.", e);
        }
    }

    public void rejectCall(Call call, boolean rejectWithMessage, String message) {
        Log.i(this, "rejectCall: " + call.getCallId() +
                ", with rejectMessage? " + rejectWithMessage);
        if (mCommandService == null) {
            Log.e(this, "Cannot reject call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.rejectCall(call, rejectWithMessage, message);
        } catch (RemoteException e) {
            Log.e(this, "Error rejecting call.", e);
        }
    }

    public void disconnectCall(int callId) {
        Log.i(this, "disconnect Call: " + callId);
        if (mCommandService == null) {
            Log.e(this, "Cannot disconnect call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.disconnectCall(callId);
        } catch (RemoteException e) {
            Log.e(this, "Error disconnecting call.", e);
        }
    }

    public void separateCall(int callId) {
        Log.i(this, "separate Call: " + callId);
        if (mCommandService == null) {
            Log.e(this, "Cannot separate call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.separateCall(callId);
        } catch (RemoteException e) {
            Log.e(this, "Error separating call.", e);
        }
    }

    public void mute(boolean onOff) {
        Log.i(this, "mute: " + onOff);
        if (mCommandService == null) {
            Log.e(this, "Cannot mute call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.mute(onOff);
        } catch (RemoteException e) {
            Log.e(this, "Error muting phone.", e);
        }
    }

    public void muteInternal(boolean onOff) {
        Log.i(this, "muteInternal: " + onOff);
        if (mCommandService == null) {
            Log.e(this, "Cannot mute call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.muteInternal(onOff);
        } catch (RemoteException e) {
            Log.e(this, "Error muting phone.", e);
        }
     }

    public void updateMuteState(int sub, boolean muted) {
        if (mCommandService == null) {
            Log.e(this, "Cannot updateMuteState; CallCommandService == null");
            return;
        }
        try {
            mCommandService.updateMuteState(sub, muted);
        } catch (RemoteException e) {
            Log.e(this, "Error updateMuteState.", e);
        }
    }

    public void hold(int callId, boolean onOff) {
        Log.i(this, "hold call(" + onOff + "): " + callId);
        if (mCommandService == null) {
            Log.e(this, "Cannot hold call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.hold(callId, onOff);
        } catch (RemoteException e) {
            Log.e(this, "Error holding call.", e);
        }
    }

    public void merge() {
        Log.i(this, "merge calls");
        if (mCommandService == null) {
            Log.e(this, "Cannot merge call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.merge();
        } catch (RemoteException e) {
            Log.e(this, "Error merging calls.", e);
        }
    }

    public void swap() {
        Log.i(this, "swap active/hold calls");
        if (mCommandService == null) {
            Log.e(this, "Cannot swap call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.swap();
        } catch (RemoteException e) {
            Log.e(this, "Error merging calls.", e);
        }
    }

    public void addCall() {
        Log.i(this, "add a new call");
        if (mCommandService == null) {
            Log.e(this, "Cannot add call; CallCommandService == null");
            return;
        }
        try {
            mCommandService.addCall();
        } catch (RemoteException e) {
            Log.e(this, "Error merging calls.", e);
        }
    }

    public void setAudioMode(int mode) {
        Log.i(this, "Set Audio Mode: " + AudioMode.toString(mode));
        if (mCommandService == null) {
            Log.e(this, "Cannot set audio mode; CallCommandService == null");
            return;
        }
        try {
            mCommandService.setAudioMode(mode);
        } catch (RemoteException e) {
            Log.e(this, "Error setting speaker.", e);
        }
    }

    public void playDtmfTone(char digit, boolean timedShortTone) {
        if (mCommandService == null) {
            Log.e(this, "Cannot start dtmf tone; CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "Sending dtmf tone " + digit);
            mCommandService.playDtmfTone(digit, timedShortTone);
        } catch (RemoteException e) {
            Log.e(this, "Error setting speaker.", e);
        }

    }

    public void stopDtmfTone() {
        if (mCommandService == null) {
            Log.e(this, "Cannot stop dtmf tone; CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "Stop dtmf tone ");
            mCommandService.stopDtmfTone();
        } catch (RemoteException e) {
            Log.e(this, "Error setting speaker.", e);
        }
    }

    public void postDialWaitContinue(int callId) {
        if (mCommandService == null) {
            Log.e(this, "Cannot postDialWaitContinue(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "postDialWaitContinue()");
            mCommandService.postDialWaitContinue(callId);
        } catch (RemoteException e) {
            Log.e(this, "Error on postDialWaitContinue().", e);
        }
    }

    public void postDialCancel(int callId) {
        if (mCommandService == null) {
            Log.e(this, "Cannot postDialCancel(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "postDialCancel()");
            mCommandService.postDialCancel(callId);
        } catch (RemoteException e) {
            Log.e(this, "Error on postDialCancel().", e);
        }
    }

    public void hangupWithReason(int callId, String userUri, boolean mpty,
            int failCause, String errorInfo) {
        if (mCommandService == null) {
            Log.e(this, "Cannot hangupWithReason(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "hangupWithReason() ");
            mCommandService.hangupWithReason(callId, userUri, mpty,
                    failCause, errorInfo);
        } catch (RemoteException e) {
            Log.e(this, "Error on hangupWithReason().", e);
        }
    }

    public void answerCallWithCallType(int callId,int callType){
        if (mCommandService == null) {
            Log.e(this, "Cannot acceptCall(); CallCommandService == null");
            return;
        }
        try {
            /*
             * To test call deflection this property has to be set with the
             * number to which the call should be deflected. If this property is
             * set to a number, on pressing the UI answer button, call deflect
             * request will be sent. This is done to provide hooks to test call
             * deflection through the UI answer button. For commercialization UI
             * should be customized to call this API through the Call deflect UI
             * button By default this property is not set and Answer button will
             * work as expected
             * Example:
             * To deflect call to number 12345
             * adb shell setprop persist.radio.deflect.number 12345
             *
             * Toggle above property and to invoke answerCallWithCallType
             * adb shell setprop persist.radio.deflect.number ""
             */
            String deflectcall = SystemProperties.get("persist.radio.deflect.number");
            if (deflectcall != null && !deflectcall.isEmpty()) {
                mCommandService.deflectCall(callId, deflectcall);
            } else {
                Log.v(this, "acceptCall() ");
                mCommandService.answerCallWithCallType(callId, callType);
            }
        } catch (RemoteException e) {
            Log.e(this, "Error on acceptCall().", e);
        }
    }

    public void deflectCall(int callId, String number) {
        if (mCommandService == null) {
            Log.e(this, "Cannot deflectCall(); CallCommandService == null");
            return;
        }
        try{
            Log.v(this, "deflectCall() ");
            mCommandService.deflectCall(callId, number);
        } catch (RemoteException e) {
            Log.e(this, "Error on deflectCall().", e);
        }
    }

    public void modifyCallInitiate(int callId, int callType) {
        if (mCommandService == null) {
            Log.e(this, "Cannot modifyCall(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "modifyCall(), callId=" + callId + " callType=" + callType);
            mCommandService.modifyCallInitiate(callId, callType);
        } catch (RemoteException e) {
            Log.e(this, "Error on modifyCall().");
        }
    }

    public void modifyCallConfirm(boolean responseType, int callId) {
        if (mCommandService == null) {
            Log.e(this, "Cannot modifyCallConfirm(); CallCommandService == null" + responseType);
            return;
        }
        try {
            Log.v(this, "modifyCallConfirm() ");
            mCommandService.modifyCallConfirm(responseType, callId);
        } catch (RemoteException e) {
            Log.e(this, "Error on modifyCallConfirm().");
        }
    }

    public void setSystemBarNavigationEnabled(boolean enable) {
        if (mCommandService == null) {
            Log.e(this, "Cannot setSystemBarNavigationEnabled(); CallCommandService == null");
            return;
        }
        try {
            Log.v(this, "setSystemBarNavigationEnabled() enabled = " + enable);
            mCommandService.setSystemBarNavigationEnabled(enable);
        } catch (RemoteException e) {
            Log.d(this, "Error on setSystemBarNavigationEnabled().");
        }
    }

    public void blacklistAndHangup(int callId) {
        if (mCommandService == null) {
            Log.e(this, "Cannot blacklistAndHangup(); CallCommandService == null");
            return;
        }
        try {
            mCommandService.blacklistAndHangup(callId);
        } catch (RemoteException e) {
            Log.e(this, "Error on blacklistAndHangup().", e);
        }
    }

    public void setActiveSubscription(int subscriptionId) {
        Log.i(this, "set active sub = " + subscriptionId);
        if (mCommandService == null) {
            Log.e(this, "Cannot set active Sub; CallCommandService == null");
            return;
        }
        try {
            mCommandService.setActiveSubscription(subscriptionId);
        } catch (RemoteException e) {
            Log.e(this, "Error setActiveSub.", e);
        }
    }

    public void setSubInConversation(int subscriptionId) {
        Log.i(this, "set conversation sub = " + subscriptionId);
        if (mCommandService == null) {
            Log.e(this, "Cannot set conversation Sub; CallCommandService == null");
            return;
        }
        try {
            mCommandService.setSubInConversation(subscriptionId);
        } catch (RemoteException e) {
            Log.e(this, "Error setSubInConversation.", e);
        }
    }

    public void setActiveAndConversationSub(int subscriptionId) {
        Log.i(this, "setActiveAndConversationSub = " + subscriptionId);
        if (mCommandService == null) {
            Log.e(this, "Cannot set active Sub; CallCommandService == null");
            return;
        }
        try {
            mCommandService.setActiveAndConversationSub(subscriptionId);
        } catch (RemoteException e) {
            Log.e(this, "Error setActiveSub.", e);
        }
    }

    public int getActiveSubscription() {
        int subscriptionId = MSimConstants.INVALID_SUBSCRIPTION;

        if (mCommandService == null) {
            Log.e(this, "Cannot get active sub; CallCommandService == null");
            return subscriptionId;
        }
        try {
            subscriptionId = mCommandService.getActiveSubscription();
        } catch (RemoteException e) {
            Log.e(this, "Error getActiveSub.", e);
        }
        Log.i(this, "get active sub " + subscriptionId);
        return subscriptionId;
    }
}
