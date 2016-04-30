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

import static com.android.incallui.CallButtonFragment.Buttons.*;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.telecom.CallAudioState;
import android.telecom.InCallService.VideoCall;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.text.TextUtils;

import com.android.dialer.deeplink.DeepLinkIntegrationManager;

import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.ContactInfoCache;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.incallapi.InCallPluginInfo;
import com.android.incallui.InCallCameraManager.Listener;
import com.android.incallui.InCallPresenter.CanAddCallListener;
import com.android.incallui.InCallPresenter.InCallPluginUpdateListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.InCallPresenter.InCallDetailsListener;

import com.android.phone.common.ambient.AmbientConnection;

import com.android.phone.common.incall.StartInCallCallReceiver;

import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.deeplink.applicationtype.DeepLinkApplicationType;
import com.cyanogen.ambient.deeplink.DeepLink;
import com.cyanogen.ambient.deeplink.DeepLink.DeepLinkResultList;
import com.cyanogen.ambient.deeplink.linkcontent.CallDeepLinkContent;
import com.cyanogen.ambient.deeplink.linkcontent.DeepLinkContentType;
import com.cyanogen.ambient.incall.InCallServices;
import com.cyanogen.ambient.incall.extension.OriginCodes;
import com.cyanogen.ambient.incall.extension.StatusCodes;
import com.cyanogen.ambient.incall.extension.StartCallRequest;

import cyanogenmod.providers.CMSettings;

import java.util.List;
import java.util.Objects;

/**
 * Logic for call buttons.
 */
