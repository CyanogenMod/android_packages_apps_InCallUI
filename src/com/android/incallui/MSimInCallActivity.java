/*
 * Copyright (c) 2013-2014 The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2006 The Android Open Source Project
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

import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.app.ActionBar.Tab;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Phone app "multisim in call" screen.
 */
public class MSimInCallActivity extends InCallActivity {

    private MSimAnswerFragment mAnswerFragment;

    private final int TAB_COUNT_ONE = 1;
    private final int TAB_COUNT_TWO = 2;
    private final int TAB_POSITION_FIRST = 0;

    private Tab[] mDsdaTab = new Tab[TAB_COUNT_TWO];
    private boolean[] mDsdaTabAdd = {false, false};

    private static final String[] MULTI_SIM_NAME = {
        "perferred_name_sub1", "perferred_name_sub2"
    };

    @Override
    protected void onCreate(Bundle icicle) {
        Log.d(this, "onCreate()...  this = " + this);

        super.onCreate(icicle);

        // set this flag so this activity will stay in front of the keyguard
        // Have the WindowManager filter out touch events that are "too fat".
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);

        setTheme(R.style.InCallScreenWithActionBar);
        requestWindowFeature(Window.FEATURE_ACTION_BAR);

        getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        getActionBar().setDisplayShowTitleEnabled(false);
        getActionBar().setDisplayShowHomeEnabled(false);

        // Inflate everything in incall_screen.xml and add it to the screen.
        setContentView(R.layout.incall_screen_msim);

        initializeInCall();

        initializeDsdaSwitchTab();
        Log.d(this, "onCreate(): exit");
    }

    @Override
    protected void onStart() {
        Log.d(this, "onStart()...");
        super.onStart();

        // setting activity should be last thing in setup process
        InCallPresenter.getInstance().setActivity(this);
    }

    @Override
    public void finish() {
        Log.i(this, "finish().  Dialog showing: " + (mDialog != null));

        // skip finish if we are still showing a dialog.
        if (!hasPendingErrorDialog() && !mAnswerFragment.hasPendingDialogs()) {
            super.finish();
        }
    }

    @Override
    protected void initializeInCall() {
        if (mCallButtonFragment == null) {
            mCallButtonFragment = (CallButtonFragment) getFragmentManager()
                    .findFragmentById(R.id.callButtonFragment);
            mCallButtonFragment.setEnabled(false, false);
        }

        if (mCallCardFragment == null) {
            mCallCardFragment = (CallCardFragment) getFragmentManager()
                    .findFragmentById(R.id.callCardFragment);
        }

        if (mAnswerFragment == null) {
            mAnswerFragment = (MSimAnswerFragment) getFragmentManager()
                    .findFragmentById(R.id.answerFragment);
        }

        if (mDialpadFragment == null) {
            mDialpadFragment = (DialpadFragment) getFragmentManager()
                    .findFragmentById(R.id.dialpadFragment);
            mDialpadFragment.getView().setVisibility(View.INVISIBLE);
        }

        if (mConferenceManagerFragment == null) {
            mConferenceManagerFragment = (ConferenceManagerFragment) getFragmentManager()
                    .findFragmentById(R.id.conferenceManagerFragment);
            mConferenceManagerFragment.getView().setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void dismissPendingDialogs() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        mAnswerFragment.dismissPendingDialogues();
    }

    private void initializeDsdaSwitchTab() {
        int phoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
        ActionBar bar = getActionBar();
        View[] mDsdaTabLayout = new View[phoneCount];
        TypedArray icons = getResources().obtainTypedArray(R.array.sim_icons);
        int[] subString = {R.string.sub_1, R.string.sub_2};

        for (int i = 0; i < phoneCount; i++) {
            mDsdaTabLayout[i] = getLayoutInflater()
                    .inflate(R.layout.msim_tab_sub_info, null);
            if (MSimTelephonyManager.getDefault().getSimState(i)
                    == TelephonyManager.SIM_STATE_ABSENT) {
                ((ImageView)mDsdaTabLayout[i].findViewById(R.id.tabSubIcon))
                    .setVisibility(View.INVISIBLE);
                ((TextView)mDsdaTabLayout[i].findViewById(R.id.tabSubText))
                    .setVisibility(View.INVISIBLE);
            } else {
                ((ImageView)mDsdaTabLayout[i].findViewById(R.id.tabSubIcon))
                        .setBackground(icons.getDrawable(i));

                ((TextView)mDsdaTabLayout[i].findViewById(R.id.tabSubText))
                        .setText(Settings.System.getString(getContentResolver(),
                                MULTI_SIM_NAME[i]));
            }
            mDsdaTab[i] = bar.newTab().setCustomView(mDsdaTabLayout[i])
                    .setTabListener(new TabListener(i));
        }
    }

    @Override
    public void updateDsdaTab() {
        int phoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
        ActionBar bar = getActionBar();

        for (int i = 0; i < phoneCount; i++) {
            if (CallList.getInstance().existsLiveCall(i)) {
                if (!mDsdaTabAdd[i]) {
                    addDsdaTab(i);
                }
            } else {
                removeDsdaTab(i);
            }
        }

        updateDsdaTabSelection();
    }

    private void addDsdaTab(int subscription) {
        ActionBar bar = getActionBar();
        int tabCount = bar.getTabCount();

        if (tabCount < subscription) {
            bar.addTab(mDsdaTab[subscription], false);
        } else {
            bar.addTab(mDsdaTab[subscription], subscription, false);
        }
        mDsdaTabAdd[subscription] = true;
        Log.d(this, "addDsdaTab, subscription = " + subscription + " tab count = " + tabCount);
    }

    private void removeDsdaTab(int subscription) {
        ActionBar bar = getActionBar();
        int tabCount = bar.getTabCount();

        for (int i = 0; i < tabCount; i++) {
            if (bar.getTabAt(i).equals(mDsdaTab[subscription])) {
                bar.removeTab(mDsdaTab[subscription]);
                mDsdaTabAdd[subscription] = false;
                return;
            }
        }
        Log.d(this, "removeDsdaTab, subscription = " + subscription + " tab count = " + tabCount);
    }

    private void updateDsdaTabSelection() {
        ActionBar bar = getActionBar();
        int barCount = bar.getTabCount();

        if (barCount == TAB_COUNT_ONE) {
            bar.selectTab(bar.getTabAt(TAB_POSITION_FIRST));
        } else if (barCount == TAB_COUNT_TWO) {
            bar.selectTab(bar.getTabAt(CallList.getInstance().getActiveSubscription()));
        }
    }

    private class TabListener implements ActionBar.TabListener {
        int mSubscription;

        public TabListener(int subId) {
            mSubscription = subId;
        }

        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            ActionBar bar = getActionBar();
            int tabCount = bar.getTabCount();
            //Don't setActiveSubscription if tab count is 1.This is to avoid
            //setting active subscription automatically when call on one sub
            //ends and it's corresponding tab is removed.For such cases active
            //subscription will be set by InCallPresenter.attemptFinishActivity.
            if (tabCount != TAB_COUNT_ONE && CallList.getInstance().existsLiveCall(mSubscription)
                    && (CallList.getInstance().getActiveSubscription() != mSubscription)) {
                Log.i(this, "setactivesub " + mSubscription);
                CallCommandClient.getInstance().setActiveAndConversationSub(mSubscription);
            }
        }

        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        }

        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }
    }
}
