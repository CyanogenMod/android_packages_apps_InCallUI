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

import com.android.dialer.deeplink.DeepLinkIntegrationManager;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.InCallPresenter.CanAddCallListener;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallPluginUpdateListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.incallapi.InCallPluginInfo;
import com.android.phone.common.ambient.AmbientConnection;
import com.android.phone.common.incall.StartInCallCallReceiver;
import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.common.api.PendingResult;
import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.deeplink.DeepLink;
import com.cyanogen.ambient.deeplink.applicationtype.DeepLinkApplicationType;
import com.cyanogen.ambient.deeplink.linkcontent.CallDeepLinkContent;
import com.cyanogen.ambient.deeplink.linkcontent.DeepLinkContentType;
import com.cyanogen.ambient.incall.InCallServices;
import com.cyanogen.ambient.incall.extension.InCallContactInfo;
import com.cyanogen.ambient.incall.extension.OriginCodes;
import com.cyanogen.ambient.incall.extension.StartCallRequest;
import com.cyanogen.ambient.incall.extension.StatusCodes;
import com.cyanogen.ambient.incall.results.PendingIntentResult;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cyanogenmod.providers.CMSettings;

import static com.android.incallui.ModButtonFragment.Buttons.BUTTON_INCALL;
import static com.android.incallui.ModButtonFragment.Buttons.BUTTON_TAKE_NOTE;

/**
 * Logic for mod buttons.
 */
