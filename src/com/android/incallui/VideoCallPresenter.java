/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Handler;
import android.telecom.AudioState;
import android.telecom.CameraCapabilities;
import android.telecom.Connection.VideoProvider;
import android.telecom.InCallService.VideoCall;
import android.telecom.VideoProfile;
import android.view.Surface;

import com.android.contacts.common.CallUtil;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallOrientationListener;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.InCallVideoCallListenerNotifier.SurfaceChangeListener;
import com.android.incallui.InCallVideoCallListenerNotifier.VideoEventListener;
import com.google.common.base.Preconditions;

import java.util.Objects;
import android.os.SystemProperties;

/**
 * Logic related to the {@link VideoCallFragment} and for managing changes to the video calling
 * surfaces based on other user interface events and incoming events from the
 * {@class VideoCallListener}.
 * <p>
 * When a call's video state changes to bi-directional video, the
 * {@link com.android.incallui.VideoCallPresenter} performs the following negotiation with the
 * telephony layer:
 * <ul>
 *     <li>{@code VideoCallPresenter} creates and informs telephony of the display surface.</li>
 *     <li>{@code VideoCallPresenter} creates the preview surface.</li>
 *     <li>{@code VideoCallPresenter} informs telephony of the currently selected camera.</li>
 *     <li>Telephony layer sends {@link CameraCapabilities}, including the
 *     dimensions of the video for the current camera.</li>
 *     <li>{@code VideoCallPresenter} adjusts size of the preview surface to match the aspect
 *     ratio of the camera.</li>
 *     <li>{@code VideoCallPresenter} informs telephony of the new preview surface.</li>
 * </ul>
 * <p>
 * When downgrading to an audio-only video state, the {@code VideoCallPresenter} nulls both
 * surfaces.
 */
