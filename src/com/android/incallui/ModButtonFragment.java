/*
 * Copyright (C) 2016 The CyanogenMod Project
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

import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;
import com.android.incallui.incallapi.InCallPluginInfo;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.ContextThemeWrapper;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import static com.android.incallui.ModButtonFragment.Buttons.BUTTON_COUNT;
import static com.android.incallui.ModButtonFragment.Buttons.BUTTON_INCALL;
import static com.android.incallui.ModButtonFragment.Buttons.BUTTON_TAKE_NOTE;

/**
 * Fragment for mod control buttons
 */
public class ModButtonFragment
        extends BaseFragment<ModButtonPresenter, ModButtonPresenter.ModButtonUi>
        implements ModButtonPresenter.ModButtonUi,
        View.OnClickListener {

    private static final String TAG = ModButtonFragment.class.getSimpleName();
    private static final boolean DEBUG = false;

    private int mButtonMaxVisible;
    // The button is currently visible in the UI
    private static final int BUTTON_VISIBLE = 1;
    // The button is hidden in the UI
    private static final int BUTTON_HIDDEN = 2;
    // The button has been collapsed into the overflow menu
    private static final int BUTTON_MENU = 3;

    private SparseIntArray mButtonVisibilityMap = new SparseIntArray(BUTTON_COUNT);

    private ImageButton mInCallProvider;
    private ImageButton mTakeNoteButton;
    private ImageButton mOverflowButton;

    private PopupMenu mOverflowPopup;

    private MaterialPalette mCurrentThemeColors;

    public interface Buttons {
        int BUTTON_INCALL = 0;
        int BUTTON_TAKE_NOTE = 1;
        int BUTTON_COUNT = 2;
    }

    @Override
    public ModButtonPresenter createPresenter() {
        return new ModButtonPresenter();
    }

    @Override
    public ModButtonPresenter.ModButtonUi getUi() {
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
        final View parent = inflater.inflate(R.layout.mod_button_fragment, container, false);

        mInCallProvider = (ImageButton) parent.findViewById(R.id.inCallProviders);
        mInCallProvider.setOnClickListener(this);
        mTakeNoteButton = (ImageButton) parent.findViewById(R.id.takeNoteButton);
        mTakeNoteButton.setOnClickListener(this);
        mOverflowButton = (ImageButton) parent.findViewById(R.id.overflowButton);
        mOverflowButton.setOnClickListener(this);

        return parent;
    }

    @Override
    public void onResume() {
        super.onResume();
        getPresenter().getPreferredLinks();
        updateColors();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (DEBUG) Log.d(this, "onClick(View " + view + ", id " + id + ")...");

        switch(id) {
            case R.id.inCallProviders:
                getPresenter().switchToVideoCall();
                break;
            case R.id.takeNoteButton:
                getPresenter().handleNoteClick();
                break;
            case R.id.overflowButton:
                if (mOverflowPopup != null) {
                    mOverflowPopup.show();
                }
                break;
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

        mCurrentThemeColors = themeColors;

        ImageButton[] normalButtons = {
                mInCallProvider,
                mTakeNoteButton,
                mOverflowButton
        };

        for (ImageButton button : normalButtons) {
            final LayerDrawable layers = (LayerDrawable) button.getBackground();
            final RippleDrawable btnDrawable = backgroundDrawable(themeColors);
            layers.setDrawableByLayerId(R.id.backgroundItem, btnDrawable);
        }

        updateButtonStates();
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

    // state_focused
    private void addFocused(Resources res, StateListDrawable drawable) {
        int[] focused = {android.R.attr.state_focused};
        Drawable focusedDrawable = res.getDrawable(R.drawable.btn_unselected_focused);
        drawable.addState(focused, focusedDrawable);
    }

    // default
    private void addUnselected(Resources res, StateListDrawable drawable, MaterialPalette palette) {
        LayerDrawable unselectedDrawable =
                (LayerDrawable) res.getDrawable(R.drawable.btn_mod_unselected);
        ((GradientDrawable) unselectedDrawable.getDrawable(0)).setColor(palette.mSecondaryColor);
        drawable.addState(new int[0], unselectedDrawable);
    }


    @Override
    public void setEnabled(boolean isEnabled) {
        mInCallProvider.setEnabled(isEnabled);
        mTakeNoteButton.setEnabled(isEnabled);
        mOverflowButton.setEnabled(isEnabled);
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
            case BUTTON_INCALL:
                return mInCallProvider;
            case BUTTON_TAKE_NOTE:
                return mTakeNoteButton;
            default:
                Log.w(this, "Invalid button id");
                return null;
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
     * setting configured by the ModButtonPresenter. If there are more visible buttons than
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
            if (button == null) {
                continue;
            }
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

    @Override
    public void setDeepLinkNoteIcon(Drawable d) {
        if (d == null) {
            mTakeNoteButton.setVisibility(View.GONE);
        } else {
            createLayers(mTakeNoteButton, d);
        }
    }

    @Override
    public void modifyChangeToVideoButton() {
        List<InCallPluginInfo> contactInCallPlugins
                = getPresenter().getContactInCallPluginInfoList();
        int listSize = (contactInCallPlugins != null) ? contactInCallPlugins.size() : 0;
        if (listSize == 1) {
            InCallPluginInfo info = contactInCallPlugins.get(0);
            if (info != null && info.getPluginVideoIcon() != null) {
                createLayers(mInCallProvider, info.getPluginVideoIcon());
            }
        } else {
            createLayers(mInCallProvider, getResources().getDrawable(R.drawable.ic_video));
        }
    }

    /**The function is called when Video Call button gets pressed. The function creates and
     * displays video call options.
     */
    @Override
    public void displayVideoCallProviderOptions() {
        ModButtonPresenter.ModButtonUi ui = getUi();
        if (ui == null) {
            Log.e(this, "Cannot display VideoCallOptions as ui is null");
            return;
        }

        Context context = getContext();

        final ArrayList<Drawable> icons = new ArrayList<Drawable>();
        final ArrayList<String> items = new ArrayList<String>();
        final ArrayList<Integer> itemToCallType = new ArrayList<Integer>();

        // Prepare the string array and mapping.
        List<InCallPluginInfo> contactInCallPlugins =
                getPresenter().getContactInCallPluginInfoList();
        if (contactInCallPlugins != null && !contactInCallPlugins.isEmpty()) {
            int i = 0;
            for (InCallPluginInfo info : contactInCallPlugins) {
                items.add(info.getPluginTitle());
                icons.add(info.getPluginBrandIcon());
                itemToCallType.add(i);
                i++;
            }
        }

        ListAdapter adapter = new ListItemWithImageArrayAdapter(context.getApplicationContext(),
                R.layout.videocall_handoff_item, items, icons);
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                final int selCallType = itemToCallType.get(item);
                //  InCall Plugin selected
                getPresenter().handoverCallToVoIPPlugin(selCallType);
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

    private void createLayers(ImageButton button, Drawable icon) {
        Drawable newIcon = icon.getConstantState().newDrawable().mutate();

        final LayerDrawable layerDrawable =
                (LayerDrawable) getResources().getDrawable(R.drawable.btn_mod_drawable).mutate();

        newIcon.setTintList(getResources().getColorStateList(R.color.mod_icon_tint));
        newIcon.setAutoMirrored(false);

        layerDrawable.setDrawableByLayerId(R.id.foregroundItem, newIcon);
        button.setBackgroundDrawable(layerDrawable);
    }
}
