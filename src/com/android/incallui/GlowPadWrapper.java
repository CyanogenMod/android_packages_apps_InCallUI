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

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.telecom.VideoProfile;
import android.telecom.TelecomManager;
import android.util.AttributeSet;
import android.view.View;

import com.android.incallui.widget.multiwaveview.GlowPadView;

/**
 *
 */
public class GlowPadWrapper extends GlowPadView implements GlowPadView.OnTriggerListener {

    // Parameters for the GlowPadView "ping" animation; see triggerPing().
    private static final int PING_MESSAGE_WHAT = 101;
    private static final boolean ENABLE_PING_AUTO_REPEAT = true;
    private static final long PING_REPEAT_DELAY_MS = 1200;

    private final Handler mPingHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case PING_MESSAGE_WHAT:
                    triggerPing();
                    break;
            }
        }
    };

    private AnswerListener mAnswerListener;
    private boolean mPingEnabled = true;
    private boolean mTargetTriggered = false;
    private int mVideoState = VideoProfile.STATE_BIDIRECTIONAL;

    public GlowPadWrapper(Context context) {
        super(context);
        Log.d(this, "class created " + this + " ");
    }

    public GlowPadWrapper(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.d(this, "class created " + this);
    }

    @Override
    protected void onFinishInflate() {
        Log.d(this, "onFinishInflate()");
        super.onFinishInflate();
        setOnTriggerListener(this);
    }

    public void startPing() {
        Log.d(this, "startPing");
        mPingEnabled = true;
        triggerPing();
    }

    public void stopPing() {
        Log.d(this, "stopPing");
        mPingEnabled = false;
        mPingHandler.removeMessages(PING_MESSAGE_WHAT);
    }

    private void triggerPing() {
        Log.d(this, "triggerPing(): " + mPingEnabled + " " + this);
        if (mPingEnabled && !mPingHandler.hasMessages(PING_MESSAGE_WHAT)) {
            ping();

            if (ENABLE_PING_AUTO_REPEAT) {
                mPingHandler.sendEmptyMessageDelayed(PING_MESSAGE_WHAT, PING_REPEAT_DELAY_MS);
            }
        }
    }

    @Override
    public void onGrabbed(View v, int handle) {
        Log.d(this, "onGrabbed()");
        stopPing();
    }

    @Override
    public void onReleased(View v, int handle) {
        Log.d(this, "onReleased()");
        if (mTargetTriggered) {
            mTargetTriggered = false;
        } else {
            startPing();
        }
    }

    @Override
    public void onTrigger(View v, int target) {
        Log.d(this, "onTrigger() view=" + v + " target=" + target);
        final int resId = getResourceIdForTarget(target);
        switch (resId) {
            case R.drawable.ic_lockscreen_answer:
                mAnswerListener.onAnswer(VideoProfile.STATE_AUDIO_ONLY, getContext());
                mTargetTriggered = true;
                break;
            case R.drawable.ic_lockscreen_decline:
                mAnswerListener.onDecline(getContext());
                mTargetTriggered = true;
                break;
            case R.drawable.ic_lockscreen_text:
                mAnswerListener.onText();
                mTargetTriggered = true;
                break;
            case R.drawable.ic_lockscreen_block:
                mAnswerListener.onBlock(getContext());
                mTargetTriggered = true;
                break;
            case R.drawable.ic_videocam:
            case R.drawable.ic_lockscreen_answer_video:
                mAnswerListener.onAnswer(mVideoState, getContext());
                mTargetTriggered = true;
                break;
            case R.drawable.ic_lockscreen_decline_video:
                mAnswerListener.onDeclineUpgradeRequest(getContext());
                mTargetTriggered = true;
                break;
            case R.drawable.qti_ic_lockscreen_answer_tx_video:
                mAnswerListener.onAnswer(VideoProfile.STATE_TX_ENABLED, getContext());
                mTargetTriggered = true;
                break;
            case R.drawable.qti_ic_lockscreen_answer_rx_video:
                mAnswerListener.onAnswer(VideoProfile.STATE_RX_ENABLED, getContext());
                mTargetTriggered = true;
                break;
            case R.drawable.qti_ic_lockscreen_deflect:
                mAnswerListener.onDeflect(getContext());
                mTargetTriggered = true;
                break;
            case R.drawable.ic_lockscreen_answer_hold_current:
                mAnswerListener.onAnswer(VideoProfile.STATE_AUDIO_ONLY, getContext(),
                        TelecomManager.CALL_WAITING_RESPONSE_NO_POPUP_HOLD_CALL);
                mTargetTriggered = true;
                break;
            case R.drawable.ic_lockscreen_answer_end_current:
                mAnswerListener.onAnswer(VideoProfile.STATE_AUDIO_ONLY, getContext(),
                        TelecomManager.CALL_WAITING_RESPONSE_NO_POPUP_END_CALL);
                mTargetTriggered = true;
                break;
            default:
                // Code should never reach here.
                Log.e(this, "Trigger detected on unhandled resource. Skipping.");
        }
    }

    @Override
    public void onGrabbedStateChange(View v, int handle) {

    }

    @Override
    public void onFinishFinalAnimation() {

    }

    public void setAnswerListener(AnswerListener listener) {
        mAnswerListener = listener;
    }

    /**
     * Sets the video state represented by the "video" icon on the glow pad.
     *
     * @param videoState The new video state.
     */
    public void setVideoState(int videoState) {
        mVideoState = videoState;
    }

    public interface AnswerListener {
        void onAnswer(int videoState, Context context);
        void onAnswer(int videoState, Context context, int callWaitingResponseType);
        void onDecline(Context context);
        void onDeclineUpgradeRequest(Context context);
        void onText();
        void onDeflect(Context context);
        void onBlock(Context context);
    }
}
