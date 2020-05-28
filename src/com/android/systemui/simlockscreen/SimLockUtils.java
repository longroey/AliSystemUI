package com.android.systemui.simlock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.media.AudioManager ;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;


public class SimLockUtils {
    private static final String TAG = "SimLockUtils";
    private static final boolean DEBUG = true;

    private SubscriptionManager mSubscriptionManager;

    public SimLockUtils(Context context) {
        mSubscriptionManager = SubscriptionManager.from(context);
    }

    /**
     * Return Operator name of related subId.
     * @param phoneId id of phone
     * @param context the context
     * @return operator name.
     */
    public String getOptrNameUsingPhoneId(int phoneId, Context context) {
        int subId = getSubIdUsingPhoneId(phoneId) ;
        SubscriptionInfo info = mSubscriptionManager.getActiveSubscriptionInfo(subId);
        if (null == info) {
           if (DEBUG) {
            Log.d(TAG, "getOptrNameUsingPhoneId, return null");
           }
        } else {
           if (DEBUG) {
               Log.d(TAG, "getOptrNameUsingPhoneId mDisplayName=" + info.getDisplayName());
           }
           if (info.getDisplayName() != null) {
                return info.getDisplayName().toString();
           }
        }
        return null;
    }

    /**
     * Return Operator drawable of related subId.
     * @param phoneId id of phone
     * @param context the context
     * @return operator related drawable.
     */
    public Bitmap getOptrBitmapUsingPhoneId(int phoneId, Context context) {
        int subId = getSubIdUsingPhoneId(phoneId) ;
        Bitmap bgBitmap = null;
        SubscriptionInfo info = mSubscriptionManager.getActiveSubscriptionInfo(subId);
        if (null == info) {
            if (DEBUG) {
                Log.d(TAG, "getOptrBitmapUsingPhoneId, return null");
            }
        } else {
            bgBitmap = info.createIconBitmap(context) ;
        }
        return bgBitmap;
    }




    /**
     * Return AirPlane mode is on or not.
     * @param context the context
     * @return airplane mode is on or not
     */
    public static boolean isAirplaneModeOn(Context context) {
        boolean airplaneModeOn = Settings.Global.getInt(context.getContentResolver(),
                                        Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        Log.d(TAG, "isAirplaneModeOn() = " + airplaneModeOn) ;
        return airplaneModeOn ;
    }

    /**
     * if return true, it means that Modem will turn off after entering AirPlane mode.
     * @return support or not
     */
    public static boolean isFlightModePowerOffMd() {
        boolean powerOffMd = SystemProperties.get("ro.mtk_flight_mode_power_off_md").equals("1") ;
        Log.d(TAG, "powerOffMd = " + powerOffMd) ;
        return powerOffMd ;
    }

    private static final int MAX_PHONE_COUNT = 4;
    private static int sPhoneCount = 0 ;
    /**
     * Get phone count.
     * @return phone count.
     **/
    public static int getNumOfPhone() {
        if (sPhoneCount == 0) {
            sPhoneCount = TelephonyManager.getDefault().getPhoneCount(); //hw can support in theory
            // MAX_PHONE_COUNT : in fact our ui layout max support 4. maybe update in future
            sPhoneCount = ((sPhoneCount > MAX_PHONE_COUNT) ? MAX_PHONE_COUNT : sPhoneCount);
        }
        return sPhoneCount;
    }

    public static final int INVALID_PHONE_ID = -1 ;
    /**
     * Is phone id valid.
     * @param phoneId phoneId.
     * @return valid or not.
     **/
    public static boolean isValidPhoneId(int phoneId) {
        return (phoneId != SubscriptionManager.DEFAULT_PHONE_INDEX) &&
               (0 <= phoneId) && (phoneId < getNumOfPhone());
    }

    /** get PhoneId from SubManager.
     * @param subId subId
     * @return phoneId
     */
    public static int getPhoneIdUsingSubId(int subId) {
        Log.e(TAG, "getPhoneIdUsingSubId: subId = " + subId);
        int phoneId = SubscriptionManager.getPhoneId(subId);
        if (phoneId < 0 || phoneId >= getNumOfPhone()) {
            Log.e(TAG, "getPhoneIdUsingSubId: invalid phonId = " + phoneId);
        } else {
            Log.e(TAG, "getPhoneIdUsingSubId: get phone ID = " + phoneId);
        }
        return phoneId ;
    }

    /**
     * Send phoneId to Sub-Mgr and get subId.
     * @param phoneId phoneId.
     * @return subid.
     */
    public static int getSubIdUsingPhoneId(int phoneId) {
        int subId = SubscriptionManager.getSubIdUsingPhoneId(phoneId);
        Log.d(TAG, "getSubIdUsingPhoneId(phoneId = " + phoneId + ") = " + subId) ;
        return subId;
    }

    public static final boolean isSimLockSupport() {
        return true;
    }

}