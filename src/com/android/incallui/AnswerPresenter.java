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

import android.telecom.PhoneCapabilities;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.SystemProperties;

import java.util.List;

/**
 * Presenter for the Incoming call widget.
 */
public class AnswerPresenter extends Presenter<AnswerPresenter.AnswerUi>
        implements CallList.CallUpdateListener, CallList.Listener,
        CallList.ActiveSubChangeListener {

    private static final String TAG = AnswerPresenter.class.getSimpleName();

    private String mCallId[] = new String[CallList.PHONE_COUNT];
    private Call mCall[] = new Call[CallList.PHONE_COUNT];
    private boolean mHasTextMessages = false;

    @Override
    public void onUiReady(AnswerUi ui) {
        Log.d(this, "onUiReady ui=" + ui);
        super.onUiReady(ui);

        final CallList calls = CallList.getInstance();
        Call call = calls.getVideoUpgradeRequestCall();
        Log.d(this, "getVideoUpgradeRequestCall call =" + call);

        if (call != null && calls.getIncomingCall() == null) {
            processVideoUpgradeRequestCall(call);
        }
        for (int i = 0; i < CallList.PHONE_COUNT; i++) {
            long[] subId = CallList.getInstance().getSubId(i);
            call = calls.getCallWithState(Call.State.INCOMING, 0, subId[0]);
            if (call == null) {
                call = calls.getCallWithState(Call.State.CALL_WAITING, 0, subId[0]);
            }
            if (call != null) {
                processIncomingCall(call);
            }
        }

        // Listen for incoming calls.
        calls.addListener(this);
        CallList.getInstance().addActiveSubChangeListener(this);
    }

    @Override
    public void onUiUnready(AnswerUi ui) {
        super.onUiUnready(ui);

        CallList.getInstance().removeListener(this);

        // This is necessary because the activity can be destroyed while an incoming call exists.
        // This happens when back button is pressed while incoming call is still being shown.
        for (int i = 0; i < CallList.PHONE_COUNT; i++) {
            if (mCallId[i] != null) {
                CallList.getInstance().removeCallUpdateListener(mCallId[i], this);
            }
        }
        CallList.getInstance().removeActiveSubChangeListener(this);
    }

    @Override
    public void onCallListChange(CallList callList) {
        Log.d(this, "onCallListChange callList=" + callList);
        // no-op
    }

    @Override
    public void onDisconnect(Call call) {
        // no-op
    }

    @Override
    public void onIncomingCall(Call call) {
        long subId = call.getSubId();
        int phoneId = CallList.getInstance().getPhoneId(subId);
        // TODO: Ui is being destroyed when the fragment detaches.  Need clean up step to stop
        // getting updates here.
        Log.d(this, "onIncomingCall: " + this);
        if (getUi() != null) {
            Call modifyCall = CallList.getInstance().getVideoUpgradeRequestCall();
            if (modifyCall != null) {
                getUi().showAnswerUi(false);
                int modifyPhoneId = CallList.getInstance().getPhoneId(modifyCall.getSubId());
                Log.d(this, "declining upgrade request id: " + modifyPhoneId);
                CallList.getInstance().removeCallUpdateListener(mCallId[modifyPhoneId], this);
                InCallPresenter.getInstance().declineUpgradeRequest(getUi().getContext());
            }
            if (!call.getId().equals(mCallId[phoneId])) {
                // A new call is coming in.
                processIncomingCall(call);
            }
        }
    }

    private boolean isVideoUpgradePending(Call call) {
        boolean isUpgradePending = false;
        if (call.getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            isUpgradePending = true;
        }
        return isUpgradePending;
    }

    @Override
    public void onUpgradeToVideo(Call call) {
        Log.d(this, "onUpgradeToVideo: " + this + " call=" + call);
        if (getUi() == null) {
            Log.d(this, "onUpgradeToVideo ui is null");
            return;
        }
        boolean isUpgradePending = isVideoUpgradePending(call);
        InCallPresenter inCallPresenter = InCallPresenter.getInstance();
        if (isUpgradePending
                && inCallPresenter.getInCallState() == InCallPresenter.InCallState.INCOMING) {
            Log.d(this, "declining upgrade request");
            inCallPresenter.declineUpgradeRequest(getUi().getContext());
        } else if (isUpgradePending) {
            Log.d(this, "process upgrade request as no MT call");
            processVideoUpgradeRequestCall(call);
        }
    }

    private void processIncomingCall(Call call) {
        long subId = call.getSubId();
        int phoneId = CallList.getInstance().getPhoneId(subId);
        mCallId[phoneId] = call.getId();
        mCall[phoneId] = call;

        // Listen for call updates for the current call.
        CallList.getInstance().addCallUpdateListener(mCallId[phoneId], this);

        Log.d(TAG, "Showing incoming for call id: " + mCallId[phoneId] + " " + this);
        final List<String> textMsgs = CallList.getInstance().getTextResponses(call.getId());
        getUi().showAnswerUi(true);
        configureAnswerTargetsForSms(call, textMsgs);
    }

    private void processVideoUpgradeRequestCall(Call call) {
        Log.d(this, " processVideoUpgradeRequestCall call=" + call);
        long subId = call.getSubId();
        int phoneId = CallList.getInstance().getPhoneId(subId);
        mCallId[phoneId] = call.getId();
        mCall[phoneId] = call;

        // Listen for call updates for the current call.
        CallList.getInstance().addCallUpdateListener(mCallId[phoneId], this);
        getUi().showAnswerUi(true);
        getUi().showTargets(AnswerFragment.TARGET_SET_FOR_VIDEO_UPGRADE_REQUEST);
    }

    @Override
    public void onCallChanged(Call call) {
        Log.d(this, "onCallStateChange() " + call + " " + this);
        if (call.getState() != Call.State.INCOMING) {
            long subId = call.getSubId();
            int phoneId = CallList.getInstance().getPhoneId(subId);

            boolean isUpgradePending = isVideoUpgradePending(call);
            if (!isUpgradePending) {
                // Stop listening for updates.
                CallList.getInstance().removeCallUpdateListener(mCallId[phoneId], this);
            }

            final Call incall = CallList.getInstance().getIncomingCall();
            if (incall != null || isUpgradePending) {
                getUi().showAnswerUi(true);
            } else {
                getUi().showAnswerUi(false);
            }

            // mCallId will hold the state of the call. We don't clear the mCall variable here as
            // it may be useful for sending text messages after phone disconnects.
            mCallId[phoneId] = null;
            mHasTextMessages = false;
        } else if (!mHasTextMessages) {
            final List<String> textMsgs = CallList.getInstance().getTextResponses(call.getId());
            if (textMsgs != null) {
                configureAnswerTargetsForSms(call, textMsgs);
            }
        }
    }

    // get active phoneId, for which call is visible to user
    private int getActivePhoneId() {
        int phoneId = -1;
        if (CallList.getInstance().isDsdaEnabled()) {
            long subId = CallList.getInstance().getActiveSubscription();
            phoneId = CallList.getInstance().getPhoneId(subId);
        } else {
            for (int i = 0; i < mCall.length; i++) {
                if (mCall[i] != null) {
                    phoneId = i;
                }
            }
        }
        return phoneId;
    }

    public void onAnswer(int videoState, Context context) {
        int phoneId = getActivePhoneId();
        Log.i(this, "onAnswer  mCallId:" + mCallId + "phoneId:" + phoneId + " videoState="
                + videoState);
        if (mCallId == null || phoneId == -1) {
            return;
        }

        /**
         * To test call deflection this property has to be set with the
         * number to which the call should be deflected. If this property is
         * set to a number, on pressing the UI answer button, call deflect
         * request will be sent. This is done to provide hooks to test call
         * deflection through the UI answer button. For commercialization UI
         * should be customized to call this API through the Call deflect UI
         * button By default this property is not set and Answer button will
         * work as expected.
         * Example:
         * To deflect call to number 12345
         * adb shell setprop persist.radio.deflect.number 12345
         *
         * Toggle above property and to invoke answerCallWithCallType
         * adb shell setprop persist.radio.deflect.number ""
         */
        String deflectcall = SystemProperties.get("persist.radio.deflect.number");
        if (deflectcall != null && !deflectcall.isEmpty()) {
            Log.i(this, "deflectCall " + mCallId + "to" + deflectcall);
            TelecomAdapter.getInstance().deflectCall(mCall[phoneId].getId(), deflectcall);
            return;
        }

        if (mCall[phoneId].getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            InCallPresenter.getInstance().acceptUpgradeRequest(videoState, context);
        } else {
            TelecomAdapter.getInstance().answerCall(mCall[phoneId].getId(), videoState);
        }
    }

    public void onDeflect(String number) {
        int phoneId = getActivePhoneId();
        Log.i(this, "onDeflect  mCallId:" + mCallId + "phoneId:" + phoneId + "to" + number);
        if (mCallId == null || phoneId == -1 || number == null || number.isEmpty()) {
            return;
        }

        TelecomAdapter.getInstance().deflectCall(mCall[phoneId].getId(), number);

    }

    /**
     * TODO: We are using reject and decline interchangeably. We should settle on
     * reject since it seems to be more prevalent.
     */
    public void onDecline(Context context) {
        int phoneId = getActivePhoneId();
        Log.i(this, "onDecline mCallId:" + mCallId + "phoneId:" + phoneId);
        if (mCall[phoneId].getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            InCallPresenter.getInstance().declineUpgradeRequest(context);
        } else {
            TelecomAdapter.getInstance().rejectCall(mCall[phoneId].getId(), false, null);
        }
    }

    public void onText() {
        if (getUi() != null) {
            getUi().showMessageDialog();
        }
    }

    public void rejectCallWithMessage(String message) {
        int phoneId = getActivePhoneId();
        Log.i(this, "sendTextToDefaultActivity()...phoneId:" + phoneId);
        TelecomAdapter.getInstance().rejectCall(mCall[phoneId].getId(), true, message);

        onDismissDialog();
    }

    public void onDismissDialog() {
        InCallPresenter.getInstance().onDismissDialog();
    }

    private void configureAnswerTargetsForSms(Call call, List<String> textMsgs) {
        final Context context = getUi().getContext();

        mHasTextMessages = textMsgs != null;
        boolean withSms = call.can(PhoneCapabilities.RESPOND_VIA_TEXT) && mHasTextMessages;
        if (call.isVideoCall(context)) {
            if (withSms) {
                getUi().showTargets(AnswerFragment.TARGET_SET_FOR_VIDEO_WITH_SMS);
                getUi().configureMessageDialog(textMsgs);
            } else {
                getUi().showTargets(AnswerFragment.TARGET_SET_FOR_VIDEO_WITHOUT_SMS);
            }
        } else {
            if (withSms) {
                getUi().showTargets(AnswerFragment.TARGET_SET_FOR_AUDIO_WITH_SMS);
                getUi().configureMessageDialog(textMsgs);
            } else {
                getUi().showTargets(AnswerFragment.TARGET_SET_FOR_AUDIO_WITHOUT_SMS);
            }
        }
    }

    interface AnswerUi extends Ui {
        public void showAnswerUi(boolean show);
        public void showTargets(int targetSet);
        public void showMessageDialog();
        public void configureMessageDialog(List<String> textResponses);
        public Context getContext();
    }

    @Override
    public void onActiveSubChanged(long subId) {
        final CallList calls = CallList.getInstance();
        final Call call = calls.getIncomingCall();
        int phoneId = CallList.getInstance().getPhoneId(subId);
        if ((call != null) && (call.getId() == mCallId[phoneId])) {
            Log.i(this, "Show incoming for call id: " + mCallId[phoneId] + " " + this);
            final List<String> textMsgs = CallList.getInstance().getTextResponses(
                    call.getId());
            getUi().showAnswerUi(true);

            boolean withSms = call.can(PhoneCapabilities.RESPOND_VIA_TEXT) && textMsgs != null;
            if (call.isVideoCall(getUi().getContext())) {
                if (withSms) {
                    getUi().showTargets(AnswerFragment.TARGET_SET_FOR_VIDEO_WITH_SMS);
                    getUi().configureMessageDialog(textMsgs);
                } else {
                    getUi().showTargets(AnswerFragment.TARGET_SET_FOR_VIDEO_WITHOUT_SMS);
                }
            } else {
                if (withSms) {
                    getUi().showTargets(AnswerFragment.TARGET_SET_FOR_AUDIO_WITH_SMS);
                    getUi().configureMessageDialog(textMsgs);
                } else {
                    getUi().showTargets(AnswerFragment.TARGET_SET_FOR_AUDIO_WITHOUT_SMS);
                }
            }
        } else if ((call == null) && (calls.hasAnyLiveCall(subId))) {
            Log.i(this, "Hide incoming for call id: " + mCallId[phoneId] + " " + this);
            getUi().showAnswerUi(false);
        } else {
            Log.i(this, "No incoming call present for sub = " + subId + " " + this);
        }
    }
}