public class ModButtonPresenter extends Presenter<ModButtonPresenter.ModButtonUi>
        implements InCallStateListener, IncomingCallListener,
        InCallDetailsListener, CanAddCallListener, CallList.ActiveSubChangeListener,
        StartInCallCallReceiver.Receiver, ContactInfoCacheCallback, InCallPluginUpdateListener {

    private static final String TAG = ModButtonPresenter.class.getSimpleName();
    private static final boolean DEBUG = false;

    private Call mCall;
    private DeepLink mNoteDeepLink;
    private ContactInfoCache.ContactCacheEntry mPrimaryContactInfo;
    private StartInCallCallReceiver mCallback;

    private Map<String, PendingIntent> mInviteIntentMap;

    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        if (DEBUG) Log.i(TAG, "Got InCallPlugin result callback code = " + resultCode);

        switch (resultCode) {
            case StatusCodes.StartCall.HANDOVER_CONNECTED:
                if (mCall == null) {
                    return;
                }

                if (DEBUG) Log.i(TAG, "Disconnecting call: " + mCall);
                TelecomAdapter.getInstance().disconnectCall(mCall.getId());
                break;
            default:
                Log.i(TAG, "Nothing to do for this InCallPlugin resultcode = " + resultCode);
        }
    }

    public ModButtonPresenter() {}

    @Override
    public void onUiReady(ModButtonUi ui) {
        super.onUiReady(ui);

        // register for call state changes last
        final InCallPresenter inCallPresenter = InCallPresenter.getInstance();
        inCallPresenter.addListener(this);
        inCallPresenter.addIncomingCallListener(this);
        inCallPresenter.addDetailsListener(this);
        inCallPresenter.addCanAddCallListener(this);
        inCallPresenter.addInCallPluginUpdateListener(this);
        CallList.getInstance().addActiveSubChangeListener(this);

        // Update the buttons state immediately for the current call
        onStateChange(InCallState.NO_CALLS, inCallPresenter.getInCallState(),
                CallList.getInstance());

        if (mInviteIntentMap == null) {
            mInviteIntentMap = new HashMap<String, PendingIntent>();
        }
    }

    @Override
    public void onUiUnready(ModButtonUi ui) {
        super.onUiUnready(ui);

        InCallPresenter.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallPresenter.getInstance().removeCanAddCallListener(this);
        InCallPresenter.getInstance().removeInCallPluginUpdateListener(this);
        CallList.getInstance().removeActiveSubChangeListener(this);
        mInviteIntentMap.clear();
    }

    @Override
    public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {

        if (newState == InCallState.OUTGOING) {
            mCall = callList.getOutgoingCall();
        } else if (newState == InCallState.INCALL) {
            mCall = callList.getActiveOrBackgroundCall();
        } else if (newState == InCallState.INCOMING) {
            mCall = callList.getIncomingCall();
        } else {
            mCall = null;
        }

        getPreferredLinks();
        updateUi(newState, mCall);
    }

    /**
     * Starts a query for more contact data for the save primary and secondary calls.
     */
    private void startContactInfoSearch(final Call call, boolean isIncoming) {
        final ContactInfoCache cache = ContactInfoCache.getInstance(getUi().getContext());

        cache.findInfo(call, isIncoming, this);
    }

    /**
     * Updates the user interface in response to a change in the details of a call.
     * Currently handles changes to the call buttons in response to a change in the details for a
     * call.  This is important to ensure changes to the active call are reflected in the available
     * buttons.
     *
     * @param call The active call.
     * @param details The call details.
     */
    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
        // Only update if the changes are for the currently active call
        if (getUi() != null && call != null && call.equals(mCall)) {
            updateButtonsState(call);
        }
    }

    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
        onStateChange(oldState, newState, CallList.getInstance());
    }

    @Override
    public void onCanAddCallChanged(boolean canAddCall) {
        if (getUi() != null && mCall != null) {
            updateButtonsState(mCall);
        }
    }

    public List<InCallPluginInfo> getContactInCallPluginInfoList() {
        List<InCallPluginInfo> inCallPluginInfoList = null;
        if (mCall != null) {
            final ContactInfoCache cache = ContactInfoCache.getInstance(getUi().getContext());
            if (cache != null) {
                ContactCacheEntry contactInfo = cache.getInfo(mCall.getId());
                if (contactInfo != null) {
                    inCallPluginInfoList = contactInfo.inCallPluginInfoList;
                }
                if (inCallPluginInfoList == null) {
                    cache.refreshPluginInfo(mCall, this);
                }
            }
        }
        return inCallPluginInfoList;
    }

    public void switchToVideoCall() {
        List<InCallPluginInfo> contactInCallPlugins = getContactInCallPluginInfoList();
        int listSize = (contactInCallPlugins != null) ? contactInCallPlugins.size() : 0;
        if (listSize == 1) {
            // If only one InCall Plugin available
            handoverCallToVoIPPlugin();
        } else if (listSize > 0){
            // If multiple sources available
            getUi().displayVideoCallProviderOptions();
        }
    }

    public void handoverCallToVoIPPlugin() {
        handoverCallToVoIPPlugin(0);
    }

    public void handoverCallToVoIPPlugin(int contactPluginIndex) {
        if (getUi() == null || getUi().getContext() == null) {
            Log.e(TAG, "getUi returned null or can't find context.");
            return;
        }

        final Context ctx = getUi().getContext();
        List<InCallPluginInfo> inCallPluginInfoList = getContactInCallPluginInfoList();
        if (inCallPluginInfoList != null && inCallPluginInfoList.size() > contactPluginIndex) {
            InCallPluginInfo info = inCallPluginInfoList.get(contactPluginIndex);
            final ComponentName component = info.getPluginComponent();
            final String userId = info.getUserId();
            final String mimeType = info.getMimeType();
            if (component != null && !TextUtils.isEmpty(component.flattenToString()) &&
                    !TextUtils.isEmpty(mimeType)) {
                // Attempt call handover
                if (!TextUtils.isEmpty(userId)) {
                    AmbientApiClient client =
                            AmbientConnection.CLIENT.get(ctx.getApplicationContext());

                    mCallback = new StartInCallCallReceiver(new Handler(Looper.myLooper()));
                    mCallback.setReceiver(ModButtonPresenter.this);
                    StartCallRequest request = new StartCallRequest(userId,
                            OriginCodes.CALL_HANDOVER,
                            StartCallRequest.FLAG_CALL_TRANSFER,
                            mCallback);

                    if (DEBUG) Log.i(TAG, "Starting InCallPlugin call for = " + userId);
                    InCallServices.getInstance().startVideoCall(client, component, request);
                } else {
                    // Attempt invite
                    if (DEBUG) {
                        final ContactInfoCache cache = ContactInfoCache.getInstance(ctx);
                        ContactCacheEntry entry = cache.getInfo(mCall.getId());
                        Uri lookupUri = entry.lookupUri;
                        if (lookupUri != null) {
                            Log.i(TAG, "Attempting invite for " + lookupUri.toString());
                        } else {
                            Log.i(TAG, "Attempting invite for " + entry.number);
                        }
                    }
                    inCallPluginContactInvite(ctx, info);
                }
            }
        }
    }

    private void inCallPluginContactInvite(final Context ctx, InCallPluginInfo info) {
        if (info == null || info.getPluginComponent() == null) {
            Log.e(TAG, "Unable to get invite intent because no InCall plugin component found.");
            return;
        }

        final String pluginComponentString = info.getPluginComponent().flattenToString();
        final String pluginTitle = info.getPluginTitle();

        // Check contains and not null here,
        // invite intent can be null if it does not exist for a certain plugin.
        if (mInviteIntentMap.containsKey(pluginComponentString)) {
            final PendingIntent inviteIntent = mInviteIntentMap.get(pluginComponentString);

            // We have an invite intent, attempt contact invite
            if (DEBUG) {
                Log.d(TAG, "Using pre-populated invite intent");
            }
            showInviteSnackbar(ctx, inviteIntent, pluginTitle);
        } else {
            // We don't have an invite intent, try to create one, or use generic one if
            // intent creation failed.
            if (DEBUG) {
                Log.d(TAG, "No pre-populated invite intent found, attempting to get one...");
            }

            final String callId = mCall.getId();
            final ContactCacheEntry cacheEntry =
                    ContactInfoCache.getInstance(ctx).getInfo(callId);

            if (cacheEntry == null) {
                Log.e(TAG, "Unable to get invite intent because contact info not found.");
                return;
            }

            // Cache a null pending intent for later, we aren't sure how long the incallservice
            // will take to populate the correct one. This will prevent many incallservice queries
            // for the same call to queue up.
            mInviteIntentMap.put(pluginComponentString, null);

            final InCallContactInfo contactInfo =
                    new InCallContactInfo(cacheEntry.name, cacheEntry.number, cacheEntry.lookupUri);
            PendingResult<PendingIntentResult> inviteResult =
                    InCallServices.getInstance().getInviteIntent(
                            AmbientConnection.CLIENT.get(ctx),
                            info.getPluginComponent(),
                            contactInfo);

            // Set callback for incallservice results
            inviteResult.setResultCallback(new ResultCallback<PendingIntentResult>() {
                @Override
                public void onResult(PendingIntentResult result) {


                    PendingIntent pendingInviteIntent = null;
                    if (result != null && result.intent != null) {
                        pendingInviteIntent = result.intent;
                    }

                    // Cache it for later
                    if (mInviteIntentMap != null) {
                        mInviteIntentMap.put(pluginComponentString, pendingInviteIntent);
                    }

                    // If UI is still available, show snackbar
                    if (getUi() == null || getUi().getContext() == null) {
                        if(DEBUG) {
                            Log.d(TAG, "Got invite creation result, but no valid UI or context.");
                        }
                        return;
                    }

                    final Context context = getUi().getContext();
                    showInviteSnackbar(context, pendingInviteIntent, pluginTitle);
                }
            });
        }
    }

    private void showInviteSnackbar(Context ctx, PendingIntent inviteIntent, String pluginTitle) {
        if (getUi() != null && !TextUtils.isEmpty(pluginTitle)) {
            String inviteText;
            if (inviteIntent != null) {
                // We have an invite intent, attempt contact invite
                inviteText =
                        ctx.getString(R.string.snackbar_incall_plugin_contact_invite, pluginTitle);
            } else {
                // Inform user to add contact manually, no invite intent found
                // (no intent defined by plugin)
                inviteText =
                        ctx.getString(R.string.snackbar_incall_plugin_no_invite_found, pluginTitle);
            }
            getUi().showInviteSnackbar(inviteIntent, inviteText);
        }
    }

    private void updateUi(InCallState state, Call call) {
        if (DEBUG) Log.d(this, "Updating call UI for call: ", call);

        final ModButtonUi ui = getUi();
        if (ui == null) {
            return;
        }

        final boolean isProvisioned = isDeviceProvisionedInSettingsDb(ui.getContext());
        final boolean isEnabled = isProvisioned &&
                state == InCallState.INCALL &&
                call != null;
        ui.setEnabled(isEnabled);

        if (call == null) {
            return;
        }

        updateButtonsState(call);
    }

    /**
     * Updates the buttons applicable for the UI.
     *
     * @param call The active call.
     */
    private void updateButtonsState(Call call) {
        Log.v(this, "updateButtonsState");
        final ModButtonUi ui = getUi();
        final boolean isProvisioned = isDeviceProvisionedInSettingsDb(ui.getContext());

        List<InCallPluginInfo> contactInCallPlugins = getContactInCallPluginInfoList();
        final boolean shouldShowInCall = isProvisioned &&
                contactInCallPlugins != null && !contactInCallPlugins.isEmpty();
        final boolean showNote = isProvisioned &&
                DeepLinkIntegrationManager.getInstance().ambientIsAvailable(getUi().getContext()) &&
                        mNoteDeepLink != null;

        ui.showButton(BUTTON_INCALL, shouldShowInCall);
        if (shouldShowInCall) {
            ui.modifyChangeToVideoButton();
        }
        ui.showButton(BUTTON_TAKE_NOTE, showNote);

        ui.updateButtonStates();
    }

    private void contactUpdated() {
        if (DEBUG) Log.i(this, "contactUpdated");
        if (getUi() != null && mCall != null) {
            updateButtonsState(mCall);
        }
    }

    @Override
    public void onInCallPluginUpdated() {
        if (DEBUG) Log.i(this, "onInCallPluginUpdated");
        contactUpdated();
    }

    @Override
    public void onContactInfoComplete(String callId, ContactCacheEntry entry) {
        if (DEBUG) Log.i(this, "onContactInfoComplete");
        mPrimaryContactInfo = entry;
        contactUpdated();
    }

    @Override
    public void onImageLoadComplete(String callId, ContactCacheEntry entry) {
        // Stub
    }

    public interface ModButtonUi extends Ui {
        void showButton(int buttonId, boolean show);
        void enableButton(int buttonId, boolean enable);
        void setEnabled(boolean on);
        void modifyChangeToVideoButton();
        void displayVideoCallProviderOptions();
        void showInviteSnackbar(PendingIntent inviteIntent, String inviteText);
        void setDeepLinkNoteIcon(Drawable d);

        /**
         * Once showButton() has been called on each of the individual buttons in the UI, call
         * this to configure the overflow menu appropriately.
         */
        void updateButtonStates();
        Context getContext();
    }

    public void onActiveSubChanged(int subId) {
        InCallState state = InCallPresenter.getInstance()
                .getPotentialStateFromCallList(CallList.getInstance());

        onStateChange(null, state, CallList.getInstance());
    }

    public void handleNoteClick() {
        if (mCall == null || mNoteDeepLink == null || getUi() == null) {
            return;
        }


        if (mNoteDeepLink.getAlreadyHasContent()) {
            showNote();
        } else {
            takeNote();
        }
    }

    private void takeNote() {
        Context ctx = getUi().getContext();
        android.telecom.Call.Details details = mCall.getTelecommCall().getDetails();
        String name;
        String number;
        if (mCall.isConferenceCall()) {
            NoteCallInfo noteCallInfo = getNoteCallInfoForConferenceCall(ctx);
            name = noteCallInfo.mNames;
            number = noteCallInfo.mNumbers;
        } else {
            name = getNormalizedName(ctx, mPrimaryContactInfo.name);
            number = mCall.getNumber();
        }
        CallDeepLinkContent content = new CallDeepLinkContent(mNoteDeepLink);
        content.setNumber(number);
        content.setName(name);
        content.setUri(DeepLinkIntegrationManager.generateCallUri(number,
                details.getCreateTimeMillis()));
        DeepLinkIntegrationManager.getInstance().sendContentSentEvent(ctx, mNoteDeepLink,
                new ComponentName(ctx, CallButtonPresenter.class));
        ctx.startActivity(content.build());
    }

    private void showNote() {
        Context ctx = getUi().getContext();
        ComponentName componentName = new ComponentName(ctx.getPackageName(),
                ModButtonPresenter.class.getName());
        DeepLinkIntegrationManager.getInstance().viewNote(ctx, mNoteDeepLink, componentName);
    }

    public void getPreferredLinks() {
        if (mCall != null && (mNoteDeepLink == null || !mNoteDeepLink.getAlreadyHasContent())) {
            Uri callUri = DeepLinkIntegrationManager.generateCallUri(mCall.getNumber(),
                    mCall.getCreateTimeMillis());
            DeepLinkIntegrationManager.getInstance().getPreferredLinksFor(mNoteDeepLinkCallback,
                DeepLinkContentType.CALL, callUri);
        }
    }

    private NoteCallInfo getNoteCallInfoForConferenceCall(Context ctx) {
        StringBuilder names = new StringBuilder();
        StringBuilder numbers = new StringBuilder();
        List<String> callIds = mCall.getChildCallIds();
        int len = callIds.size();
        for (int i = 0; i < len; i++) {
            ContactCacheEntry callInfo = ContactInfoCache.getInstance(
                    getUi().getContext()).getInfo(callIds.get(i));
            if (callInfo != null) {
                numbers.append(callInfo.number);
                names.append(getNormalizedName(ctx, callInfo.name));
                if (i < len - 1) {
                    numbers.append(", ");
                    names.append(", ");
                }
            }
        }
        return new NoteCallInfo(numbers.toString(), names.toString());
    }

    private String getNormalizedName(Context ctx, String name) {
        return TextUtils.isEmpty(name) ?
                ctx.getString(R.string.deeplink_unknown_caller) : mPrimaryContactInfo.name;
    }

    private ResultCallback<DeepLink.DeepLinkResultList> mNoteDeepLinkCallback =
            new ResultCallback<DeepLink.DeepLinkResultList>() {
                @Override
                public void onResult(DeepLink.DeepLinkResultList deepLinkResult) {
                    if (getUi() == null) {
                        return;
                    }

                    Drawable toDraw = null;
                    if (deepLinkResult != null && deepLinkResult.getResults() != null &&
                            getUi() != null) {
                        List<DeepLink> links = deepLinkResult.getResults();
                        for (DeepLink result : links) {
                            if (result.getApplicationType() == DeepLinkApplicationType.NOTE) {
                                mNoteDeepLink = result;
                                toDraw = result.getDrawableIcon(getUi().getContext()).mutate();
                                break;
                            }
                        }
                    }
                    getUi().setDeepLinkNoteIcon(toDraw);
                }
            };

    private boolean isDeviceProvisionedInSettingsDb(Context context) {
        return (CMSettings.Secure.getInt(context.getContentResolver(),
                CMSettings.Secure.CM_SETUP_WIZARD_COMPLETED, 0) != 0) &&
                (Settings.Global.getInt(context.getContentResolver(),
                        Settings.Global.DEVICE_PROVISIONED, 0) != 0);
    }

    private class NoteCallInfo {
        public NoteCallInfo(String numbers, String names) {
            mNames = names;
            mNumbers = numbers;
        }
        String mNames;
        String mNumbers;
    }
}
