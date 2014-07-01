/* Copyright (c) 2013, The Linux Foundation. All rights reserved.
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

import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.CallDetails;
import com.google.common.base.Preconditions;

public class CallUtils {

    public static boolean isVideoCall(int callType) {
        return callType == CallDetails.CALL_TYPE_VT ||
                callType == CallDetails.CALL_TYPE_VT_TX ||
                callType == CallDetails.CALL_TYPE_VT_RX ||
                callType == CallDetails.CALL_TYPE_VT_NODIR ||
                callType == CallDetails.CALL_TYPE_VT_PAUSE ||
                callType == CallDetails.CALL_TYPE_VT_RESUME;
    }

    public static int getCallType(Call call) {
        final CallDetails cd = getCallDetails(call);
        return cd != null ? cd.getCallType() : CallDetails.CALL_TYPE_UNKNOWN;
    }

    public static int getProposedCallType(Call call) {
        final CallDetails cd = getCallModifyDetails(call);
        return cd != null ? cd.getCallType() : CallDetails.CALL_TYPE_UNKNOWN;
    }

    public static boolean hasCallModifyFailed(Call call) {
        final CallDetails modifyCallDetails = getCallModifyDetails(call);
        boolean hasError = false;
        try {
            if (modifyCallDetails != null && modifyCallDetails.getErrorInfo() != null) {
                hasError = !modifyCallDetails.getErrorInfo().isEmpty()
                        && Integer.parseInt(modifyCallDetails.getErrorInfo()) != 0;
            }
        } catch (Exception e) {
            hasError = true;
        }
        return hasError;
    }

    private static CallDetails getCallDetails(Call call) {
        return call != null ? call.getCallDetails() : null;
    }

    private static CallDetails getCallModifyDetails(Call call) {
        return call != null ? call.getCallModifyDetails() : null;
    }

    public static boolean isVideoCall(Call call) {
        if (call == null || call.getCallDetails() == null) {
            return false;
        }
        return isVideoCall(call.getCallDetails().getCallType());
    }

    public static String fromCallType(int callType) {
        String str = "";
        switch (callType) {
            case CallDetails.CALL_TYPE_VT:
                str = "VT";
                break;
            case CallDetails.CALL_TYPE_VT_TX:
                str = "VT_TX";
                break;
            case CallDetails.CALL_TYPE_VT_RX:
                str = "VT_RX";
                break;
        }
        return str;
    }

    public static boolean isImsCall(Call call) {
        if (call == null) return false;
        Preconditions.checkNotNull(call.getCallDetails());
        final int callType = call.getCallDetails().getCallType();
        final boolean isImsVideoCall = isVideoCall(call);
        final boolean isImsVoiceCall = (callType == CallDetails.CALL_TYPE_VOICE
                && call.getCallDetails().getCallDomain() == CallDetails.CALL_DOMAIN_PS);
        return isImsVideoCall || isImsVoiceCall;
    }

    public static boolean hasImsCall(CallList callList) {
        Preconditions.checkNotNull(callList);
        return isImsCall(callList.getIncomingCall())
                || isImsCall(callList.getOutgoingCall())
                || isImsCall(callList.getActiveCall())
                || isImsCall(callList.getBackgroundCall())
                || isImsCall(callList.getDisconnectingCall())
                || isImsCall(callList.getDisconnectedCall());
    }

    public static boolean isVideoPaused(Call call) {
        return call!=null && call.getCallDetails().getCallType() == CallDetails.CALL_TYPE_VT_PAUSE;
    }

    public static boolean areCallsSame(Call call1, Call call2) {
        if (call1 == null && call2 == null) {
            return true;
        } else if (call1 == null || call2 == null) {
            return false;
        }
        return (call1.getCallId() == call2.getCallId());
    }

    public static boolean canVideoPause(Call call) {
        return isVideoCall(call) &&  call.getState() == Call.State.ACTIVE;
    }
}
