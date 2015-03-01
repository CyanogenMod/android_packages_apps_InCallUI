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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.base.Preconditions;

import android.os.Handler;
import android.os.Message;
import android.telecom.DisconnectCause;
import android.telecom.Phone;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.PhoneConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Maintains the list of active calls and notifies interested classes of changes to the call list
 * as they are received from the telephony stack. Primary listener of changes to this class is
 * InCallPresenter.
 */
public class CallList implements InCallPhoneListener {

    private static final int DISCONNECTED_CALL_SHORT_TIMEOUT_MS = 200;
    private static final int DISCONNECTED_CALL_MEDIUM_TIMEOUT_MS = 2000;
    private static final int DISCONNECTED_CALL_LONG_TIMEOUT_MS = 5000;

    private static final int EVENT_DISCONNECTED_TIMEOUT = 1;
    private static final int EVENT_NOTIFY_CHANGE = 2;

    private static CallList sInstance = new CallList();

    private final HashMap<String, Call> mCallById = new HashMap<>();
    private final HashMap<android.telecom.Call, Call> mCallByTelecommCall = new HashMap<>();
    private final HashMap<String, List<String>> mCallTextReponsesMap = Maps.newHashMap();
    /**
     * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is
     * load factor before resizing, 1 means we only expect a single thread to
     * access the map so make only a single shard
     */
    private final Set<Listener> mListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<Listener, Boolean>(8, 0.9f, 1));
    private final HashMap<String, List<CallUpdateListener>> mCallUpdateListenerMap = Maps
            .newHashMap();

    private Phone mPhone;
    private long mSubId = SubscriptionManager.INVALID_SUB_ID;
    private final ArrayList<ActiveSubChangeListener> mActiveSubChangeListeners =
            Lists.newArrayList();

    static final int PHONE_COUNT = TelephonyManager.getDefault().getPhoneCount();

    /**
     * Static singleton accessor method.
     */
    public static CallList getInstance() {
        return sInstance;
    }

    private Phone.Listener mPhoneListener = new Phone.Listener() {
        @Override
        public void onCallAdded(Phone phone, android.telecom.Call telecommCall) {
            Call call = new Call(telecommCall);
            Log.d(this, "onCallAdded: callState=" + call.getState());
            if (call.getState() == Call.State.INCOMING ||
                    call.getState() == Call.State.CALL_WAITING) {
                onIncoming(call, call.getCannedSmsResponses());
            } else {
                onUpdate(call);
            }
        }
        @Override
        public void onCallRemoved(Phone phone, android.telecom.Call telecommCall) {
            if (mCallByTelecommCall.containsKey(telecommCall)) {
                Call call = mCallByTelecommCall.get(telecommCall);
                call.setState(Call.State.DISCONNECTED);
                call.setDisconnectCause(new DisconnectCause(DisconnectCause.UNKNOWN));
                if (updateCallInMap(call)) {
                    Log.w(this, "Removing call not previously disconnected " + call.getId());
                }
                updateCallTextMap(call, null);
            }
        }
    };

    /**
     * Private constructor.  Instance should only be acquired through getInstance().
     */
    private CallList() {
    }

    @Override
    public void setPhone(Phone phone) {
        mPhone = phone;
        mPhone.addListener(mPhoneListener);
    }

    @Override
    public void clearPhone() {
        mPhone.removeListener(mPhoneListener);
        mPhone = null;
    }

    int getPhoneId(long subId) {
        return SubscriptionManager.getPhoneId(subId);
    }

    long[] getSubId(int phoneId) {
        return SubscriptionManager.getSubId(phoneId);
    }

    /**
     * Called when a single call disconnects.
     */
    public void onDisconnect(Call call) {
        if (updateCallInMap(call)) {
            Log.i(this, "onDisconnect: " + call);
            // notify those listening for changes on this specific change
            notifyCallUpdateListeners(call);
            // notify those listening for all disconnects
            notifyListenersOfDisconnect(call);
        }
    }

    /**
     * Called when a single call has changed.
     */
    public void onIncoming(Call call, List<String> textMessages) {
        Log.d(this, "onIncoming - " + call);

        // Update active subscription from call object. it will be set by
        // Telecomm service for incoming call and whenever active sub changes.
        if (call.mIsActiveSub) {
            long sub = call.getSubId();
            Log.d(this, "onIncoming - sub:" + sub + " mSubId:" + mSubId);
            if (sub != mSubId) {
                setActiveSubscription(sub);
            }
        }

        if (updateCallInMap(call)) {
            Log.i(this, "onIncoming - " + call);
        }
        updateCallTextMap(call, textMessages);

        for (Listener listener : mListeners) {
            listener.onIncomingCall(call);
        }
    }

    public void onUpgradeToVideo(Call call){
        Log.d(this, "onUpgradeToVideo call=" + call);
        for (Listener listener : mListeners) {
            listener.onUpgradeToVideo(call);
        }
    }
    /**
     * Called when a single call has changed.
     */
    public void onUpdate(Call call) {
        PhoneAccountHandle ph = call.getAccountHandle();
        Log.d(this, "onUpdate - " + call  + " ph:" + ph);
        if (call.mIsActiveSub && ph != null && (!ph.getId().equals("E"))) {
            long sub = call.getSubId();
            Log.i(this, "onUpdate - sub:" + sub + " mSubId:" + mSubId);
            if(sub != mSubId) {
                setActiveSubscription(sub);
            }
        }
        onUpdateCall(call);
        notifyGenericListeners();
    }

    public void notifyCallUpdateListeners(Call call) {
        final List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(call.getId());
        if (listeners != null) {
            for (CallUpdateListener listener : listeners) {
                listener.onCallChanged(call);
            }
        }
    }

    /**
     * Add a call update listener for a call id.
     *
     * @param callId The call id to get updates for.
     * @param listener The listener to add.
     */
    public void addCallUpdateListener(String callId, CallUpdateListener listener) {
        List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(callId);
        if (listeners == null) {
            listeners = new CopyOnWriteArrayList<CallUpdateListener>();
            mCallUpdateListenerMap.put(callId, listeners);
        }
        listeners.add(listener);
    }

    /**
     * Remove a call update listener for a call id.
     *
     * @param callId The call id to remove the listener for.
     * @param listener The listener to remove.
     */
    public void removeCallUpdateListener(String callId, CallUpdateListener listener) {
        List<CallUpdateListener> listeners = mCallUpdateListenerMap.get(callId);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    public void addListener(Listener listener) {
        Preconditions.checkNotNull(listener);

        mListeners.add(listener);

        // Let the listener know about the active calls immediately.
        listener.onCallListChange(this);
    }

    public void removeListener(Listener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    /**
     * TODO: Change so that this function is not needed. Instead of assuming there is an active
     * call, the code should rely on the status of a specific Call and allow the presenters to
     * update the Call object when the active call changes.
     */
    public Call getIncomingOrActive() {
        Call retval = getIncomingCall();
        if (retval == null) {
            retval = getActiveCall();
        }
        return retval;
    }

    public Call getOutgoingOrActive() {
        Call retval = getOutgoingCall();
        if (retval == null) {
            retval = getActiveCall();
        }
        return retval;
    }

    /**
     * A call that is waiting for {@link PhoneAccount} selection
     */
    public Call getWaitingForAccountCall() {
        return getFirstCallWithState(Call.State.PRE_DIAL_WAIT);
    }

    public Call getPendingOutgoingCall() {
        return getFirstCallWithState(Call.State.CONNECTING);
    }

    public Call getOutgoingCall() {
        Call call = getFirstCallWithState(Call.State.DIALING);
        if (call == null) {
            call = getFirstCallWithState(Call.State.REDIALING);
        }
        return call;
    }

    public Call getActiveCall() {
        return getFirstCallWithState(Call.State.ACTIVE);
    }

    public Call getBackgroundCall() {
        return getFirstCallWithState(Call.State.ONHOLD);
    }

    public Call getDisconnectedCall() {
        return getFirstCallWithState(Call.State.DISCONNECTED);
    }

    public Call getDisconnectingCall() {
        return getFirstCallWithState(Call.State.DISCONNECTING);
    }

    public Call getSecondBackgroundCall() {
        return getCallWithState(Call.State.ONHOLD, 1);
    }

    public Call getActiveOrBackgroundCall() {
        Call call = getActiveCall();
        if (call == null) {
            call = getBackgroundCall();
        }
        return call;
    }

    public Call getIncomingCall() {
        Call call = getFirstCallWithState(Call.State.INCOMING);
        if (call == null) {
            call = getFirstCallWithState(Call.State.CALL_WAITING);
        }

        return call;
    }

    public Call getFirstCall() {
        Call result = getIncomingCall();
        if (result == null) {
            result = getPendingOutgoingCall();
        }
        if (result == null) {
            result = getOutgoingCall();
        }
        if (result == null) {
            result = getFirstCallWithState(Call.State.ACTIVE);
        }
        if (result == null) {
            result = getDisconnectingCall();
        }
        if (result == null) {
            result = getDisconnectedCall();
        }
        return result;
    }

    public boolean hasLiveCall() {
        Call call = getFirstCall();
        if (call == null) {
            return false;
        }
        return call != getDisconnectingCall() && call != getDisconnectedCall();
    }

    /**
     * Returns the first call found in the call map with the specified call modification state.
     * @param state The session modification state to search for.
     * @return The first call with the specified state.
     */
    public Call getVideoUpgradeRequestCall() {
        for(Call call : mCallById.values()) {
            if (call.getSessionModificationState() ==
                    Call.SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST) {
                return call;
            }
        }
        return null;
    }

    public Call getCallById(String callId) {
        return mCallById.get(callId);
    }

    public Call getCallByTelecommCall(android.telecom.Call telecommCall) {
        return mCallByTelecommCall.get(telecommCall);
    }

    public List<String> getTextResponses(String callId) {
        return mCallTextReponsesMap.get(callId);
    }

    /**
     * Returns first call found in the call map with the specified state.
     */
    public Call getFirstCallWithState(int state) {
        return getCallWithState(state, 0);
    }

    public boolean isDsdaEnabled() {
        if (TelephonyManager.getDefault().getMultiSimConfiguration()
                == TelephonyManager.MultiSimVariants.DSDA) {
            return true;
        }
        return false;
    }

    /**
     * Returns the [position]th call found in the call map with the specified state.
     * TODO: Improve this logic to sort by call time.
     */
    public Call getCallWithState(int state, int positionToFind) {
        if (state != Call.State.PRE_DIAL_WAIT && getActiveSubscription()
                != SubscriptionManager.INVALID_SUB_ID && isDsdaEnabled()) {
            return getCallWithState(state, positionToFind, getActiveSubscription());
        }

        Call retval = null;
        int position = 0;
        for (Call call : mCallById.values()) {
            if (call.getState() == state) {
                if (position >= positionToFind) {
                    retval = call;
                    break;
                } else {
                    position++;
                }
            }
        }

        return retval;
    }

    /**
     * This is called when the service disconnects, either expectedly or unexpectedly.
     * For the expected case, it's because we have no calls left.  For the unexpected case,
     * it is likely a crash of phone and we need to clean up our calls manually.  Without phone,
     * there can be no active calls, so this is relatively safe thing to do.
     */
    public void clearOnDisconnect() {
        for (Call call : mCallById.values()) {
            final int state = call.getState();
            if (state != Call.State.IDLE &&
                    state != Call.State.INVALID &&
                    state != Call.State.DISCONNECTED) {

                call.setState(Call.State.DISCONNECTED);
                call.setDisconnectCause(new DisconnectCause(DisconnectCause.UNKNOWN));
                updateCallInMap(call);
            }
        }
        notifyGenericListeners();
    }

    /**
     * Processes an update for a single call.
     *
     * @param call The call to update.
     */
    private void onUpdateCall(Call call) {
        Log.d(this, "\t" + call);
        if (updateCallInMap(call)) {
            Log.i(this, "onUpdate - " + call);
        }
        updateCallTextMap(call, call.getCannedSmsResponses());
        notifyCallUpdateListeners(call);
    }

    /**
     * Sends a generic notification to all listeners that something has changed.
     * It is up to the listeners to call back to determine what changed.
     */
    private void notifyGenericListeners() {
        for (Listener listener : mListeners) {
            listener.onCallListChange(this);
        }
    }

    private void notifyListenersOfDisconnect(Call call) {
        for (Listener listener : mListeners) {
            listener.onDisconnect(call);
        }
    }

    /**
     * Updates the call entry in the local map.
     * @return false if no call previously existed and no call was added, otherwise true.
     */
    private boolean updateCallInMap(Call call) {
        Preconditions.checkNotNull(call);

        boolean updated = false;

        if (call.getState() == Call.State.DISCONNECTED) {
            // update existing (but do not add!!) disconnected calls
            if (mCallById.containsKey(call.getId())) {

                // For disconnected calls, we want to keep them alive for a few seconds so that the
                // UI has a chance to display anything it needs when a call is disconnected.

                // Set up a timer to destroy the call after X seconds.
                final Message msg = mHandler.obtainMessage(EVENT_DISCONNECTED_TIMEOUT, call);
                mHandler.sendMessageDelayed(msg, getDelayForDisconnect(call));

                mCallById.put(call.getId(), call);
                mCallByTelecommCall.put(call.getTelecommCall(), call);
                updated = true;
            }
        } else if (!isCallDead(call)) {
            mCallById.put(call.getId(), call);
            mCallByTelecommCall.put(call.getTelecommCall(), call);
            updated = true;
        } else if (mCallById.containsKey(call.getId())) {
            mCallById.remove(call.getId());
            mCallByTelecommCall.remove(call.getTelecommCall());
            updated = true;
        }

        return updated;
    }

    private int getDelayForDisconnect(Call call) {
        Preconditions.checkState(call.getState() == Call.State.DISCONNECTED);


        final int cause = call.getDisconnectCause().getCode();
        final int delay;
        switch (cause) {
            case DisconnectCause.LOCAL:
                delay = DISCONNECTED_CALL_SHORT_TIMEOUT_MS;
                break;
            case DisconnectCause.REMOTE:
                delay = DISCONNECTED_CALL_MEDIUM_TIMEOUT_MS;
                break;
            case DisconnectCause.REJECTED:
            case DisconnectCause.MISSED:
            case DisconnectCause.CANCELED:
                // no delay for missed/rejected incoming calls and canceled outgoing calls.
                delay = 0;
                break;
            default:
                delay = DISCONNECTED_CALL_LONG_TIMEOUT_MS;
                break;
        }

        return delay;
    }

    private void updateCallTextMap(Call call, List<String> textResponses) {
        Preconditions.checkNotNull(call);

        if (!isCallDead(call)) {
            if (textResponses != null) {
                mCallTextReponsesMap.put(call.getId(), textResponses);
            }
        } else if (mCallById.containsKey(call.getId())) {
            mCallTextReponsesMap.remove(call.getId());
        }
    }

    private boolean isCallDead(Call call) {
        final int state = call.getState();
        return Call.State.IDLE == state || Call.State.INVALID == state;
    }

    /**
     * Sets up a call for deletion and notifies listeners of change.
     */
    private void finishDisconnectedCall(Call call) {
        call.setState(Call.State.IDLE);
        updateCallInMap(call);
        notifyGenericListeners();
        if (!hasAnyLiveCall()) {
           // update to Telecomm service that no active sub
           TelecomAdapter.getInstance().switchToOtherActiveSub(null, false);
           mSubId = SubscriptionManager.INVALID_SUB_ID;
        }
    }

    /**
     * Notifies all video calls of a change in device orientation.
     *
     * @param rotation The new rotation angle (in degrees).
     */
    public void notifyCallsOfDeviceRotation(int rotation) {
        for (Call call : mCallById.values()) {
            if (call.getVideoCall() != null) {
                call.getVideoCall().setDeviceOrientation(rotation);
            }
        }
    }

    /**
     * Handles the timeout for destroying disconnected calls.
     */
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_DISCONNECTED_TIMEOUT:
                    Log.d(this, "EVENT_DISCONNECTED_TIMEOUT ", msg.obj);
                    finishDisconnectedCall((Call) msg.obj);
                    break;
                case EVENT_NOTIFY_CHANGE:
                    Log.d(this, "EVENT_NOTIFY_CHANGE: ");
                    notifyGenericListeners();
                    for (ActiveSubChangeListener listener : mActiveSubChangeListeners) {
                        listener.onActiveSubChanged(getActiveSubscription());
                    }
                    break;
                default:
                    Log.wtf(this, "Message not expected: " + msg.what);
                    break;
            }
        }
    };

    /**
     * Listener interface for any class that wants to be notified of changes
     * to the call list.
     */
    public interface Listener {
        /**
         * Called when a new incoming call comes in.
         * This is the only method that gets called for incoming calls. Listeners
         * that want to perform an action on incoming call should respond in this method
         * because {@link #onCallListChange} does not automatically get called for
         * incoming calls.
         */
        public void onIncomingCall(Call call);
        /**
         * Called when a new modify call request comes in
         * This is the only method that gets called for modify requests. Listeners
         * that want to perform an action on incoming call should respond in this method
         * because {@link #onCallListChange} does not automatically get called for
         * incoming calls.
         */
        public void onUpgradeToVideo(Call call);
        /**
         * Called anytime there are changes to the call list.  The change can be switching call
         * states, updating information, etc. This method will NOT be called for new incoming
         * calls and for calls that switch to disconnected state. Listeners must add actions
         * to those method implementations if they want to deal with those actions.
         */
        public void onCallListChange(CallList callList);

        /**
         * Called when a call switches to the disconnected state.  This is the only method
         * that will get called upon disconnection.
         */
        public void onDisconnect(Call call);
    }

    public interface CallUpdateListener {
        // TODO: refactor and limit arg to be call state.  Caller info is not needed.
        public void onCallChanged(Call call);
    }

    /**
     * Called when active subscription changes.
     */
    public void onActiveSubChanged(long activeSub) {
        Log.i(this, "onActiveSubChanged  = " + activeSub);
        if (hasAnyLiveCall(activeSub)) {
            setActiveSubscription(activeSub);
        }
    }

    public long getActiveSubscription() {
        return mSubId;
    }

    /**
     * Called to update the latest active subscription id, and also it
     * notifies the registred clients about subscription change information.
     */
    public void setActiveSubscription(long subId) {
        if (subId != mSubId) {
            Log.i(this, "setActiveSubscription, old = " + mSubId + " new = " + subId);
            mSubId = subId;
            final Message msg = mHandler.obtainMessage(EVENT_NOTIFY_CHANGE, null);
            mHandler.sendMessage(msg);
        }
    }

    /**
     * Returns true, if any voice call in ACTIVE on the provided subscription.
     */
    public boolean hasAnyLiveCall(long subId) {
        for (Call call : mCallById.values()) {
            PhoneAccountHandle ph = call.getAccountHandle();
            try {
                if (!isCallDead(call) && ph != null && (Long.parseLong(ph.getId()) == subId)) {
                    Log.i(this, "hasAnyLiveCall sub = " + subId);
                    return true;
                }
            } catch (NumberFormatException e) {
                Log.w(this,"Sub Id is not a number " + e);
            }
        }
        Log.i(this, "no active call ");
        return false;
    }

    /**
     * Returns true, if any call in ACTIVE on the provided subscription.
     */
    public boolean hasAnyLiveCall() {
        for (Call call : mCallById.values()) {
            if (!isCallDead(call)) {
                Log.i(this, "hasAnyLiveCall call = " + call);
                return true;
            }
        }
        Log.i(this, "no active call ");
        return false;
    }

    /**
     * This method checks whether any other subscription currently has active voice
     * call other than current active subscription, if yes it makes that other
     * subscription as active subscription i.e user visible subscription.
     * @param retainLch  whether to retain the LCH state of the other active sub
     */
    public boolean switchToOtherActiveSub(boolean retainLch) {
        long activeSub = getActiveSubscription();
        boolean subSwitched = false;

        for (int i = 0; i < PHONE_COUNT; i++) {
            long[] subId = getSubId(i);
            if ((subId[0] != activeSub) && hasAnyLiveCall(subId[0])) {
                Log.i(this, "switchToOtherActiveSub, subId = " + subId[0] +
                        " retainLch = " + retainLch);
                subSwitched = true;
                TelecomAdapter.getInstance().switchToOtherActiveSub(
                        String.valueOf(subId[0]), retainLch);
                setActiveSubscription(subId[0]);
                break;
            }
        }
        return subSwitched;
    }

    /**
     * Method to check if there is any live call in a sub other than the one supplied.
     * @param currentSub  The subscription to exclude while checking for active calls.
     */
    public boolean isAnyOtherSubActive(long currentSub) {
        boolean result = false;
        if(!isDsdaEnabled()) {
            return false;
        }

        for (int phoneId = 0; phoneId < PHONE_COUNT;
                phoneId++) {
            long[] subId = getSubId(phoneId);

            if ((subId[0] != currentSub) && hasAnyLiveCall(subId[0])) {
                Log.d(this, "Live call found on another sub = " + subId[0]);
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * Returns the [position]th call which belongs to provided subscription and
     * found in the call map with the specified state.
     */
    public Call getCallWithState(int state, int positionToFind, long subId) {
        Call retval = null;
        int position = 0;
        for (Call call : mCallById.values()) {
            PhoneAccountHandle ph = call.getAccountHandle();
            if ((call.getState() == state) && ((ph == null) || ph.getId().equals("E") ||
                    (call.getSubId() == subId))) {
                if ((ph == null) && (!call.getTelecommCall().getChildren().isEmpty()) &&
                        (call.getTelecommCall().getChildren().size() > 1)) {
                    List<android.telecom.Call> children = call.getTelecommCall().getChildren();
                    android.telecom.Call child = children.get(0);
                    PhoneAccountHandle childph = child.getDetails().getAccountHandle();
                    if (Long.parseLong(childph.getId()) == subId) {
                        Log.d(this,"getCallWithState:retval = "+call);
                        retval = call;
                        break;
                    } else {
                        position++;
                        continue;
                    }
                }
                if (position >= positionToFind) {
                    retval = call;
                    break;
                } else {
                    position++;
                }
            }
        }
        return retval;
    }

    public void addActiveSubChangeListener(ActiveSubChangeListener listener) {
        Preconditions.checkNotNull(listener);
        mActiveSubChangeListeners.add(listener);
    }

    public void removeActiveSubChangeListener(ActiveSubChangeListener listener) {
        Preconditions.checkNotNull(listener);
        mActiveSubChangeListeners.remove(listener);
    }

    public interface ActiveSubChangeListener {
        public void onActiveSubChanged(long subId);
    }
}
