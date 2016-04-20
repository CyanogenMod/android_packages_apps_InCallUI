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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;

import com.android.contacts.common.util.BlockContactHelper;
import com.android.dialer.util.TelecomUtil;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.internal.telephony.util.BlacklistUtils;

import com.cyanogen.lookup.phonenumber.provider.LookupProviderImpl;
import org.codeaurora.ims.qtiims.IQtiImsInterface;
import org.codeaurora.ims.qtiims.IQtiImsInterfaceListener;
import org.codeaurora.ims.qtiims.QtiImsInterfaceUtils;
import org.codeaurora.ims.qtiims.QtiViceInfo;
import org.codeaurora.QtiVideoCallConstants;

import java.util.List;

/**
 * Presenter for the Incoming call widget. The {@link AnswerPresenter} handles the logic during
 * incoming calls. It is also in charge of responding to incoming calls, so there needs to be
 * an instance alive so that it can receive onIncomingCall callbacks.
 *
 * An instance of {@link AnswerPresenter} is created by InCallPresenter at startup, registers
 * for callbacks via InCallPresenter, and shows/hides the {@link AnswerFragment} via IncallActivity.
 *
 */
public class AnswerPresenter extends Presenter<AnswerPresenter.AnswerUi>
        implements CallList.CallUpdateListener, InCallPresenter.InCallUiListener,
                InCallPresenter.IncomingCallListener,
                CallList.Listener, CallList.ActiveSubChangeListener {

    private static final String TAG = AnswerPresenter.class.getSimpleName();

    private String mCallId[] = new String[InCallServiceImpl.sPhoneCount];
    private Call mCall[] = new Call[InCallServiceImpl.sPhoneCount];
    private final CallList mCalls = CallList.getInstance();
    private boolean mHasTextMessages = false;
    private BlockContactHelper mBlockContactHelper;

    /**
     * Details required to support call deflection feature.
     */
    private static final String IMS_SERVICE_PKG_NAME = "org.codeaurora.ims";
    private IQtiImsInterface mQtiImsInterface = null;
    private boolean mImsServiceBound = false;

    /* Variables to cache the request details during asynchronous bind request */
    private boolean mPendingDeflectRequest = false;
    private String mDeflectToNumber = null;
    private int mDeflectPhoneId = 0;

    /* Service connection bound to IQtiImsInterface */
    private ServiceConnection mConnection = new ServiceConnection() {

        /* Below API gets invoked when connection to ImsService is established */
        public void onServiceConnected(ComponentName className, IBinder service) {
            /* Retrieve the IQtiImsInterface */
            mQtiImsInterface = IQtiImsInterface.Stub.asInterface(service);

            /**
             * If interface is available and deflect request is pending,
             * then, process the deflect request.
             */
            if (mQtiImsInterface != null && mPendingDeflectRequest) {
                sendCallDeflectRequest(mDeflectPhoneId, mDeflectToNumber);
            } else {
                /* Request or interface is unavailable, unbind the service */
                unbindImsService();
            }
        }

        /* Below API gets invoked when connection to ImsService is disconnected */
        public void onServiceDisconnected(ComponentName className) {
        }
    };

    /* IQtiImsInterfaceListener instance to handle call deflection response */
    private IQtiImsInterfaceListener imsInterfaceListener = new IQtiImsInterfaceListener.Stub() {
        public void onSetCallForwardUncondTimer(int status) {
            /* Not implemented, dummy implementation to avoid compilation errors */
        }

        public void onGetCallForwardUncondTimer(int startHour, int endHour,
                int startMinute, int endMinute, int reason, int status,
                String number, int serviceClass) {
            /* Not implemented, dummy implementation to avoid compilation errors */
        }

        public void onUTReqFailed(int errCode, String errString) {
            /* Not implemented, dummy implementation to avoid compilation errors */
        }

        public void onGetPacketCount(int status, long packetCount) {
            /* Not implemented, dummy implementation to avoid compilation errors */
        }

        public void onGetPacketErrorCount(int status, long packetErrorCount) {
            /* Not implemented, dummy implementation to avoid compilation errors */
        }

        /* Handles call deflect response */
        public void receiveCallDeflectResponse(int result) {
            Log.w(this, "receiveCallDeflectResponse: " + result);
        }

        public void notifyRefreshViceInfo(QtiViceInfo qtiViceInfo) {
        }
    };

    /**
     * Informs if call deflection interafce is available or not.
     * Returns true if allowed, false otherwise.
     */
    public boolean isQtiImsInterfaceAvailable() {
        return (mImsServiceBound && (mQtiImsInterface != null));
    }

    /**
     * Checks if ims service is bound or not
     * Returns true when bound, false otherwise.
     */
    public boolean isImsServiceAvailable() {
        return mImsServiceBound;
    }

    /**
     * Bind to the ims service
     * Returns true if bound sucessfully, false otherwise.
     */
    public boolean bindImsService() {
        Intent intent = new Intent(IQtiImsInterface.class.getName());
        intent.setPackage(IMS_SERVICE_PKG_NAME);
        mImsServiceBound = getUi().getContext().bindService(intent,
                                   mConnection,
                                   Context.BIND_AUTO_CREATE);
        Log.d(this, "Getting IQtiImsInterface : " + (mImsServiceBound?"yes":"failed"));
        return mImsServiceBound;
    }

    /* Unbind the ims service if was already bound */
    public void unbindImsService() {
        if (mImsServiceBound) {
            Log.d(this, "UnBinding IQtiImsInterface: callId " + mCallId);

            /* When disconnecting, reset the globals variables */
            mImsServiceBound = false;
            mPendingDeflectRequest = false;
            getUi().getContext().unbindService(mConnection);
        }
    }

    /* Send call deflect request to lower layers */
    public void sendCallDeflectRequest(int phoneId, String deflectNumber) {
        /* Call deflection interface is available, send the request now */
        try {
            Log.d(this, "Sending deflect request with Phone id " + phoneId +
                    " to " + deflectNumber);
            mQtiImsInterface.sendCallDeflectRequest(phoneId, deflectNumber,
                    imsInterfaceListener);
        } catch (RemoteException e) {
            Log.e(this, "sendCallDeflectRequest exception " + e);
            mPendingDeflectRequest = false;
            QtiCallUtils.displayToast(getUi().getContext(),
                    R.string.qti_description_deflect_service_error);
        }
    }

    private boolean checkSubId(int phoneId) {
        int subId[] = mCalls.getSubId(phoneId);
        return (subId != null && subId.length > 0);
    }

    @Override
    public void onUiShowing(boolean showing) {
        if (showing) {
            mCalls.addListener(this);
            mCalls.addActiveSubChangeListener(this);
            Call call;
            // Consider incoming/waiting calls on both subscriptions
            // for DSDA.
            for (int i = 0; i < InCallServiceImpl.sPhoneCount; i++) {
                int[] subId = mCalls.getSubId(i);
                if (checkSubId(i)) {
                    call = mCalls.getCallWithState(Call.State.INCOMING, 0, subId[0]);
                    if (call == null) {
                        call = mCalls.getCallWithState(Call.State.CALL_WAITING, 0, subId[0]);
                    }
                    if (call != null) {
                        processIncomingCall(call);
                    }
                } else {
                    Log.d(TAG, "No valid sub");
                }
            }
            call = mCalls.getVideoUpgradeRequestCall();
            Log.d(this, "getVideoUpgradeRequestCall call =" + call);
            if (call != null) {
                showAnswerUi(true);
                processVideoUpgradeRequestCall(call);
            }
        } else {
            mCalls.removeListener(this);
            // This is necessary because the activity can be destroyed while an incoming call exists.
            // This happens when back button is pressed while incoming call is still being shown.
            for (int i = 0; i < InCallServiceImpl.sPhoneCount; i++) {
                int[] subId = mCalls.getSubId(i);
                if (checkSubId(i)) {
                    Call call = mCalls.getCallWithState(Call.State.INCOMING, 0, subId[0]);
                    if (call == null) {
                        call = mCalls.getCallWithState(Call.State.CALL_WAITING, 0, subId[0]);
                    }
                    if (call == null) {
                        call = mCalls.getCallWithState(Call.State.ACTIVE, 0, subId[0]);
                    }
                    if (mCallId[i] != null && call == null) {
                        mCalls.removeCallUpdateListener(mCallId[i], this);
                        mCalls.removeActiveSubChangeListener(this);
                    }
                } else {
                    Log.d(TAG, "No valid sub");
                }
            }
        }
    }

    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
        int subId = call.getSubId();
        int phoneId = mCalls.getPhoneId(subId);
        Log.d(this, "onIncomingCall: " + this);
        Call modifyCall = mCalls.getVideoUpgradeRequestCall();
        if (modifyCall != null) {
            showAnswerUi(false);
            Log.d(this, "declining upgrade request id: ");
            mCalls.removeCallUpdateListener(mCallId[phoneId], this);
            InCallPresenter.getInstance().declineUpgradeRequest(getUi().getContext());
        }
        if (!call.getId().equals(mCallId[phoneId])) {
            // A new call is coming in.
            processIncomingCall(call);
        }
    }

    @Override
    public void onIncomingCall(Call call) {
    }

    @Override
    public void onCallListChange(CallList list) {
    }

    @Override
    public void onDisconnect(Call call) {
        int subId = call.getSubId();
        int phoneId = mCalls.getPhoneId(subId);
        if (call.equals(mCall[phoneId])) {
            mCall[phoneId] = null;
        }
    }

    public void onSessionModificationStateChange(int sessionModificationState) {
        boolean isUpgradePending = sessionModificationState ==
                Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST;

        if (!isUpgradePending) {
            // Stop listening for updates.
            for (int i = 0; i < InCallServiceImpl.sPhoneCount; i++) {
                if (mCallId[i] != null) {
                    mCalls.removeCallUpdateListener(mCallId[i], this);
                }
            }
            showAnswerUi(false);
        }
    }

    @Override
    public void onLastForwardedNumberChange() {
        // no-op
    }

    @Override
    public void onChildNumberChange() {
        // no-op
    }

    private boolean isVideoUpgradePending(Call call) {
        return call.getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST;
    }

    @Override
    public void onUpgradeToVideo(Call call) {
        Log.d(this, "onUpgradeToVideo: " + this + " call=" + call);
        showAnswerUi(true);
        boolean isUpgradePending = isVideoUpgradePending(call);
        InCallPresenter inCallPresenter = InCallPresenter.getInstance();
        if (isUpgradePending
                && inCallPresenter.getInCallState() == InCallPresenter.InCallState.INCOMING) {
            Log.d(this, "declining upgrade request");
            //If there is incoming call reject upgrade request
            inCallPresenter.declineUpgradeRequest(getUi().getContext());
        } else if (isUpgradePending) {
            Log.d(this, "process upgrade request as no MT call");
            processVideoUpgradeRequestCall(call);
        }
    }

    private void processIncomingCall(Call call) {
        int subId = call.getSubId();
        int phoneId = mCalls.getPhoneId(subId);
        mCallId[phoneId] = call.getId();
        mCall[phoneId] = call;
        mCalls.addListener(this);
        // Listen for call updates for the current call.
        mCalls.addCallUpdateListener(mCallId[phoneId], this);

        Log.d(TAG, "Showing incoming for call id: " + mCallId[phoneId] + " " + this);
        if (showAnswerUi(true)) {
            final List<String> textMsgs = mCalls.getTextResponses(call.getId());
            configureAnswerTargetsForSms(call, textMsgs);
        }
    }

    private boolean showAnswerUi(boolean show) {
        final InCallActivity activity = InCallPresenter.getInstance().getActivity();
        if (activity != null) {
            activity.showAnswerFragment(show);
            if (getUi() != null) {
                getUi().onShowAnswerUi(show);
            }
            return true;
        } else {
            return false;
        }
    }

    private void processVideoUpgradeRequestCall(Call call) {
        Log.d(this, " processVideoUpgradeRequestCall call=" + call);
        int subId = call.getSubId();
        int phoneId = mCalls.getPhoneId(subId);
        mCallId[phoneId] = call.getId();
        mCall[phoneId] = call;

        // Listen for call updates for the current call.
        CallList.getInstance().addCallUpdateListener(mCallId[phoneId], this);

        final int currentVideoState = call.getVideoState();
        final int modifyToVideoState = call.getModifyToVideoState();

        if (currentVideoState == modifyToVideoState) {
            Log.w(this, "processVideoUpgradeRequestCall: Video states are same. Return.");
            return;
        }

        AnswerUi ui = getUi();

        if (ui == null) {
            Log.e(this, "Ui is null. Can't process upgrade request");
            return;
        }
        showAnswerUi(true);
        ui.showTargets(QtiCallUtils.getSessionModificationOptions(getUi().getContext(),
                currentVideoState, modifyToVideoState));

    }

    private boolean isEnabled(int videoState, int mask) {
        return (videoState & mask) == mask;
    }

    @Override
    public void onCallChanged(Call call) {
        Log.d(this, "onCallStateChange() " + call + " " + this);
        if (call.getState() != Call.State.INCOMING) {
            boolean isUpgradePending = isVideoUpgradePending(call);
            int subId = call.getSubId();
            int phoneId = mCalls.getPhoneId(subId);
            if (!isUpgradePending) {
                // Stop listening for updates.
                mCalls.removeCallUpdateListener(mCallId[phoneId], this);
            }

            final Call incall = mCalls.getIncomingCall();
            if (incall != null || isUpgradePending) {
                showAnswerUi(true);
            } else {
                showAnswerUi(false);
            }

            mHasTextMessages = false;

            /* if available, release the call deflect interface */
            unbindImsService();

        } else if (!mHasTextMessages) {
            final List<String> textMsgs = mCalls.getTextResponses(call.getId());
            if (textMsgs != null) {
                configureAnswerTargetsForSms(call, textMsgs);
            }
        }
    }

    // get active phoneId, for which call is visible to user
    private int getActivePhoneId() {
        int phoneId = -1;
        if (InCallServiceImpl.isDsdaEnabled()) {
            int subId = mCalls.getActiveSubId();
            phoneId = mCalls.getPhoneId(subId);
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
        Log.i(this, "onAnswer  mCallId:" + mCallId + "phoneId:" + phoneId);
        if (mCallId == null || phoneId == -1) {
            return;
        }

        if (mCall[phoneId].getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            Log.d(this, "onAnswer (upgradeCall) mCallId=" + mCallId + " videoState=" + videoState);
            InCallPresenter.getInstance().acceptUpgradeRequest(videoState, context);
        } else {
            Log.d(this, "onAnswer (answerCall) mCallId=" + mCallId + " videoState=" + videoState);
            TelecomAdapter.getInstance().answerCall(mCall[phoneId].getId(), videoState);
        }
    }

    /**
     * TODO: We are using reject and decline interchangeably. We should settle on
     * reject since it seems to be more prevalent.
     */
    public void onDecline(Context context) {
        int phoneId = getActivePhoneId();
        Log.d(this, "onDecline mCallId:" + mCallId + "phoneId:" + phoneId);
        if (mCall[phoneId].getSessionModificationState()
                == Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
            InCallPresenter.getInstance().declineUpgradeRequest(context);
        } else {
            TelecomAdapter.getInstance().rejectCall(mCall[phoneId].getId(), false, null);
        }
    }

    public void onText() {
        if (getUi() != null) {
            TelecomUtil.silenceRinger(getUi().getContext());
            getUi().showMessageDialog();
        }
    }

    public void onBlock(boolean notifyLookupProvider) {
        if (mBlockContactHelper != null) {
            mBlockContactHelper.blockContactAsync(notifyLookupProvider);
            // end the call
            onDecline(getUi().getContext());
        }
    }

    public boolean isBlockingEnabled() {
        return BlacklistUtils.isBlacklistEnabled(getUi().getContext());
    }

    public void onBlockDialogInitialize() {
        int phoneId = getActivePhoneId();
        Log.d(this, "onBlock mCallId:" + mCallId + "phoneId:" + phoneId);
        Call call = mCall[phoneId];
        final String number = call.getNumber();
        final Context context = getUi().getContext();
        mBlockContactHelper = new BlockContactHelper(context, new LookupProviderImpl(context));
        mBlockContactHelper.setContactInfo(number);
    }

    public String getLookupProviderName() {
        return mBlockContactHelper.getLookupProviderName();
    }

    /**
     * Deflect the incoming call.
     */
    public void onDeflect(Context context) {
        String deflectCallNumber = QtiImsInterfaceUtils.getCallDeflectNumber(
                                           context.getContentResolver());
        /* If not set properly, inform user via toast */
        if (deflectCallNumber == null) {
            Log.w(this, "getCallDeflectNumber is null or Empty.");
            QtiCallUtils.displayToast(context, R.string.qti_description_deflect_error);
        } else {
            /* Cache the variables and bind to service */
            mPendingDeflectRequest = true;
            mDeflectToNumber = deflectCallNumber;

            /* Try to get required interface */
            if (bindImsService()) {
                /* wait for the service connection callback */
            } else {
                /* Ims service is available, but no QtiImsInterface available */
                Log.d(this, "Ims Service is not available for call deflection interface");
                mPendingDeflectRequest = false;
                QtiCallUtils.displayToast(context,
                        R.string.qti_description_deflect_service_error);
           }
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
        if (getUi() == null) {
            return;
        }
        mHasTextMessages = textMsgs != null;
        boolean withSms =
                call.can(android.telecom.Call.Details.CAPABILITY_RESPOND_VIA_TEXT)
                && mHasTextMessages;
        boolean withBlock = isBlockingEnabled();

        // Only present the user with the option to answer as a video call if the incoming call is
        // a bi-directional video call.
        if (call.isVideoCall(getUi().getContext())) {
            getUi().showTargets(QtiCallUtils.getIncomingCallAnswerOptions(
                    getUi().getContext(), withSms, withBlock));
            if (withSms) {
                getUi().configureMessageDialog(textMsgs);
            }
        } else if (isCallDeflectSupported()) {
            /**
             * Only present the user with the option to deflect call,
             * if the incoming call is only an audio call.
             */
            if (withSms) {
                getUi().showTargets(AnswerFragment.TARGET_SET_FOR_QTI_AUDIO_WITH_SMS);
                getUi().configureMessageDialog(textMsgs);
            } else {
                getUi().showTargets(AnswerFragment.TARGET_SET_FOR_QTI_AUDIO_WITHOUT_SMS);
            }
        } else {
            if (withSms) {
                getUi().showTargets(withBlock
                        ? AnswerFragment.TARGET_SET_FOR_AUDIO_WITH_SMS_AND_BLOCK
                        : AnswerFragment.TARGET_SET_FOR_AUDIO_WITH_SMS_WITHOUT_BLOCK);
                getUi().configureMessageDialog(textMsgs);
            } else {
                getUi().showTargets(withBlock
                        ? AnswerFragment.TARGET_SET_FOR_AUDIO_WITHOUT_SMS_WITH_BLOCK
                        : AnswerFragment.TARGET_SET_FOR_AUDIO_WITHOUT_SMS_AND_BLOCK);
            }
        }
    }

    @Override
    public void onUiUnready(AnswerUi ui) {
        super.onUiUnready(ui);
        if (mBlockContactHelper != null) {
            mBlockContactHelper.destroy();
        }
    }

    /**
     * Checks the Settings to conclude on the call deflect support.
     * Returns true if call deflect is possible, false otherwise.
     */
    public boolean isCallDeflectSupported() {
        int value = 0;
        try{
            value = android.provider.Settings.Global.getInt(
                              getUi().getContext().getContentResolver(),
                              QtiImsInterfaceUtils.QTI_IMS_DEFLECT_ENABLED);
        } catch(Settings.SettingNotFoundException e) {
            //do Nothing
        }
        return (value == 1);
    }

    interface AnswerUi extends Ui {
        public void onShowAnswerUi(boolean shown);
        public void showTargets(int targetSet);
        public void showTargets(int targetSet, int videoState);
        public void showMessageDialog();
        public void configureMessageDialog(List<String> textResponses);
        public Context getContext();
    }

    @Override
    public void onActiveSubChanged(int subId) {
        final Call call = mCalls.getIncomingCall();
        int phoneId = CallList.getInstance().getPhoneId(subId);
        if ((call != null) && (call.getId() == mCallId[phoneId])) {
            Log.d(this, "Show incoming for call id: " + mCallId[phoneId] + " " + this);
            if (showAnswerUi(true)) {
                final List<String> textMsgs = mCalls.getTextResponses(
                        call.getId());
                configureAnswerTargetsForSms(call, textMsgs);
            }
        } else if ((call == null) && (mCalls.hasAnyLiveCall(subId))) {
            Log.d(this, "Hide incoming for call id: " + mCallId[phoneId] + " " + this);
            showAnswerUi(false);
        } else {
            Log.d(this, "No incoming call present for sub = " + subId + " " + this);
        }
    }
}