public class CallButtonPresenter extends Presenter<CallButtonPresenter.CallButtonUi>
        implements InCallStateListener, AudioModeListener, IncomingCallListener,
        InCallDetailsListener, CanAddCallListener, CallList.ActiveSubChangeListener, Listener,
        StartInCallCallReceiver.Receiver, ContactInfoCacheCallback, InCallPluginUpdateListener {

    private static final String TAG = CallButtonPresenter.class.getSimpleName();
    private static final String KEY_AUTOMATICALLY_MUTED = "incall_key_automatically_muted";
    private static final String KEY_PREVIOUS_MUTE_STATE = "incall_key_previous_mute_state";
    private static final String RECORDING_WARNING_PRESENTED = "recording_warning_presented";
    private static final boolean DEBUG = false;

    private Call mCall;
    private DeepLink mNoteDeepLink;
    private boolean mAutomaticallyMuted = false;
    private boolean mPreviousMuteState = false;
    private ContactInfoCache.ContactCacheEntry mPrimaryContactInfo;
    private StartInCallCallReceiver mCallback;

    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        if (DEBUG) Log.i(TAG, "Got InCallPlugin result callback code = " + resultCode);

        switch (resultCode) {
            case StatusCodes.StartCall.HANDOVER_CONNECTED:
                if (mCall == null) {
                    return;
                }

                if (DEBUG) Log.i(TAG, "Disconnecting call: " + mCall);
                TelecomAdapter.getInstance().disconnectCall(mCall.getId());
                break;
            default:
                Log.i(TAG, "Nothing to do for this InCallPlugin resultcode = " + resultCode);
        }
    }

    public CallButtonPresenter() {}

    @Override
    public void onUiReady(CallButtonUi ui) {
        super.onUiReady(ui);

        AudioModeProvider.getInstance().addListener(this);

        // register for call state changes last
        final InCallPresenter inCallPresenter = InCallPresenter.getInstance();
        inCallPresenter.addListener(this);
        inCallPresenter.addIncomingCallListener(this);
        inCallPresenter.addDetailsListener(this);
        inCallPresenter.addCanAddCallListener(this);
        inCallPresenter.getInCallCameraManager().addCameraSelectionListener(this);
        inCallPresenter.addInCallPluginUpdateListener(this);
        CallList.getInstance().addActiveSubChangeListener(this);

        // Update the buttons state immediately for the current call
        onStateChange(InCallState.NO_CALLS, inCallPresenter.getInCallState(),
                CallList.getInstance());
    }

    @Override
    public void onUiUnready(CallButtonUi ui) {
        super.onUiUnready(ui);

        InCallPresenter.getInstance().removeListener(this);
        AudioModeProvider.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallPresenter.getInstance().getInCallCameraManager().removeCameraSelectionListener(this);
        InCallPresenter.getInstance().removeCanAddCallListener(this);
        InCallPresenter.getInstance().removeInCallPluginUpdateListener(this);
        CallList.getInstance().removeActiveSubChangeListener(this);
    }

    @Override
    public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
        CallButtonUi ui = getUi();

        if (newState == InCallState.OUTGOING) {
            mCall = callList.getOutgoingCall();
        } else if (newState == InCallState.INCALL) {
            mCall = callList.getActiveOrBackgroundCall();

            // When connected to voice mail, automatically shows the dialpad.
            // (On previous releases we showed it when in-call shows up, before waiting for
            // OUTGOING.  We may want to do that once we start showing "Voice mail" label on
            // the dialpad too.)
            if (ui != null) {
                if (oldState == InCallState.OUTGOING && mCall != null) {
                    if (CallerInfoUtils.isVoiceMailNumber(ui.getContext(), mCall)) {
                        ui.displayDialpad(true /* show */, true /* animate */);
                    }
                }
            }
        } else if (newState == InCallState.INCOMING) {
            if (ui != null) {
                ui.displayDialpad(false /* show */, true /* animate */);
            }
            mCall = callList.getIncomingCall();
        } else {
            mCall = null;
        }
        if (mCall != null && mPrimaryContactInfo == null) {
            startContactInfoSearch(mCall, true, newState == InCallState.INCOMING);
            getPreferredLinks();
        }
        updateUi(newState, mCall);
    }

    /**
     * Starts a query for more contact data for the save primary and secondary calls.
     */
    private void startContactInfoSearch(final Call call, final boolean isPrimary,
            boolean isIncoming) {
        final ContactInfoCache cache = ContactInfoCache.getInstance(getUi().getContext());

        cache.findInfo(call, isIncoming, this);
    }

    /**
     * Updates the user interface in response to a change in the details of a call.
     * Currently handles changes to the call buttons in response to a change in the details for a
     * call.  This is important to ensure changes to the active call are reflected in the available
     * buttons.
     *
     * @param call The active call.
     * @param details The call details.
     */
    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
        // Only update if the changes are for the currently active call
        if (getUi() != null && call != null && call.equals(mCall)) {
            updateButtonsState(call);
        }
    }

    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
        onStateChange(oldState, newState, CallList.getInstance());
    }

    @Override
    public void onCanAddCallChanged(boolean canAddCall) {
        if (getUi() != null && mCall != null) {
            updateButtonsState(mCall);
        }
    }

    @Override
    public void onAudioMode(int mode) {
        if (getUi() != null) {
            getUi().setAudio(mode);
        }
    }

    @Override
    public void onSupportedAudioMode(int mask) {
        if (getUi() != null) {
            getUi().setSupportedAudio(mask);
        }
    }

    @Override
    public void onMute(boolean muted) {
        if (getUi() != null && !mAutomaticallyMuted) {
            getUi().setMute(muted);
        }
    }

    public int getAudioMode() {
        return AudioModeProvider.getInstance().getAudioMode();
    }

    public int getSupportedAudio() {
        return AudioModeProvider.getInstance().getSupportedModes();
    }

    public void setAudioMode(int mode) {

        // TODO: Set a intermediate state in this presenter until we get
        // an update for onAudioMode().  This will make UI response immediate
        // if it turns out to be slow

        Log.d(this, "Sending new Audio Mode: " + CallAudioState.audioRouteToString(mode));
        TelecomAdapter.getInstance().setAudioRoute(mode);
    }

    /**
     * Function assumes that bluetooth is not supported.
     */
    public void toggleSpeakerphone() {
        // this function should not be called if bluetooth is available
        if (0 != (CallAudioState.ROUTE_BLUETOOTH & getSupportedAudio())) {

            // It's clear the UI is wrong, so update the supported mode once again.
            Log.e(this, "toggling speakerphone not allowed when bluetooth supported.");
            getUi().setSupportedAudio(getSupportedAudio());
            return;
        }

        int newMode = CallAudioState.ROUTE_SPEAKER;

        // if speakerphone is already on, change to wired/earpiece
        if (getAudioMode() == CallAudioState.ROUTE_SPEAKER) {
            newMode = CallAudioState.ROUTE_WIRED_OR_EARPIECE;
        }

        setAudioMode(newMode);
    }

    public void muteClicked(boolean checked) {
        Log.d(this, "turning on mute: " + checked);
        TelecomAdapter.getInstance().mute(checked);
    }

    public void holdClicked(boolean checked) {
        if (mCall == null) {
            return;
        }
        if (checked) {
            Log.i(this, "Putting the call on hold: " + mCall);
            TelecomAdapter.getInstance().holdCall(mCall.getId());
        } else {
            Log.i(this, "Removing the call from hold: " + mCall);
            TelecomAdapter.getInstance().unholdCall(mCall.getId());
        }
    }

    public void transferCallClicked() {
        if (mCall == null) {
            return;
        }

        Log.i(this, "transferring call : " + mCall);
        TelecomAdapter.getInstance().transferCall(mCall.getId());
    }

    public void swapClicked() {
        if (mCall == null) {
            return;
        }

        Log.i(this, "Swapping the call: " + mCall);
        TelecomAdapter.getInstance().swap(mCall.getId());
    }

    public void mergeClicked() {
        TelecomAdapter.getInstance().merge(mCall.getId());
        InCallAudioManager.getInstance().onMergeClicked();
    }

    public void addParticipantClicked() {
        InCallPresenter.getInstance().sendAddParticipantIntent();
    }

    public List<InCallPluginInfo> getContactInCallPluginInfoList() {
        List<InCallPluginInfo> inCallPluginInfoList = null;
        if (mCall != null) {
            final ContactInfoCache cache = ContactInfoCache.getInstance(getUi().getContext());
            if (cache != null) {
                ContactCacheEntry contactInfo = cache.getInfo(mCall.getId());
                if (contactInfo != null) {
                    inCallPluginInfoList = contactInfo.inCallPluginInfoList;
                }
                if (inCallPluginInfoList == null) {
                    cache.refreshPluginInfo(mCall, this);
                }
            }
        }
        return inCallPluginInfoList;
    }

    public void handoverCallToVoIPPlugin() {
        handoverCallToVoIPPlugin(0);
    }

    public void handoverCallToVoIPPlugin(int contactPluginIndex) {
        List<InCallPluginInfo> inCallPluginInfoList = getContactInCallPluginInfoList();
        if (inCallPluginInfoList != null && inCallPluginInfoList.size() > contactPluginIndex) {
            InCallPluginInfo info = inCallPluginInfoList.get(contactPluginIndex);
            final ComponentName component = info.getPluginComponent();
            final String userId = info.getUserId();
            final String mimeType = info.getMimeType();
            if (component != null && !TextUtils.isEmpty(component.flattenToString()) &&
                    !TextUtils.isEmpty(mimeType)) {
                // Attempt call handover
                final PendingIntent inviteIntent = info.getPluginInviteIntent();
                if (!TextUtils.isEmpty(userId)) {
                    AmbientApiClient client = AmbientConnection.CLIENT
                            .get(getUi().getContext().getApplicationContext());

                    mCallback = new StartInCallCallReceiver(new Handler(Looper.myLooper()));
                    mCallback.setReceiver(CallButtonPresenter.this);
                    StartCallRequest request = new StartCallRequest(userId,
                            OriginCodes.CALL_HANDOVER,
                            StartCallRequest.FLAG_CALL_TRANSFER,
                            mCallback);

                    if (DEBUG) Log.i(TAG, "Starting InCallPlugin call for = " + userId);
                    InCallServices.getInstance().startVideoCall(client, component, request);
                } else if (inviteIntent != null) {
                    // Attempt contact invite
                    if (DEBUG) {
                        final ContactInfoCache cache =
                                ContactInfoCache.getInstance(getUi().getContext());
                        ContactCacheEntry entry = cache.getInfo(mCall.getId());
                        Uri lookupUri = entry.lookupUri;
                        Log.i(TAG, "Attempting invite for " + lookupUri.toString());
                    }
                    String inviteText = getUi().getContext().getApplicationContext()
                            .getString(R.string.snackbar_incall_plugin_contact_invite,
                                    info.getPluginTitle());
                    getUi().showInviteSnackbar(inviteIntent, inviteText);
                } else {
                    // Inform user to add contact manually, no invite intent found
                    if (DEBUG) {
                        final ContactInfoCache cache =
                                ContactInfoCache.getInstance(getUi().getContext());
                        ContactCacheEntry entry = cache.getInfo(mCall.getId());
                        Uri lookupUri = entry.lookupUri;
                        Log.i(TAG, "No invite intent for " + lookupUri.toString());
                    }
                    String inviteText = getUi().getContext().getApplicationContext()
                            .getString(R.string.snackbar_incall_plugin_no_invite_found,
                                    info.getPluginTitle());
                    getUi().showInviteSnackbar(null, inviteText);
                }
            }
        }
    }

    public void addCallClicked() {
        // Automatically mute the current call
        mAutomaticallyMuted = true;
        mPreviousMuteState = AudioModeProvider.getInstance().getMute();
        // Simulate a click on the mute button
        muteClicked(true);
        TelecomAdapter.getInstance().addCall();
    }

    public void changeToVoiceClicked() {
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        VideoProfile videoProfile = new VideoProfile(
                VideoProfile.STATE_AUDIO_ONLY, VideoProfile.QUALITY_DEFAULT);
        videoCall.sendSessionModifyRequest(videoProfile);
    }

    public void showDialpadClicked(boolean checked) {
        Log.v(this, "Show dialpad " + String.valueOf(checked));
        getUi().displayDialpad(checked /* show */, true /* animate */);
    }

    public void changeToVideoClicked() {
        final Context context = getUi().getContext();
        if (QtiCallUtils.useExt(context)) {
            QtiCallUtils.displayModifyCallOptions(mCall, context);
            return;
        }

        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }
        int currVideoState = mCall.getVideoState();
        int currUnpausedVideoState = CallUtils.getUnPausedVideoState(currVideoState);
        currUnpausedVideoState |= VideoProfile.STATE_BIDIRECTIONAL;

        VideoProfile videoProfile = new VideoProfile(currUnpausedVideoState);
        videoCall.sendSessionModifyRequest(videoProfile);
        mCall.setSessionModificationState(Call.SessionModificationState.WAITING_FOR_RESPONSE);
    }

    public void switchToVideoCall() {
        boolean canVideoCall = canVideoCall();
        List<InCallPluginInfo> contactInCallPlugins = getContactInCallPluginInfoList();
        int listSize = (contactInCallPlugins != null) ? contactInCallPlugins.size() : 0;
        if (canVideoCall && listSize == 0) {
            // If only VT Call available
            changeToVideoClicked();
        } else if (!canVideoCall && listSize == 1) {
            // If only one InCall Plugin available
            handoverCallToVoIPPlugin();
        } else if (canVideoCall || listSize > 0){
            // If multiple sources available
            getUi().displayVideoCallOptions();
        }
    }

    /**
     * Switches the camera between the front-facing and back-facing camera.
     * @param useFrontFacingCamera True if we should switch to using the front-facing camera, or
     *     false if we should switch to using the back-facing camera.
     */
    public void switchCameraClicked(boolean useFrontFacingCamera) {
        InCallCameraManager cameraManager = InCallPresenter.getInstance().getInCallCameraManager();
        cameraManager.setUseFrontFacingCamera(useFrontFacingCamera);

        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        String cameraId = cameraManager.getActiveCameraId();
        if (cameraId != null) {
            final int cameraDir = cameraManager.isUsingFrontFacingCamera()
                    ? Call.VideoSettings.CAMERA_DIRECTION_FRONT_FACING
                    : Call.VideoSettings.CAMERA_DIRECTION_BACK_FACING;
            mCall.getVideoSettings().setCameraDir(cameraDir);
            videoCall.setCamera(cameraId);
            videoCall.requestCameraCapabilities();
        }
    }


    /**
     * Stop or start client's video transmission.
     * @param pause True if pausing the local user's video, or false if starting the local user's
     *    video.
     */
    public void pauseVideoClicked(boolean pause) {
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        if (pause) {
            videoCall.setCamera(null);
            VideoProfile videoProfile = new VideoProfile(
                    mCall.getVideoState() & ~VideoProfile.STATE_TX_ENABLED);
            videoCall.sendSessionModifyRequest(videoProfile);
        } else {
            InCallCameraManager cameraManager = InCallPresenter.getInstance().
                    getInCallCameraManager();
            videoCall.setCamera(cameraManager.getActiveCameraId());
            VideoProfile videoProfile = new VideoProfile(
                    mCall.getVideoState() | VideoProfile.STATE_TX_ENABLED);
            videoCall.sendSessionModifyRequest(videoProfile);
            mCall.setSessionModificationState(Call.SessionModificationState.WAITING_FOR_RESPONSE);
        }
        getUi().setVideoPaused(pause);
    }

    public void callRecordClicked(boolean startRecording) {
        CallRecorder recorder = CallRecorder.getInstance();
        if (startRecording) {
            Context context = getUi().getContext();
            final SharedPreferences prefs = getPrefs(context);
            boolean warningPresented = prefs.getBoolean(RECORDING_WARNING_PRESENTED, false);
            if (!warningPresented) {
                new AlertDialog.Builder(context)
                        .setTitle(R.string.recording_warning_title)
                        .setMessage(R.string.recording_warning_text)
                        .setPositiveButton(R.string.onscreenCallRecordText,
                                new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                prefs.edit()
                                        .putBoolean(RECORDING_WARNING_PRESENTED, true)
                                        .apply();
                                startCallRecordingOrAskForPermission();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } else {
                startCallRecordingOrAskForPermission();
            }
        } else {
            if (recorder.isRecording()) {
                recorder.finishRecording();
            }
            getUi().setCallRecordingState(recorder.isRecording());
        }
    }

    public void startCallRecording() {
        CallRecorder recorder = CallRecorder.getInstance();
        recorder.startRecording(mCall.getNumber(), mCall.getCreateTimeMillis());
        getUi().setCallRecordingState(recorder.isRecording());
    }

    private void startCallRecordingOrAskForPermission() {
        if (hasAllPermissions(CallRecorder.REQUIRED_PERMISSIONS)) {
            startCallRecording();
        } else {
            getUi().requestCallRecordingPermission(CallRecorder.REQUIRED_PERMISSIONS);
        }
    }

    private boolean hasAllPermissions(String[] permissions) {
        Context context = getUi().getContext();
        for (String p : permissions) {
            if (context.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void updateUi(InCallState state, Call call) {
        Log.d(this, "Updating call UI for call: ", call);

        final CallButtonUi ui = getUi();
        if (ui == null) {
            return;
        }

        final boolean isEnabled =
                state.isConnectingOrConnected() &&!state.isIncoming() && call != null;
        ui.setEnabled(isEnabled);

        if (call == null) {
            return;
        }

        updateButtonsState(call);
    }

    public boolean canVideoCall() {
        return (mCall == null) ? false : (QtiCallUtils.hasVideoCapabilities(mCall) ||
                QtiCallUtils.hasVoiceCapabilities(mCall));
    }

    private boolean isDeviceProvisionedInSettingsDb(Context context) {
        return (CMSettings.Secure.getInt(context.getContentResolver(),
                CMSettings.Secure.CM_SETUP_WIZARD_COMPLETED, 0) != 0)
                && (Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) != 0);
    }

    /**
     * Updates the buttons applicable for the UI.
     *
     * @param call The active call.
     */
    private void updateButtonsState(Call call) {
        Log.v(this, "updateButtonsState");
        final CallButtonUi ui = getUi();
        final boolean isVideo = CallUtils.isVideoCall(call);

        // Common functionality (audio, hold, etc).
        // Show either HOLD or SWAP, but not both. If neither HOLD or SWAP is available:
        //     (1) If the device normally can hold, show HOLD in a disabled state.
        //     (2) If the device doesn't have the concept of hold/swap, remove the button.
        final boolean showSwap = call.can(
                android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE);
        final boolean showHold = !showSwap
                && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD)
                && call.can(android.telecom.Call.Details.CAPABILITY_HOLD);
        final boolean isCallOnHold = call.getState() == Call.State.ONHOLD;

        final boolean useExt = QtiCallUtils.useExt(ui.getContext());
        final boolean showAddCall = TelecomAdapter.getInstance().canAddCall();
        final boolean showMerge = call.can(
                android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE);
        final int callState = call.getState();
        List<InCallPluginInfo> contactInCallPlugins = getContactInCallPluginInfoList();
        final boolean showUpgradeToVideo = (!isVideo || useExt) &&
                (QtiCallUtils.hasVideoCapabilities(call) ||
                        QtiCallUtils.hasVoiceCapabilities(call) ||
                        (contactInCallPlugins != null && !contactInCallPlugins.isEmpty())) &&
                (callState == Call.State.ACTIVE || callState == Call.State.ONHOLD)
                && isDeviceProvisionedInSettingsDb(ui.getContext());
        final boolean showNote =
                DeepLinkIntegrationManager.getInstance().ambientIsAvailable(getUi().getContext()) &&
                (callState == Call.State.ACTIVE || callState == Call.State.ONHOLD) &&
                mNoteDeepLink != null && isDeviceProvisionedInSettingsDb(ui.getContext());

        final boolean showMute = call.can(android.telecom.Call.Details.CAPABILITY_MUTE);
        final boolean showAddParticipant = call.can(
                android.telecom.Call.Details.CAPABILITY_ADD_PARTICIPANT);

        final CallRecorder recorder = CallRecorder.getInstance();
        boolean showCallRecordOption = recorder.isEnabled()
                && !isVideo && call.getState() == Call.State.ACTIVE;
        final boolean showTransferCall = call.can(
                android.telecom.Call.Details.CAPABILITY_SUPPORTS_TRANSFER);

        ui.showButton(BUTTON_AUDIO, true);
        ui.showButton(BUTTON_SWAP, showSwap);
        ui.showButton(BUTTON_HOLD, showHold);
        ui.showButton(BUTTON_TAKE_NOTE, showNote);
        ui.setHold(isCallOnHold);
        ui.showButton(BUTTON_MUTE, showMute);
        ui.showButton(BUTTON_ADD_CALL, showAddCall);
        ui.showButton(BUTTON_UPGRADE_TO_VIDEO, showUpgradeToVideo);
        if (showUpgradeToVideo) {
            ui.modifyChangeToVideoButton();
        }
        ui.showButton(BUTTON_SWITCH_CAMERA, isVideo);
        ui.showButton(BUTTON_PAUSE_VIDEO, isVideo && !useExt);
        ui.showButton(BUTTON_DIALPAD, !isVideo || useExt);
        ui.showButton(BUTTON_MERGE, showMerge);
        ui.showButton(BUTTON_RECORD_CALL, showCallRecordOption);
        ui.showButton(BUTTON_TRANSFER_CALL, showTransferCall);
        ui.enableAddParticipant(showAddParticipant);

        ui.updateButtonStates();
    }

    public void refreshMuteState() {
        // Restore the previous mute state
        if (mAutomaticallyMuted &&
                AudioModeProvider.getInstance().getMute() != mPreviousMuteState) {
            if (getUi() == null) {
                return;
            }
            muteClicked(mPreviousMuteState);
        }
        mAutomaticallyMuted = false;
    }

    private void contactUpdated() {
        if (DEBUG) Log.i(this, "contactUpdated");
        if (getUi() != null && mCall != null) {
            updateButtonsState(mCall);
        }
    }

    @Override
    public void onInCallPluginUpdated() {
        if (DEBUG) Log.i(this, "onInCallPluginUpdated");
        contactUpdated();
    }

    @Override
    public void onContactInfoComplete(String callId, ContactInfoCache.ContactCacheEntry entry) {
        if (DEBUG) Log.i(this, "onContactInfoComplete");
        mPrimaryContactInfo = entry;
        contactUpdated();
    }

    @Override
    public void onImageLoadComplete(String callId, ContactInfoCache.ContactCacheEntry entry) {
        // Stub
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_AUTOMATICALLY_MUTED, mAutomaticallyMuted);
        outState.putBoolean(KEY_PREVIOUS_MUTE_STATE, mPreviousMuteState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        mAutomaticallyMuted =
                savedInstanceState.getBoolean(KEY_AUTOMATICALLY_MUTED, mAutomaticallyMuted);
        mPreviousMuteState =
                savedInstanceState.getBoolean(KEY_PREVIOUS_MUTE_STATE, mPreviousMuteState);
        super.onRestoreInstanceState(savedInstanceState);
    }

    public interface CallButtonUi extends Ui {
        void showButton(int buttonId, boolean show);
        void enableButton(int buttonId, boolean enable);
        void setEnabled(boolean on);
        void setMute(boolean on);
        void setHold(boolean on);
        void setCameraSwitched(boolean isBackFacingCamera);
        void setVideoPaused(boolean isPaused);
        void enableAddParticipant(boolean show);
        void setAudio(int mode);
        void setSupportedAudio(int mask);
        void setCallRecordingState(boolean isRecording);
        void requestCallRecordingPermission(String[] permissions);
        void displayDialpad(boolean on, boolean animate);
        boolean isDialpadVisible();
        void modifyChangeToVideoButton();
        void displayVideoCallOptions();
        void showInviteSnackbar(PendingIntent inviteIntent, String inviteText);
        void setDeepLinkNoteIcon(Drawable d);
        /**
         * Once showButton() has been called on each of the individual buttons in the UI, call
         * this to configure the overflow menu appropriately.
         */
        void updateButtonStates();
        Context getContext();
    }

    @Override
    public void onActiveCameraSelectionChanged(boolean isUsingFrontFacingCamera) {
        if (getUi() == null) {
            return;
        }
        getUi().setCameraSwitched(!isUsingFrontFacingCamera);
    }

    public void onActiveSubChanged(int subId) {
        InCallState state = InCallPresenter.getInstance()
                .getPotentialStateFromCallList(CallList.getInstance());

        onStateChange(null, state, CallList.getInstance());
    }

    public void takeNote() {
        if (mCall != null && mNoteDeepLink != null) {
            Context ctx = getUi().getContext();

            android.telecom.Call.Details details = mCall.getTelecommCall().getDetails();
            CallDeepLinkContent content = new CallDeepLinkContent(mNoteDeepLink);
            content.setName(TextUtils.isEmpty(mPrimaryContactInfo.name) ?
                    ctx.getString(R.string.deeplink_unknown_caller) : mPrimaryContactInfo.name);
            content.setNumber(mCall.getNumber());
            content.setUri(DeepLinkIntegrationManager.generateCallUri(mCall.getNumber(),
                    details.getCreateTimeMillis()));
            DeepLinkIntegrationManager.getInstance().sendContentSentEvent(ctx, mNoteDeepLink,
                    new ComponentName(ctx, CallButtonPresenter.class));
            ctx.startActivity(content.build());

        }
    }

    public void getPreferredLinks() {
        if (mCall != null) {
            Uri callUri = DeepLinkIntegrationManager.generateCallUri(mCall.getNumber(),
                    mCall.getCreateTimeMillis());
            DeepLinkIntegrationManager.getInstance().getPreferredLinksFor(mNoteDeepLinkCallback,
                    DeepLinkContentType.CALL, callUri);
        }
    }

    private ResultCallback<DeepLinkResultList> mNoteDeepLinkCallback =
            new ResultCallback<DeepLinkResultList>() {
        @Override
        public void onResult(DeepLinkResultList deepLinkResult) {
            List<DeepLink> links = deepLinkResult.getResults();
            Drawable toDraw = null;
            if (links != null && getUi() != null) {
                for (DeepLink result : links) {
                    if (result.getApplicationType() == DeepLinkApplicationType.NOTE) {
                        mNoteDeepLink = result;
                        toDraw = result.getDrawableIcon(getUi().getContext()).mutate();
                        toDraw.setColorFilter(
                                getUi().getContext().getColor(R.color.button_default_color),
                                PorterDuff.Mode.SRC_IN);
                        break;
                    }
                }
            }
            getUi().setDeepLinkNoteIcon(toDraw);
        }
    };
}
