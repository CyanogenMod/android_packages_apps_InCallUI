/*
 * Copyright (C) 2014 The CyanogenMod Project
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
 * limitations under the License.
 */

package com.android.incallui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import com.android.services.telephony.common.Call;

public class CallRecordingButton extends ImageButton
        implements CallRecorder.RecordingProgressListener, View.OnClickListener {

    public CallRecordingButton(Context context) {
        super(context);
    }

    public CallRecordingButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CallRecordingButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onClick(View v) {
        // toggle recording depending on button state
        final CallRecorder recorder = CallRecorder.getInstance();
        if (recorder.isRecording()) {
            recorder.finishRecording();
        } else {
            Call call = CallList.getInstance().getActiveCall();
            // can't start recording with no active call
            if (call != null) {
                recorder.startRecording(call.getNumber(), call.getCreateTime());
            }
        }
        updateDrawable();
    }

    @Override
    public void onStartRecording() {
        updateDrawable();
    }

    @Override
    public void onStopRecording() {
        updateDrawable();
    }

    @Override
    public void onRecordingTimeProgress(final long elapsedTimeMs) {
        // no-op
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        CallRecorder.getInstance().addRecordingProgressListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        CallRecorder.getInstance().removeRecordingProgressListener(this);
    }

    private void updateDrawable() {
        boolean recording = CallRecorder.getInstance().isRecording();
        setImageResource(recording
                ? R.drawable.ic_record_stop_holo_dark : R.drawable.ic_record_holo_dark);
    }
}
