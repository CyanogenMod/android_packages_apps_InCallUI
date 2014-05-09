/* Copyright (c) 2012-2013, The Linux Foundation. All rights reserved.
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
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.SystemProperties;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.android.incallui.CameraHandler.CameraState;
import com.android.services.telephony.common.CallDetails;

import java.io.IOException;

/**
 * Helper class to initialize and run the InCallScreen's "Video Call" UI.
 */
public class VideoCallPanel extends RelativeLayout implements TextureView.SurfaceTextureListener, View.OnClickListener {
    private static final int LOOPBACK_MODE_HEIGHT = 144;
    private static final int LOOPBACK_MODE_WIDTH = 176;
    private static final int CAMERA_UNKNOWN = -1;
    private static final String LOG_TAG = "VideoCallPanel";
    private static final boolean DBG = true;

    private static final int MEDIA_TO_CAMERA_CONV_UNIT = 1000;
    private static final int DEFAULT_CAMERA_ZOOM_VALUE = 0;
    private static final int INVALID_SIZE = -1;

    private Context mContext;
    private VideoCallManager mVideoCallManager;

    // "Video Call" UI elements and state
    private ViewGroup mVideoCallPanel;
    private ZoomControlBar mZoomControl;

    private TextureView mFarEndView;
    private TextureView mCameraPreview;
    private SurfaceTexture mCameraSurface;
    private SurfaceTexture mFarEndSurface;
    private boolean mCanReleaseFarEndSurface = false;
    private ImageView mCameraPicker;
    private final Resize mResize = new Resize();

    private int mZoomMax;
    private int mZoomValue;  // The current zoom value

    // Multiple cameras support
    private int mNumberOfCameras;
    private int mFrontCameraId;
    private int mBackCameraId;
    private int mCameraId;

    private int mHeight = INVALID_SIZE;
    private int mWidth = INVALID_SIZE;

    // Property used to indicate that the Media running in loopback mode
    private boolean mIsMediaLoopback = false;

    // Flag to indicate if camera is needed for a certain call type.
    // For eg. VT_RX call will not need camera
    private boolean mCameraNeeded = false;

    // Flag to indicate if recording can be started
    private boolean mStartReceived = false;

    /*
     * Flag to indicate whether recording has started or not
     * False: recording hasn't started
     * True: recording has started
     */
    private boolean mIsRecordingStarted = false;

