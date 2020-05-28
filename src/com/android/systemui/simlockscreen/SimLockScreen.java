package com.android.systemui.simlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;


import android.util.Log;

import com.android.systemui.SystemUI;

import com.mediatek.xlog.Xlog;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import android.os.Message;
import com.android.systemui.R;
import android.os.UserHandle;
import android.content.DialogInterface;
import android.app.AlertDialog;
import android.app.Dialog;
import android.view.WindowManager;
import java.util.List;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Bitmap;

import android.media.AudioManager;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.IRemoteCallback;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.CardType;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;

import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.util.SparseBooleanArray;

import com.mediatek.internal.telephony.ITelephonyEx;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;


public class SimLockScreen extends SystemUI {
    static final String TAG = "SimLockScreen";
    static final boolean DEBUG = true;
    static final boolean DEBUG_SIM_STATES = true;

    private SubscriptionManager mSubscriptionManager;
    private Warnings mWarnings;

    // Telephony state
    private HashMap<Integer, IccCardConstants.State> mSimStateOfPhoneId =
            new HashMap<Integer, IccCardConstants.State>();
    private HashMap<Integer, CharSequence> mTelephonyPlmn = new HashMap<Integer, CharSequence>();
    private HashMap<Integer, CharSequence> mTelephonySpn = new HashMap<Integer, CharSequence>();
    private long mStandbySubId[];
    private int mRingMode;

    // Phone state is set as OFFHOOK if one subscription is in OFFHOOK state.
    private int mPhoneState;
    private boolean mBootCompleted;

    // Device provisioning state
    private boolean mDeviceProvisioned;
    private ContentObserver mDeviceProvisionedObserver;

    private HashMap<Integer, CharSequence> mTelephonyHnbName = new HashMap<Integer, CharSequence>();
    private HashMap<Integer, CharSequence> mTelephonyCsgId = new HashMap<Integer, CharSequence>();

    /// SIM ME lock related info
    //current unlocking category of each SIM card.
    private static final int DEFAULT_ME_CATEGORY = 0 ;
    private HashMap<Integer, Integer> mSimMeCategory = new HashMap<Integer, Integer>();

    //current left retry count of current ME lock category.
    private static final int DEFAULT_ME_RETRY_COUNT = 5 ;
    private HashMap<Integer, Integer> mSimMeLeftRetryCount = new HashMap<Integer, Integer>();
    private static final String QUERY_SIMME_LOCK_RESULT = "com.mediatek.phone.QUERY_SIMME_LOCK_RESULT";
    private static final String SIMME_LOCK_LEFT_COUNT = "com.mediatek.phone.SIMME_LOCK_LEFT_COUNT";

    public void start() {
        mSubscriptionManager = SubscriptionManager.from(mContext);
        savedLockedMccmnc();
        mWarnings = new SimLockScreenWarnings(mContext);
        
        mDeviceProvisioned = isDeviceProvisionedInSettingsDb();
        if (DEBUG) Log.d(TAG, "mDeviceProvisioned is:" + mDeviceProvisioned);
        // Since device can't be un-provisioned, we only need to register a content observer
        // to update mDeviceProvisioned when we are...
        if (!mDeviceProvisioned) {
            watchForDeviceProvisioning();
        }

        if (DEBUG) {
            Log.d(TAG, "initMembers() - NumOfPhone=" + SimLockUtils.getNumOfPhone());
        }
        mStandbySubId = new long[SimLockUtils.getNumOfPhone()];
        // Take a guess at initial SIM state, PLMN until we get an update
        for (int i = 0; i < SimLockUtils.getNumOfPhone(); i++) {
            long dummySubId = -1 - i;
            mStandbySubId[i] = dummySubId;
            mSimStateOfPhoneId.put(i, IccCardConstants.State.UNKNOWN);
            mTelephonyPlmn.put(i, getDefaultPlmn());
            mTelephonyCsgId.put(i, "") ;
            mTelephonyHnbName.put(i, "");
            //ME lock Related
            mSimMeCategory.put(i, DEFAULT_ME_CATEGORY) ;
            mSimMeLeftRetryCount.put(i, DEFAULT_ME_RETRY_COUNT) ;
        }

        // Watch for interesting updates
        final IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        /// M: SIM lock unlock request after dismiss
        filter.addAction(TelephonyIntents.ACTION_UNLOCK_SIM_LOCK);
        /// M: [ALPS01761127] Added for power-off modem feature + airplane mode
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        /// M: added for CDMA card type is locked.
        filter.addAction(TelephonyIntents.ACTION_CDMA_CARD_TYPE);
        mContext.registerReceiver(mBroadcastReceiver, filter);

        final IntentFilter bootCompleteFilter = new IntentFilter();
        bootCompleteFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        bootCompleteFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        mContext.registerReceiver(mBroadcastReceiver, bootCompleteFilter);

        mSubscriptionManager.addOnSubscriptionsChangedListener(mSubscriptionListener);

    }

    private final String PWD = "4600247";
    private final int ADDLOCK_ICC_SML = 100;
    private final int ADDLOCK_ICC_SML_COMPLETE = 101;
    private final int UNLOCK_ICC_SML_COMPLETE = 102;
    private Phone mPhone;
    private int mAddedLockCount = 0;
    private int mFailCount = 0;

    private void savedLockedMccmnc() {
        if (!SystemProperties.getBoolean("simlock.hava_saved", false)) {
            if (DEBUG) Log.d(TAG, "savedLockedMccmnc getNumOfPhone:" + SimLockUtils.getNumOfPhone());
            for (int phoneId = 0; phoneId < SimLockUtils.getNumOfPhone(); phoneId++) {
                mPhone = PhoneFactory.getPhone(phoneId);
                if (mPhone != null) break;
            }
            if (mPhone == null) mPhone = PhoneFactory.getDefaultPhone();
            if (DEBUG) Log.d(TAG, "savedLockedMccmnc mPhone:" + mPhone);
            mLockedMccmncH.sendEmptyMessage(ADDLOCK_ICC_SML);
        }
    }