public class VideoCallPresenter extends Presenter<VideoCallPresenter.VideoCallUi>  implements
        IncomingCallListener, InCallOrientationListener, InCallStateListener,
        InCallDetailsListener, SurfaceChangeListener, VideoEventListener,
        InCallVideoCallListenerNotifier.SessionModificationListener {

    /**
     * Determines the device orientation (portrait/lanscape).
     */
    public int getDeviceOrientation() {
        return mDeviceOrientation;
    }

    /**
     * Defines the state of the preview surface negotiation with the telephony layer.
     */
    private class PreviewSurfaceState {
        /**
         * The camera has not yet been set on the {@link VideoCall}; negotiation has not yet
         * started.
         */
        private static final int NONE = 0;

        /**
         * The camera has been set on the {@link VideoCall}, but camera capabilities have not yet
         * been received.
         */
        private static final int CAMERA_SET = 1;

        /**
         * The camera capabilties have been received from telephony, but the surface has not yet
         * been set on the {@link VideoCall}.
         */
        private static final int CAPABILITIES_RECEIVED = 2;

        /**
         * The surface has been set on the {@link VideoCall}.
         */
        private static final int SURFACE_SET = 3;
    }

    /**
     * The minimum width or height of the preview surface.  Used when re-sizing the preview surface
     * to match the aspect ratio of the currently selected camera.
     */
    private float mMinimumVideoDimension;

    /**
     * The current context.
     */
    private Context mContext;

    /**
     * The call the video surfaces are currently related to
     */
    private Call mPrimaryCall;

    /**
     * The {@link VideoCall} used to inform the video telephony layer of changes to the video
     * surfaces.
     */
    private VideoCall mVideoCall;

    /**
     * Determines if the current UI state represents a video call.
     */
    private int mCurrentVideoState;

    /**
     * Determines the device orientation (portrait/lanscape).
     */
    private int mDeviceOrientation;

    /**
     * Tracks the state of the preview surface negotiation with the telephony layer.
     */
    private int mPreviewSurfaceState = PreviewSurfaceState.NONE;

    /**
     * Determines whether the video surface is in full-screen mode.
     */
    private boolean mIsFullScreen = false;

    /**
     * Saves the audio mode which was selected prior to going into a video call.
     */
    private int mPreVideoAudioMode = AudioModeProvider.AUDIO_MODE_INVALID;

    /**
     * Stores the current call substate.
     */
    private int mCurrentCallSubstate;

    /** Handler which resets request state to NO_REQUEST after an interval. */
    private Handler mSessionModificationResetHandler;
    private static final long SESSION_MODIFICATION_RESET_DELAY_MS = 3000;

    /**
     * Controls audio route for VT calls.
     * 0 - Use the default audio routing strategy.
     * 1 - Disable the speaker. Route the audio to Headset or Bloutooth
     *     or Earpiece, based on the default audio routing strategy.
     */
    private static final String PROPERTY_IMS_AUDIO_OUTPUT = "persist.radio.ims.audio.output";

    /**
     * Values for the above adb property "persist.radio.ims.audio.output"
     */
    private static final int IMS_AUDIO_OUTPUT_DEFAULT = 0;
    private static final int IMS_AUDIO_OUTPUT_DISABLE_SPEAKER = 1;

    /**
     * Initializes the presenter.
     *
     * @param context The current context.
     */
    public void init(Context context) {
        mContext = Preconditions.checkNotNull(context);
        mMinimumVideoDimension = mContext.getResources().getDimension(
                R.dimen.video_preview_small_dimension);
        mSessionModificationResetHandler = new Handler();
    }

    /**
     * Called when the user interface is ready to be used.
     *
     * @param ui The Ui implementation that is now ready to be used.
     */
    @Override
    public void onUiReady(VideoCallUi ui) {
        super.onUiReady(ui);
        Log.d(this, "onUiReady:");

        // Register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addDetailsListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);
        InCallPresenter.getInstance().addOrientationListener(this);

        // Register for surface and video events from {@link InCallVideoCallListener}s.
        InCallVideoCallListenerNotifier.getInstance().addSurfaceChangeListener(this);
        InCallVideoCallListenerNotifier.getInstance().addVideoEventListener(this);
        InCallVideoCallListenerNotifier.getInstance().addSessionModificationListener(this);
        mCurrentVideoState = VideoProfile.VideoState.AUDIO_ONLY;
    }

    /**
     * Called when the user interface is no longer ready to be used.
     *
     * @param ui The Ui implementation that is no longer ready to be used.
     */
    @Override
    public void onUiUnready(VideoCallUi ui) {
        super.onUiUnready(ui);
        Log.d(this, "onUiUnready:");

        InCallPresenter.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        InCallPresenter.getInstance().removeOrientationListener(this);
        InCallVideoCallListenerNotifier.getInstance().removeSurfaceChangeListener(this);
        InCallVideoCallListenerNotifier.getInstance().removeVideoEventListener(this);
        InCallVideoCallListenerNotifier.getInstance().removeSessionModificationListener(this);
    }

    /**
     * Handles the creation of a surface in the {@link VideoCallFragment}.
     *
     * @param surface The surface which was created.
     */
    public void onSurfaceCreated(int surface) {
        Log.d(this, "onSurfaceCreated surface=" + surface + " mVideoCall=" + mVideoCall);
        Log.d(this, "onSurfaceCreated PreviewSurfaceState=" + mPreviewSurfaceState);
        Log.d(this, "onSurfaceCreated presenter=" + this);

        final VideoCallUi ui = getUi();
        if (ui == null || mVideoCall == null) {
            Log.w(this, "onSurfaceCreated: Error bad state VideoCallUi=" + ui + " mVideoCall="
                    + mVideoCall);
            return;
        }

        // If the preview surface has just been created and we have already received camera
        // capabilities, but not yet set the surface, we will set the surface now.
        if (surface == VideoCallFragment.SURFACE_PREVIEW ) {
            if (mPreviewSurfaceState == PreviewSurfaceState.CAPABILITIES_RECEIVED) {
                mPreviewSurfaceState = PreviewSurfaceState.SURFACE_SET;
                mVideoCall.setPreviewSurface(ui.getPreviewVideoSurface());
            } else if (mPreviewSurfaceState == PreviewSurfaceState.NONE && isCameraRequired()){
                enableCamera(true);
            }
        } else if (surface == VideoCallFragment.SURFACE_DISPLAY) {
            mVideoCall.setDisplaySurface(ui.getDisplayVideoSurface());
        }
    }

    /**
     * Handles structural changes (format or size) to a surface.
     *
     * @param surface The surface which changed.
     * @param format The new PixelFormat of the surface.
     * @param width The new width of the surface.
     * @param height The new height of the surface.
     */
    public void onSurfaceChanged(int surface, int format, int width, int height) {
        //Do stuff
    }

    /**
     * Handles the destruction of a surface in the {@link VideoCallFragment}.
     * Note: The surface is being released, that is, it is no longer valid.
     *
     * @param surface The surface which was destroyed.
     */
    public void onSurfaceReleased(int surface) {
        Log.d(this, "onSurfaceDestroyed: mSurfaceId=" + surface);
        if ( mVideoCall == null) {
            Log.w(this, "onSurfaceDestroyed: VideoCall is null. mSurfaceId=" +
                    surface);
            return;
        }

        if (surface == VideoCallFragment.SURFACE_DISPLAY) {
            mVideoCall.setDisplaySurface(null);
        } else if (surface == VideoCallFragment.SURFACE_PREVIEW) {
            mVideoCall.setPreviewSurface(null);
            enableCamera(false);
        }
    }

    /**
     * Called by {@link VideoCallFragment} when the surface is detached from UI (TextureView).
     * Note: The surface will be cached by {@link VideoCallFragment}, so we don't immediately
     * null out incoming video surface.
     * @see VideoCallPresenter#onSurfaceReleased(int)
     *
     * @param surface The surface which was detached.
     */
    public void onSurfaceDestroyed(int surface) {
        Log.d(this, "onSurfaceDestroyed: mSurfaceId=" + surface);
        if (mVideoCall == null) {
            return;
        }

        final boolean isChangingConfigurations =
                InCallPresenter.getInstance().isChangingConfigurations();
        Log.d(this, "onSurfaceDestroyed: isChangingConfigurations=" + isChangingConfigurations);

        if (surface == VideoCallFragment.SURFACE_PREVIEW) {
            if (!isChangingConfigurations) {
                enableCamera(false);
            } else {
                Log.w(this, "onSurfaceDestroyed: Activity is being destroyed due "
                        + "to configuration changes. Not closing the camera.");
            }
        }
    }

    private void toggleFullScreen() {
        mIsFullScreen = !mIsFullScreen;
        InCallPresenter.getInstance().setFullScreenVideoState(mIsFullScreen);
    }

    /**
     * Handles clicks on the video surfaces by toggling full screen state.
     * Informs the {@link InCallPresenter} of the change so that it can inform the
     * {@link CallCardPresenter} of the change.
     *
     * @param surfaceId The video surface receiving the click.
     */
    public void onSurfaceClick(int surfaceId) {
        toggleFullScreen();
    }


    /**
     * Handles incoming calls.
     *
     * @param state The in call state.
     * @param call The call.
     */
    @Override
    public void onIncomingCall(InCallPresenter.InCallState oldState,
            InCallPresenter.InCallState newState, Call call) {
        // same logic should happen as with onStateChange()
        onStateChange(oldState, newState, CallList.getInstance());
    }

    /**
     * Handles state changes (including incoming calls)
     *
     * @param newState The in call state.
     * @param callList The call list.
     */
    @Override
    public void onStateChange(InCallPresenter.InCallState oldState,
            InCallPresenter.InCallState newState, CallList callList) {
        Log.d(this, "onStateChange oldState" + oldState + " newState=" + newState);
        // Bail if video calling is disabled for the device.
        if (!CallUtil.isVideoEnabled(mContext)) {
            return;
        }

        if (newState == InCallPresenter.InCallState.NO_CALLS) {
            exitVideoMode();
            cleanupSurfaces();
        }

        // Determine the primary active call).
        Call primary = null;
        if (newState == InCallPresenter.InCallState.INCOMING) {
            primary = callList.getIncomingCall();
        } else if (newState == InCallPresenter.InCallState.OUTGOING) {
            primary = callList.getOutgoingCall();
        } else if (newState == InCallPresenter.InCallState.INCALL) {
            primary = callList.getActiveCall();
        }

        final boolean primaryChanged = !Objects.equals(mPrimaryCall, primary);
        Log.d(this, "onStateChange primaryChanged=" + primaryChanged);
        Log.d(this, "onStateChange primary= " + primary);
        Log.d(this, "onStateChange mPrimaryCall = " + mPrimaryCall);
        if (primaryChanged) {
            mPrimaryCall = primary;

            if (primary != null) {
                checkForVideoCallChange();
                checkForVideoStateChange();
            } else if (primary == null) {
                // If no primary call, ensure we exit video state and clean up the video surfaces.
                exitVideoMode();
            }
        } else if(mPrimaryCall!=null) {
            checkForVideoStateChange();
        }
    }

    private void checkForVideoStateChange() {
        final boolean isVideoCall = mPrimaryCall.isVideoCall(mContext);
        final boolean hasVideoStateChanged = mCurrentVideoState != mPrimaryCall.getVideoState();

        Log.d(this, "isVideoCall= " + isVideoCall + " hasVideoStateChanged=" +
                hasVideoStateChanged);

        if (!hasVideoStateChanged) { return;}

        if (isVideoCall) {
            enterVideoMode(mPrimaryCall.getVideoState());
        } else {
            exitVideoMode();
        }
    }

    private void checkForCallSubstateChange() {
        if (mCurrentCallSubstate != mPrimaryCall.getCallSubstate()) {
            VideoCallUi ui = getUi();
            if (ui == null) {
                Log.e(this, "Error VideoCallUi is null. Return.");
                return;
            }
            mCurrentCallSubstate = mPrimaryCall.getCallSubstate();
            // Display a call substate changed message on UI.
            ui.showCallSubstateChanged(mCurrentCallSubstate);
        }
    }

    private void cleanupSurfaces() {
        final VideoCallUi ui = getUi();
        if (ui == null) {
            Log.w(this, "cleanupSurfaces");
            return;
        }
        ui.cleanupSurfaces();
    }

    /**
     * Handles changes to the details of the call.  The {@link VideoCallPresenter} is interested in
     * changes to the video state.
     *
     * @param call The call for which the details changed.
     * @param details The new call details.
     */
    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
        Log.d(this, " onDetailsChanged call=" + call + " details=" + details + " mPrimaryCall="
                + mPrimaryCall);
        // If the details change is not for the currently active call no update is required.
        if (!call.equals(mPrimaryCall)) {
            Log.d(this,
                    " onDetailsChanged: Details not for current active call so returning. ");
            return;
        }

        checkForVideoStateChange();
        checkForCallSubstateChange();
    }

    /**
     * Checks for a change to the video call and changes it if required.
     */
    private void checkForVideoCallChange() {
        VideoCall videoCall = mPrimaryCall.getTelecommCall().getVideoCall();
        if (!Objects.equals(videoCall, mVideoCall)) {
            changeVideoCall(videoCall);
        }
    }

    /**
     * Handles a change to the video call.  Sets the surfaces on the previous call to null and sets
     * the surfaces on the new video call accordingly.
     *
     * @param videoCall The new video call.
     */
    private void changeVideoCall(VideoCall videoCall) {
        Log.d(this, "changeVideoCall to videoCall=" + videoCall + " mVideoCall=" + mVideoCall);
        // Null out the surfaces on the previous video call.
        if (mVideoCall != null) {
            //Log.d(this, "Null out the surfaces on the previous video call.");
            //mVideoCall.setDisplaySurface(null);
            //mVideoCall.setPreviewSurface(null);
        }

        mVideoCall = videoCall;
    }

    private boolean isCameraRequired(int videoState) {
        return VideoProfile.VideoState.isBidirectional(videoState) ||
                VideoProfile.VideoState.isTransmissionEnabled(videoState);
    }

    private boolean isCameraRequired() {
        return mPrimaryCall != null ? isCameraRequired(mPrimaryCall.getVideoState()) : false;
    }

    /**
     * Enters video mode by showing the video surfaces and making other adjustments (eg. audio).
     * TODO(vt): Need to adjust size and orientation of preview surface here.
     */
    private void enterVideoMode(int newVideoState) {
        Log.d(this, "enterVideoMode mVideoCall= " + mVideoCall + " videoState: " + newVideoState);
        VideoCallUi ui = getUi();
        if (ui == null) {
            Log.e(this, "Error VideoCallUi is null so returning");
            return;
        }

        showVideoUi(newVideoState);
        InCallPresenter.getInstance().setInCallAllowsOrientationChange(true);

        // Communicate the current camera to telephony and make a request for the camera
        // capabilities.
        if (mVideoCall != null) {
            // Do not reset the surfaces if we just restarted the activity due to an orientation
            // change.
//            if (ui.isActivityRestart()) {
//                Log.e(this, "enterVideoMode: Activity Restarted so no action");
//                return;
//            }
            int videoState = mPrimaryCall.getVideoState();
            if (videoState == mCurrentVideoState) {
                Log.d(this, "enterVideoMode: Nothing changed exiting...");
                return;
            }
            final boolean wasCameraRequired = isCameraRequired(mCurrentVideoState);
            final boolean isCameraRequired = isCameraRequired(videoState);

            if (wasCameraRequired != isCameraRequired) {
                enableCamera(isCameraRequired);
            }

            if (ui.isDisplayVideoSurfaceCreated()) {
                Log.d(this, "Calling setDisplaySurface with " + ui.getDisplayVideoSurface());
                mVideoCall.setDisplaySurface(ui.getDisplayVideoSurface());
            }

            final int rotation = ui.getCurrentRotation();
            if (rotation != VideoCallFragment.ORIENTATION_UNKNOWN) {
                mVideoCall.setDeviceOrientation(InCallPresenter.toRotationAngle(rotation));
            }
        }
        mCurrentVideoState = newVideoState;

        // If the speaker is explicitly disabled then do not enable it.
        if (SystemProperties.getInt(PROPERTY_IMS_AUDIO_OUTPUT,
                IMS_AUDIO_OUTPUT_DEFAULT) != IMS_AUDIO_OUTPUT_DISABLE_SPEAKER) {

            int currentAudioMode = AudioModeProvider.getInstance().getAudioMode();
            if (!isAudioRouteEnabled(currentAudioMode,
                AudioState.ROUTE_BLUETOOTH | AudioState.ROUTE_WIRED_HEADSET)) {
                mPreVideoAudioMode = currentAudioMode;

                Log.d(this, "Routing audio to speaker");
                TelecomAdapter.getInstance().setAudioRoute(AudioState.ROUTE_SPEAKER);
            }
        }

    }

    private void enableCamera(boolean isCameraRequired) {
        Log.d(this, "enableCamera: enabling=" + isCameraRequired);
        if (mVideoCall == null) {
            Log.w(this, "enableCamera: VideoCall is null.");
            return;
        }

        if (isCameraRequired) {
            InCallCameraManager cameraManager = InCallPresenter.getInstance().
                    getInCallCameraManager();
            mVideoCall.setCamera(cameraManager.getActiveCameraId());
            mPreviewSurfaceState = PreviewSurfaceState.CAMERA_SET;

            mVideoCall.requestCameraCapabilities();
        } else {
            mPreviewSurfaceState = PreviewSurfaceState.NONE;
            mVideoCall.setCamera(null);
        }
    }

    /**
     * Exits video mode by hiding the video surfaces  and making other adjustments (eg. audio).
     */
    private void exitVideoMode() {
        Log.d(this, "exitVideoMode");
        VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }
        InCallPresenter.getInstance().setInCallAllowsOrientationChange(false);
        mCurrentVideoState = VideoProfile.VideoState.AUDIO_ONLY;
        showVideoUi(mCurrentVideoState);

        if (mPreVideoAudioMode != AudioModeProvider.AUDIO_MODE_INVALID) {
            TelecomAdapter.getInstance().setAudioRoute(mPreVideoAudioMode);
            mPreVideoAudioMode = AudioModeProvider.AUDIO_MODE_INVALID;
        }

        enableCamera(false);

        Log.d(this, "exitVideoMode mIsFullScreen: " + mIsFullScreen);
        if (mIsFullScreen) {
            toggleFullScreen();
        }
    }

    /**
     * Show video Ui depends on video state.
     */
    private void showVideoUi(int videoState) {
        VideoCallUi ui = getUi();
        if (ui == null) {
            Log.e(this, "showVideoUi, VideoCallUi is null returning");
            return;
        }

        if (VideoProfile.VideoState.isBidirectional(videoState)) {
            ui.showVideoBidrectionalUi();
        } else if (VideoProfile.VideoState.isTransmissionEnabled(videoState)) {
            ui.showVideoTransmissionUi();
        } else if (VideoProfile.VideoState.isReceptionEnabled(videoState)) {
            ui.showVideoReceptionUi();
        } else {
            ui.hideVideoUi();
        }
    }

    /**
     * Handles peer video pause state changes.
     *
     * @param call The call which paused or un-pausedvideo transmission.
     * @param paused {@code True} when the video transmission is paused, {@code false} when video
     *               transmission resumes.
     */
    @Override
    public void onPeerPauseStateChanged(Call call, boolean paused) {
        if (!call.equals(mPrimaryCall)) {
            return;
        }

        // TODO(vt): Show/hide the peer contact photo.
    }

    /**
     * Handles peer video dimension changes.
     *
     * @param call The call which experienced a peer video dimension change.
     * @param width The new peer video width .
     * @param height The new peer video height.
     */
    @Override
    public void onUpdatePeerDimensions(Call call, int width, int height) {
        Log.d(this, "onUpdatePeerDimensions: width= " + width + " height= " + height);
        VideoCallUi ui = getUi();
        if (ui == null) {
            Log.e(this, "VideoCallUi is null. Bail out");
            return;
        }
        if (!call.equals(mPrimaryCall)) {
            Log.e(this, "Current call is not equal to primary call. Bail out");
            return;
        }

        // Change size of display surface to match the peer aspect ratio
        if (width > 0 && height > 0) {
            setDisplayVideoSize(width, height);
        }
    }

    /**
     * Handles any video quality changes in the call.
     *
     * @param call The call which experienced a video quality change.
     * @param videoQuality The new video call quality.
     */
    @Override
    public void onVideoQualityChanged(Call call, int videoQuality) {
        if (!call.equals(mPrimaryCall)) {
            return;
        }

        VideoCallUi ui = getUi();
        if (ui == null) {
            Log.e(this, "Error VideoCallUi is null. Return.");
            return;
        }

        // Display a video quality changed message on UI.
        ui.showVideoQualityChanged(videoQuality);
    }

    /**
     * Handles a change to the dimensions of the local camera.  Receiving the camera capabilities
     * triggers the creation of the video
     *
     * @param call The call which experienced the camera dimension change.
     * @param width The new camera video width.
     * @param height The new camera video height.
     */
    @Override
    public void onCameraDimensionsChange(Call call, int width, int height) {
        Log.d(this, "onCameraDimensionsChange call=" + call + " width=" + width + " height="
                + height);
        VideoCallUi ui = getUi();
        if (ui == null) {
            Log.e(this, "onCameraDimensionsChange ui is null");
            return;
        }

        if (!call.equals(mPrimaryCall)) {
            Log.e(this, "Call is not primary call");
            return;
        }

        mPreviewSurfaceState = PreviewSurfaceState.CAPABILITIES_RECEIVED;
        ui.setPreviewSurfaceSize(width, height);

        // Configure the preview surface to the correct aspect ratio.
        float aspectRatio = 1.0f;
        if (width > 0 && height > 0) {
            aspectRatio = (float) width / (float) height;
        }
        setPreviewSize(mDeviceOrientation, aspectRatio);

        // Check if the preview surface is ready yet; if it is, set it on the {@code VideoCall}.
        // If it not yet ready, it will be set when when creation completes.
        if (ui.isPreviewVideoSurfaceCreated()) {
            mPreviewSurfaceState = PreviewSurfaceState.SURFACE_SET;
            mVideoCall.setPreviewSurface(ui.getPreviewVideoSurface());
        }
    }

    /**
     * Called when call session event is raised.
     *
     * @param event The call session event.
     */
    @Override
    public void onCallSessionEvent(int event) {
        Log.d(this, "onCallSessionEvent event =" + event);
        VideoCallUi ui = getUi();
        if (ui == null) {
            Log.e(this, "onCallSessionEvent: VideoCallUi is null");
            return;
        }
        ui.displayCallSessionEvent(event);
    }

    /**
     * Handles a change to the call data usage
     *
     * @param dataUsage call data usage value
     */
    @Override
    public void onCallDataUsageChange(long dataUsage) {
        Log.d(this, "onCallDataUsageChange dataUsage=" + dataUsage);
        VideoCallUi ui = getUi();
        if (ui == null) {
            Log.e(this, "onCallDataUsageChange: VideoCallUi is null");
            return;
        }
        ui.setCallDataUsage(mContext, dataUsage);
    }

    /**
     * Handles hanges to the device orientation.
     * See: {@link Configuration.ORIENTATION_LANDSCAPE}, {@link Configuration.ORIENTATION_PORTRAIT}
     * @param orientation The device orientation.
     */
    @Override
    public void onDeviceOrientationChanged(int orientation) {
        Log.d(this, "onDeviceOrientationChanged: orientation=" + orientation);
        mDeviceOrientation = orientation;
    }

    @Override
    public void onUpgradeToVideoRequest(Call call) {
        Log.d(this, "onUpgradeToVideoRequest call=" + call);
        if (mPrimaryCall == null || !Call.areSame(mPrimaryCall, call)) {
            Log.w(this, "UpgradeToVideoRequest received for non-primary call");
        }

        if (call == null) {
            return;
        }

        call.setSessionModificationState(
                Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST);
    }

    @Override
    public void onUpgradeToVideoSuccess(Call call) {
        Log.d(this, "onUpgradeToVideoSuccess call=" + call);
        if (mPrimaryCall == null || !Call.areSame(mPrimaryCall, call)) {
            Log.w(this, "UpgradeToVideoSuccess received for non-primary call");
        }

        if (call == null) {
            return;
        }

        call.setSessionModificationState(Call.SessionModificationState.NO_REQUEST);
    }

    @Override
    public void onUpgradeToVideoFail(int status, Call call) {
        Log.d(this, "onUpgradeToVideoFail call=" + call);
        if (mPrimaryCall == null || !Call.areSame(mPrimaryCall, call)) {
            Log.w(this, "UpgradeToVideoFail received for non-primary call");
        }

        if (call == null) {
            return;
        }

        if (status == VideoProvider.SESSION_MODIFY_REQUEST_TIMED_OUT) {
            call.setSessionModificationState(
                    Call.SessionModificationState.UPGRADE_TO_VIDEO_REQUEST_TIMED_OUT);
        } else {
            call.setSessionModificationState(Call.SessionModificationState.REQUEST_FAILED);

            final Call modifyCall = call;
            // Start handler to change state from REQUEST_FAILED to NO_REQUEST after an interval.
            mSessionModificationResetHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (modifyCall != null) {
                        modifyCall
                            .setSessionModificationState(Call.SessionModificationState.NO_REQUEST);
                    }
                }
            }, SESSION_MODIFICATION_RESET_DELAY_MS);
        }
    }

    @Override
    public void onDowngradeToAudio(Call call) {
        // Implementing to satsify interface.
    }

    /**
     * Sets the preview surface size based on the current device orientation.
     * See: {@link Configuration.ORIENTATION_LANDSCAPE}, {@link Configuration.ORIENTATION_PORTRAIT}
     *
     * @param orientation The device orientation.
     * @param aspectRatio The aspect ratio of the camera (width / height).
     */
    private void setPreviewSize(int orientation, float aspectRatio) {
        VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }

        int height;
        int width;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            width = (int) (mMinimumVideoDimension * aspectRatio);
            height = (int) mMinimumVideoDimension;
        } else {
            width = (int) mMinimumVideoDimension;
            height = (int) (mMinimumVideoDimension * aspectRatio);
        }
        ui.setPreviewSize(width, height);
    }

    /**
     * Sets the display video surface size based on peer width and height
     *
     * @param width peer width
     * @param height peer height
     */

    private void setDisplayVideoSize(int width, int height) {
        Log.d(this, "setDisplayVideoSize:Received peer width=" + width + " peer height=" + height);
        VideoCallUi ui = getUi();
        if (ui == null) {
            return;
        }

        // Get current display size
        Point size = ui.getScreenSize();
        Log.d("VideoCallPresenter", "setDisplayVideoSize: windowmgr width=" + size.x
                + " windowmgr height=" + size.y);
        if (size.y * width > size.x * height) {
            // current display height is too much. Correct it
            size.y = (int) (size.x * height / width);
        } else if (size.y * width < size.x * height) {
            // current display width is too much. Correct it
            size.x = (int) (size.y * width / height);
        }
        ui.setDisplayVideoSize(size.x, size.y);
    }

    private static boolean isAudioRouteEnabled(int audioRoute, int audioRouteMask) {
        return ((audioRoute & audioRouteMask) != 0);
    }

    /**
     * Defines the VideoCallUI interactions.
     */
    public interface VideoCallUi extends Ui {
        void showVideoBidrectionalUi();
        void showVideoTransmissionUi();
        void showVideoReceptionUi();
        void hideVideoUi();
        void showVideoQualityChanged(int videoQuality);
        boolean isDisplayVideoSurfaceCreated();
        boolean isPreviewVideoSurfaceCreated();
        Surface getDisplayVideoSurface();
        Surface getPreviewVideoSurface();
        int getCurrentRotation();
        void setPreviewSize(int width, int height);
        void setPreviewSurfaceSize(int width, int height);
        void setDisplayVideoSize(int width, int height);
        void setCallDataUsage(Context context, long dataUsage);
        void displayCallSessionEvent(int event);
        Point getScreenSize();
        void cleanupSurfaces();
        boolean isActivityRestart();
        void showCallSubstateChanged(int callSubstate);
    }
}