    /**
    * This class implements the zoom listener for zoomControl
    */
    private class ZoomChangeListener implements ZoomControl.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(int index) {
            VideoCallPanel.this.onZoomValueChanged(index);
        }
    }


    /**
     * This class implements the listener for PARAM READY EVENT
     */
    public class MediaEventListener implements MediaHandler.IMediaEventListener {
        @Override
        public void onParamReadyEvent() {
            CameraState cameraState = mVideoCallManager.getCameraState();
            if (DBG) log("onParamReadyEvent cameraState= " + cameraState);
            if (cameraState == CameraState.PREVIEW_STARTED) {
                // If camera is already capturing stop preview, reset the
                // parameters and then start preview again
                stopRecordingAndPreview();
                initializeCameraParams();
                startPreviewAndRecording();
            }
        }

        @Override
        public void onPlayerStateChanged(int state) {
            if (state == MediaHandler.PLAYER_STATE_STARTED) {
                log("PLAYER_STARTED");
            } else if (state == MediaHandler.PLAYER_STATE_STOPPED) {
                log("PLAYER_STOPPED");
            } else {
                log("UNKOWN_STATE");
            }
        }

        @Override
        public void onDisplayModeEvent() {
            // NO-OP
        }

        @Override
        public void onStartReadyEvent() {
            if (DBG) {
                log("onStartReadyEvent mCameraNeeded=" + mCameraNeeded + " CameraSurface= "
                        + mCameraSurface + " camera state = "
                        + mVideoCallManager.getCameraState());
            }

            mStartReceived = true;

            if (mCameraNeeded && mCameraSurface != null) {
                // startCameraRecording (@ Camerahandler.java) does camera
                // preview_started state check.
                startCameraRecording();
            }
        }

        @Override
        public void onStopReadyEvent() {
            if (DBG) log("onStopReadyEvent");
            mStartReceived = false;
        }

        @Override
        public void onPeerResolutionChangeEvent() {
            if (DBG) log("onPeerResolutionChangeEvent");

            if (mHeight != INVALID_SIZE && mWidth != INVALID_SIZE) {
                resizeFarEndView();
            }
        }
    }

    public class CvoListener implements CvoHandler.CvoEventListener {
        @Override
        public void onDeviceOrientationChanged(int rotation) {
            int requiredSurfaceRotation = 360 - rotation;
            if (DBG) {
                log("onDeviceOrientationChanged: Local sensor rotation =" + rotation +
                        " Rotate far end based on local sensor by " + requiredSurfaceRotation);
            }
            mFarEndView.setRotation(requiredSurfaceRotation);
        }
    }

    public VideoCallPanel(Context context) {
        super(context);
        mContext = context;
    }

    public VideoCallPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public VideoCallPanel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    /**
     * Finalize view from inflation.
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (DBG) log("onFinishInflate(this = " + this + ")...");

        // Check the Media loopback property
        int property = SystemProperties.getInt("net.lte.VT_LOOPBACK_ENABLE", 0);
        mIsMediaLoopback = (property == 1) ? true : false;
        if (DBG) log("Is Media running in loopback mode: " + mIsMediaLoopback);

        // Get UI widgets
        mVideoCallPanel = (ViewGroup) findViewById(R.id.videoCallPanel);
        mZoomControl = (ZoomControlBar) findViewById(R.id.zoom_control);
        mFarEndView = (TextureView) findViewById(R.id.video_view);
        mCameraPreview = (TextureView) findViewById(R.id.camera_view);
        mCameraPicker = (ImageView) findViewById(R.id.camera_picker);

        // Set listeners
        mCameraPreview.setSurfaceTextureListener(this);
        mFarEndView.setSurfaceTextureListener(this);
        mCameraPicker.setOnClickListener(this);

        // Get the camera IDs for front and back cameras
        mVideoCallManager = VideoCallManager.getInstance(mContext);
        mBackCameraId = mVideoCallManager.getBackCameraId();
        mFrontCameraId = mVideoCallManager.getFrontCameraId();
        chooseCamera(true);

        // Check if camera supports dual cameras
        mNumberOfCameras = mVideoCallManager.getNumberOfCameras();
        if (mNumberOfCameras > 1) {
            mCameraPicker.setVisibility(View.VISIBLE);
        } else {
            mCameraPicker.setVisibility(View.GONE);
        }

        // Set media event listener
        mVideoCallManager.setMediaEventListener(new MediaEventListener());
        mVideoCallManager.setCvoEventListener(new CvoListener());
    }

    // The function must be called from the parent's onDestroy function.
    public void onDestroy() {
        log("onDestroy...");
        releaseCachedSurfaces();
    }

    public void setCameraNeeded(boolean mCameraNeeded) {
        this.mCameraNeeded = mCameraNeeded;
    }

    /**
     * Call is either is either being originated or an MT call is received.
     */
    public void onCallInitiating(int callType) {
        if (DBG) log("onCallInitiating");

        // Only for VT TX it is required to default to back camera
        boolean chooseFrontCamera = true;
        if (callType == CallDetails.CALL_TYPE_VT_TX) {
            chooseFrontCamera = false;
        }

        chooseCamera(chooseFrontCamera);

        if (callType == CallDetails.CALL_TYPE_VT
                || callType == CallDetails.CALL_TYPE_VT_TX) {
            mCameraNeeded = true;
        } else {
            mCameraNeeded = false;
        }
    }

    /**
     * Called during layout when the size of the view has changed. This method
     * store the VideoCallPanel size to be later used to resize the camera
     * preview accordingly
     */
    @Override
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld) {
        log("onSizeChanged");
        log("Video Panel xNew=" + xNew + ", yNew=" + yNew + " xOld=" + xOld + " yOld=" + yOld);
        if (xNew != xOld || yNew != yOld) {
            post(mResize);
        }
    }

    private class Resize implements Runnable {
        @Override
        public void run() {
            doSizeChanged();
        }
    }

    private void doSizeChanged() {
        mWidth = getWidth();
        mHeight = getHeight();

        if (DBG) log("doSizeChanged: VideoCallPanel width=" + mWidth + ", height=" + mHeight);
        resizeCameraPreview();
        resizeFarEndView();
    }

    /**
     * Called when the InCallScreen activity is being paused. This method hides
     * the VideoCallPanel so that other activities can use the camera at this
     * time.
     */
    public void onPause() {
        if (DBG) log("onPause");
        mVideoCallPanel.setVisibility(View.GONE);
    }

    /**
     * This method opens the camera and starts the camera preview
     */
    private void initializeCamera() {
        if (DBG) log("Initializing camera id=" + mCameraId);

        if (mCameraId == CAMERA_UNKNOWN) {
            loge("initializeCamera: Not initializing camera as mCameraId is unknown");
            return;
        }

        // Open camera if not already open
        if (false == openCamera(mCameraId)) {
            return;
        }
        initializeZoom();
        initializeCameraParams();
        startPreviewAndRecording();
    }

    public boolean isCameraInitNeeded() {
        if (DBG) {
            log("isCameraInitNeeded mCameraNeeded=" + mCameraNeeded + " CameraSurface= "
                    + mCameraSurface + " camera state = "
                    + mVideoCallManager.getCameraState());
        }
        return mCameraNeeded && mCameraSurface != null
                && mVideoCallManager.getCameraState() == CameraState.CAMERA_CLOSED;
    }

    /**
     * This method creates the camera object if camera is not disabled
     *
     * @param cameraId ID of the front or the back camera
     * @return Camera instance on success, null otherwise
     */
    private boolean openCamera(int cameraId) {
        boolean result = false;

        try {
            return mVideoCallManager.openCamera(cameraId);
        } catch (Exception e) {
            loge("Failed to open camera device, error " + e.toString());
            return result;
        }
    }

    /**
     * This method disconnect and releases the camera
     */
    private void closeCamera() {
        mVideoCallManager.closeCamera();
        mIsRecordingStarted = false;
    }

    /**
     * This method stops the camera recording
     */
    private void stopCameraRecording() {
        // stop recording only if it has started
        if (!mIsRecordingStarted) {
            log("camera recording hasn't started so no need to stop it");
            return;
        }

        mVideoCallManager.stopCameraRecording();
        mIsRecordingStarted = false;
    }

    /**
     * This method starts the camera recording
     */
    private void startCameraRecording() {
        if (mStartReceived) {
            mVideoCallManager.startCameraRecording();
            mIsRecordingStarted = true;
        } else {
            log("waiting for START_READY. Deferring camera recording");
        }
    }

    /**
     * This method starts the camera preview and recording
     */
    private void startPreviewAndRecording() {
        try {
            mVideoCallManager.startCameraPreview(mCameraSurface);
            startCameraRecording();
        } catch (IOException ioe) {
            closeCamera();
            loge("Exception startPreviewAndRecording, " + ioe.toString());
        }
    }

    /**
     * This method stops the camera recording and preview
     */
    private void stopRecordingAndPreview() {
        stopCameraRecording();
        mVideoCallManager.stopCameraPreview();
    }

    /* Implementation of listeners */

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (surface.equals(mCameraPreview.getSurfaceTexture())) {
            if (DBG) log("Camera surface texture created");

            mCameraSurface = surface;

            // Initialize Camera as needed.
            if (isCameraInitNeeded()) {
                initializeCamera();
            }
        } else if (surface.equals(mFarEndView.getSurfaceTexture())) {

            // Use cached surface texture.
            if (DBG) log("Video surface texture created");
            if (mFarEndSurface == null) {
                log("Caching video surface texture.");
                mFarEndSurface = surface;
            } else {
                log("Resetting video surface texture.");
                mFarEndView.setSurfaceTexture(mFarEndSurface);
            }

            mVideoCallManager.setFarEndSurface(mFarEndSurface);
            mCanReleaseFarEndSurface = false;
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        boolean relaseSurface = true;
        if (surface.equals(mCameraPreview.getSurfaceTexture())) {
            if (DBG) log("CameraPreview surface texture destroyed");
            stopRecordingAndPreview();
            closeCamera();
            mCameraSurface = null;
            relaseSurface = true;
        } else if (surface.equals(mFarEndView.getSurfaceTexture())) {
            if (DBG) log("FarEndView surface texture destroyed, CanReleaseFarEndSurface=" +
                        mCanReleaseFarEndSurface);
            relaseSurface = mCanReleaseFarEndSurface;
            if (relaseSurface) setFarEndNull();
        }
        return relaseSurface;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // Invoked every time there's a new Camera preview frame
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Ignored camera does all the work for us
    }

    /**
     * This method is called when the visibility of the VideoCallPanel is changed
     */
    @Override
    protected void onVisibilityChanged (View changedView, int visibility) {
        if (changedView != this || mVideoCallManager == null) {
            return;
        }

        switch(visibility)
        {
            case View.INVISIBLE:
            case View.GONE:
                if (DBG) log("VideoCallPanel View is GONE or INVISIBLE");
                // Stop the preview and close the camera now because other
                // activities may need to use it
                if (mVideoCallManager.getCameraState() != CameraState.CAMERA_CLOSED) {
                    stopRecordingAndPreview();
                    closeCamera();
                }
                break;
            case View.VISIBLE:
                if (DBG) log("VideoCallPanel View is VISIBLE");
                if (isCameraInitNeeded()) {
                    initializeCamera();
                }
                break;
        }
    }

    @Override
    public void onClick(View v) {
        int direction =  mVideoCallManager.getCameraDirection();

        // Switch the camera front/back/off
        // The state machine is as follows
        // front --> back --> stop preview --> front...
        switch(direction) {
            case CAMERA_UNKNOWN:
                switchCamera(mFrontCameraId);
                break;
            case Camera.CameraInfo.CAMERA_FACING_FRONT:
                switchCamera(mBackCameraId);
                break;
            case Camera.CameraInfo.CAMERA_FACING_BACK:
                switchCamera(CAMERA_UNKNOWN);
                break;
        }
    }

    /**
     * This method get the zoom related parameters from the camera and
     * initialized the zoom control
     */
    private void initializeZoom() {
        ImsCamera imsCamera = mVideoCallManager.getImsCameraInstance();
        if (imsCamera == null) {
            return;
        }
        if (!imsCamera.isZoomSupported()) {
            mZoomControl.setVisibility(View.GONE); // Disable ZoomControl
            return;
        }

        mZoomControl.setVisibility(View.VISIBLE); // Enable ZoomControl
        mZoomMax = imsCamera.getMaxZoom();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        mZoomControl.setZoomMax(mZoomMax);
        mZoomControl.setZoomIndex(DEFAULT_CAMERA_ZOOM_VALUE);
        mZoomControl.setOnZoomChangeListener(new ZoomChangeListener());
    }

    /**
     * This method gets called when the zoom control reports that the zoom value
     * has changed. This method sets the camera zoom value accordingly.
     * @param index
     */
    private void onZoomValueChanged(int index) {
        mZoomValue = index;
        ImsCamera imsCamera = mVideoCallManager.getImsCameraInstance();
        // Set zoom
        if (imsCamera.isZoomSupported()) {
            imsCamera.setZoom(mZoomValue);
        }
    }

    /**
     * Initialize camera parameters based on negotiated height, width
     */
    private void initializeCameraParams() {
        try {
            // Get the parameter to make sure we have the up-to-date value.
            ImsCamera imsCamera = mVideoCallManager.getImsCameraInstance();
            // Set the camera preview size
            if (mIsMediaLoopback) {
                // In loopback mode the IMS is hard coded to render the
                // camera frames of only the size 176x144 on the far end surface
                imsCamera.setPreviewSize(LOOPBACK_MODE_WIDTH, LOOPBACK_MODE_HEIGHT);
            } else {
                log("Set Preview Size directly with negotiated Height = "
                        + mVideoCallManager.getNegotiatedHeight()
                        + " negotiated width= " + mVideoCallManager.getNegotiatedWidth());
                imsCamera.setPreviewSize(mVideoCallManager.getNegotiatedWidth(),
                        mVideoCallManager.getNegotiatedHeight());
                imsCamera.setPreviewFpsRange(mVideoCallManager.getNegotiatedFps());
            }
        } catch (RuntimeException e) {
            loge("Error setting Camera preview size/fps exception=" + e);
        }
    }

    public void setPanelElementsVisibility(int callType) {
        log("setPanelElementsVisibility: callType= " + callType);
        switch (callType) {
            case CallDetails.CALL_TYPE_VT:
                mCameraPreview.setVisibility(VISIBLE);
                mFarEndView.setVisibility(VISIBLE);
                if (isCameraInitNeeded()) {
                    initializeCamera();
                }
                log("setPanelElementsVisibility: VT: mCameraPreview:VISIBLE, mFarEndView:VISIBLE");
                break;
            case CallDetails.CALL_TYPE_VT_TX:
                mCameraPreview.setVisibility(View.VISIBLE);
                if (isCameraInitNeeded()) {
                    initializeCamera();
                }
                // Not setting mFarEndView to GONE as receiver side did not get the frames
                log("setPanelElementsVisibility VT_TX: mCameraPreview:VISIBLE");
                break;
            case CallDetails.CALL_TYPE_VT_RX:
                mFarEndView.setVisibility(View.VISIBLE);
                // Stop the preview and close the camera now because other
                // activities may need to use it
                if (mVideoCallManager.getCameraState() != CameraState.CAMERA_CLOSED) {
                    stopRecordingAndPreview();
                    closeCamera();
                }
                mCameraPreview.setVisibility(View.GONE);
                log("setPanelElementsVisibility VT_RX: mCameraPreview:GONE mFarEndView:VISIBLE");
                break;
            default:
                log("setPanelElementsVisibility: Default: "
                        + "VideoCallPanel is " + mVideoCallPanel.getVisibility()
                        + "mCameraPreview is " + mCameraPreview.getVisibility()
                        + "mFarEndView is " + mFarEndView.getVisibility());
                break;
        }
    }

    /**
     * This method resizes the camera preview based on the size of the
     * VideoCallPanel
     */
    private void resizeCameraPreview() {
        if (DBG) log("resizeCameraPreview: mHeight=" + mHeight);
        // For now, set the preview size to be 1/4th of the VideoCallPanel
        ViewGroup.LayoutParams cameraPreivewLp = mCameraPreview.getLayoutParams();
        cameraPreivewLp.height = mHeight / 4;
        cameraPreivewLp.width = mHeight / 4; // use mHeight to create small
                                             // square box for camera preview
        mCameraPreview.setLayoutParams(cameraPreivewLp);
    }

    /**
     * This method resizes the far end view based on the size of VideoCallPanel
     * Presently supports only full size far end video
     */
    private void resizeFarEndView() {
        int minDimension = Math.min(mWidth, mHeight);
        int farEndWidth = mWidth;
        int farEndHeight = mHeight;
        float aspectRatio = mVideoCallManager.getPeerAspectRatio();
        if (aspectRatio > 1) {
            // Width > Height, so fix the width
            farEndWidth = minDimension;
            farEndHeight = Math.round(minDimension / aspectRatio);
        } else if (aspectRatio > 0 && aspectRatio <= 1) {
            farEndHeight = minDimension;
            farEndWidth = Math.round(aspectRatio * minDimension);
        } // In other cases continue with target height and width
        if (DBG) {
            log("resizeFarEndView FarEnd to width:" + farEndWidth + ", height:" + farEndHeight);
        }

        ViewGroup.LayoutParams farEndViewLp = mFarEndView.getLayoutParams();
        farEndViewLp.height = farEndHeight;
        farEndViewLp.width = farEndWidth;

        mFarEndView.setLayoutParams(farEndViewLp);
    }

    /**
     * This method switches the camera to front/back or off
     * @param cameraId
     */
    private void switchCamera(int cameraId) {
        // Change the camera Id
        mCameraId = cameraId;

        // Stop camera preview if already running
        if (mVideoCallManager.getCameraState() != CameraState.CAMERA_CLOSED) {
            stopRecordingAndPreview();
            closeCamera();
        }

        log("VideoCall: switchCamera: IsCameraNeeded=" + mCameraNeeded + " cameraId=" + cameraId);
        final boolean showCameraPreview = mCameraNeeded && cameraId != CAMERA_UNKNOWN;
        mCameraPreview.setVisibility(showCameraPreview ? TextureView.VISIBLE : TextureView.GONE);

        // Restart camera if camera doesn't need to stay off
        if (isCameraInitNeeded()) {
            initializeCamera();
        }
    }

    /**
     * Choose the camera direction to the front camera if it is available. Else
     * set the camera direction to the rear facing
     */
    private void chooseCamera(boolean chooseFrontCamera) {
        // Assuming that both cameras are available.
        mCameraId = chooseFrontCamera ? mFrontCameraId : mBackCameraId;

        // The requested camera is not available.
        if (mCameraId == CAMERA_UNKNOWN) {
            // Reverse the above logic, this will choose the other camera.
            mCameraId = !chooseFrontCamera ? mFrontCameraId : mBackCameraId;
        }

        if (mCameraId == CAMERA_UNKNOWN) {
            loge("chooseCamera " + chooseFrontCamera + " Both camera ids unknown");
        }
    }

    // Releases surface texture if it's not null.
    private void release(SurfaceTexture s) {
        if (s == null) return;
        log("VideoCall: Releasing surface texture, " + s);
        s.release();
    }

    private void releaseCachedSurfaces() {
        log("releaseCachedSurfaces");

        mCanReleaseFarEndSurface = true;
        if (mFarEndView==null || !mFarEndView.isAvailable()) {
            release(mFarEndSurface);
            setFarEndNull();
        }
    }

    private void setFarEndNull() {
        log("setFarEndNull, " + mFarEndSurface);
        if (mFarEndSurface != null) {
            mFarEndSurface = null;
            mVideoCallManager.setFarEndSurface(mFarEndSurface);
        }
    }

    public void startOrientationListener(boolean start) {
        mVideoCallManager.startOrientationListener(start);
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    private void loge(String msg) {
        Log.e(LOG_TAG, msg);
    }
}
