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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.telecom.AudioState;
import android.telecom.InCallService.VideoCall;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.InCallCameraManager.CameraSelectionListener;
import com.android.incallui.InCallPresenter.CanAddCallListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.internal.telephony.util.BlacklistUtils;

import java.util.Objects;

/**
 * Logic for call buttons.
 */
public class CallButtonPresenter extends Presenter<CallButtonPresenter.CallButtonUi>
        implements InCallStateListener, AudioModeListener, IncomingCallListener,
        InCallDetailsListener, CallList.ActiveSubChangeListener, CanAddCallListener,
        CameraSelectionListener {

    private static final String KEY_AUTOMATICALLY_MUTED = "incall_key_automatically_muted";
    private static final String KEY_PREVIOUS_MUTE_STATE = "incall_key_previous_mute_state";

    private Call mCall;
    private boolean mAutomaticallyMuted = false;
    private boolean mPreviousMuteState = false;

    public CallButtonPresenter() {
    }

    @Override
    public void onUiReady(CallButtonUi ui) {
        super.onUiReady(ui);

        AudioModeProvider.getInstance().addListener(this);

        // register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);
        InCallPresenter.getInstance().addDetailsListener(this);
        CallList.getInstance().addActiveSubChangeListener(this);
        InCallPresenter.getInstance().addCanAddCallListener(this);
        InCallPresenter.getInstance().getInCallCameraManager().addCameraSelectionListener(this,
            true);
    }

    @Override
    public void onUiUnready(CallButtonUi ui) {
        super.onUiUnready(ui);

        InCallPresenter.getInstance().removeListener(this);
        AudioModeProvider.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        InCallPresenter.getInstance().removeDetailsListener(this);
        CallList.getInstance().removeActiveSubChangeListener(this);
        InCallPresenter.getInstance().removeCanAddCallListener(this);
        InCallPresenter.getInstance().getInCallCameraManager().removeCameraSelectionListener(this);
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
            mCall = null;
        } else {
            mCall = null;
        }
        updateUi(newState, mCall);
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
        if (getUi() != null && Objects.equals(call, mCall)) {
            updateCallButtons(call, getUi().getContext());
        }
    }

    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
        onStateChange(oldState, newState, CallList.getInstance());
    }

    @Override
    public void onCanAddCallChanged(boolean canAddCall) {
        if (getUi() != null && mCall != null) {
            updateCallButtons(mCall, getUi().getContext());
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

        Log.d(this, "Sending new Audio Mode: " + AudioState.audioRouteToString(mode));
        TelecomAdapter.getInstance().setAudioRoute(mode);
    }

    /**
     * Function assumes that bluetooth is not supported.
     */
    public void toggleSpeakerphone() {
        // this function should not be called if bluetooth is available
        if (0 != (AudioState.ROUTE_BLUETOOTH & getSupportedAudio())) {

            // It's clear the UI is wrong, so update the supported mode once again.
            Log.e(this, "toggling speakerphone not allowed when bluetooth supported.");
            getUi().setSupportedAudio(getSupportedAudio());
            return;
        }

        int newMode = AudioState.ROUTE_SPEAKER;

        // if speakerphone is already on, change to wired/earpiece
        if (getAudioMode() == AudioState.ROUTE_SPEAKER) {
            newMode = AudioState.ROUTE_WIRED_OR_EARPIECE;
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

    public void swapClicked() {
        if (mCall == null) {
            return;
        }

        Log.i(this, "Swapping the call: " + mCall);
        TelecomAdapter.getInstance().swap(mCall.getId());
    }

    public void mergeClicked() {
        TelecomAdapter.getInstance().merge(mCall.getId());
    }

    public void addParticipantClicked() {
        InCallPresenter.getInstance().sendAddParticipantIntent();
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
                VideoProfile.VideoState.AUDIO_ONLY, VideoProfile.QUALITY_DEFAULT);
        videoCall.sendSessionModifyRequest(videoProfile);
    }

    public void showDialpadClicked(boolean checked) {
        Log.v(this, "Show dialpad " + String.valueOf(checked));
        getUi().displayDialpad(checked /* show */, true /* animate */);
    }

    public void displayModifyCallOptions() {
        getUi().displayModifyCallOptions();
    }

    public int getCurrentVideoState() {
        return mCall.getVideoState();
    }

    public void changeToVideoClicked(VideoProfile videoProfile) {
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        videoCall.sendSessionModifyRequest(videoProfile);
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
                    mCall.getVideoState() | VideoProfile.VideoState.PAUSED);
            videoCall.sendSessionModifyRequest(videoProfile);
        } else {
            InCallCameraManager cameraManager = InCallPresenter.getInstance().
                    getInCallCameraManager();
            videoCall.setCamera(cameraManager.getActiveCameraId());
            VideoProfile videoProfile = new VideoProfile(
                    mCall.getVideoState() & ~VideoProfile.VideoState.PAUSED);
            videoCall.sendSessionModifyRequest(videoProfile);
        }
        getUi().setPauseVideoButton(pause);
    }

    public void callRecordClicked(boolean startRecording) {
        CallRecorder recorder = CallRecorder.getInstance();
        if (startRecording) {
            recorder.startRecording(mCall.getNumber(),
                    mCall.getCreateTimeMillis());
        } else if (recorder.isRecording()) {
            recorder.finishRecording();
        }
        getUi().setCallRecordButton(recorder.isRecording());
    }

    public void addToBlacklistClicked() {
        final String number = mCall.getNumber();
        final Context context = getUi().getContext();
        final String message = context.getString(R.string.blacklist_dialog_message, number);

        new AlertDialog.Builder(context)
                .setTitle(R.string.blacklist_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.pause_prompt_yes,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(this, "hanging up due to blacklist: " + mCall.getId());
                        TelecomAdapter.getInstance().disconnectCall(mCall.getId());
                        BlacklistUtils.addOrUpdate(context, number,
                                BlacklistUtils.BLOCK_CALLS, BlacklistUtils.BLOCK_CALLS);
                    }
                })
                .setNegativeButton(R.string.pause_prompt_no, null)
                .show();
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

        if (!isEnabled) {
            return;
        }

        updateCallButtons(call, ui.getContext());

        ui.enableMute(call.can(android.telecom.Call.Details.CAPABILITY_MUTE));
    }

    /**
     * Updates the buttons applicable for the UI.
     *
     * @param call The active call.
     * @param context The context.
     */
    private void updateCallButtons(Call call, Context context) {
        if (CallUtils.isVideoCall(call)) {
            updateVideoCallButtons(call);
        } else {
            updateVoiceCallButtons(call);
        }
    }

    private void updateVideoCallButtons(Call call) {
        Log.v(this, "Showing buttons for video call.");
        final CallButtonUi ui = getUi();
        final Context context = ui.getContext();
        final int maxButtonCount =
                context.getResources().getInteger(R.integer.max_call_button_count);

        // Hide all voice-call-related buttons.
        ui.showAudioButton(false);
        ui.showDialpadButton(false);
        ui.showSwapButton(false);
        ui.showChangeToVideoButton(false);
        ui.showAddCallButton(false);
        ui.showMergeButton(false);
        ui.showCallRecordButton(false);

        // Show all video-call-related buttons.
        ui.showChangeToVoiceButton(true);
        ui.showSwitchCameraButton(true);
        ui.showPauseVideoButton(true);

        boolean showAddToBlacklistOption = !TextUtils.isEmpty(call.getNumber())
                && BlacklistUtils.isBlacklistEnabled(context);
        boolean showAddParticipantOption = call.can(android.telecom.Call.Details.ADD_PARTICIPANT);
        boolean showManageVideoCallConferenceOption =
                call.can(android.telecom.Call.Details.CAPABILITY_MANAGE_CONFERENCE);
        final boolean supportHold = call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD);
        final boolean enableHoldOption = call.can(android.telecom.Call.Details.CAPABILITY_HOLD);

        ui.showHoldButton(supportHold);
        ui.enableHold(enableHoldOption);
        ui.setHold(call.getState() == Call.State.ONHOLD);

        int activeButtonCount = supportHold ? 4 : 3;
        int buttonCount = activeButtonCount;
        if (showAddParticipantOption) buttonCount++;
        if (showAddToBlacklistOption) buttonCount++;
        if (showManageVideoCallConferenceOption) buttonCount++;

        boolean needOverflow = buttonCount > maxButtonCount;
        int populatableButtonCount = maxButtonCount;
        if (needOverflow) {
            populatableButtonCount--;
        }

        if (showAddParticipantOption && activeButtonCount < populatableButtonCount) {
            ui.showAddParticipantButton(true);
            showAddParticipantOption = false;
            activeButtonCount++;
        } else {
            ui.showAddParticipantButton(false);
        }
        if (showManageVideoCallConferenceOption && activeButtonCount < populatableButtonCount) {
            ui.showManageConferenceVideoCallButton(true);
            showManageVideoCallConferenceOption = false;
            activeButtonCount++;
        } else {
            ui.showManageConferenceVideoCallButton(false);
        }
        if (showAddToBlacklistOption && activeButtonCount < populatableButtonCount) {
            ui.showAddToBlacklistButton(true);
            showAddToBlacklistOption = false;
            activeButtonCount++;
        } else {
            ui.showAddToBlacklistButton(false);
        }

        if (needOverflow) {
            ui.configureOverflowMenu(
                    false /* showMergeOption */, false /* showAddMenuOption */,
                    false /* showHoldMenuOption */, false /* showSwapOption */,
                    showAddParticipantOption,
                    showManageVideoCallConferenceOption,
                    false /* showCallRecordOption */, showAddToBlacklistOption);
            ui.showOverflowButton(true);
        } else {
            ui.showOverflowButton(false);
        }
    }

    private boolean canShowMergeOption() {
        CallList callList = CallList.getInstance();
        Call activeCall = callList.getActiveCall(), backgroundCall = callList.getBackgroundCall();
        boolean activeCallCanMerge  =
                (activeCall != null) &&
                activeCall.can(android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE);
        boolean backgroundCallCanMerge =
                (backgroundCall != null) &&
                backgroundCall.can(android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE);
        String acId = (activeCall != null) ? activeCall.getId() : "null";
        String bcId = (backgroundCall != null) ? backgroundCall.getId() : "null";
        Log.v(this, "canShowMergeOption: " + acId + " " + activeCallCanMerge +
                " " + bcId + " " + backgroundCallCanMerge);
        return activeCallCanMerge && backgroundCallCanMerge;
    }

    private void updateVoiceCallButtons(Call call) {
        Log.v(this, "Showing buttons for voice call.");
        final CallButtonUi ui = getUi();
        final Context context = ui.getContext();
        final int maxButtonCount =
                context.getResources().getInteger(R.integer.max_call_button_count);

        // Hide all video-call-related buttons.
        ui.showChangeToVoiceButton(false);
        ui.showSwitchCameraButton(false);
        ui.showPauseVideoButton(false);
        ui.showManageConferenceVideoCallButton(false);

        // Show all voice-call-related buttons.
        ui.showAudioButton(true);
        ui.showDialpadButton(true);

        Log.v(this, "Show hold ", call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD));
        Log.v(this, "Enable hold", call.can(android.telecom.Call.Details.CAPABILITY_HOLD));
        Log.v(this, "Show merge ", call.can(
                android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE));
        Log.v(this, "Show swap ", call.can(
                android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE));
        Log.v(this, "Show add call ", TelecomAdapter.getInstance().canAddCall());
        Log.v(this, "Show mute ", call.can(android.telecom.Call.Details.CAPABILITY_MUTE));
        Log.v(this, "Show video call local:",
                call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL)
                + " remote: " + call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE));

        final boolean canAdd = TelecomAdapter.getInstance().canAddCall();
        final boolean enableHoldOption = call.can(android.telecom.Call.Details.CAPABILITY_HOLD);
        final boolean supportHold = call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD);
        final boolean isCallOnHold = call.getState() == Call.State.ONHOLD;

        boolean canVideoCall = call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL)
                && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE);
        ui.enableChangeToVideoButton(!isCallOnHold);

        boolean showMergeOption = call.can(
                android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE);
        boolean showAddCallOption = canAdd;
        boolean showAddParticipantOption = call.can(android.telecom.Call.Details.ADD_PARTICIPANT);
        boolean showManageVideoCallConferenceOption = call.can(
            android.telecom.Call.Details.CAPABILITY_MANAGE_CONFERENCE) && CallUtils.isVideoCall(call);

        // Show either HOLD or SWAP, but not both. If neither HOLD or SWAP is available:
        //     (1) If the device normally can hold, show HOLD in a disabled state.
        //     (2) If the device doesn't have the concept of hold/swap, remove the button.
        boolean showSwapOption = call.can(android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE);
        boolean showHoldOption = !showSwapOption && (enableHoldOption || supportHold);
        ui.setHold(isCallOnHold);

        boolean showAddToBlacklistOption = !TextUtils.isEmpty(call.getNumber())
                && BlacklistUtils.isBlacklistEnabled(context);

        final CallRecorder recorder = CallRecorder.getInstance();
        boolean showCallRecordOption =
                recorder.isEnabled() && call.getState() == Call.State.ACTIVE;
        ui.setCallRecordButton(showCallRecordOption && recorder.isRecording());

        int activeButtonCount = 3; // audio, mute, dialpad
        int buttonCount = activeButtonCount;
        if (showHoldOption) buttonCount++;
        if (showSwapOption) buttonCount++;
        if (canVideoCall) buttonCount++;
        if (showAddCallOption) buttonCount++;
        if (showMergeOption) buttonCount++;
        if (showAddParticipantOption) buttonCount++;
        if (showAddToBlacklistOption) buttonCount++;
        if (showCallRecordOption) buttonCount++;

        if (buttonCount == (maxButtonCount + 1) && showHoldOption && !enableHoldOption) {
            // don't populate a hold button that is disabled anyway
            buttonCount--;
        }

        boolean needOverflow = buttonCount > maxButtonCount;
        int populatableButtonCount = maxButtonCount;
        if (needOverflow) {
            populatableButtonCount--;
        }

        if (showHoldOption && (enableHoldOption || !needOverflow)
                && activeButtonCount < populatableButtonCount) {
            ui.showHoldButton(true);
            showHoldOption = false;
            activeButtonCount++;
        } else {
            ui.showHoldButton(false);
        }
        if (canVideoCall && activeButtonCount < populatableButtonCount) {
            ui.showChangeToVideoButton(true);
            ui.showHoldButton(true);
            canVideoCall = false;
            activeButtonCount++;
        } else {
            ui.showChangeToVideoButton(false);
        }
        if (showAddCallOption && activeButtonCount < populatableButtonCount) {
            ui.showAddCallButton(true);
            showAddCallOption = false;
            activeButtonCount++;
        } else {
            ui.showAddCallButton(false);
        }
        if (showSwapOption && activeButtonCount < populatableButtonCount) {
            ui.showSwapButton(true);
            showSwapOption = false;
            activeButtonCount++;
        } else {
            ui.showSwapButton(false);
        }
        if (showMergeOption && activeButtonCount < populatableButtonCount) {
            ui.showMergeButton(true);
            showMergeOption = false;
            activeButtonCount++;
        } else {
            ui.showMergeButton(false);
        }
        if (showAddParticipantOption && activeButtonCount < populatableButtonCount) {
            ui.showAddParticipantButton(true);
            showAddParticipantOption = false;
            activeButtonCount++;
        } else {
            ui.showAddParticipantButton(false);
        }
        if (showCallRecordOption && activeButtonCount < populatableButtonCount) {
            ui.showCallRecordButton(true);
            showCallRecordOption = false;
            activeButtonCount++;
        } else {
            ui.showCallRecordButton(false);
        }
        if (showAddToBlacklistOption && activeButtonCount < populatableButtonCount) {
            ui.showAddToBlacklistButton(true);
            showAddToBlacklistOption = false;
            activeButtonCount++;
        } else {
            ui.showAddToBlacklistButton(false);
        }

        if (needOverflow) {
            ui.configureOverflowMenu(
                    showMergeOption,
                    showAddCallOption /* showAddMenuOption */,
                    showHoldOption && enableHoldOption /* showHoldMenuOption */,
                    showSwapOption,
                    showAddParticipantOption,
                    false /* showManageVideoCallConferenceOption */,
                    showCallRecordOption,
                    showAddToBlacklistOption);
            ui.showOverflowButton(true);
        } else {
            ui.showOverflowButton(false);
        }
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
        void setEnabled(boolean on);
        void setMute(boolean on);
        void enableMute(boolean enabled);
        void showAudioButton(boolean show);
        void showChangeToVoiceButton(boolean show);
        void showDialpadButton(boolean show);
        void setHold(boolean on);
        void showHoldButton(boolean show);
        void enableHold(boolean enabled);
        void showSwapButton(boolean show);
        void showChangeToVideoButton(boolean show);
        void enableChangeToVideoButton(boolean enable);
        void showSwitchCameraButton(boolean show);
        void setSwitchCameraButton(boolean isBackFacingCamera);
        void showAddCallButton(boolean show);
        void showAddParticipantButton(boolean show);
        void showManageConferenceVideoCallButton(boolean show);
        void showMergeButton(boolean show);
        void showPauseVideoButton(boolean show);
        void setPauseVideoButton(boolean isPaused);
        void showCallRecordButton(boolean show);
        void setCallRecordButton(boolean isRecording);
        void showAddToBlacklistButton(boolean show);
        void showOverflowButton(boolean show);
        void displayDialpad(boolean on, boolean animate);
        void displayModifyCallOptions();
        boolean isDialpadVisible();
        void setAudio(int mode);
        void setSupportedAudio(int mask);
        void configureOverflowMenu(boolean showMergeMenuOption, boolean showAddMenuOption,
                boolean showHoldMenuOption, boolean showSwapMenuOption,
                boolean showAddParticipantOption, boolean showManageConferenceVideoCallOption,
                boolean showCallRecordOption, boolean showAddToBlacklistOption);
        Context getContext();
    }

    @Override
    public void onActiveSubChanged(int subId) {
        InCallState state = InCallPresenter.getInstance()
                .getPotentialStateFromCallList(CallList.getInstance());

        onStateChange(null, state, CallList.getInstance());
    }

    @Override
    public void onActiveCameraSelectionChanged(boolean isUsingFrontFacingCamera) {
        if (getUi() == null) {
            return;
        }
        getUi().setSwitchCameraButton(!isUsingFrontFacingCamera);
    }
}
