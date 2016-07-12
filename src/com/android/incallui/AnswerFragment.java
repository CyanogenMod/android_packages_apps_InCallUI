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
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.android.contacts.common.activity.fragment.BlockContactDialogFragment;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class AnswerFragment extends BaseFragment<AnswerPresenter, AnswerPresenter.AnswerUi>
        implements GlowPadWrapper.AnswerListener, AnswerPresenter.AnswerUi,
        BlockContactDialogFragment.Callbacks {

    public static final int TARGET_SET_FOR_AUDIO_WITHOUT_SMS_AND_BLOCK = 0;
    public static final int TARGET_SET_FOR_AUDIO_WITHOUT_SMS_WITH_BLOCK = 1;
    public static final int TARGET_SET_FOR_AUDIO_WITH_SMS_WITHOUT_BLOCK = 2;
    public static final int TARGET_SET_FOR_AUDIO_WITH_SMS_AND_BLOCK = 3;
    public static final int TARGET_SET_FOR_VIDEO_WITHOUT_SMS_AND_BLOCK = 4;
    public static final int TARGET_SET_FOR_VIDEO_WITHOUT_SMS_WITH_BLOCK = 5;
    public static final int TARGET_SET_FOR_VIDEO_WITH_SMS = 6;
    public static final int TARGET_SET_FOR_VIDEO_ACCEPT_REJECT_REQUEST = 7;
    public static final int TARGET_SET_FOR_AUDIO_WITHOUT_SMS_WITH_CALL_WAITING = 8;
    public static final int TARGET_SET_FOR_AUDIO_WITH_SMS_AND_CALL_WAITING = 9;


    public static final int TARGET_SET_FOR_QTI_VIDEO_WITHOUT_SMS = 1000;
    public static final int TARGET_SET_FOR_QTI_VIDEO_WITH_SMS = 1001;
    public static final int TARGET_SET_FOR_QTI_VIDEO_ACCEPT_REJECT_REQUEST = 1003;
    public static final int TARGET_SET_FOR_QTI_BIDIRECTIONAL_VIDEO_ACCEPT_REJECT_REQUEST = 1004;
    public static final int TARGET_SET_FOR_QTI_VIDEO_TRANSMIT_ACCEPT_REJECT_REQUEST = 1005;
    public static final int TARGET_SET_FOR_QTI_VIDEO_RECEIVE_ACCEPT_REJECT_REQUEST = 1006;
    public static final int TARGET_SET_FOR_QTI_AUDIO_WITHOUT_SMS = 1007;
    public static final int TARGET_SET_FOR_QTI_AUDIO_WITH_SMS = 1008;

    private static final class TargetResources {
        int targetResourceId;
        int targetDescriptionsResourceId;
        int directionDescriptionsResourceId;
        int targetDisplayTextResourceId;
        int handleDrawableResourceId;

        public TargetResources(int target, int descs, int directionDescs, int displayText, int
                handle) {
            targetResourceId = target;
            targetDescriptionsResourceId = descs;
            directionDescriptionsResourceId = directionDescs;
            targetDisplayTextResourceId = displayText;
            handleDrawableResourceId = handle;
        }
    }

    private static final SparseArray<TargetResources> RESOURCE_LOOKUP = new SparseArray<>();
    static {
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_AUDIO_WITHOUT_SMS_AND_BLOCK, new TargetResources(
                R.array.incoming_call_widget_audio_without_sms_and_block_targets,
                R.array.incoming_call_widget_audio_without_sms_and_block_target_descriptions,
                R.array.incoming_call_widget_audio_without_sms_and_block_direction_descriptions,
                R.array.incoming_call_widget_audio_without_sms_and_block_display_text,
                R.drawable.ic_incall_audio_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_AUDIO_WITHOUT_SMS_WITH_BLOCK, new TargetResources(
                R.array.incoming_call_widget_audio_without_sms_with_block_targets,
                R.array.incoming_call_widget_audio_without_sms_with_block_target_descriptions,
                R.array.incoming_call_widget_audio_without_sms_with_block_direction_descriptions,
                R.array.incoming_call_widget_audio_without_sms_with_block_display_text,
                R.drawable.ic_incall_audio_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_AUDIO_WITH_SMS_WITHOUT_BLOCK, new TargetResources(
                R.array.incoming_call_widget_audio_with_sms_without_block_targets,
                R.array.incoming_call_widget_audio_with_sms_without_block_target_descriptions,
                R.array.incoming_call_widget_audio_with_sms_without_block_direction_descriptions,
                R.array.incoming_call_widget_audio_with_sms_without_block_display_text,
                R.drawable.ic_incall_audio_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_AUDIO_WITH_SMS_AND_BLOCK, new TargetResources(
                R.array.incoming_call_widget_audio_with_sms_and_block_targets,
                R.array.incoming_call_widget_audio_with_sms_and_block_target_descriptions,
                R.array.incoming_call_widget_audio_with_sms_and_block_direction_descriptions,
                R.array.incoming_call_widget_audio_with_sms_and_block_display_text,
                R.drawable.ic_incall_audio_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_AUDIO_WITHOUT_SMS_WITH_CALL_WAITING, new TargetResources(
                R.array.incoming_call_widget_audio_without_sms_with_call_waiting_targets,
                R.array
                .incoming_call_widget_audio_without_sms_with_call_waiting_target_descriptions,
                R.array
                .incoming_call_widget_audio_without_sms_with_call_waiting_direction_descriptions,
                R.array.incoming_call_widget_audio_without_sms_with_call_waiting_display_text,
                R.drawable.ic_incall_audio_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_AUDIO_WITH_SMS_AND_CALL_WAITING, new TargetResources(
                R.array.incoming_call_widget_audio_with_sms_and_call_waiting_targets,
                R.array.incoming_call_widget_audio_with_sms_and_call_waiting_target_descriptions,
                R.array.incoming_call_widget_audio_with_sms_and_call_waiting_direction_descriptions,
                R.array.incoming_call_widget_audio_with_sms_and_call_waiting_display_text,
                R.drawable.ic_incall_audio_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_VIDEO_WITHOUT_SMS_AND_BLOCK, new TargetResources(
                R.array.incoming_call_widget_video_without_sms_and_block_targets,
                R.array.incoming_call_widget_video_without_sms_and_block_target_descriptions,
                R.array.incoming_call_widget_video_without_sms_and_block_direction_descriptions,
                R.array.incoming_call_widget_default_target_display_text,
                R.drawable.ic_incall_video_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_VIDEO_WITHOUT_SMS_WITH_BLOCK, new TargetResources(
                R.array.incoming_call_widget_video_without_sms_targets,
                R.array.incoming_call_widget_video_without_sms_target_descriptions,
                R.array.incoming_call_widget_video_without_sms_direction_descriptions,
                R.array.incoming_call_widget_default_target_display_text,
                R.drawable.ic_incall_video_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_VIDEO_WITH_SMS, new TargetResources(
                R.array.incoming_call_widget_video_with_sms_targets,
                R.array.incoming_call_widget_video_with_sms_target_descriptions,
                R.array.incoming_call_widget_video_with_sms_direction_descriptions,
                R.array.incoming_call_widget_default_target_display_text,
                R.drawable.ic_incall_video_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_VIDEO_ACCEPT_REJECT_REQUEST, new TargetResources(
                R.array.incoming_call_widget_video_request_targets,
                R.array.incoming_call_widget_video_request_target_descriptions,
                R.array.incoming_call_widget_video_request_target_direction_descriptions,
                R.array.incoming_call_widget_default_target_display_text,
                R.drawable.ic_incall_video_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_QTI_VIDEO_WITHOUT_SMS, new TargetResources(
                R.array.qti_incoming_call_widget_video_without_sms_targets,
                R.array.qti_incoming_call_widget_video_without_sms_target_descriptions,
                R.array.qti_incoming_call_widget_video_without_sms_direction_descriptions,
                R.array.incoming_call_widget_default_target_display_text,
                R.drawable.ic_incall_video_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_QTI_VIDEO_WITH_SMS, new TargetResources(
                R.array.qti_incoming_call_widget_video_with_sms_targets,
                R.array.qti_incoming_call_widget_video_with_sms_target_descriptions,
                R.array.qti_incoming_call_widget_video_with_sms_direction_descriptions,
                R.array.incoming_call_widget_default_target_display_text,
                R.drawable.ic_incall_video_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_QTI_VIDEO_ACCEPT_REJECT_REQUEST, new TargetResources(
                R.array.qti_incoming_call_widget_video_request_targets,
                R.array.qti_incoming_call_widget_video_request_target_descriptions,
                R.array.qti_incoming_call_widget_video_request_target_direction_descriptions,
                R.array.incoming_call_widget_default_target_display_text,
                R.drawable.ic_incall_video_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_QTI_BIDIRECTIONAL_VIDEO_ACCEPT_REJECT_REQUEST,
                new TargetResources(
                R.array.qti_incoming_call_widget_bidirectional_video_accept_reject_request_targets,
                R.array.qti_incoming_call_widget_video_request_target_descriptions,
                R.array.qti_incoming_call_widget_video_request_target_direction_descriptions,
                R.array.incoming_call_widget_default_target_display_text,
                R.drawable.ic_incall_video_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_QTI_VIDEO_TRANSMIT_ACCEPT_REJECT_REQUEST,
                new TargetResources(
                R.array.qti_incoming_call_widget_video_transmit_accept_reject_request_targets,
                R.array.qti_incoming_call_widget_video_transmit_request_target_descriptions,
                R.array.qti_incoming_call_widget_video_request_target_direction_descriptions,
                R.array.incoming_call_widget_default_target_display_text,
                R.drawable.ic_incall_video_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_QTI_VIDEO_RECEIVE_ACCEPT_REJECT_REQUEST,
                new TargetResources(
                R.array.qti_incoming_call_widget_video_receive_accept_reject_request_targets,
                R.array.qti_incoming_call_widget_video_receive_request_target_descriptions,
                R.array.qti_incoming_call_widget_video_request_target_direction_descriptions,
                R.array.incoming_call_widget_default_target_display_text,
                R.drawable.ic_incall_video_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_QTI_AUDIO_WITH_SMS, new TargetResources(
                R.array.qti_incoming_call_widget_audio_with_sms_targets,
                R.array.qti_incoming_call_widget_audio_with_sms_target_descriptions,
                R.array.qti_incoming_call_widget_audio_with_sms_direction_descriptions,
                R.array.incoming_call_widget_default_target_display_text,
                R.drawable.ic_incall_audio_handle
        ));
        RESOURCE_LOOKUP.put(TARGET_SET_FOR_QTI_AUDIO_WITHOUT_SMS, new TargetResources(
                R.array.qti_incoming_call_widget_audio_without_sms_targets,
                R.array.qti_incoming_call_widget_audio_without_sms_target_descriptions,
                R.array.qti_incoming_call_widget_audio_without_sms_direction_descriptions,
                R.array.incoming_call_widget_default_target_display_text,
                R.drawable.ic_incall_audio_handle
        ));
    }

    /**
     * The popup showing the list of canned responses.
     *
     * This is an AlertDialog containing a ListView showing the possible choices.  This may be null
     * if the InCallScreen hasn't ever called showRespondViaSmsPopup() yet, or if the popup was
     * visible once but then got dismissed.
     */
    private Dialog mCannedResponsePopup = null;

    /**
     * The popup showing a text field for users to type in their custom message.
     */
    private AlertDialog mCustomMessagePopup = null;

    private ArrayAdapter<String> mSmsResponsesAdapter;

    private final List<String> mSmsResponses = new ArrayList<>();

    private GlowPadWrapper mGlowpad;

    public AnswerFragment() {
    }

    @Override
    public AnswerPresenter createPresenter() {
        return InCallPresenter.getInstance().getAnswerPresenter();
    }

    @Override
    public AnswerPresenter.AnswerUi getUi() {
        return this;
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mGlowpad = (GlowPadWrapper) inflater.inflate(R.layout.answer_fragment,
                container, false);

        Log.d(this, "Creating view for answer fragment ", this);
        Log.d(this, "Created from activity", getActivity());
        mGlowpad.setAnswerListener(this);

        return mGlowpad;
    }

    @Override
    public void onDestroyView() {
        Log.d(this, "onDestroyView");
        if (mGlowpad != null) {
            mGlowpad.stopPing();
            mGlowpad = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onShowAnswerUi(boolean shown) {
        Log.d(this, "Show answer UI: " + shown);
        if (shown) {
            mGlowpad.startPing();
        } else {
            mGlowpad.stopPing();
        }
    }

    /**
     * Sets targets on the glowpad according to target set identified by the parameter.
     * @param targetSet Integer identifying the set of targets to use.
     */
    public void showTargets(int targetSet) {
        showTargets(targetSet, VideoProfile.STATE_BIDIRECTIONAL);
    }

    /**
     * Sets targets on the glowpad according to target set identified by the parameter.
     * @param targetSet Integer identifying the set of targets to use.
     */
    @Override
    public void showTargets(int targetSet, int videoState) {
        mGlowpad.setVideoState(videoState);

        if (RESOURCE_LOOKUP.indexOfKey(targetSet) < 0) {
            targetSet = TARGET_SET_FOR_AUDIO_WITHOUT_SMS_AND_BLOCK;
        }
        final TargetResources res = RESOURCE_LOOKUP.get(targetSet);

        if (res.targetResourceId != mGlowpad.getTargetResourceId()) {
            mGlowpad.setTargetResources(res.targetResourceId);
            mGlowpad.setTargetDescriptionsResourceId(res.targetDescriptionsResourceId);
            mGlowpad.setDirectionDescriptionsResourceId(res.directionDescriptionsResourceId);
            mGlowpad.setHandleDrawable(res.handleDrawableResourceId);
            mGlowpad.setTargetDisplayTextResourceId(res.targetDisplayTextResourceId);
            mGlowpad.reset(false);
        }
    }

    @Override
    public void showMessageDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        mSmsResponsesAdapter = new ArrayAdapter<>(builder.getContext(),
                android.R.layout.simple_list_item_1, android.R.id.text1, mSmsResponses);

        final ListView lv = new ListView(getActivity());
        lv.setAdapter(mSmsResponsesAdapter);
        lv.setOnItemClickListener(new RespondViaSmsItemClickListener());

        builder.setCancelable(true).setView(lv).setOnCancelListener(
                new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        if (mGlowpad != null) {
                            mGlowpad.startPing();
                            mGlowpad.reset(false);
                        }
                        dismissCannedResponsePopup();
                        getPresenter().onDismissDialog();
                    }
                });
        mCannedResponsePopup = builder.create();
        mCannedResponsePopup.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        mCannedResponsePopup.show();
    }

    private boolean isCannedResponsePopupShowing() {
        if (mCannedResponsePopup != null) {
            return mCannedResponsePopup.isShowing();
        }
        return false;
    }

    private boolean isCustomMessagePopupShowing() {
        if (mCustomMessagePopup != null) {
            return mCustomMessagePopup.isShowing();
        }
        return false;
    }

    /**
     * Dismiss the canned response list popup.
     *
     * This is safe to call even if the popup is already dismissed, and even if you never called
     * showRespondViaSmsPopup() in the first place.
     */
    private void dismissCannedResponsePopup() {
        if (mCannedResponsePopup != null) {
            mCannedResponsePopup.dismiss();  // safe even if already dismissed
            mCannedResponsePopup = null;
        }
    }

    /**
     * Dismiss the custom compose message popup.
     */
    private void dismissCustomMessagePopup() {
       if (mCustomMessagePopup != null) {
           mCustomMessagePopup.dismiss();
           mCustomMessagePopup = null;
           if (mGlowpad != null) {
               mGlowpad.reset(false);
           }
       }
    }

    public void dismissPendingDialogs() {
        if (isCannedResponsePopupShowing()) {
            dismissCannedResponsePopup();
        }

        if (isCustomMessagePopupShowing()) {
            dismissCustomMessagePopup();
        }
    }

    public boolean hasPendingDialogs() {
        return !(mCannedResponsePopup == null && mCustomMessagePopup == null);
    }

    /**
     * Shows the custom message entry dialog.
     */
    public void showCustomMessageDialog() {
        // Create an alert dialog containing an EditText
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final EditText et = new EditText(builder.getContext());
        builder.setCancelable(true).setView(et)
                .setPositiveButton(R.string.custom_message_send,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // The order is arranged in a way that the popup will be destroyed when the
                        // InCallActivity is about to finish.
                        final String textMessage = et.getText().toString().trim();
                        dismissCustomMessagePopup();
                        getPresenter().rejectCallWithMessage(textMessage);
                    }
                })
                .setNegativeButton(R.string.custom_message_cancel,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismissCustomMessagePopup();
                        getPresenter().onDismissDialog();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        dismissCustomMessagePopup();
                        getPresenter().onDismissDialog();
                    }
                })
                .setTitle(R.string.respond_via_sms_custom_message);
        mCustomMessagePopup = builder.create();

        // Enable/disable the send button based on whether there is a message in the EditText
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                final Button sendButton = mCustomMessagePopup.getButton(
                        DialogInterface.BUTTON_POSITIVE);
                sendButton.setEnabled(s != null && s.toString().trim().length() != 0);
            }
        });

        // Keyboard up, show the dialog
        mCustomMessagePopup.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        mCustomMessagePopup.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        mCustomMessagePopup.show();

        // Send button starts out disabled
        final Button sendButton = mCustomMessagePopup.getButton(DialogInterface.BUTTON_POSITIVE);
        sendButton.setEnabled(false);
    }

    @Override
    public void configureMessageDialog(List<String> textResponses) {
        mSmsResponses.clear();
        mSmsResponses.addAll(textResponses);
        mSmsResponses.add(getResources().getString(
                R.string.respond_via_sms_custom_message));
        if (mSmsResponsesAdapter != null) {
            mSmsResponsesAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public void onAnswer(int videoState, Context context) {
        Log.d(this, "onAnswer videoState=" + videoState + " context=" + context);
        getPresenter().onAnswer(videoState, context, TelecomManager
                .CALL_WAITING_RESPONSE_NO_POPUP_END_CALL);
    }

    @Override
    public void onAnswer(int videoState, Context context, int callWaitingResponseType) {
        Log.d(this, "onAnswer videoState=" + videoState + " context=" + context);
        getPresenter().onAnswer(videoState, context, callWaitingResponseType);
    }

    @Override
    public void onDecline(Context context) {
        getPresenter().onDecline(context);
    }

    @Override
    public void onDeclineUpgradeRequest(Context context) {
        InCallPresenter.getInstance().declineUpgradeRequest(context);
    }

    @Override
    public void onText() {
        getPresenter().onText();
    }

    @Override
    public void onDeflect(Context context) {
        getPresenter().onDeflect(context);
    }

    @Override
    public void onBlock(Context context) {
        if (!getPresenter().isBlockingEnabled()) {
            // shouldn't happen
            return;
        }

        getPresenter().onBlockDialogInitialize();
        BlockContactDialogFragment bcdf = BlockContactDialogFragment.create(
                BlockContactDialogFragment.BLOCK_MODE,
                getPresenter().getLookupProviderName(),
                this);
        bcdf.show(getFragmentManager(), "block_contact_dialog");
    }

    @Override
    public void onBlockCancelled() {
        if (mGlowpad != null) {
            mGlowpad.reset(false);
        }
    }

    @Override
    public void onBlockSelected(boolean notifyLookupProvider) {
        getPresenter().onBlock(notifyLookupProvider);
    }

    @Override
    public void onUnblockSelected(boolean notifyLookupProvider) {
        /* Not used in this context */
    }

    /**
     * OnItemClickListener for the "Respond via SMS" popup.
     */
    public class RespondViaSmsItemClickListener implements AdapterView.OnItemClickListener {

        /**
         * Handles the user selecting an item from the popup.
         */
        @Override
        public void onItemClick(AdapterView<?> parent,  // The ListView
                View view,  // The TextView that was clicked
                int position, long id) {
            Log.d(this, "RespondViaSmsItemClickListener.onItemClick(" + position + ")...");
            final String message = (String) parent.getItemAtPosition(position);
            Log.v(this, "- message: '" + message + "'");
            dismissCannedResponsePopup();

            // The "Custom" choice is a special case.
            // (For now, it's guaranteed to be the last item.)
            if (position == (parent.getCount() - 1)) {
                // Show the custom message dialog
                showCustomMessageDialog();
            } else {
                getPresenter().rejectCallWithMessage(message);
            }
        }
    }
}
