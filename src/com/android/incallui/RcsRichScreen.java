/*
 * Copyright (c) 2014 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.android.incallui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.telecom.VideoProfile;
import com.android.incallui.RcsApiManager;
import com.suntek.mway.rcs.client.aidl.plugin.entity.richscrn.ResultInfo;
import com.suntek.mway.rcs.client.aidl.plugin.entity.richscrn.RichScrnShowing;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.voip.impl.RichScreenApi;
import com.suntek.mway.rcs.client.api.RCSServiceListener;
import android.os.RemoteException;

public class RcsRichScreen {
    private static String TAG = "RCS_UI_RcsRichScreen";
    // those Strings is use for rcs enhance screen(phone event)
    // start a voice call
    private static String INITIATE_A_VOICE_CALL = "111 00 00000";
    // incoming a voice call
    private static String INCOMING_VOICE_CALL_THE_TERMINAL_STARTS_RINGING = "122 00 18000";
    // accepct a incoming voice call
    private static String SWITCHED_VOICE_CALLS_CALLINGSIDE = "113 00 20000";
    // incoming vocie call been accepcted
    private static String SWITCHED_VOICE_CALLS_CALLED_SIDE = "123 00 20000";
    // calling vocie call has been hanged up
    private static String VOICE_CALL_HANG_UP_CALLING_SIDE = "114 00 20000";
    // incoming vocie call has been hanged up
    private static String VOICE_CALL_HANG_UP_CALLED_SIDE = "124 00 20000";
    // dailing a video call
    private static String OUTGOING_VIDEO_CALL = "211 00 00000";
    // incomming a video call and start ringing
    private static String VIDEO_CALL_COMES_IN_THE_TERMINAL_STARTS_RINGING = "222 00 00000";
    // dailing video call was accepct
    private static String VIDEO_CALL_IS_CONNECTED_CALLING_SIDE = "213 00 20000";
    // incoming video call has been accpet as a video call.
    private static String VIDEO_CALL_IS_CONNECTED_CALLED_SIDE_SELECT_THE_VIDEO_CALL = "223 00 20000";
    // dailing video call has been hanged up
    private static String VIDEO_CALL_HANG_UP_CALLING_SIDE = "214 00 20000";
    // imcoming video call has been hanged up
    private static String VIDEO_CALL_HANG_UP_CALLED_SIDE = "224 00 20000";
    // phone book was being viewed and require to view the enhanced screen
    private static String ADDRESS_BOOK_IS_BING_VIEWED_VIEW_ENHANCED_SCREEN = "933 10 11000";
    // phone book was being viewed and require to setin the enhance screen
    private static String ADDRESS_BOOK_IS_BING_VIEWED_ENHANCED_OSD_SETTINGS = "933 10 12000";
    // phone book hans been update
    private static String ADDRESS_BOOK_HAVE_BEEN_UPDATED = "934 10 20000";
    // completed terminal factory reset.
    private static String COMPLETED_RESTORE_FACTORY_SETTINGS = "934 90 30000";
    // sim card has been switched
    private static String SIM_CARD_HAS_BEEN_REPLACED = "944 90 40000";
    // invailable
    private static String NON_SPECIFIC_EVENTS = "000 00 00000";

    // RCS enhance screen resource type
    private static final int RCS_STATIC_IMAGE = 0;
    private static final int RCS_VIRTUAL_IMAGE = 1;
    private static final int RCS_VIDEO = 2;

    private static final int DEFAULT_NUMBER_LENGTH = 11;
    // for RCS
    private SurfaceView msurface = null;
    MediaPlayer mediaPlayer;
    private SurfaceHolder surfaceholder;
    private String mNumber = null;
    private ImageView mRcsPhoto;
    private GifMovieView mGifMovieView;
    private TextView mGreeting;
    private TextView missdnAddress;
    private String videoPath = null;
    private Context mContext;
    private String mPhoneEevnt = null;
    private boolean isGetRichScreenCompleted = false;

    public RcsRichScreen(Context context, ImageView rcsImageView,
            TextView Greeting, TextView rcsMissdnAddress,
            GifMovieView GifMovieView, SurfaceView surface) {
        mGifMovieView = GifMovieView;
        mGreeting = Greeting;
        missdnAddress = rcsMissdnAddress;
        mRcsPhoto = rcsImageView;
        msurface = surface;
        mContext = context;
        initSurfaceView();
    }

    private void initSurfaceView() {
        surfaceholder = msurface.getHolder();
        surfaceholder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceholder.addCallback(new SurfaceHolder.Callback() {

            @Override
            public void surfaceDestroyed(SurfaceHolder arg0) {
                // TODO Auto-generated method stub
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                }
            }

            @Override
            public void surfaceCreated(SurfaceHolder arg0) {
                // TODO Auto-generated method stub
                play(videoPath);
            }

            @Override
            public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2,
                    int arg3) {
                // TODO Auto-generated method stub
            }
        });
    }

    private void play(String videoPath) {
        if (videoPath == null) {
            return;
        }
        if(null != mediaPlayer){
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mediaPlayer = new MediaPlayer();
        // set video type
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setLooping(true);
        mediaPlayer.setDisplay(surfaceholder);
        try {
            mediaPlayer.setDataSource(videoPath);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Log.i(TAG, "play video wrong");
        }
    }

    private void reset() {
        if(null == mediaPlayer){
            return ;
        }
        mediaPlayer.seekTo(0);
        mediaPlayer.start();
    }

    private void stop() {
        if(null == mediaPlayer){
            return ;
        }
        Log.i(TAG, "stop the video");
        mediaPlayer.stop();
        mediaPlayer.release();
        mediaPlayer = null;
    }

    public void getRichScreenInfo(String PhoneEevnt) {
        if (mNumber == null) {
            return;
        }
        RichScrnShowing result = null;
        mPhoneEevnt = PhoneEevnt;

        if (mNumber != null) {
            try {
                Log.i(TAG,
                        "getRichScreenApi" + mNumber);
                result = RcsApiManager.getRichScreenApi().getRichScrnObj(
                        mNumber, PhoneEevnt);
                Log.i(TAG, "result" + result);
            } catch (ServiceDisconnectedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        getResultUtilInfo(result);
    }

    private void getResultUtilInfo(final RichScrnShowing result) {
        if (result == null) {
            Log.i(TAG, "getResultUtilInfo retult is null");
            setRcsFragmentVisibleDefault();
            return;
        }
        Log.i(TAG, "result.getGreeting()" + result.getGreeting());
        Log.i(TAG, "result.getSourceType()" + result.getSourceType());
        Log.i(TAG, "result.getSourceType()" + result.getLocalSourceUrl());
        mGreeting.setVisibility(View.GONE);
        mRcsPhoto.setVisibility(View.GONE);
        msurface.setVisibility(View.GONE);
        mGifMovieView.setVisibility(View.GONE);
        missdnAddress.setVisibility(View.GONE);
        if (!TextUtils.isEmpty(result.getGreeting())) {
            mGreeting.setVisibility(View.VISIBLE);
            StringBuilder greetingString = new StringBuilder();
            greetingString.append(mContext.getResources().getString(
                 R.string.rcs_greeting_string));
            greetingString.append(result.getGreeting());
            mGreeting.setText(greetingString.toString());
        }
        if (!TextUtils.isEmpty(result.getMissdnAddress())) {
            missdnAddress.setVisibility(View.VISIBLE);
            StringBuilder missdnAddressString = new StringBuilder();
            missdnAddressString.append(mContext.getResources().getString(
                    R.string.rcs_missdnaddress_string));
            missdnAddressString.append(result.getMissdnAddress());
            missdnAddress.setText(missdnAddressString.toString());
        } else {
            missdnAddress.setVisibility(View.GONE);
            try {
                Log.i(TAG, "getRichScreenApi.DownloadHomeLocRules"
                        + RcsApiManager.getRichScreenApi());
                RcsApiManager.getRichScreenApi().downloadHomeLocRules(
                        mPhoneEevnt);
            } catch (Exception e) {
                Log.w(TAG,e);
            }
        }
        String sorceType = result.getSourceType();
        switch (Integer.valueOf(sorceType)) {
            case RCS_STATIC_IMAGE: {

                mRcsPhoto.setVisibility(View.VISIBLE);
                Bitmap bitmap = BitmapFactory
                        .decodeFile(result.getLocalSourceUrl());
                mRcsPhoto.setImageBitmap(bitmap);

            }
             break;
        case RCS_VIRTUAL_IMAGE: {
            mGifMovieView.setVisibility(View.VISIBLE);
            mGifMovieView.setMovieResource(result.getLocalSourceUrl());
            }
            break;
        case RCS_VIDEO: {
            msurface.setVisibility(View.VISIBLE);
            videoPath = result.getLocalSourceUrl();
            }
            break;
        default:
            mRcsPhoto.setVisibility(View.VISIBLE);
            break;
        }
    }

    public String getPhoneEventForRichScreen(int state, int videoState) {
        String phoneEevnt = INITIATE_A_VOICE_CALL;
        Log.i(TAG, "PhoneEevnt:" + phoneEevnt);
        switch (state) {
        case Call.State.ACTIVE:

            if (phoneEevnt.equals(INITIATE_A_VOICE_CALL)) {

                if (VideoProfile.VideoState.isBidirectional(videoState)) {
                    // this is judge it is a video call or no
                    phoneEevnt = VIDEO_CALL_IS_CONNECTED_CALLING_SIDE;
                } else {
                    phoneEevnt = SWITCHED_VOICE_CALLS_CALLINGSIDE;
                }

            } else if (phoneEevnt
                    .equals(INCOMING_VOICE_CALL_THE_TERMINAL_STARTS_RINGING)
                    || phoneEevnt
                            .equals(VIDEO_CALL_COMES_IN_THE_TERMINAL_STARTS_RINGING)) {

                if (VideoProfile.VideoState.isBidirectional(videoState)) {
                    phoneEevnt = VIDEO_CALL_IS_CONNECTED_CALLED_SIDE_SELECT_THE_VIDEO_CALL;
                    setRcsFragmentVisibleGone();
                } else {
                    phoneEevnt = SWITCHED_VOICE_CALLS_CALLED_SIDE;
                }
            }
            break;
        case Call.State.CONNECTING:
        case Call.State.DIALING:
        case Call.State.REDIALING:
            if (VideoProfile.VideoState.isBidirectional(videoState)) {
                phoneEevnt = OUTGOING_VIDEO_CALL;
                setRcsFragmentVisibleDefault();
            } else {
                phoneEevnt = INITIATE_A_VOICE_CALL;
            }
            break;
        case Call.State.INCOMING:
        case Call.State.CALL_WAITING:
            if (VideoProfile.VideoState.isBidirectional(videoState)) {
                phoneEevnt = VIDEO_CALL_COMES_IN_THE_TERMINAL_STARTS_RINGING;
            } else {
                phoneEevnt = INCOMING_VOICE_CALL_THE_TERMINAL_STARTS_RINGING;
            }
            break;
        default:
            Log.i(TAG, "updateCallStateWidgets: unexpected call: " + state);
        }
        Log.i(TAG, "mPhoneEevnt:" + phoneEevnt);
        return phoneEevnt;
    }

    /**
     * Changes the visibility of the RCS fragment
     */
    private void setRcsFragmentVisibleGone() {

        mGreeting.setVisibility(View.GONE);
        mRcsPhoto.setVisibility(View.GONE);
        msurface.setVisibility(View.GONE);
        mGifMovieView.setVisibility(View.GONE);
        missdnAddress.setVisibility(View.GONE);

    }

    private void setRcsFragmentVisibleDefault() {

        mGreeting.setVisibility(View.GONE);
        mRcsPhoto.setVisibility(View.VISIBLE);
        msurface.setVisibility(View.GONE);
        mGifMovieView.setVisibility(View.GONE);
        missdnAddress.setVisibility(View.GONE);

    }

    private void createComfirmDialogInVideCall(final String phoneEvent) {
        new AlertDialog.Builder(mContext)
                .setMessage(
                        mContext.getResources().getString(
                                R.string.display_rich_screen))
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                getRichScreenInfo(phoneEvent);
                            }
                        }).setNegativeButton(android.R.string.cancel, null)
                .create().show();

    }

    public void updateRichScreenByCallState(int state, int videoState) {
        if (state == Call.State.DISCONNECTED) {
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    try {
                        Log.i(TAG, "getRichScreenApi.downloadRichScreen:"
                                + mNumber);
                        RcsApiManager.getRichScreenApi().downloadRichScrnObj(
                                mNumber, mPhoneEevnt);
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }, 1000);
        } else {
            String phoneEvent = getPhoneEventForRichScreen(state, videoState);
            if (phoneEvent
                    .equals(VIDEO_CALL_COMES_IN_THE_TERMINAL_STARTS_RINGING)) {
                Log.i(TAG, "video call income do not set richscreen");
                isGetRichScreenCompleted = true;
                createComfirmDialogInVideCall(phoneEvent);
            }
            if (!isGetRichScreenCompleted) {
                getRichScreenInfo(phoneEvent);
                isGetRichScreenCompleted = true;
            }
        }

    }

    public void setNumber(String number) {
        mNumber = getFormatNumber(number);
    }

    public static String getFormatNumber(String number){
        if(null == number){
            return "";
        }
        number = number.replaceAll("-", "");
        number = number.replaceAll(" ", "");
        number = number.replaceAll(",", "");
        int numberLen = number.length();
        if(numberLen > DEFAULT_NUMBER_LENGTH){
            number = number.substring(numberLen - DEFAULT_NUMBER_LENGTH, numberLen);
        }
        return number;
    }
}
