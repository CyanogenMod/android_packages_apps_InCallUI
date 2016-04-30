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

import android.annotation.NonNull;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.util.SparseIntArray;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnDismissListener;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;

import com.android.incallui.incallapi.InCallPluginInfo;

import java.lang.Override;
import java.util.ArrayList;
import java.util.List;

import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;

/**
 * Fragment for call control buttons
 */
public class CallButtonFragment
        extends BaseFragment<CallButtonPresenter, CallButtonPresenter.CallButtonUi>
        implements CallButtonPresenter.CallButtonUi, OnMenuItemClickListener, OnDismissListener,
        View.OnClickListener {
    private static final String TAG = CallButtonFragment.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int INVALID_INDEX = -1;
    private int mButtonMaxVisible;
    // The button is currently visible in the UI
    private static final int BUTTON_VISIBLE = 1;
    // The button is hidden in the UI
    private static final int BUTTON_HIDDEN = 2;
    // The button has been collapsed into the overflow menu
    private static final int BUTTON_MENU = 3;

    private static final int REQUEST_CODE_CALL_RECORD_PERMISSION = 1000;

    public interface Buttons {
        public static final int BUTTON_AUDIO = 0;
        public static final int BUTTON_MUTE = 1;
        public static final int BUTTON_DIALPAD = 2;
        public static final int BUTTON_UPGRADE_TO_VIDEO = 3;
        public static final int BUTTON_HOLD = 4;
        public static final int BUTTON_SWAP = 5;
        public static final int BUTTON_SWITCH_CAMERA = 6;
        public static final int BUTTON_ADD_CALL = 7;
        public static final int BUTTON_MERGE = 8;
        public static final int BUTTON_PAUSE_VIDEO = 9;
        public static final int BUTTON_MANAGE_VIDEO_CONFERENCE = 10;
        public static final int BUTTON_RECORD_CALL = 11;
        public static final int BUTTON_TRANSFER_CALL = 12;
        public static final int BUTTON_TAKE_NOTE = 13;
        public static final int BUTTON_COUNT = 14;
    }

    private SparseIntArray mButtonVisibilityMap = new SparseIntArray(BUTTON_COUNT);

    private CompoundButton mAudioButton;
    private CompoundButton mMuteButton;
    private CompoundButton mShowDialpadButton;
    private CompoundButton mHoldButton;
    private ImageButton mSwapButton;
    private ImageButton mChangeToVideoButton;
    private CompoundButton mSwitchCameraButton;
    private ImageButton mAddCallButton;
    private ImageButton mMergeButton;
    private CompoundButton mPauseVideoButton;
    private CompoundButton mCallRecordButton;
    private ImageButton mOverflowButton;
    private ImageButton mManageVideoCallConferenceButton;
    private ImageButton mAddParticipantButton;
    private ImageButton mTransferCallButton;
    private ImageButton mTakeNoteButton;

    private PopupMenu mAudioModePopup;
    private boolean mAudioModePopupVisible;
    private PopupMenu mOverflowPopup;

    private int mPrevAudioMode = 0;

    // Constants for Drawable.setAlpha()
    private static final int HIDDEN = 0;
    private static final int VISIBLE = 255;

    private boolean mIsEnabled;
    private MaterialPalette mCurrentThemeColors;

    @Override
    public CallButtonPresenter createPresenter() {
        // TODO: find a cleaner way to include audio mode provider than having a singleton instance.
        return new CallButtonPresenter();
    }

    @Override
    public CallButtonPresenter.CallButtonUi getUi() {
        return this;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        for (int i = 0; i < BUTTON_COUNT; i++) {
            mButtonVisibilityMap.put(i, BUTTON_HIDDEN);
        }

        mButtonMaxVisible = getResources().getInteger(R.integer.call_card_max_buttons);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View parent = inflater.inflate(R.layout.call_button_fragment, container, false);

        mAudioButton = (CompoundButton) parent.findViewById(R.id.audioButton);
        mAudioButton.setOnClickListener(this);
        mMuteButton = (CompoundButton) parent.findViewById(R.id.muteButton);
        mMuteButton.setOnClickListener(this);
        mShowDialpadButton = (CompoundButton) parent.findViewById(R.id.dialpadButton);
        mShowDialpadButton.setOnClickListener(this);
        mHoldButton = (CompoundButton) parent.findViewById(R.id.holdButton);
        mHoldButton.setOnClickListener(this);
        mSwapButton = (ImageButton) parent.findViewById(R.id.swapButton);
        mSwapButton.setOnClickListener(this);
        mChangeToVideoButton = (ImageButton) parent.findViewById(R.id.changeToVideoButton);
        mChangeToVideoButton.setOnClickListener(this);
        mSwitchCameraButton = (CompoundButton) parent.findViewById(R.id.switchCameraButton);
        mSwitchCameraButton.setOnClickListener(this);
        mAddCallButton = (ImageButton) parent.findViewById(R.id.addButton);
        mAddCallButton.setOnClickListener(this);
        mMergeButton = (ImageButton) parent.findViewById(R.id.mergeButton);
        mMergeButton.setOnClickListener(this);
        mPauseVideoButton = (CompoundButton) parent.findViewById(R.id.pauseVideoButton);
        mPauseVideoButton.setOnClickListener(this);
        mCallRecordButton = (CompoundButton) parent.findViewById(R.id.callRecordButton);
        mCallRecordButton.setOnClickListener(this);
        mAddParticipantButton = (ImageButton) parent.findViewById(R.id.addParticipant);
        mAddParticipantButton.setOnClickListener(this);
        mTransferCallButton = (ImageButton) parent.findViewById(R.id.transferCall);
        mTransferCallButton.setOnClickListener(this);
        mOverflowButton = (ImageButton) parent.findViewById(R.id.overflowButton);
        mOverflowButton.setOnClickListener(this);
        mManageVideoCallConferenceButton = (ImageButton) parent.findViewById(
                R.id.manageVideoCallConferenceButton);
        mManageVideoCallConferenceButton.setOnClickListener(this);
        mTakeNoteButton = (ImageButton) parent.findViewById(R.id.takeNoteButton);
        mTakeNoteButton.setOnClickListener(this);
        return parent;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // set the buttons
        updateAudioButtons(getPresenter().getSupportedAudio());
    }

    @Override
    public void onResume() {
        if (getPresenter() != null) {
            getPresenter().refreshMuteState();
        }
        super.onResume();

        updateColors();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        Log.d(this, "onClick(View " + view + ", id " + id + ")...");

        switch(id) {
            case R.id.audioButton:
                onAudioButtonClicked();
                break;
            case R.id.addButton:
                getPresenter().addCallClicked();
                break;
            case R.id.muteButton: {
                getPresenter().muteClicked(!mMuteButton.isSelected());
                break;
            }
            case R.id.mergeButton:
                getPresenter().mergeClicked();
                mMergeButton.setEnabled(false);
                break;
            case R.id.holdButton: {
                getPresenter().holdClicked(!mHoldButton.isSelected());
                break;
            }
            case R.id.swapButton:
                getPresenter().swapClicked();
                break;
            case R.id.dialpadButton:
                getPresenter().showDialpadClicked(!mShowDialpadButton.isSelected());
                break;
            case R.id.addParticipant:
                getPresenter().addParticipantClicked();
                break;
            case R.id.changeToVideoButton:
                getPresenter().switchToVideoCall();
                break;
            case R.id.switchCameraButton:
                getPresenter().switchCameraClicked(
                        mSwitchCameraButton.isSelected() /* useFrontFacingCamera */);
                break;
            case R.id.pauseVideoButton:
                getPresenter().pauseVideoClicked(
                        !mPauseVideoButton.isSelected() /* pause */);
                break;
            case R.id.callRecordButton:
                getPresenter().callRecordClicked(!mCallRecordButton.isSelected());
                break;
            case R.id.overflowButton:
                if (mOverflowPopup != null) {
                    mOverflowPopup.show();
                }
                break;
            case R.id.manageVideoCallConferenceButton:
                onManageVideoCallConferenceClicked();
                break;
            case R.id.transferCall:
                getPresenter().transferCallClicked();
                break;
            case R.id.takeNoteButton:
                getPresenter().takeNote();
            default:
                Log.wtf(this, "onClick: unexpected");
                return;
        }

        view.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    }

    public void updateColors() {
        MaterialPalette themeColors = InCallPresenter.getInstance().getThemeColors();

        if (mCurrentThemeColors != null && mCurrentThemeColors.equals(themeColors)) {
            return;
        }

        View[] compoundButtons = {
                mAudioButton,
                mMuteButton,
                mShowDialpadButton,
                mHoldButton,
                mSwitchCameraButton,
                mPauseVideoButton,
                mCallRecordButton
        };

        for (View button : compoundButtons) {
            final LayerDrawable layers = (LayerDrawable) button.getBackground();
            final RippleDrawable btnCompoundDrawable = compoundBackgroundDrawable(themeColors);
            layers.setDrawableByLayerId(R.id.compoundBackgroundItem, btnCompoundDrawable);
        }

        ImageButton[] normalButtons = {
            mSwapButton,
            mChangeToVideoButton,
            mAddCallButton,
            mMergeButton,
            mTransferCallButton,
            mOverflowButton
        };

        for (ImageButton button : normalButtons) {
            final LayerDrawable layers = (LayerDrawable) button.getBackground();
            final RippleDrawable btnDrawable = backgroundDrawable(themeColors);
            layers.setDrawableByLayerId(R.id.backgroundItem, btnDrawable);
        }

        mCurrentThemeColors = themeColors;
    }

    /**
     * Generate a RippleDrawable which will be the background for a compound button, i.e.
     * a button with pressed and unpressed states. The unpressed state will be the same color
     * as the rest of the call card, the pressed state will be the dark version of that color.
     */
    private RippleDrawable compoundBackgroundDrawable(MaterialPalette palette) {
        Resources res = getResources();
        ColorStateList rippleColor =
                ColorStateList.valueOf(res.getColor(R.color.incall_accent_color));

        StateListDrawable stateListDrawable = new StateListDrawable();
        addSelectedAndFocused(res, stateListDrawable);
        addFocused(res, stateListDrawable);
        addSelected(res, stateListDrawable, palette);
        addUnselected(res, stateListDrawable, palette);

        return new RippleDrawable(rippleColor, stateListDrawable, null);
    }

    /**
     * Generate a RippleDrawable which will be the background of a button to ensure it
     * is the same color as the rest of the call card.
     */
    private RippleDrawable backgroundDrawable(MaterialPalette palette) {
        Resources res = getResources();
        ColorStateList rippleColor =
                ColorStateList.valueOf(res.getColor(R.color.incall_accent_color));

        StateListDrawable stateListDrawable = new StateListDrawable();
        addFocused(res, stateListDrawable);
        addUnselected(res, stateListDrawable, palette);

        return new RippleDrawable(rippleColor, stateListDrawable, null);
    }

    // state_selected and state_focused
    private void addSelectedAndFocused(Resources res, StateListDrawable drawable) {
        int[] selectedAndFocused = {android.R.attr.state_selected, android.R.attr.state_focused};
        Drawable selectedAndFocusedDrawable = res.getDrawable(R.drawable.btn_selected_focused);
        drawable.addState(selectedAndFocused, selectedAndFocusedDrawable);
    }

    // state_focused
    private void addFocused(Resources res, StateListDrawable drawable) {
        int[] focused = {android.R.attr.state_focused};
        Drawable focusedDrawable = res.getDrawable(R.drawable.btn_unselected_focused);
        drawable.addState(focused, focusedDrawable);
    }

    // state_selected
    private void addSelected(Resources res, StateListDrawable drawable, MaterialPalette palette) {
        int[] selected = {android.R.attr.state_selected};
        LayerDrawable selectedDrawable = (LayerDrawable) res.getDrawable(R.drawable.btn_selected);
        ((GradientDrawable) selectedDrawable.getDrawable(0)).setColor(palette.mSecondaryColor);
        drawable.addState(selected, selectedDrawable);
    }

    // default
    private void addUnselected(Resources res, StateListDrawable drawable, MaterialPalette palette) {
        LayerDrawable unselectedDrawable =
                (LayerDrawable) res.getDrawable(R.drawable.btn_unselected);
        ((GradientDrawable) unselectedDrawable.getDrawable(0)).setColor(palette.mPrimaryColor);
        drawable.addState(new int[0], unselectedDrawable);
    }

    @Override
    public void setEnabled(boolean isEnabled) {
        mIsEnabled = isEnabled;

        mAudioButton.setEnabled(isEnabled);
        mMuteButton.setEnabled(isEnabled);
        mShowDialpadButton.setEnabled(isEnabled);
        mHoldButton.setEnabled(isEnabled);
        mSwapButton.setEnabled(isEnabled);
        mChangeToVideoButton.setEnabled(isEnabled);
        mSwitchCameraButton.setEnabled(isEnabled);
        mAddCallButton.setEnabled(isEnabled);
        mMergeButton.setEnabled(isEnabled);
        mPauseVideoButton.setEnabled(isEnabled);
        mCallRecordButton.setEnabled(isEnabled);
        mOverflowButton.setEnabled(isEnabled);
        mManageVideoCallConferenceButton.setEnabled(isEnabled);
        mAddParticipantButton.setEnabled(isEnabled);
        mTransferCallButton.setEnabled(isEnabled);
        mTakeNoteButton.setEnabled(isEnabled);
    }

    @Override
    public void showButton(int buttonId, boolean show) {
        mButtonVisibilityMap.put(buttonId, show ? BUTTON_VISIBLE : BUTTON_HIDDEN);
    }

    @Override
    public void enableButton(int buttonId, boolean enable) {
        final View button = getButtonById(buttonId);
        if (button != null) {
            button.setEnabled(enable);
        }
    }

    private View getButtonById(int id) {
        switch (id) {
            case BUTTON_AUDIO:
                return mAudioButton;
            case BUTTON_MUTE:
                return mMuteButton;
            case BUTTON_DIALPAD:
                return mShowDialpadButton;
            case BUTTON_HOLD:
                return mHoldButton;
            case BUTTON_SWAP:
                return mSwapButton;
            case BUTTON_UPGRADE_TO_VIDEO:
                return mChangeToVideoButton;
            case BUTTON_SWITCH_CAMERA:
                return mSwitchCameraButton;
            case BUTTON_ADD_CALL:
                return mAddCallButton;
            case BUTTON_MERGE:
                return mMergeButton;
            case BUTTON_PAUSE_VIDEO:
                return mPauseVideoButton;
            case BUTTON_MANAGE_VIDEO_CONFERENCE:
                return mManageVideoCallConferenceButton;
            case BUTTON_RECORD_CALL:
                return mCallRecordButton;
            case BUTTON_TRANSFER_CALL:
                return mTransferCallButton;
            case BUTTON_TAKE_NOTE:
                return mTakeNoteButton;
            default:
                Log.w(this, "Invalid button id");
                return null;
        }
    }

    @Override
    public void setHold(boolean value) {
        if (mHoldButton.isSelected() != value) {
            mHoldButton.setSelected(value);
            mHoldButton.setContentDescription(getContext().getString(
                    value ? R.string.onscreenHoldText_selected
                            : R.string.onscreenHoldText_unselected));
        }
    }

    @Override
    public void setCameraSwitched(boolean isBackFacingCamera) {
        mSwitchCameraButton.setSelected(isBackFacingCamera);
    }

    // The icons used for the call button fragment are constructed using layerdrawables,
    // where the second layer is a item containing a bitmap with it's gravity set to "center".
    // This method gets the insets set on the layerdrawable layer when the bitmap is centered,
    // and applies it to the vector drawable that we are replacing it with.
    public void modifyChangeToVideoButton() {
        boolean canVideoCall = getPresenter().canVideoCall();
        List<InCallPluginInfo> contactInCallPlugins =
                getPresenter().getContactInCallPluginInfoList();
        int listSize = (contactInCallPlugins != null) ? contactInCallPlugins.size() : 0;
        if (!canVideoCall && listSize == 1) {
            InCallPluginInfo info = contactInCallPlugins.get(0);
            if (info != null && info.getPluginVideoIcon() != null) {
                LayerDrawable layerDrawable =
                        (LayerDrawable) getResources().getDrawable(R.drawable.btn_change_to_video)
                                .mutate();

                int buttonWidth = mChangeToVideoButton.getWidth();
                int buttonHeight = mChangeToVideoButton.getWidth();
                if (buttonWidth == 0 || buttonHeight == 0) {
                    buttonWidth =
                            getResources().getDimensionPixelSize(R.dimen.in_call_button_dimension);
                    buttonHeight =
                            getResources().getDimensionPixelSize(R.dimen.in_call_button_dimension);
                }
                int xInset = buttonWidth - layerDrawable.getIntrinsicWidth();
                if (xInset > 0) {
                    xInset = xInset / 2;
                } else {
                    xInset = 0;
                }
                int yInset = buttonHeight - layerDrawable.getIntrinsicHeight();
                if (yInset > 0) {
                    yInset = yInset / 2;
                } else {
                    yInset = 0;
                }

                if (DEBUG) {
                    Log.i(TAG, "mChangeToVideoButton: [w h] [" + mChangeToVideoButton.getWidth() +
                            " " + mChangeToVideoButton.getHeight() + "]\n" +
                            "adjusted button: [w h] [" + buttonWidth + " " + buttonHeight + "]\n" +
                            "layerDrawable: [w h] [" + layerDrawable.getIntrinsicWidth() + " " +
                            layerDrawable.getIntrinsicHeight() + "]\n" +
                            "xInset = " + xInset + ", xInset = " + yInset);
                }

                Drawable icon = info.getPluginVideoIcon();
                icon.setTintList(getResources().getColorStateList(R.color.selectable_icon_tint));
                icon.setAutoMirrored(false);

                // layer 0 is background, layer 1 is the icon to use.
                layerDrawable.setLayerInset(1, xInset, yInset, xInset, yInset);
                layerDrawable.setDrawableByLayerId(R.id.foregroundItem, icon);
                mChangeToVideoButton.setBackground(layerDrawable);
            }
        }
    }

    @Override
    public void setVideoPaused(boolean isPaused) {
        mPauseVideoButton.setSelected(isPaused);
    }

    public void enableAddParticipant(boolean show) {
        mAddParticipantButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setMute(boolean value) {
        if (mMuteButton.isSelected() != value) {
            mMuteButton.setSelected(value);
        }
    }

    @Override
    public void setCallRecordingState(boolean isRecording) {
        mCallRecordButton.setSelected(isRecording);
        String description = getContext().getString(isRecording
                ? R.string.onscreenStopCallRecordText
                : R.string.onscreenCallRecordText);
        mCallRecordButton.setContentDescription(description);
        if (mOverflowPopup != null) {
            MenuItem item = mOverflowPopup.getMenu().findItem(BUTTON_RECORD_CALL);
            if (item != null) {
                item.setTitle(description);
            }
        }
    }

    private void addToOverflowMenu(int id, View button, PopupMenu menu) {
        button.setVisibility(View.GONE);
        menu.getMenu().add(Menu.NONE, id, Menu.NONE, button.getContentDescription());
        mButtonVisibilityMap.put(id, BUTTON_MENU);
    }

    private PopupMenu getPopupMenu() {
        return new PopupMenu(new ContextThemeWrapper(getActivity(), R.style.InCallPopupMenuStyle),
                mOverflowButton);
    }

    /**
     * Iterates through the list of buttons and toggles their visibility depending on the
     * setting configured by the CallButtonPresenter. If there are more visible buttons than
     * the allowed maximum, the excess buttons are collapsed into a single overflow menu.
     */
    @Override
    public void updateButtonStates() {
        View prevVisibleButton = null;
        int prevVisibleId = -1;
        PopupMenu menu = null;
        int visibleCount = 0;
        for (int i = 0; i < BUTTON_COUNT; i++) {
            final int visibility = mButtonVisibilityMap.get(i);
            final View button = getButtonById(i);
            if (visibility == BUTTON_VISIBLE) {
                visibleCount++;
                if (visibleCount <= mButtonMaxVisible) {
                    button.setVisibility(View.VISIBLE);
                    prevVisibleButton = button;
                    prevVisibleId = i;
                } else {
                    if (menu == null) {
                        menu = getPopupMenu();
                    }
                    // Collapse the current button into the overflow menu. If is the first visible
                    // button that exceeds the threshold, also collapse the previous visible button
                    // so that the total number of visible buttons will never exceed the threshold.
                    if (prevVisibleButton != null) {
                        addToOverflowMenu(prevVisibleId, prevVisibleButton, menu);
                        prevVisibleButton = null;
                        prevVisibleId = -1;
                    }
                    addToOverflowMenu(i, button, menu);
                }
            } else if (visibility == BUTTON_HIDDEN){
                button.setVisibility(View.GONE);
            }
        }

        mOverflowButton.setVisibility(menu != null ? View.VISIBLE : View.GONE);
        if (menu != null) {
            mOverflowPopup = menu;
            mOverflowPopup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    final int id = item.getItemId();
                    getButtonById(id).performClick();
                    return true;
                }
            });
        }
    }

    /**The function is called when Video Call button gets pressed. The function creates and
     * displays video call options.
     */
    @Override
    public void displayVideoCallOptions() {
        CallButtonPresenter.CallButtonUi ui = getUi();
        if (ui == null) {
            Log.e(this, "Cannot display VideoCallOptions as ui is null");
            return;
        }

        Context context = getContext();

        final ArrayList<Drawable> icons = new ArrayList<Drawable>();
        final ArrayList<String> items = new ArrayList<String>();
        final ArrayList<Integer> itemToCallType = new ArrayList<Integer>();
        final Resources res = ui.getContext().getResources();

        // Prepare the string array and mapping.
        List<InCallPluginInfo> contactInCallPlugins =
                getPresenter().getContactInCallPluginInfoList();
        if (contactInCallPlugins != null && !contactInCallPlugins.isEmpty()) {
            int i = 0;
            for (InCallPluginInfo info : contactInCallPlugins) {
                items.add(info.getPluginTitle());
                icons.add(info.getPluginBrandIcon());
                itemToCallType.add(i++);
            }
        }

        boolean canVideoCall = getPresenter().canVideoCall();
        if (canVideoCall) {
            // First item, if available is VT IMS call
            items.add(res.getString(R.string.modify_call_option_vt));
            Drawable icon = res.getDrawable(R.drawable.ic_toolbar_video);
            icon.setTint(res.getColor(R.color.vidoecall_handoff_default_video_call_color));
            icons.add(icon);
            itemToCallType.add(-1);
        }

        ListAdapter adapter = new ListItemWithImageArrayAdapter(context.getApplicationContext(),
                R.layout.videocall_handoff_item, items, icons);
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                final int selCallType = itemToCallType.get(item);
                if (selCallType < 0) {
                    // VT Call selected
                    getPresenter().changeToVideoClicked();
                } else {
                    //  InCall Plugin selected
                    getPresenter().handoverCallToVoIPPlugin(selCallType);
                }
                dialog.dismiss();
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getUi().getContext());
        builder.setTitle(R.string.video_call_option_title);
        builder.setAdapter(adapter, listener);
        final AlertDialog alert;
        alert = builder.create();
        alert.show();
    }

    @Override
    public void setAudio(int mode) {
        updateAudioButtons(getPresenter().getSupportedAudio());
        refreshAudioModePopup();

        if (mPrevAudioMode != mode) {
            updateAudioButtonContentDescription(mode);
            mPrevAudioMode = mode;
        }
    }

    @Override
    public void setSupportedAudio(int modeMask) {
        updateAudioButtons(modeMask);
        refreshAudioModePopup();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        Log.d(this, "- onMenuItemClick: " + item);
        Log.d(this, "  id: " + item.getItemId());
        Log.d(this, "  title: '" + item.getTitle() + "'");

        int mode = CallAudioState.ROUTE_WIRED_OR_EARPIECE;

        switch (item.getItemId()) {
            case R.id.audio_mode_speaker:
                mode = CallAudioState.ROUTE_SPEAKER;
                break;
            case R.id.audio_mode_earpiece:
            case R.id.audio_mode_wired_headset:
                // InCallCallAudioState.ROUTE_EARPIECE means either the handset earpiece,
                // or the wired headset (if connected.)
                mode = CallAudioState.ROUTE_WIRED_OR_EARPIECE;
                break;
            case R.id.audio_mode_bluetooth:
                mode = CallAudioState.ROUTE_BLUETOOTH;
                break;
            default:
                Log.e(this, "onMenuItemClick:  unexpected View ID " + item.getItemId()
                        + " (MenuItem = '" + item + "')");
                break;
        }

        getPresenter().setAudioMode(mode);

        return true;
    }

    // PopupMenu.OnDismissListener implementation; see showAudioModePopup().
    // This gets called when the PopupMenu gets dismissed for *any* reason, like
    // the user tapping outside its bounds, or pressing Back, or selecting one
    // of the menu items.
    @Override
    public void onDismiss(PopupMenu menu) {
        Log.d(this, "- onDismiss: " + menu);
        mAudioModePopupVisible = false;
        updateAudioButtons(getPresenter().getSupportedAudio());
    }

    /**
     * Checks for supporting modes.  If bluetooth is supported, it uses the audio
     * pop up menu.  Otherwise, it toggles the speakerphone.
     */
    private void onAudioButtonClicked() {
        Log.d(this, "onAudioButtonClicked: " +
                CallAudioState.audioRouteToString(getPresenter().getSupportedAudio()));

        if (isSupported(CallAudioState.ROUTE_BLUETOOTH)) {
            showAudioModePopup();
        } else {
            getPresenter().toggleSpeakerphone();
        }
    }

    private void onManageVideoCallConferenceClicked() {
        Log.d(this, "onManageVideoCallConferenceClicked");
        InCallPresenter.getInstance().showConferenceCallManager(true);
    }

    /**
     * Refreshes the "Audio mode" popup if it's visible.  This is useful
     * (for example) when a wired headset is plugged or unplugged,
     * since we need to switch back and forth between the "earpiece"
     * and "wired headset" items.
     *
     * This is safe to call even if the popup is already dismissed, or even if
     * you never called showAudioModePopup() in the first place.
     */
    public void refreshAudioModePopup() {
        if (mAudioModePopup != null && mAudioModePopupVisible) {
            // Dismiss the previous one
            mAudioModePopup.dismiss();  // safe even if already dismissed
            // And bring up a fresh PopupMenu
            showAudioModePopup();
        }
    }

    /**
     * Updates the audio button so that the appriopriate visual layers
     * are visible based on the supported audio formats.
     */
    private void updateAudioButtons(int supportedModes) {
        final boolean bluetoothSupported = isSupported(CallAudioState.ROUTE_BLUETOOTH);
        final boolean speakerSupported = isSupported(CallAudioState.ROUTE_SPEAKER);

        boolean audioButtonEnabled = false;
        boolean audioButtonChecked = false;
        boolean showMoreIndicator = false;

        boolean showBluetoothIcon = false;
        boolean showSpeakerphoneIcon = false;
        boolean showHandsetIcon = false;

        boolean showToggleIndicator = false;

        if (bluetoothSupported) {
            Log.d(this, "updateAudioButtons - popup menu mode");

            audioButtonEnabled = true;
            audioButtonChecked = true;
            showMoreIndicator = true;

            // Update desired layers:
            if (isAudio(CallAudioState.ROUTE_BLUETOOTH)) {
                showBluetoothIcon = true;
            } else if (isAudio(CallAudioState.ROUTE_SPEAKER)) {
                showSpeakerphoneIcon = true;
            } else {
                showHandsetIcon = true;
                // TODO: if a wired headset is plugged in, that takes precedence
                // over the handset earpiece.  If so, maybe we should show some
                // sort of "wired headset" icon here instead of the "handset
                // earpiece" icon.  (Still need an asset for that, though.)
            }

            // The audio button is NOT a toggle in this state, so set selected to false.
            mAudioButton.setSelected(false);
        } else if (speakerSupported) {
            Log.d(this, "updateAudioButtons - speaker toggle mode");

            audioButtonEnabled = true;

            // The audio button *is* a toggle in this state, and indicated the
            // current state of the speakerphone.
            audioButtonChecked = isAudio(CallAudioState.ROUTE_SPEAKER);
            mAudioButton.setSelected(audioButtonChecked);

            // update desired layers:
            showToggleIndicator = true;
            showSpeakerphoneIcon = true;
        } else {
            Log.d(this, "updateAudioButtons - disabled...");

            // The audio button is a toggle in this state, but that's mostly
            // irrelevant since it's always disabled and unchecked.
            audioButtonEnabled = false;
            audioButtonChecked = false;
            mAudioButton.setSelected(false);

            // update desired layers:
            showToggleIndicator = true;
            showSpeakerphoneIcon = true;
        }

        // Finally, update it all!

        Log.v(this, "audioButtonEnabled: " + audioButtonEnabled);
        Log.v(this, "audioButtonChecked: " + audioButtonChecked);
        Log.v(this, "showMoreIndicator: " + showMoreIndicator);
        Log.v(this, "showBluetoothIcon: " + showBluetoothIcon);
        Log.v(this, "showSpeakerphoneIcon: " + showSpeakerphoneIcon);
        Log.v(this, "showHandsetIcon: " + showHandsetIcon);

        // Only enable the audio button if the fragment is enabled.
        mAudioButton.setEnabled(audioButtonEnabled && mIsEnabled);
        mAudioButton.setChecked(audioButtonChecked);

        final LayerDrawable layers = (LayerDrawable) mAudioButton.getBackground();
        Log.d(this, "'layers' drawable: " + layers);

        layers.findDrawableByLayerId(R.id.compoundBackgroundItem)
                .setAlpha(showToggleIndicator ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.moreIndicatorItem)
                .setAlpha(showMoreIndicator ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.bluetoothItem)
                .setAlpha(showBluetoothIcon ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.handsetItem)
                .setAlpha(showHandsetIcon ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.speakerphoneItem)
                .setAlpha(showSpeakerphoneIcon ? VISIBLE : HIDDEN);

    }

    /**
     * Update the content description of the audio button.
     */
    private void updateAudioButtonContentDescription(int mode) {
        int stringId = 0;

        // If bluetooth is not supported, the audio buttion will toggle, so use the label "speaker".
        // Otherwise, use the label of the currently selected audio mode.
        if (!isSupported(CallAudioState.ROUTE_BLUETOOTH)) {
            stringId = R.string.audio_mode_speaker;
        } else {
            switch (mode) {
                case CallAudioState.ROUTE_EARPIECE:
                    stringId = R.string.audio_mode_earpiece;
                    break;
                case CallAudioState.ROUTE_BLUETOOTH:
                    stringId = R.string.audio_mode_bluetooth;
                    break;
                case CallAudioState.ROUTE_WIRED_HEADSET:
                    stringId = R.string.audio_mode_wired_headset;
                    break;
                case CallAudioState.ROUTE_SPEAKER:
                    stringId = R.string.audio_mode_speaker;
                    break;
            }
        }

        if (stringId != 0) {
            mAudioButton.setContentDescription(getResources().getString(stringId));
        }
    }

    private void showAudioModePopup() {
        Log.d(this, "showAudioPopup()...");

        final ContextThemeWrapper contextWrapper = new ContextThemeWrapper(getActivity(),
                R.style.InCallPopupMenuStyle);
        mAudioModePopup = new PopupMenu(contextWrapper, mAudioButton /* anchorView */);
        mAudioModePopup.getMenuInflater().inflate(R.menu.incall_audio_mode_menu,
                mAudioModePopup.getMenu());
        mAudioModePopup.setOnMenuItemClickListener(this);
        mAudioModePopup.setOnDismissListener(this);

        final Menu menu = mAudioModePopup.getMenu();

        // TODO: Still need to have the "currently active" audio mode come
        // up pre-selected (or focused?) with a blue highlight.  Still
        // need exact visual design, and possibly framework support for this.
        // See comments below for the exact logic.

        final MenuItem speakerItem = menu.findItem(R.id.audio_mode_speaker);
        speakerItem.setEnabled(isSupported(CallAudioState.ROUTE_SPEAKER));
        // TODO: Show speakerItem as initially "selected" if
        // speaker is on.

        // We display *either* "earpiece" or "wired headset", never both,
        // depending on whether a wired headset is physically plugged in.
        final MenuItem earpieceItem = menu.findItem(R.id.audio_mode_earpiece);
        final MenuItem wiredHeadsetItem = menu.findItem(R.id.audio_mode_wired_headset);

        final boolean usingHeadset = isSupported(CallAudioState.ROUTE_WIRED_HEADSET);
        earpieceItem.setVisible(!usingHeadset);
        earpieceItem.setEnabled(!usingHeadset);
        wiredHeadsetItem.setVisible(usingHeadset);
        wiredHeadsetItem.setEnabled(usingHeadset);
        // TODO: Show the above item (either earpieceItem or wiredHeadsetItem)
        // as initially "selected" if speakerOn and
        // bluetoothIndicatorOn are both false.

        final MenuItem bluetoothItem = menu.findItem(R.id.audio_mode_bluetooth);
        bluetoothItem.setEnabled(isSupported(CallAudioState.ROUTE_BLUETOOTH));
        // TODO: Show bluetoothItem as initially "selected" if
        // bluetoothIndicatorOn is true.

        mAudioModePopup.show();

        // Unfortunately we need to manually keep track of the popup menu's
        // visiblity, since PopupMenu doesn't have an isShowing() method like
        // Dialogs do.
        mAudioModePopupVisible = true;
    }

    private boolean isSupported(int mode) {
        return (mode == (getPresenter().getSupportedAudio() & mode));
    }

    private boolean isAudio(int mode) {
        return (mode == getPresenter().getAudioMode());
    }

    @Override
    public void displayDialpad(boolean value, boolean animate) {
        mShowDialpadButton.setSelected(value);
        if (getActivity() != null && getActivity() instanceof InCallActivity) {
            ((InCallActivity) getActivity()).showDialpadFragment(value, animate);
        }
    }

    @Override
    public boolean isDialpadVisible() {
        if (getActivity() != null && getActivity() instanceof InCallActivity) {
            return ((InCallActivity) getActivity()).isDialpadVisible();
        }
        return false;
    }

    @Override
    public void requestCallRecordingPermission(String[] permissions) {
        requestPermissions(permissions, REQUEST_CODE_CALL_RECORD_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_CALL_RECORD_PERMISSION) {
            boolean allGranted = grantResults.length > 0;
            for (int i = 0; i < grantResults.length; i++) {
                allGranted &= grantResults[i] == PackageManager.PERMISSION_GRANTED;
            }
            if (allGranted) {
                getPresenter().startCallRecording();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public void showInviteSnackbar(final PendingIntent inviteIntent, String inviteText) {
        if (TextUtils.isEmpty(inviteText)) {
            return;
        }
        final InCallActivity activity = (InCallActivity) getActivity();
        if (activity != null) {
            activity.showInviteSnackbar(inviteIntent, inviteText);
        }
    }

    @Override
    public void setDeepLinkNoteIcon(Drawable d) {
        if (d == null) {
            mTakeNoteButton.setVisibility(View.GONE);
        } else {
            mTakeNoteButton.setImageDrawable(d);
        }
    }

    /**
     * Adapter used to Array adapter with an icon and custom item layout
     */
    private class ListItemWithImageArrayAdapter extends ArrayAdapter<String> {
        private int mLayout;
        private List<Drawable> mIcons;

        public ListItemWithImageArrayAdapter(Context context, int layout, List<String> titles,
                List<Drawable> icons) {
            super(context, 0, titles);
            mLayout = layout;
            mIcons = icons;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            String title = getItem(position);
            Drawable icon = mIcons.get(position);
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(mLayout, parent, false);
            }

            TextView textView = (TextView) convertView.findViewById(R.id.title);
            textView.setText(title);

            ImageView msgIcon = (ImageView) convertView.findViewById(R.id.icon);
            msgIcon.setImageDrawable(icon);

            return convertView;
        }
    }
}