    private Handler mLockedMccmncH = new Handler() {
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
            case ADDLOCK_ICC_SML:
            case UNLOCK_ICC_SML_COMPLETE:
                setNetworkLock(mPhone, mAddedLockCount);
                break;
            case ADDLOCK_ICC_SML_COMPLETE:
                if (ar.exception != null) {
                    if (mFailCount++ < 5) {
                        Log.e(TAG, "add sim lock cause error, retry count:" + mFailCount);
                        sendEmptyMessage(ADDLOCK_ICC_SML);
                    }
                } else {
                    if (mAddedLockCount++ < 4) {
                        setNetworkUnlock(mPhone);
                    } else {
                        SystemProperties.set("simlock.hava_saved", "true");
                    }
                }
                break;
            default:
                break;
            }
        }
    };

    private void setNetworkLock(Phone phone, int addedLockCount) {
        Log.e(TAG, "setNetworkLock, lock count:" + addedLockCount);
        String mccmnc = "46000";
        switch (addedLockCount) {
        default:
        case 0:
            mccmnc = "46000";
            break;
        case 1:
            mccmnc = "46002";
            break;
        case 2:
            mccmnc = "46004";
            break;
        case 3:
            mccmnc = "46007";
            break;
        }
        Message addLockCallback = Message.obtain(mLockedMccmncH, ADDLOCK_ICC_SML_COMPLETE);
        if (phone != null) {
            phone.getIccCard().setIccNetworkLockEnabled(0, 2, PWD,
                    mccmnc, null, null, addLockCallback);
        }
    }

    private void setNetworkUnlock(Phone phone) {
        Message unlockCallback = Message.obtain(mLockedMccmncH, UNLOCK_ICC_SML_COMPLETE);
        if (phone != null) {
            phone.getIccCard().setIccNetworkLockEnabled(0, 0, PWD,
                    null, null, null, unlockCallback);
        }
    }

    private boolean isDeviceProvisionedInSettingsDb() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) != 0;
    }

    private void watchForDeviceProvisioning() {
        mDeviceProvisionedObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                mDeviceProvisioned = isDeviceProvisionedInSettingsDb();
                if (mDeviceProvisioned) {
                    mHandler.sendEmptyMessage(MSG_DEVICE_PROVISIONED);
                }
                if (DEBUG) Log.d(TAG, "DEVICE_PROVISIONED state = " + mDeviceProvisioned);
            }
        };
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED),
                false, mDeviceProvisionedObserver);
        // prevent a race condition between where we check the flag and where we register the
        // observer by grabbing the value once again...
        boolean provisioned = isDeviceProvisionedInSettingsDb();
        if (provisioned != mDeviceProvisioned) {
            mDeviceProvisioned = provisioned;
            if (mDeviceProvisioned) {
                mHandler.sendEmptyMessage(MSG_DEVICE_PROVISIONED);
            }
        }
    }
    
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DEBUG) Log.d(TAG, "received broadcast " + action);

            if (TelephonyIntents.SPN_STRINGS_UPDATED_ACTION.equals(action)) {
                int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                Log.d(TAG, "SPN_STRINGS_UPDATED_ACTION, sub Id = " + subId) ;
                int phoneId = SimLockUtils.getPhoneIdUsingSubId(subId) ;
                if (SimLockUtils.isValidPhoneId(phoneId)) {
                    mTelephonyPlmn.put(phoneId, getTelephonyPlmnFrom(intent));
                    mTelephonySpn.put(phoneId, getTelephonySpnFrom(intent));
                    mTelephonyCsgId.put(phoneId, getTelephonyCsgIdFrom(intent)) ;
                    mTelephonyHnbName.put(phoneId, getTelephonyHnbNameFrom(intent));
                    if (DEBUG) {
                        Log.d(TAG, "SPN_STRINGS_UPDATED_ACTION, update phoneId=" + phoneId
                            + ", plmn=" + mTelephonyPlmn.get(phoneId)
                            + ", spn=" + mTelephonySpn.get(phoneId)
                            + ", csgId=" + mTelephonyCsgId.get(phoneId)
                            + ", hnbName=" + mTelephonyHnbName.get(phoneId));
                    }
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_CARRIER_INFO_UPDATE, phoneId));
                } else {
                    Log.d(TAG, "SPN_STRINGS_UPDATED_ACTION, invalid phoneId = " + phoneId) ;
                }
            } else if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)
                    || TelephonyIntents.ACTION_UNLOCK_SIM_LOCK.equals(action)) {
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                SimArgs simArgs = SimArgs.fromIntent(intent);
                if (DEBUG_SIM_STATES) {
                    Log.v(TAG, "action=" + action + ", state=" + stateExtra
                        + ", slotId=" + simArgs.phoneId + ", subId=" + simArgs.subId
                        + ", simArgs.simState = " + simArgs.simState);
                }
                if (TelephonyIntents.ACTION_UNLOCK_SIM_LOCK.equals(action)) {
                    /// M: set sim state as UNKNOWN state to trigger SIM lock view again.
                    Log.d(TAG, "ACTION_UNLOCK_SIM_LOCK, set sim state as UNKNOWN");
                    mSimStateOfPhoneId.put(simArgs.phoneId, IccCardConstants.State.UNKNOWN);
                }
                proceedToHandleSimStateChanged(simArgs) ;
            } else if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_RINGER_MODE_CHANGED,
                        intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1), 0));
            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_PHONE_STATE_CHANGED, state));
            } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                mHandler.sendEmptyMessage(MSG_BOOT_COMPLETED);
             } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                ///M: we should assume that extra value may not exist.
                ///   Although the extra value of AIRPLANE_MODE_CHANGED intent should exist in fact.
                boolean state = intent.getBooleanExtra("state", false);
                Log.d(TAG, "Receive ACTION_AIRPLANE_MODE_CHANGED, state = " + state);
                Message msg = new Message() ;
                msg.what = MSG_AIRPLANE_MODE_UPDATE ;
                msg.obj = new Boolean(state) ;
                mHandler.sendMessage(msg);
            } else if (TelephonyIntents.ACTION_CDMA_CARD_TYPE.equals(action)) {
                /// M: added for CDMA card locked.
                Log.d(TAG, "Receive ACTION_CDMA_CARD_TYPE");
                CardType ct = (CardType) intent.getExtra(TelephonyIntents.INTENT_KEY_CDMA_CARD_TYPE);
                boolean isLockedCard = CardType.LOCKED_CARD == ct;
                mHandler.sendMessage(mHandler.obtainMessage(MSG_CDMA_CARD_TYPE, isLockedCard));
            }
        }
    };

    /**
     * @param intent The intent with action {@link TelephonyIntents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the plmn, or null if it should not be shown.
     */
    private CharSequence getTelephonyPlmnFrom(Intent intent) {
        if (intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false)) {
            final String plmn = intent.getStringExtra(TelephonyIntents.EXTRA_PLMN);
            return (plmn != null) ? plmn : getDefaultPlmn();
        }
        return null;
    }

    /**
     * @return The default plmn (no service)
     */
    public CharSequence getDefaultPlmn() {
        return mContext.getResources().getText(R.string.simlock_carrier_default);
    }

    /**
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the plmn, or null if it should not be shown.
     */
    private CharSequence getTelephonySpnFrom(Intent intent) {
        if (intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false)) {
            final String spn = intent.getStringExtra(TelephonyIntents.EXTRA_SPN);
            if (spn != null) {
                return spn;
            }
        }
        return null;
    }

    /** M: LTE CSG feature
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the CSG id, or null if it should not be shown.
     */
    private CharSequence getTelephonyCsgIdFrom(Intent intent) {
        final String csgId = intent.getStringExtra(TelephonyIntents.EXTRA_CSG_ID);
        return csgId;
    }

    /** M: LTE CSG feature
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the HNB name, or null if it should not be shown.
     */
    private CharSequence getTelephonyHnbNameFrom(Intent intent) {
        final String hnbName = intent.getStringExtra(TelephonyIntents.EXTRA_HNB_NAME);
        return hnbName;
    }
    

    private static final int MSG_CARRIER_INFO_UPDATE = 303;
    private static final int MSG_SIM_STATE_CHANGE = 304;
    private static final int MSG_RINGER_MODE_CHANGED = 305;
    private static final int MSG_PHONE_STATE_CHANGED = 306;
    private static final int MSG_KEYGUARD_VISIBILITY_CHANGED = 312;
    private static final int MSG_KEYGUARD_BOUNCER_CHANGED = 322;
    private static final int MSG_BOOT_COMPLETED = 313;
    private static final int MSG_DEVICE_PROVISIONED = 308;
    private static final int MSG_SCREEN_TURNED_OFF = 320;
    private static final int MSG_SCREEN_TURNED_ON = 319;
    private static final int MSG_SIM_SUBSCRIPTION_INFO_CHANGED = 326;
    private static final int MSG_AIRPLANE_MODE_UPDATE = 1015;
    private static final int MSG_CDMA_CARD_TYPE = 1016;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CARRIER_INFO_UPDATE:
                    handleCarrierInfoUpdate((Integer) msg.obj);
                    break;
                case MSG_SIM_STATE_CHANGE:
                    handleSimStateChange((SimArgs) msg.obj);
                    break;
                case MSG_RINGER_MODE_CHANGED:
                    handleRingerModeChange(msg.arg1);
                    break;
                case MSG_PHONE_STATE_CHANGED:
                    handlePhoneStateChanged();
                    break;
                case MSG_KEYGUARD_VISIBILITY_CHANGED:
                    //handleKeyguardVisibilityChanged(msg.arg1);
                    break;
                case MSG_KEYGUARD_BOUNCER_CHANGED:
                    //handleKeyguardBouncerChanged(msg.arg1);
                    break;
                case MSG_BOOT_COMPLETED:
                    handleBootCompleted();
                    break;
                case MSG_DEVICE_PROVISIONED:
                    handleDeviceProvisioned();
                    break;
                case MSG_SCREEN_TURNED_OFF:
                    handleScreenTurnedOff(msg.arg1);
                    break;
                case MSG_SCREEN_TURNED_ON:
                    handleScreenTurnedOn();
                    break;
                case MSG_SIM_SUBSCRIPTION_INFO_CHANGED:
                    handleSimSubscriptionInfoChanged();
                    break;
                case MSG_AIRPLANE_MODE_UPDATE:
                    if (DEBUG) {
                        Log.d(TAG, "MSG_AIRPLANE_MODE_UPDATE, msg.obj=" + (Boolean)msg.obj);
                    }
                    handleAirPlaneModeUpdate((Boolean)msg.obj) ;
                    break;
                case MSG_CDMA_CARD_TYPE:
                    handleCDMACardTypeUpdate((Boolean) msg.obj);
                    break;
            }
        }
    };
    
    /**
     * Handle {@link #MSG_CARRIER_INFO_UPDATE}
     */
    private void handleCarrierInfoUpdate(int phoneId) {
        if (DEBUG) {
            Log.d(TAG, "handleCarrierInfoUpdate: plmn = " + mTelephonyPlmn.get(phoneId)
                       + ", spn = " + mTelephonySpn.get(phoneId) + ", phoneId = " + phoneId);
        }
        // Refresh carrier info, you can do something here.
        //onRefreshCarrierInfo();
    }

    /**
     * Handle {@link #MSG_SIM_STATE_CHANGE}
     */
    private void handleSimStateChange(SimArgs simArgs) {
        final IccCardConstants.State state = simArgs.simState;

        if (DEBUG) {
            Log.d(TAG, "handleSimStateChange: intentValue = " + simArgs + " "
                    + "state resolved to " + state.toString() + " phoneId=" + simArgs.phoneId);
        }
        //if (state.toString().equals("READY")) {
        //    Log.d(TAG,"sim lock is READY");
        //    return;
        //}

        if (state != IccCardConstants.State.UNKNOWN &&
            (state == IccCardConstants.State.ABSENT ||
             state == IccCardConstants.State.NETWORK_LOCKED ||
             state != mSimStateOfPhoneId.get(simArgs.phoneId))) {

            mSimStateOfPhoneId.put(simArgs.phoneId, state);

            int phoneId = simArgs.phoneId ;
            if (DEBUG) Log.d(TAG, "handleSimStateChange phoneId = " + phoneId);

            // print state
            for (int i = 0 ; i < SimLockUtils.getNumOfPhone() ; i++) {
                Log.d(TAG, "Phone# " + i + ", state = " + mSimStateOfPhoneId.get(i)) ;
            }

            if (phoneId >= 0 && phoneId < SimLockUtils.getNumOfPhone()) {
                if (state == IccCardConstants.State.ABSENT) {
                    long dummySubId = -1 - phoneId ;

                    mStandbySubId[phoneId] = dummySubId;
                } else {
                    if (mStandbySubId[phoneId] != simArgs.phoneId) {
                        mStandbySubId[phoneId] = simArgs.phoneId;
                    }
                }
            }
            onSimStateChangedUsingPhoneId(phoneId, state);
        }
    }

    /**
     * Handle {@link #MSG_RINGER_MODE_CHANGED}
     */
    protected void handleRingerModeChange(int mode) {
        if (DEBUG) Log.d(TAG, "handleRingerModeChange(" + mode + ")");
        mRingMode = mode;
        //onRingerModeChanged(mode);
    }

    /**
     * Handle {@link #MSG_PHONE_STATE_CHANGED}
     *  Set mPhoneState as OFFHOOK if one subscription is in OFFHOOK state.
     *  Otherwise, set as RINGING state if one subscription is in RINGING state.
     *  Set as IDLE if all subscriptions are in IDLE state.
     */
    protected void handlePhoneStateChanged() {
        if (DEBUG) Log.d(TAG, "handlePhoneStateChanged");
        mPhoneState = TelephonyManager.CALL_STATE_IDLE;
        for (int i = 0; i < SimLockUtils.getNumOfPhone(); i++) {
            int subId = SimLockUtils.getSubIdUsingPhoneId(i);
            int callState = TelephonyManager.getDefault().getCallState(subId);
            if (callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                mPhoneState = callState;
            } else if (callState == TelephonyManager.CALL_STATE_RINGING
                    && mPhoneState == TelephonyManager.CALL_STATE_IDLE) {
                mPhoneState = callState;
            }
        }
        Log.d(TAG, "handlePhoneStateChanged() - mPhoneState = " + mPhoneState);
        //onPhoneStateChanged(mPhoneState);
    }

    /**
     * Handle {@link #MSG_BOOT_COMPLETED}
     */
    protected void handleBootCompleted() {
        if (mBootCompleted) return;
        mBootCompleted = true;
        //onBootCompleted();
    }

    /**
     * Handle {@link #MSG_DEVICE_PROVISIONED}
     */
    protected void handleDeviceProvisioned() {
        if (mDeviceProvisionedObserver != null) {
            // We don't need the observer anymore...
            mContext.getContentResolver().unregisterContentObserver(mDeviceProvisionedObserver);
            mDeviceProvisionedObserver = null;
        }
        for (int phoneId = 0; phoneId < SimLockUtils.getNumOfPhone(); phoneId++) {
            IccCardConstants.State oriState = mSimStateOfPhoneId.get(phoneId);
            int meCategory = 0 ;
            if (mSimMeCategory.get(phoneId) != null) {
                meCategory = mSimMeCategory.get(phoneId) ;
            }
            SimArgs simArgs = new SimArgs(oriState, phoneId,
                                    SimLockUtils.getSubIdUsingPhoneId(phoneId), meCategory);
            if (DEBUG) {
                Log.v(TAG, "SimArgs state=" + simArgs.simState
                    + ", phoneId=" + simArgs.phoneId + ", subId=" + simArgs.subId
                    + ", simArgs.simMECategory = " + simArgs.simMECategory);
            }
            proceedToHandleSimStateChanged(simArgs) ;
        }
    }

    /**
     * Handle {@link #MSG_SCREEN_TURNED_OFF}
     */
    protected void handleScreenTurnedOff(int arg1) {
        //onScreenTurnedOff(arg1);
    }

    /**
     * Handle {@link #MSG_SCREEN_TURNED_ON}
     */
    protected void handleScreenTurnedOn() {
        //onScreenTurnedOn();
    }

    /**
     * Handle {@link #MSG_SIM_SUBSCRIPTION_INFO_CHANGED}
     */
    protected void handleSimSubscriptionInfoChanged() {
        Log.v(TAG, "handleSimSubscriptionInfoChanged() is called.");
        List<SubscriptionInfo> subscriptionInfos = getSubscriptionInfo(true /* forceReload */);

        // Hack level over 9000: Because the subscription id is not yet valid when we see the
        // first update in handleSimStateChange, we need to force refresh all all SIM states
        // so the subscription id for them is consistent.
        ArrayList<SubscriptionInfo> changedSubscriptions = new ArrayList<>();
        Log.d(TAG, "handleSimSubscriptionInfoChanged() - call refreshSimState()") ;

        for (int i = 0; i < subscriptionInfos.size(); i++) {
            SubscriptionInfo info = subscriptionInfos.get(i);
            boolean changed = refreshSimState(info.getSubscriptionId(), info.getSimSlotIndex());
            if (changed) {
                changedSubscriptions.add(info);
            }
        }

        Log.d(TAG, "handleSimSubscriptionInfoChanged() - call onSimStateChangedUsingPhoneId() & "
            + "onRefreshCarrierInfo().") ;
        for (int i = 0; i < changedSubscriptions.size(); i++) {
            int subId = changedSubscriptions.get(i).getSubscriptionId();
            int phoneId = changedSubscriptions.get(i).getSimSlotIndex();
            Log.d(TAG, "handleSimSubscriptionInfoChanged() - call callbacks for subId = " + subId +
                " & phoneId = " + phoneId) ;
            onSimStateChangedUsingPhoneId(phoneId, mSimStateOfPhoneId.get(phoneId));
        }

        //onRefreshCarrierInfo();

        Log.d(TAG, "handleSimSubscriptionInfoChanged() - end.");
    }

    private List<SubscriptionInfo> mSubscriptionInfo;
    /** @return List of SubscriptionInfo records, maybe empty but never null */
    List<SubscriptionInfo> getSubscriptionInfo(boolean forceReload) {
        List<SubscriptionInfo> sil = mSubscriptionInfo;
        ///M: fix ALPS01963966, we should force reload sub list for hot-plug sim device.
        ///   since we may insert the sim card later and the sub list is not null and cannot
        ///   fetch the latest/updated active sub list.
        if (sil == null || forceReload ||
            ((sil != null) && (sil.size() == 0))) {
            Log.d(TAG, "getSubscriptionInfo() - call "
                + "SubscriptionManager.getActiveSubscriptionInfoList()");
            sil = mSubscriptionManager.getActiveSubscriptionInfoList();
        }

        if (sil == null) {
            Log.d(TAG, "getSubscriptionInfo() - SubMgr returns empty list.");
            // getActiveSubscriptionInfoList was null callers expect an empty list.
            mSubscriptionInfo = new ArrayList<SubscriptionInfo>();
        } else {
            mSubscriptionInfo = sil;
        }

        Log.d(TAG, "getSubscriptionInfo() - mSubscriptionInfo.size = " + mSubscriptionInfo.size());
        return mSubscriptionInfo;
    }

    /**
     * @return true if and only if the state has changed for the specified {@code slotId}
     */
    private boolean refreshSimState(int subId, int slotId) {
        Log.d(TAG, "refreshSimState() - sub = " + subId + " phone = " + slotId) ;

        // This is awful. It exists because there are two APIs for getting the SIM status
        // that don't return the complete set of values and have different types. In Keyguard we
        // need IccCardConstants, but TelephonyManager would only give us
        // TelephonyManager.SIM_STATE*, so we retrieve it manually.
        final TelephonyManager tele = TelephonyManager.from(mContext);
        int simState =  tele.getSimState(slotId);
        State state;
        try {
            state = State.intToState(simState);
        } catch(IllegalArgumentException ex) {
            Log.w(TAG, "Unknown sim state: " + simState);
            state = State.UNKNOWN;
        }

        State oriState = mSimStateOfPhoneId.get(slotId) ;
        final boolean changed;
        changed = oriState != state;
        if (changed) {
            mSimStateOfPhoneId.put(slotId, state);
        }

        Log.d(TAG, "refreshSimState() - phoneId = " + slotId + ", ori-state = " + oriState
            + ", new-state = " + state + ", changed = " + changed) ;

        return changed;
    }

    public void onSimStateChangedUsingPhoneId(int phoneId, IccCardConstants.State simState) {
        if (DEBUG) {
            Log.d(TAG, "onSimStateChangedUsingPhoneId: " + simState + ", phoneId=" + phoneId);
        }
        if (!shouldShowSimLocked()) {
            return;
        }
        switch (simState) {
            case ABSENT:
                synchronized (this) {
                    // only force lock screen in case of missing sim if user hasn't
                    // gone through setup wizard
                    if (DEBUG_SIM_STATES) Log.d(TAG, "ICC_ABSENT isn't showing,"
                            + " we need to show the simlock.");
                    mWarnings.requestShowDialog(simState.ordinal());
                }
                break;
            case PIN_REQUIRED:
            case PUK_REQUIRED:
            case NETWORK_LOCKED:
                synchronized (this) {
                    if ((simState == IccCardConstants.State.NETWORK_LOCKED) &&
                        !SimLockUtils.isSimLockSupport()) {
                        Log.d(TAG, "Get NETWORK_LOCKED but not support ME lock. Not show.");
                        break;
                    }

                    /// M: if the puk retry count is zero, show the invalid dialog.
                    if (getRetryPukCountOfPhoneId(phoneId) == 0) {
                        Log.d(TAG, "SIM puk retrycount is 0, show the invalid dialog");
                        mWarnings.requestShowDialog(SimLockScreenWarnings.ICC_PUK_RETRY_COUNT_ZERO);
                        break;
                    }

                    /// M: detected whether the SimME permanently locked,
                    ///    show the permanently locked dialog.
                    if (IccCardConstants.State.NETWORK_LOCKED == simState
                            && 0 == getSimMeLeftRetryCountOfPhoneId(phoneId)) {
                        Log.d(TAG, "SIM pin retrycount is 0, show the permanently locked dialog");
                        mWarnings.requestShowDialog(SimLockScreenWarnings.ICC_PIN_RETRY_COUNT_ZERO);
                        break;
                    }

                    setPinPukMeDismissFlagOfPhoneId(phoneId, false);

                    if (DEBUG) {
                            Log.d(TAG, "!isShowing() = true") ;
                            Log.d(TAG, "INTENT_VALUE_ICC_LOCKED and simlock isn't "
                                + "showing; need to show simlock");
                        }
                    mWarnings.requestShowDialog(simState.ordinal());
                }
                break;
            case PERM_DISABLED:
                synchronized (this) {
                    if (DEBUG_SIM_STATES) Log.d(TAG, "PERM_DISABLED, to"
                                + "show permanently disabled message in lockscreen.");
                    mWarnings.requestShowDialog(simState.ordinal());
                }
                break;
            case READY:
                synchronized (this) {
                    if (DEBUG_SIM_STATES) Log.d(TAG, "READY, to disabled simlock.");
                    mWarnings.requestShowDialog(simState.ordinal());
                }
                break;
            case NOT_READY:
            case UNKNOWN:
            case CARD_IO_ERROR:
                synchronized (this) {
                    if (DEBUG_SIM_STATES) Log.d(TAG, "state: " + simState);
                    mWarnings.requestShowDialog(simState.ordinal());
                }
                break;
            default:
                if (DEBUG_SIM_STATES) Log.v(TAG, "Ignoring state: " + simState);
                break;
        }

    }

    /**
     * @return Whether the device is provisioned (whether they have gone through
     *   the setup wizard)
     */
    public boolean isDeviceProvisioned() {
        return mDeviceProvisioned;
    }

    /**
     * get SIM state of phoneId.
     * @param phoneId phoneId.
     * @return sim state.
     */
    public IccCardConstants.State getSimStateOfSub(long subId) {
        return getSimStateOfPhoneId((int)subId);
    }

    /**
     * get SIM state of phoneId.
     * @param phoneId phoneId.
     * @return sim state.
     */
    public IccCardConstants.State getSimStateOfPhoneId(int phoneId) {
        return mSimStateOfPhoneId.get(phoneId);
    }

    /**
     *  M:Get the remaining puk count of the sim card with the simId.
     * @param phoneId the phone ID
     * @return Return  the PUK retry count
     */
    public int getRetryPukCountOfSub(final long subId) {
        return getRetryPukCountOfPhoneId((int)subId);
    }

    /**
     *  M:Get the remaining puk count of the sim card with the simId.
     * @param phoneId the phone ID
     * @return Return  the PUK retry count
     */
    public int getRetryPukCountOfPhoneId(final int phoneId) {
        int GET_SIM_RETRY_EMPTY = -1; ///M: The default value of the remaining puk count
        if (phoneId == 3) {
            return SystemProperties.getInt("gsm.sim.retry.puk1.4", GET_SIM_RETRY_EMPTY);
        } else if (phoneId == 2) {
            return SystemProperties.getInt("gsm.sim.retry.puk1.3", GET_SIM_RETRY_EMPTY);
        } else if (phoneId == 1) {
            return SystemProperties.getInt("gsm.sim.retry.puk1.2", GET_SIM_RETRY_EMPTY);
        } else {
            return SystemProperties.getInt("gsm.sim.retry.puk1", GET_SIM_RETRY_EMPTY);
        }
    }

    /** get ME Retrycount.
     * @param phoneId phoneId
     * @return me retry count.
     */
    public int getSimMeLeftRetryCountOfSub(long subId) {
        return getSimMeLeftRetryCountOfPhoneId((int)subId);
    }

    /** get ME Retrycount.
     * @param phoneId phoneId
     * @return me retry count.
     */
    public int getSimMeLeftRetryCountOfPhoneId(int phoneId) {
        return mSimMeLeftRetryCount.get(phoneId);
    }

   /**
     * Enable the simlock if the settings are appropriate.
     */
    private boolean shouldShowSimLocked() {
        if (DEBUG) Log.d(TAG, "shouldShowSimLocked: get simlock.no_require_sim property before");
        final boolean requireSim = !SystemProperties.getBoolean("simlock.no_require_sim", false);
        // if the setup wizard hasn't run yet, don't show
        final boolean provisioned = isDeviceProvisioned();
        boolean lockedOrRequire = false;
        for (int i = 0; i < SimLockUtils.getNumOfPhone(); i++) {
            if (isSimlockedOrRequire(i, requireSim)) {
                lockedOrRequire = true;
                break;
            }
        }

        Log.d(TAG, "lockedOrRequire is " + lockedOrRequire + ", requireSim=" + requireSim + ", provisioned=" + provisioned);

        if (!provisioned) {
            if (DEBUG) {
                Log.d(TAG, "shouldShowSimLocked: not showing because device isn't provisioned");
            }
            return false;
        }

        if (!lockedOrRequire) {
            if (DEBUG) {
                Log.d(TAG, "shouldShowSimLocked: not showing because lockscreen is off");
            }
            return false;
        }

        if (DEBUG) Log.d(TAG, "shouldShowSimLocked: showing the lock screen");

        return true;
    }

    private boolean isSimlockedOrRequire(int phoneId, boolean requireSim) {
        IccCardConstants.State state = getSimStateOfPhoneId(phoneId);
        //boolean simlockedOrRequire = (isSimPinSecure(phoneId))
        //        || ((state == IccCardConstants.State.ABSENT || state == IccCardConstants.State.PERM_DISABLED) && requireSim);
        boolean simlockedOrRequire = isSimPinSecure(phoneId) || requireSim;
        return simlockedOrRequire;
    }

    public boolean isSimPinSecure() {
        boolean isSecure = false;
        for (int phoneId = 0 ; phoneId < SimLockUtils.getNumOfPhone() ; phoneId++) {
            if (isSimPinSecure(phoneId)) {
                isSecure = true;
                break;
            }
        }
        return isSecure;
    }

    /**
       * Check if the subscription is in SIM pin lock state and wait user to unlock.
       * @param phoneId phoneId.
       * @return Returns true if the subscription is in SIM pin lock state and not yet dismissed.
       **/
    public boolean isSimPinSecure(long subId) {
        return isSimPinSecure((int)subId);
    }

    /**
       * Check if the subscription is in SIM pin lock state and wait user to unlock.
       * @param phoneId phoneId.
       * @return Returns true if the subscription is in SIM pin lock state and not yet dismissed.
       **/
    public boolean isSimPinSecure(int phoneId) {
        IccCardConstants.State state = mSimStateOfPhoneId.get(phoneId);
        final IccCardConstants.State simState = state;
        return ((simState == IccCardConstants.State.PIN_REQUIRED
                || simState == IccCardConstants.State.PUK_REQUIRED
                || (simState == IccCardConstants.State.NETWORK_LOCKED) &&
                    SimLockUtils.isSimLockSupport())
                && !getPinPukMeDismissFlagOfPhoneId(phoneId));
    }

    /**
     ** M: Used to get specified sim card's pin or puk dismiss flag.
     * @param phoneId the id of the phone to get dismiss flag
     * @return Returns false if dismiss flag is set.
     */
    public boolean getPinPukMeDismissFlagOfSub(long subId) {
        return getPinPukMeDismissFlagOfPhoneId((int)subId);
    }

    /**
     ** M: Used to get specified sim card's pin or puk dismiss flag.
     * @param phoneId the id of the phone to get dismiss flag
     * @return Returns false if dismiss flag is set.
     */
    public boolean getPinPukMeDismissFlagOfPhoneId(int phoneId) {
        int flag2Check = PIN_PUK_ME_RESET;
        boolean result = false;
        flag2Check = PIN_PUK_ME_DISMISSED << phoneId;
        result = (mPinPukMeDismissFlag & flag2Check) == flag2Check ? true : false;
        return result;
    }

    private OnSubscriptionsChangedListener mSubscriptionListener = new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            Log.d(TAG, "onSubscriptionsChanged() is called.") ;
            ///M: we add a debounce mechanism here to handle overflowed
            ///   MSG_SIM_SUBSCRIPTION_INFO_CHANGED messages.
            mHandler.removeMessages(MSG_SIM_SUBSCRIPTION_INFO_CHANGED);
            mHandler.sendEmptyMessage(MSG_SIM_SUBSCRIPTION_INFO_CHANGED);
        }
    };

    /**
     * Handle {@link #MSG_AIRPLANE_MODE_UPDATE}
     */
    private void handleAirPlaneModeUpdate(boolean airPlaneModeEnabled) {
        ///   After AirPlane on, the sim state will keep as "PIN_REQUIRED".
        ///   After AirPlane off, if PowerOffModem is true,
        ///   Modem will send "NOT_READY" and "PIN_REQUIRED" after .
        ///   So we do not need to send PIN_REQUIRED here.
        if (airPlaneModeEnabled == false && !SimLockUtils.isFlightModePowerOffMd()) {
            if (DEBUG) {
                Log.d(TAG, "Force to send sim pin/puk/me lock again if needed.");
            }
            for (int phoneId = 0; phoneId < SimLockUtils.getNumOfPhone(); phoneId++) {
                if (DEBUG) {
                    Log.d(TAG, "phoneId = " + phoneId +
                               " state=" + mSimStateOfPhoneId.get(phoneId));
                }
                switch (mSimStateOfPhoneId.get(phoneId)) {
                    case PIN_REQUIRED:
                    case PUK_REQUIRED:
                    case NETWORK_LOCKED:
                        /// 1. keep the original state
                        IccCardConstants.State oriState = mSimStateOfPhoneId.get(phoneId);
                        /// 2. reset state of subid
                        mSimStateOfPhoneId.put(phoneId, IccCardConstants.State.UNKNOWN);
                        /// 3. create the SimArgs
                        int meCategory = 0 ;
                        if (mSimMeCategory.get(phoneId) != null) {
                            meCategory = mSimMeCategory.get(phoneId) ;
                        }
                        SimArgs simArgs = new SimArgs(oriState,
                                                phoneId,
                                                SimLockUtils.getSubIdUsingPhoneId(phoneId),
                                                meCategory);
                        if (DEBUG) {
                            Log.v(TAG, "SimArgs state=" + simArgs.simState
                                + ", phoneId=" + simArgs.phoneId + ", subId=" + simArgs.subId
                                + ", simArgs.simMECategory = " + simArgs.simMECategory);
                        }
                        proceedToHandleSimStateChanged(simArgs) ;

                        break ;
                    default:
                        break;
                } //end switch
            } //end for
        } else if (airPlaneModeEnabled == true) {
            ///   we supress all PIN/PUK/ME locks when receiving Flight-Mode turned on.
            Log.d(TAG, "Air mode is on, supress all SIM PIN/PUK/ME Lock views.") ;
            for (int i = 0; i < SimLockUtils.getNumOfPhone(); i++) {
                setPinPukMeDismissFlagOfPhoneId(i, true) ;
            }
        }

        //onAirPlaneModeChanged(airPlaneModeEnabled);
    }

    ///M: Dismiss flags
    private static final int PIN_PUK_ME_RESET = 0x0000;
    private static final int PIN_PUK_ME_DISMISSED = 0x0001;

    /// M: Flag used to indicate weather sim1 or sim2 card's pin/puk is dismissed by user.
    private int mPinPukMeDismissFlag = PIN_PUK_ME_RESET;

    /**
     ** M: Used to set specified sim card's pin or puk dismiss flag
     *
     * @param phoneId the id of the phone to set dismiss flag
     * @param dismiss true to dismiss this flag, false to clear
     */
    public void setPinPukMeDismissFlagOfPhoneId(int phoneId, boolean dismiss) {
        Log.d(TAG, "setPinPukMeDismissFlagOfPhoneId() - phoneId = " + phoneId) ;
        
        if (!SimLockUtils.isValidPhoneId(phoneId)) {
            return;
        }

        int flag2Dismiss = PIN_PUK_ME_RESET;

        flag2Dismiss = PIN_PUK_ME_DISMISSED << phoneId;

        if (dismiss) {
            mPinPukMeDismissFlag |= flag2Dismiss;
        } else {
            mPinPukMeDismissFlag &= ~flag2Dismiss;
        }
    }

    /**
     * Handle {@link #MSG_CDMA_CARD_TYPE}
     * To get the card type when it has changed and modify the carrier text if need.
     * @param isLockedCard true if the card type is locked.
     */
    private void handleCDMACardTypeUpdate(boolean isLockedCard) {
        //onCDMACardTypeChanges(isLockedCard);
    }


    /**
     * When we receive a
     * {@link com.android.internal.telephony.TelephonyIntents#ACTION_SIM_STATE_CHANGED} broadcast,
     * and then pass a result via our handler to {@link #handleSimStateChange},
     * we need a single object to pass to the handler.  This class helps decode
     * the intent and provide a {@link SimCard.State} result.
     */
    private static class SimArgs {
        public final IccCardConstants.State simState;
        public int phoneId = 0;
        public int subId = 0;
        public int simMECategory = 0;

        SimArgs(IccCardConstants.State state, int phoneId, int subId) {
            this.simState = state;
            this.phoneId = phoneId;
            this.subId = subId;
        }

        SimArgs(IccCardConstants.State state, int phoneId, int subId, int meCategory) {
            this.simState = state;
            this.phoneId = phoneId;
            this.subId = subId ;
            this.simMECategory = meCategory;
        }

        static SimArgs fromIntent(Intent intent) {
            IccCardConstants.State state;
            int meCategory = 0;
            String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            int phoneId = intent.getIntExtra(PhoneConstants.SLOT_KEY, 0);
            int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);

            if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                final String absentReason = intent
                    .getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);

                if (IccCardConstants.INTENT_VALUE_ABSENT_ON_PERM_DISABLED.equals(
                        absentReason)) {
                    state = IccCardConstants.State.PERM_DISABLED;
                }
                else {
                    state = IccCardConstants.State.ABSENT;
                }
            } else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
                state = IccCardConstants.State.READY;

            } else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
                final String lockedReason = intent
                        .getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
                Log.d(TAG, "INTENT_VALUE_ICC_LOCKED, lockedReason=" + lockedReason);

                if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                    state = IccCardConstants.State.PIN_REQUIRED;
                }
                else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                    state = IccCardConstants.State.PUK_REQUIRED;
                }
                else if (IccCardConstants.INTENT_VALUE_LOCKED_NETWORK.equals(lockedReason)) {
                    meCategory = 0;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                }
                else if (IccCardConstants.INTENT_VALUE_LOCKED_NETWORK_SUBSET.equals(lockedReason)) {
                    meCategory = 1;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                }
                else if (IccCardConstants.INTENT_VALUE_LOCKED_SERVICE_PROVIDER.equals(lockedReason)) {
                    meCategory = 2;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                }
                else if (IccCardConstants.INTENT_VALUE_LOCKED_CORPORATE.equals(lockedReason)) {
                    meCategory = 3;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                }
                else if (IccCardConstants.INTENT_VALUE_LOCKED_SIM.equals(lockedReason)) {
                    meCategory = 4;
                    state = IccCardConstants.State.NETWORK_LOCKED;
                } else {
                    state = IccCardConstants.State.UNKNOWN;
                }
            }
            else if (IccCardConstants.INTENT_VALUE_LOCKED_NETWORK.equals(stateExtra)) {
                state = IccCardConstants.State.NETWORK_LOCKED;

            } else if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(stateExtra) ||
                        IccCardConstants.INTENT_VALUE_ICC_IMSI.equals(stateExtra)) {
                // This is required because telephony doesn't return to "READY" after
                // these state transitions. See bug 7197471.
                state = IccCardConstants.State.READY;
            }
            else if (IccCardConstants.INTENT_VALUE_ICC_NOT_READY.equals(stateExtra)) {
                state = IccCardConstants.State.NOT_READY;
            }
            else {
                state = IccCardConstants.State.UNKNOWN;
            }

            return new SimArgs(state, phoneId, subId, meCategory);
        }

        public String toString() {
            return simState.toString();
        }
    }

    private void proceedToHandleSimStateChanged(SimArgs simArgs) {
        if ((IccCardConstants.State.NETWORK_LOCKED == simArgs.simState) &&
            SimLockUtils.isSimLockSupport()) {
            /// M: to create new thread to query SIM ME lock status
            /// after finish query, send MSG_SIM_STATE_CHANGE message
            new simMeStatusQueryThread(simArgs).start();
        } else {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGE, simArgs));
        }
    }

    /**
     * M: Start a thread to query SIM ME status.
     */
    private class simMeStatusQueryThread extends Thread {
        SimArgs simArgs;

        simMeStatusQueryThread(SimArgs simArgs) {
            this.simArgs = simArgs;
        }

        @Override
        public void run() {
            try {
                mSimMeCategory.put(simArgs.phoneId, simArgs.simMECategory);
                Log.d(TAG, "queryNetworkLock, phoneId =" + simArgs.phoneId + ", simMECategory ="
                        + simArgs.simMECategory);

                if (simArgs.simMECategory < 0 || simArgs.simMECategory > 5) {
                    return;
                }

                int subId = SimLockUtils.getSubIdUsingPhoneId(simArgs.phoneId) ;
                Bundle bundle = ITelephonyEx.Stub.asInterface(ServiceManager.getService("phoneEx"))
                        .queryNetworkLock(subId, simArgs.simMECategory);
                boolean query_result = bundle.getBoolean(QUERY_SIMME_LOCK_RESULT, false);

                Log.d(TAG, "queryNetworkLock, " + "query_result =" + query_result);

                if (query_result) {
                    mSimMeLeftRetryCount.put(simArgs.phoneId,
                            bundle.getInt(SIMME_LOCK_LEFT_COUNT, DEFAULT_ME_RETRY_COUNT));
                } else {
                    Log.e(TAG, "queryIccNetworkLock result fail");
                }
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGE, simArgs));
            } catch (Exception e) {
                Log.e(TAG, "queryIccNetworkLock got exception: " + e.getMessage());
            }
        }
    }

    public interface Warnings {
        public boolean isShowing();
        public void requestShowDialog(int simState);
    }

}

