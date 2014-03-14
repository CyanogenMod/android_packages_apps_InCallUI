/* Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.incallui;

import android.content.Context;
import android.widget.Toast;

import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.internal.util.Preconditions;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.CallDetails;

/**
 * The class is responsible for generating video pause/resume request.
 */
class VideoPauseController implements InCallStateListener, IncomingCallListener {
    private static final String TAG = "VideoCallPauseController";

    private CallCommandClient mCallCommandClient;
    private Context mContext;

    private Call mCurrCall = null; // call visible to the user, if any.
    private boolean mIsInBackground = false; // True if UI is not visible, false otherwise.

    public VideoPauseController(Context context, CallCommandClient callCommandClient) {
        mCallCommandClient = Preconditions.checkNotNull(callCommandClient);
        mContext = Preconditions.checkNotNull(context);
    }

    /**
     * The function gets called when call state changes.
     * @param state Phone state.
     * @param callList List of current call.
     */
    @Override
    public void onStateChange(InCallState state, CallList callList) {
        log("onStateChange, state=" + state);

        Call call = null;
        if (state == InCallState.INCOMING) {
            call = callList.getIncomingCall();
        } else if (state == InCallState.OUTGOING) {
            call = callList.getOutgoingCall();
        } else {
            call = callList.getActiveCall();
        }

        // Check if we should display a toast message.
        displayToast(call);

        boolean hasPrimaryCallChanged = !CallUtils.areCallsSame(call, mCurrCall);
        boolean canVideoPause = CallUtils.canVideoPause(call);
        log("onStateChange, hasPrimaryCallChanged=" + hasPrimaryCallChanged);
        log("onStateChange, canVideoPause=" + canVideoPause);
        log("onStateChange, IsInBackground=" + mIsInBackground);
        log("onStateChange, New call = " + call);

        // Send pause request if outgoing request becomes active while UI is in
        // background.
        if (!hasPrimaryCallChanged && isOutgoing(mCurrCall) && canVideoPause && mIsInBackground) {
            sendRequest(call, false);
        }

        // Send pause request if VoLTE call becomes active while UI is in
        // background.
        if (!hasPrimaryCallChanged && !CallUtils.isVideoCall(mCurrCall) && canVideoPause
                && mIsInBackground) {
            sendRequest(call, false);
        }

        // Send resume request for the active call, if user rejects incoming
        // call
        // and UI is in foreground.
        if (hasPrimaryCallChanged && isIncomming(mCurrCall) && canVideoPause && !mIsInBackground) {
            sendRequest(call, true);
        }

        // Send resume request for the active call, if user ends outgoing call
        // and UI is in foreground.
        if (hasPrimaryCallChanged && isOutgoing(mCurrCall) && canVideoPause && !mIsInBackground) {
            sendRequest(call, true);
        }

        // Send pause request for the active call, if the holding call ends
        // while UI is in background
        if (hasPrimaryCallChanged && isHolding(mCurrCall) && canVideoPause && mIsInBackground) {
            sendRequest(call, false);
        }

        mCurrCall = call;
    }

    /**
     * The function gets called when InCallUI receives a new incoming call.
     */
    @Override
    public void onIncomingCall(InCallState state, Call call) {
        log("onIncomingCall, call=" + call);

        if (CallUtils.areCallsSame(call, mCurrCall)) {
            return;
        }

        // Pause current video call, if there is an incoming call.
        if (CallUtils.canVideoPause(mCurrCall)) {
            sendRequest(mCurrCall, false);
        }
        mCurrCall = call;
    }

    /**
     * Called when UI goes in/out of the foreground.
     * @param showing true if UI is in the foreground, false otherwise.
     */
    public void onUiShowing(boolean showing) {
        if (showing) {
            onResume();
        } else {
            onPause();
        }
    }

    /**
     * Sends Pause/Resume request.
     * @param call Call to be paused/resumed.
     * @param resume If true resume request will be sent, otherwise pause request.
     */
    private void sendRequest(Call call, boolean resume) {
        if (resume) {
            log("sending resume request, call=" + call);
            mCallCommandClient.modifyCallInitiate(call.getCallId(),
                    CallDetails.CALL_TYPE_VT_RESUME);
        } else {
            log("sending pause request, call=" + call);
            mCallCommandClient.modifyCallInitiate(call.getCallId(),
                    CallDetails.CALL_TYPE_VT_PAUSE);
        }
    }

    /**
     * Returns true if call is in incoming/waiting state, false otherwise.
     */
    private boolean isIncomming(Call call) {
        return call!=null && call.getState() == Call.State.CALL_WAITING;
    }

    /**
     * Returns true if the call is outgoing, false otherwise
     */
    private boolean isOutgoing(Call call) {
        return call!=null && (call.getState() == Call.State.DIALING || call.getState() == Call.State.REDIALING);
    }

    /**
     * Returns true if the call is on hold, false otherwise
     */
    private boolean isHolding(Call call) {
        return call!=null && call.getState() == Call.State.ONHOLD;
    }

    /**
     * Called when UI becomes visible. This will send resume request for current video call, if any.
     */
    private void onResume() {
        log("onResume");

        mIsInBackground = false;
        if (CallUtils.canVideoPause(mCurrCall)) {
            sendRequest(mCurrCall, true);
        } else {
            log("onResume. Ignoring...");
        }
    }

    /**
     * Called when UI becomes invisible. This will send pause request for current video call, if any.
     */
    private void onPause() {
        log("onPause");

        mIsInBackground = true;
        if (CallUtils.canVideoPause(mCurrCall)) {
            sendRequest(mCurrCall, false);
        } else {
            log("onPause, Ignoring...");
        }
    }

    /**
     * Displays toast message if video call has been paused/resumed.
     */
    private void displayToast(Call newCall) {
        if (!CallUtils.isVideoCall(newCall)) {
            log("displayToast Not a video call, ignoring... call " + newCall);
            return;
        }
        boolean primaryChanged = !CallUtils.areCallsSame(mCurrCall, newCall);
        boolean videoPauseStateChanged = CallUtils.isVideoPaused(mCurrCall) != CallUtils
                .isVideoPaused(newCall);
        final String msg = CallUtils.isVideoPaused(newCall) ? "Video Paused" : "Video Resumed";
        if ((primaryChanged && CallUtils.isVideoPaused(newCall))
                || (!primaryChanged && videoPauseStateChanged)) {
            log("Call " + newCall.getCallId() + " has been " + msg);
            Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private static void loge(String msg) {
        Log.e(TAG, msg);
    }

}
