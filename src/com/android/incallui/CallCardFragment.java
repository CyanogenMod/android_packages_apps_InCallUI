/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only.
 *
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

import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import static android.telephony.TelephonyManager.SIM_STATE_ABSENT;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.Call;

import java.util.List;

/**
 * Fragment for call card.
 */
public class CallCardFragment extends BaseFragment<CallCardPresenter, CallCardPresenter.CallCardUi>
        implements CallCardPresenter.CallCardUi {

    // Primary caller info
    private TextView mPhoneNumber;
    private TextView mNumberLabel;
    private TextView mPrimaryName;
    private TextView mCallStateLabel;
    private TextView mCallTypeLabel;
    private ImageView mPhoto;
    private TextView mElapsedTime;
    private View mProviderInfo;
    private TextView mProviderLabel;
    private TextView mProviderNumber;
    private TextView mSubscriptionId;
    private ViewGroup mSupplementaryInfoContainer;
    private TextView mCallRecordingTimer;
    private Button mVBButton;
    private AudioManager mAudioManager;
    private Toast mVBNotify;
    private boolean mVBEnabled;

    // Secondary caller info
    private ViewStub mSecondaryCallInfo;
    private TextView mSecondaryCallName;
    private ImageView mSecondaryPhoto;
    private View mSecondaryPhotoOverlay;

    // Cached DisplayMetrics density.
    private float mDensity;

    private VideoCallPanel mVideoCallPanel;
    private boolean mAudioDeviceInitialized = false;

    // Constants for TelephonyProperties.PROPERTY_IMS_AUDIO_OUTPUT property.
    // Currently, the default audio output is headset if connected, bluetooth
    // if connected, speaker/earpiece for video/voice call.
    private static final int IMS_AUDIO_OUTPUT_DEFAULT = 0;
    private static final int IMS_AUDIO_OUTPUT_DISABLE_SPEAKER = 1;

    private static final int TTY_MODE_OFF = 0;
    private static final int TTY_MODE_HCO = 2;

    private static final String VOLUME_BOOST = "volume_boost";

    /**
     * Controls audio route for VT calls.
     * 0 - Use the default audio routing strategy.
     * 1 - Disable the speaker. Route the audio to Headset or Bloutooth
     *     or Earpiece, based on the default audio routing strategy.
     * This property is for testing purpose only.
     */
    static final String PROPERTY_IMS_AUDIO_OUTPUT =
                                "persist.radio.ims.audio.output";

    private CallRecorder.RecordingProgressListener mRecordingProgressListener =
            new CallRecorder.RecordingProgressListener() {
        @Override
        public void onStartRecording() {
            mCallRecordingTimer.setText(DateUtils.formatElapsedTime(0));
            mCallRecordingTimer.setVisibility(View.VISIBLE);
        }

        @Override
        public void onStopRecording() {
            mCallRecordingTimer.setVisibility(View.GONE);
        }

        @Override
        public void onRecordingTimeProgress(final long elapsedTimeMs) {
            long elapsedSeconds = (elapsedTimeMs + 500) / 1000;
            mCallRecordingTimer.setText(DateUtils.formatElapsedTime(elapsedSeconds));

            // make sure this is visible in case we re-loaded the UI for a call in progress
            mCallRecordingTimer.setVisibility(View.VISIBLE);
        }
    };

    /**
     * A subclass of ImageView which allows animation by LayoutTransition
     */
    public static class PhotoImageView extends ImageView {
        private boolean mHasFrame = false;

        public PhotoImageView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        protected boolean setFrame(int l, int t, int r, int b) {
            boolean changed = super.setFrame(l, t, r, b);
            mHasFrame = true;
            return changed;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            // force recomputation of draw matrix
            if (mHasFrame) {
                setFrame(getLeft(), getTop(), getRight(), getBottom());
            }
        }
    }

    @Override
    CallCardPresenter.CallCardUi getUi() {
        return this;
    }

    @Override
    CallCardPresenter createPresenter() {
        return new CallCardPresenter();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioManager = (AudioManager) getActivity()
                .getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mVBEnabled = activity.getResources().getBoolean(R.bool.volume_boost_enabled);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final CallList calls = CallList.getInstance();
        final Call call = calls.getFirstCall();
        getPresenter().init(getActivity(), call);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        mDensity = getResources().getDisplayMetrics().density;

        return inflater.inflate(R.layout.call_card, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPhoneNumber = (TextView) view.findViewById(R.id.phoneNumber);
        mPrimaryName = (TextView) view.findViewById(R.id.name);
        mNumberLabel = (TextView) view.findViewById(R.id.label);
        mSecondaryCallInfo = (ViewStub) view.findViewById(R.id.secondary_call_info);
        mPhoto = (ImageView) view.findViewById(R.id.photo);
        mCallStateLabel = (TextView) view.findViewById(R.id.callStateLabel);
        mCallTypeLabel = (TextView) view.findViewById(R.id.callTypeLabel);
        mElapsedTime = (TextView) view.findViewById(R.id.elapsedTime);
        mProviderInfo = view.findViewById(R.id.providerInfo);
        mProviderLabel = (TextView) view.findViewById(R.id.providerLabel);
        mProviderNumber = (TextView) view.findViewById(R.id.providerAddress);
        mSubscriptionId = (TextView) view.findViewById(R.id.subId);
        mSupplementaryInfoContainer =
            (ViewGroup) view.findViewById(R.id.supplementary_info_container);
        mVideoCallPanel = (VideoCallPanel) view.findViewById(R.id.videoCallPanel);
        mCallRecordingTimer = (TextView) view.findViewById(R.id.callRecordingTimer);

        CallRecorder recorder = CallRecorder.getInstance();
        recorder.addRecordingProgressListener(mRecordingProgressListener);

        ViewGroup photoContainer = (ViewGroup) view.findViewById(R.id.photo_container);
        LayoutTransition transition = photoContainer.getLayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        transition.setAnimateParentHierarchy(false);
        transition.setDuration(200);

        if (mVBEnabled) {
            mVBButton = (Button) view.findViewById(R.id.volumeBoost);
            if (null != mVBButton) {
                mVBButton.setOnClickListener(mVBListener);
                mVBButton.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mVideoCallPanel!=null) {
            mVideoCallPanel.onDestroy();
            mVideoCallPanel = null;
        }

        CallRecorder recorder = CallRecorder.getInstance();
        recorder.removeRecordingProgressListener(mRecordingProgressListener);
    }

    @Override
    public void setVisible(boolean on) {
        if (on) {
            getView().setVisibility(View.VISIBLE);
        } else {
            getView().setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void setPrimaryName(String name, boolean nameIsNumber) {
        if (TextUtils.isEmpty(name)) {
            mPrimaryName.setText("");
        } else {
            mPrimaryName.setText(name);

            // Set direction of the name field
            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mPrimaryName.setTextDirection(nameDirection);
        }
    }

    @Override
    public void setPrimaryImage(Drawable image) {
        if (image != null) {
            setDrawableToImageView(mPhoto, image);
        }
    }

    @Override
    public void setPrimaryPhoneNumber(String number) {
        // Set the number
        if (TextUtils.isEmpty(number)) {
            mPhoneNumber.setText("");
            mPhoneNumber.setVisibility(View.GONE);
        } else {
            mPhoneNumber.setText(number);
            mPhoneNumber.setVisibility(View.VISIBLE);
            mPhoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
    }

    @Override
    public void setPrimaryLabel(String label) {
        if (!TextUtils.isEmpty(label)) {
            mNumberLabel.setText(label);
            mNumberLabel.setVisibility(View.VISIBLE);
        } else {
            mNumberLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public void setPrimary(String number, String name, boolean nameIsNumber, String label,
            Drawable photo, boolean isConference, boolean isGeneric, boolean isSipCall,
            boolean isForwarded, boolean isVideo) {
        Log.d(this, "Setting primary call");


        if (isConference) {
            name = getConferenceString(isGeneric);
            photo = getConferencePhoto(isGeneric);
            nameIsNumber = false;
        }

        setPrimaryPhoneNumber(number);

        // set the name field.
        setPrimaryName(name, nameIsNumber);

        // Set the label (Mobile, Work, etc)
        setPrimaryLabel(label);

        showCallTypeLabel(isSipCall, isForwarded);
        MSimTelephonyManager tm = MSimTelephonyManager.getDefault();

        if (tm.isMultiSimEnabled() && !(tm.getMultiSimConfiguration()
                == MSimTelephonyManager.MultiSimVariants.DSDA)) {
            final String multiSimName = "perferred_name_sub";
            int subscription = getPresenter().getActiveSubscription();

            if ((subscription != -1) && (!isSipCall)
                    && MSimTelephonyManager.getDefault().getSimState(subscription)
                            != TelephonyManager.SIM_STATE_ABSENT) {
                final String simName = Settings.System.getString(getActivity()
                        .getContentResolver(), multiSimName + (subscription + 1));
                showSubscriptionInfo(simName);
            }
        }  else {
            mSubscriptionId.setVisibility(View.GONE);
        }

        if (! isVideo) {
            setDrawableToImageView(mPhoto, photo);
        }
    }

    @Override
    public void setSecondary(boolean show, String name, boolean nameIsNumber, String label,
            Drawable photo, boolean isConference, boolean isGeneric) {

        if (show) {
            if (isConference) {
                name = getConferenceString(isGeneric);
                photo = getConferencePhoto(isGeneric);
                nameIsNumber = false;
            }

            showAndInitializeSecondaryCallInfo();
            mSecondaryCallName.setText(name);

            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mSecondaryCallName.setTextDirection(nameDirection);

            setDrawableToImageView(mSecondaryPhoto, photo);
        } else {
            mSecondaryCallInfo.setVisibility(View.GONE);
        }
    }

    @Override
    public void setSecondaryImage(Drawable image) {
        if (image != null) {
            setDrawableToImageView(mSecondaryPhoto, image);
        }
    }

    @Override
    public void setCallState(int state, Call.DisconnectCause cause, boolean bluetoothOn,
            String gatewayLabel, String gatewayNumber, boolean isWaitingForRemoteSide,
            int callType) {
        String callStateLabel = null;

        // If this is a video call then update the state of the VideoCallPanel
        if (CallUtils.isVideoCall(callType)) {
            updateVideoCallState(state, callType);
        } else {
            // This will hide the VideoCallPanel for any non VT/ non VS call or
            // downgrade scenarios
            hideVideoCallWidgets();
        }

        // States other than disconnected not yet supported
        callStateLabel = getCallStateLabelFromState(state, cause, isWaitingForRemoteSide);

        if (mVBEnabled) {
            updateVBbyCall(state);
        }

        Log.v(this, "setCallState " + callStateLabel);
        Log.v(this, "DisconnectCause " + cause);
        Log.v(this, "bluetooth on " + bluetoothOn);
        Log.v(this, "gateway " + gatewayLabel + gatewayNumber);

        // There are cases where we totally skip the animation, in which case remove the transition
        // animation here and restore it afterwards.
        final boolean skipAnimation = (Call.State.isDialing(state)
                || state == Call.State.DISCONNECTED || state == Call.State.DISCONNECTING);
        LayoutTransition transition = null;
        if (skipAnimation) {
            transition = mSupplementaryInfoContainer.getLayoutTransition();
            mSupplementaryInfoContainer.setLayoutTransition(null);
        }

        // Update the call state label.
        if (!TextUtils.isEmpty(callStateLabel)) {
            mCallStateLabel.setVisibility(View.VISIBLE);
            mCallStateLabel.setText(callStateLabel);

            if (Call.State.INCOMING == state) {
                setBluetoothOn(bluetoothOn);
            }
        } else {
            mCallStateLabel.setVisibility(View.GONE);
            // Gravity is aligned left when receiving an incoming call in landscape.
            // In that rare case, the gravity needs to be reset to the right.
            // Also, setText("") is used since there is a delay in making the view GONE,
            // so the user will otherwise see the text jump to the right side before disappearing.
            if(mCallStateLabel.getGravity() != Gravity.END) {
                mCallStateLabel.setText("");
                mCallStateLabel.setGravity(Gravity.END);
            }
        }

        // Provider info: (e.g. "Calling via <gatewayLabel>")
        if (!TextUtils.isEmpty(gatewayLabel) && !TextUtils.isEmpty(gatewayNumber)) {
            mProviderLabel.setText(gatewayLabel);
            mProviderNumber.setText(gatewayNumber);
            mProviderInfo.setVisibility(View.VISIBLE);
        } else {
            mProviderInfo.setVisibility(View.GONE);
        }

        // Restore the animation.
        if (skipAnimation) {
            mSupplementaryInfoContainer.setLayoutTransition(transition);
        }
    }

    private void showCallTypeLabel(boolean isSipCall, boolean isForwarded) {
        if (isSipCall) {
            mCallTypeLabel.setVisibility(View.VISIBLE);
            mCallTypeLabel.setText(R.string.incall_call_type_label_sip);
        } else if (isForwarded) {
            mCallTypeLabel.setVisibility(View.VISIBLE);
            mCallTypeLabel.setText(R.string.incall_call_type_label_forwarded);
        } else {
            mCallTypeLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public void setPrimaryCallElapsedTime(boolean show, String callTimeElapsed) {
        if (show) {
            if (mElapsedTime.getVisibility() != View.VISIBLE) {
                AnimationUtils.Fade.show(mElapsedTime);
            }
            mElapsedTime.setText(callTimeElapsed);
        } else {
            // hide() animation has no effect if it is already hidden.
            AnimationUtils.Fade.hide(mElapsedTime, View.INVISIBLE);
        }
    }

    private void showSubscriptionInfo(String subString) {
        if (!TextUtils.isEmpty(subString)) {
            mSubscriptionId.setText(subString);
            mSubscriptionId.setVisibility(View.VISIBLE);
        } else {
            mSubscriptionId.setVisibility(View.GONE);
        }
    }

    private void setDrawableToImageView(ImageView view, Drawable photo) {
        if (photo == null) {
            photo = view.getResources().getDrawable(R.drawable.picture_unknown);
        }

        final Drawable current = view.getDrawable();
        if (current == null) {
            view.setImageDrawable(photo);
            AnimationUtils.Fade.show(view);
        } else {
            AnimationUtils.startCrossFade(view, current, photo);
            view.setVisibility(View.VISIBLE);
        }
    }

    private String getConferenceString(boolean isGeneric) {
        Log.v(this, "isGenericString: " + isGeneric);
        final int resId = isGeneric ? R.string.card_title_in_call : R.string.card_title_conf_call;
        return getView().getResources().getString(resId);
    }

    private Drawable getConferencePhoto(boolean isGeneric) {
        Log.v(this, "isGenericPhoto: " + isGeneric);
        final int resId = isGeneric ? R.drawable.picture_dialing : R.drawable.picture_conference;
        return getView().getResources().getDrawable(resId);
    }

    private void setBluetoothOn(boolean onOff) {
        // Also, display a special icon (alongside the "Incoming call"
        // label) if there's an incoming call and audio will be routed
        // to bluetooth when you answer it.
        final int bluetoothIconId = R.drawable.ic_in_call_bt_dk;

        if (onOff) {
            mCallStateLabel.setCompoundDrawablesWithIntrinsicBounds(bluetoothIconId, 0, 0, 0);
            mCallStateLabel.setCompoundDrawablePadding((int) (mDensity * 5));
        } else {
            // Clear out any icons
            mCallStateLabel.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    /**
     * Gets the call state label based on the state of the call and
     * cause of disconnect
     */
    private String getCallStateLabelFromState(int state, Call.DisconnectCause cause,
            boolean isWaitingForRemoteSide) {
        final Context context = getView().getContext();
        String callStateLabel = null;  // Label to display as part of the call banner

        if (Call.State.IDLE == state) {
            // "Call state" is meaningless in this state.

        } else if (Call.State.ACTIVE == state) {
            // We normally don't show a "call state label" at all in
            // this state (but see below for some special cases).
            if (isWaitingForRemoteSide) {
                callStateLabel = context.getString(R.string.card_title_waiting_call);
            }
        } else if (Call.State.ONHOLD == state) {
            callStateLabel = context.getString(R.string.card_title_on_hold);
        } else if (Call.State.DIALING == state) {
            callStateLabel = context.getString(isWaitingForRemoteSide
                    ? R.string.card_title_dialing_waiting : R.string.card_title_dialing);
        } else if (Call.State.REDIALING == state) {
            callStateLabel = context.getString(R.string.card_title_redialing);
        } else if (Call.State.INCOMING == state || Call.State.CALL_WAITING == state) {
            callStateLabel = context.getString(R.string.card_title_incoming_call);

        } else if (Call.State.DISCONNECTING == state) {
            // While in the DISCONNECTING state we display a "Hanging up"
            // message in order to make the UI feel more responsive.  (In
            // GSM it's normal to see a delay of a couple of seconds while
            // negotiating the disconnect with the network, so the "Hanging
            // up" state at least lets the user know that we're doing
            // something.  This state is currently not used with CDMA.)
            callStateLabel = context.getString(R.string.card_title_hanging_up);

        } else if (Call.State.DISCONNECTED == state) {
            callStateLabel = getCallFailedString(cause);

        } else {
            Log.wtf(this, "updateCallStateWidgets: unexpected call: " + state);
        }

        return callStateLabel;
    }

    /**
     * Maps the disconnect cause to a resource string.
     */
    private String getCallFailedString(Call.DisconnectCause cause) {
        int resID = R.string.card_title_call_ended;

        // TODO: The card *title* should probably be "Call ended" in all
        // cases, but if the DisconnectCause was an error condition we should
        // probably also display the specific failure reason somewhere...

        switch (cause) {
            case BUSY:
                resID = R.string.callFailed_userBusy;
                break;

            case CONGESTION:
                resID = R.string.callFailed_congestion;
                break;

            case TIMED_OUT:
                resID = R.string.callFailed_timedOut;
                break;

            case SERVER_UNREACHABLE:
                resID = R.string.callFailed_server_unreachable;
                break;

            case NUMBER_UNREACHABLE:
                resID = R.string.callFailed_number_unreachable;
                break;

            case INVALID_CREDENTIALS:
                resID = R.string.callFailed_invalid_credentials;
                break;

            case SERVER_ERROR:
                resID = R.string.callFailed_server_error;
                break;

            case OUT_OF_NETWORK:
                resID = R.string.callFailed_out_of_network;
                break;

            case LOST_SIGNAL:
            case CDMA_DROP:
                resID = R.string.callFailed_noSignal;
                break;

            case LIMIT_EXCEEDED:
                resID = R.string.callFailed_limitExceeded;
                break;

            case POWER_OFF:
                resID = R.string.callFailed_powerOff;
                break;

            case ICC_ERROR:
                resID = R.string.callFailed_simError;
                break;

            case OUT_OF_SERVICE:
                resID = R.string.callFailed_outOfService;
                break;

            case INVALID_NUMBER:
            case UNOBTAINABLE_NUMBER:
                resID = R.string.callFailed_unobtainable_number;
                break;

            default:
                resID = R.string.card_title_call_ended;
                break;
        }
        return this.getView().getContext().getString(resID);
    }

    private void showAndInitializeSecondaryCallInfo() {
        mSecondaryCallInfo.setVisibility(View.VISIBLE);

        // mSecondaryCallName is initialized here (vs. onViewCreated) because it is inaccesible
        // until mSecondaryCallInfo is inflated in the call above.
        if (mSecondaryCallName == null) {
            mSecondaryCallName = (TextView) getView().findViewById(R.id.secondaryCallName);
        }
        if (mSecondaryPhoto == null) {
            mSecondaryPhoto = (ImageView) getView().findViewById(R.id.secondaryCallPhoto);
        }

        if (mSecondaryPhotoOverlay == null) {
            mSecondaryPhotoOverlay = getView().findViewById(R.id.dim_effect_for_secondary_photo);
            mSecondaryPhotoOverlay.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    getPresenter().secondaryPhotoClicked();
                }
            });
            mSecondaryPhotoOverlay.setOnTouchListener(new SmallerHitTargetTouchListener());
        }
    }

    public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            dispatchPopulateAccessibilityEvent(event, mPrimaryName);
            dispatchPopulateAccessibilityEvent(event, mPhoneNumber);
            return;
        }
        dispatchPopulateAccessibilityEvent(event, mCallStateLabel);
        dispatchPopulateAccessibilityEvent(event, mPrimaryName);
        dispatchPopulateAccessibilityEvent(event, mPhoneNumber);
        dispatchPopulateAccessibilityEvent(event, mCallTypeLabel);
        dispatchPopulateAccessibilityEvent(event, mSubscriptionId);
        dispatchPopulateAccessibilityEvent(event, mSecondaryCallName);

        return;
    }

    private void dispatchPopulateAccessibilityEvent(AccessibilityEvent event, View view) {
        if (view == null) return;
        final List<CharSequence> eventText = event.getText();
        int size = eventText.size();
        view.dispatchPopulateAccessibilityEvent(event);
        // if no text added write null to keep relative position
        if (size == eventText.size()) {
            eventText.add(null);
        }
    }

    /**
     * Updates the VideoCallPanel based on the current state of the call
     * TODO: Move to a separate file.
     * @param call
     */
    private void updateVideoCallState(int callState, int callType) {
        log("  - Videocall.state: " + callState);

        if (mVideoCallPanel == null) {
            loge("VideocallPanel is null");
            return;
        }
        switch (callState) {
            case Call.State.INCOMING:
                break;

            case Call.State.DIALING:
            case Call.State.REDIALING:
            case Call.State.ACTIVE:
                initVideoCall(callType);
                showVideoCallWidgets(callType);
                break;

            case Call.State.DISCONNECTING:
            case Call.State.DISCONNECTED:
            case Call.State.ONHOLD:
            case Call.State.IDLE:
            case Call.State.CALL_WAITING:
                hideVideoCallWidgets();
                break;

            default:
                Log.e(this, "videocall: updateVideoCallState in bad state:" + callState);
                hideVideoCallWidgets();
                break;
        }
    }

    /**
     * If this is a video call then hide the photo widget and show the video
     * call panel
     */
    private void showVideoCallWidgets(int callType) {

        if (isPhotoVisible()) {
            log("show videocall widget");
            mPhoto.setVisibility(View.GONE);
        }

        mVideoCallPanel.setVisibility(View.VISIBLE);
        mVideoCallPanel.setPanelElementsVisibility(callType);
        mVideoCallPanel.startOrientationListener(true);
    }

    /**
     * Hide the video call widget and restore the photo widget and reset
     * mAudioDeviceInitialized
     */
    private void hideVideoCallWidgets() {
        mAudioDeviceInitialized = false;

        if ((mVideoCallPanel != null) && (mVideoCallPanel.getVisibility() == View.VISIBLE)) {
            log("Hide videocall widget");

            mPhoto.setVisibility(View.VISIBLE);
            mVideoCallPanel.setVisibility(View.GONE);
            mVideoCallPanel.setCameraNeeded(false);
            mVideoCallPanel.startOrientationListener(false);
        }
    }

    /**
     * Initializes the video call widgets if not already initialized
     */
    private void initVideoCall(int callType) {
        /*
         * 1. Speaker state is updated only at the beginning of a video call 2.
         * For MO video call, speaker update happens in dialing state 3. For MT
         * video call, it happens in active state 4. Speaker state not changed
         * during a call when VOLTE<->VT call type change happens.
         */
        log("initVideoCall mAudioDeviceInitialized: " + mAudioDeviceInitialized);
        if (!mAudioDeviceInitialized ) {
            switchInVideoCallAudio(); // Set audio to speaker by default
            mAudioDeviceInitialized = true;
        }
        // Choose camera direction based on call type
        mVideoCallPanel.onCallInitiating(callType);
    }

    /**
     * Switches the current routing of in-call audio for the video call
     */
    private void switchInVideoCallAudio() {
        Log.d(this,"In switchInVideoCallAudio");

        // If the wired headset is connected then the AudioService takes care of
        // routing audio to the headset
        int mode = AudioModeProvider.getInstance().getAudioMode();
        CallCommandClient.getInstance().setAudioMode(mode);
        if (mode == AudioMode.WIRED_HEADSET) {
            Log.d(this,"Wired headset connected, not routing audio to speaker");
            return;
        }

        // If the bluetooth is available then BluetoothHandsfree class takes
        // care of making sure that the audio is routed to Bluetooth by default.
        // However if the audio is not connected to Bluetooth because user wanted
        // audio off then continue to turn on the speaker
        if (mode == AudioMode.BLUETOOTH ) {
            Log.d(this, "Bluetooth connected, not routing audio to speaker");
            return;
        }

        // If the speaker is explicitly disabled then do not enable it.
        if (SystemProperties.getInt(PROPERTY_IMS_AUDIO_OUTPUT,
                IMS_AUDIO_OUTPUT_DEFAULT) == IMS_AUDIO_OUTPUT_DISABLE_SPEAKER) {
            Log.d(this, "Speaker disabled, not routing audio to speaker");
            return;
        }

        // If the bluetooth headset or the wired headset is not connected and
        // the speaker is not disabled then turn on speaker by default
        // for the VT call
        CallCommandClient.getInstance().setAudioMode(AudioMode.SPEAKER);
    }

    /**
     * Return true if mPhoto is available and is visible
     *
     * @return
     */
    private boolean isPhotoVisible() {
        return ((mPhoto != null) && (mPhoto.getVisibility() == View.VISIBLE));
    }

    private void log(String msg) {
        Log.d(this, msg);
    }

    private void loge(String msg) {
        Log.e(this, msg);
    }

    private OnClickListener mVBListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (isVBAvailable()) {
                switchVBStatus();
            }

            updateVBButton();
            showVBNotify();
        }
    };

    private boolean isVBAvailable() {
        int mode = AudioModeProvider.getInstance().getAudioMode();

        int settingsTtyMode = Settings.Secure.getInt(getActivity().getContentResolver(),
                Settings.Secure.PREFERRED_TTY_MODE, TTY_MODE_OFF);

        return (mode == AudioMode.EARPIECE || mode == AudioMode.SPEAKER
                || settingsTtyMode == TTY_MODE_HCO);
    }

    private void switchVBStatus() {
        if (mAudioManager.getParameters(VOLUME_BOOST).contains("=on")) {
            mAudioManager.setParameters(VOLUME_BOOST + "=off");
        } else {
            mAudioManager.setParameters(VOLUME_BOOST + "=on");
        }
    }

    private void updateVBButton() {
        if (isVBAvailable()) {
            if (mAudioManager.getParameters(VOLUME_BOOST).contains("=on")) {
                mVBButton.setBackgroundResource(R.drawable.volume_in_boost_sel);
            } else {
                mVBButton.setBackgroundResource(R.drawable.volume_in_boost_nor);
            }
        } else {
            mVBButton.setBackgroundResource(R.drawable.volume_in_boost_unavailable);
        }
    }

    private void showVBNotify() {
        if (mVBNotify != null) {
            mVBNotify.cancel();
        }

        int textResId;

        if (isVBAvailable()) {
            if (mAudioManager.getParameters(VOLUME_BOOST).contains("=on")) {
                textResId = R.string.volume_boost_notify_enabled;
            } else {
                textResId = R.string.volume_boost_notify_disabled;
            }
        } else {
            textResId = R.string.volume_boost_notify_unavailable;
        }

        mVBNotify = Toast.makeText(getActivity(), textResId, Toast.LENGTH_SHORT);
        mVBNotify.show();
    }

    private void updateVBbyCall(int state) {
        // If there is Ims call, disable volume boost
        boolean hasImsCall = CallUtils.hasImsCall(CallList.getInstance());

        updateVBButton();

        if (Call.State.ACTIVE == state && !hasImsCall) {
            mVBButton.setVisibility(View.VISIBLE);
        } else if (Call.State.DISCONNECTED == state || Call.State.IDLE == state) {
            if (!CallList.getInstance().existsLiveCall()
                    && mAudioManager.getParameters(VOLUME_BOOST).contains("=on")) {
                mVBButton.setVisibility(View.INVISIBLE);

                mAudioManager.setParameters(VOLUME_BOOST + "=off");
            }
        }
    }
}
