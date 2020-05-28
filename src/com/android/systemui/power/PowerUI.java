/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.power;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;

import com.android.systemui.SystemUI;
import com.android.systemui.statusbar.phone.PhoneStatusBar;

import com.mediatek.carcorder.CarcorderManager;
import com.mediatek.systemui.statusbar.util.SIMHelper;
import com.mediatek.xlog.Xlog;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import android.os.Handler;
import android.os.Message;
import com.android.systemui.R;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.content.DialogInterface;
import android.app.AlertDialog;
import android.app.Dialog;
import android.view.WindowManager;
// PQLJ chenpengfei add 20160504 begin
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import java.util.List;
import android.content.ComponentName;
// PQLJ chenpengfei add 20160504 end

public class PowerUI extends SystemUI {
    static final String TAG = "PowerUI";
    static final boolean DEBUG = true; // Log.isLoggable(TAG, Log.DEBUG);

    private final Handler mHandler = new Handler();
    private final Receiver mReceiver = new Receiver();

    private PowerManager mPowerManager;
    private WarningsUI mWarnings;
    private int mBatteryLevel = 100;
    private int mBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
    private int mPlugType = 0;
    private int mInvalidCharger = 0;

    private int mLowBatteryAlertCloseLevel;
    private final int[] mLowBatteryReminderLevels = new int[2];

    private long mScreenOffTime = -1;
    ///M:Add for plug out dialog @{
    private AlertDialog plugOutDialog = null;
    private final static int SHOW_PLUGOUT_DIALOG = 0;
    private final static int DISMISS_PLUGOUT_DIALOG = 1;
    private final static int DIALOG_REQUEST_SHUTDOWN = 2;
    private int time = 10;
    private String strFormt;
    private String message="";
    private String title;
    private boolean mBootCompleted = false;
    ///M:@}

    /// M: [SystemUI] Support Smartbook Feature.
    /// M: Support Laptop Battery Status.
    boolean mIsLaptopBatteryPresent = false;
    private CarcorderManager mCarcorderManager;

    private static final String ACTION_IPO_BOOT = "android.intent.action.ACTION_BOOT_IPO";

    private final static int CAN_PLAY_CHARGING_SOUND = 0;

    public void start() {
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mScreenOffTime = mPowerManager.isScreenOn() ? -1 : SystemClock.elapsedRealtime();
        mWarnings = new PowerNotificationWarnings(mContext, getComponent(PhoneStatusBar.class));
        mCarcorderManager = CarcorderManager.get();

        ContentObserver obs = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                updateBatteryWarningLevels();
            }
        };
        final ContentResolver resolver = mContext.getContentResolver();
        resolver.registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL),
                false, obs, UserHandle.USER_ALL);
        updateBatteryWarningLevels();
        mReceiver.init();
    }

    private void setSaverMode(boolean mode) {
        mWarnings.showSaverMode(mode);
    }

    void updateBatteryWarningLevels() {
        int critLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel);

        final ContentResolver resolver = mContext.getContentResolver();
        int defWarnLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryWarningLevel);
        int warnLevel = Settings.Global.getInt(resolver,
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, defWarnLevel);
        if (warnLevel == 0) {
            warnLevel = defWarnLevel;
        }
        if (warnLevel < critLevel) {
            warnLevel = critLevel;
        }

        mLowBatteryReminderLevels[0] = warnLevel;
        mLowBatteryReminderLevels[1] = critLevel;
        mLowBatteryAlertCloseLevel = mLowBatteryReminderLevels[0]
                + mContext.getResources().getInteger(
                        com.android.internal.R.integer.config_lowBatteryCloseWarningBump);
    }

    /**
     * Buckets the battery level.
     *
     * The code in this function is a little weird because I couldn't comprehend
     * the bucket going up when the battery level was going down. --joeo
     *
     * 1 means that the battery is "ok"
     * 0 means that the battery is between "ok" and what we should warn about.
     * less than 0 means that the battery is low
     */
    private int findBatteryLevelBucket(int level) {
        if (level >= mLowBatteryAlertCloseLevel) {
            return 1;
        }
        if (level > mLowBatteryReminderLevels[0]) {
            return 0;
        }
        final int N = mLowBatteryReminderLevels.length;
        for (int i=N-1; i>=0; i--) {
            if (level <= mLowBatteryReminderLevels[i]) {
                return -1-i;
            }
        }
        throw new RuntimeException("not possible!");
    }

    private final class Receiver extends BroadcastReceiver {

        public void init() {
            // Register for Intent broadcasts for...
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_SWITCHED);
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGING);
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_BOOT_COMPLETED);
            filter.addAction(Intent.ACTION_SHUTDOWN);
            filter.addAction(ACTION_IPO_BOOT);
            /// M: Support play low battery sound when IPO reboot
            filter.addAction("android.intent.action.ACTION_SHUTDOWN_IPO");
            mContext.registerReceiver(this, filter, null, mHandler);
            updateSaverMode();
        }

        private void updateSaverMode() {
            setSaverMode(mPowerManager.isPowerSaveMode());
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                /// M: [SystemUI] Support Smartbook Feature. @{
                /// M: Support Laptop Battery Status.
                if (SIMHelper.isMtkSmartBookSupport()) {
                    final boolean isPresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT_SMARTBOOK, false);
                    if (isPresent != mIsLaptopBatteryPresent) {
                        /// Reset the status for laptop battery.
                        mWarnings.dismissLowBatteryWarning();
                        mBatteryLevel = 100;
                        mBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
                        mPlugType = 0;
                        mInvalidCharger = 0;
                        /// Update the variable.
                        mIsLaptopBatteryPresent = isPresent;
                    }
                }
                /// @}

                final int oldBatteryLevel = mBatteryLevel;
                /// M: [SystemUI] Support Smartbook Feature. @{
                /// M: Support Laptop Battery Status.
                if (SIMHelper.isMtkSmartBookSupport()) {
                    mBatteryLevel = (mIsLaptopBatteryPresent)
                        ? intent.getIntExtra(BatteryManager.EXTRA_LEVEL_SMARTBOOK, 100)
                        : intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100);
                }
                /// @}
                else
                    mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100);
                final int oldBatteryStatus = mBatteryStatus;
                /// M: [SystemUI] Support Smartbook Feature. @{
                /// M: Support Laptop Battery Status.
                if (SIMHelper.isMtkSmartBookSupport()) {
                    mBatteryStatus = (mIsLaptopBatteryPresent)
                        ? intent.getIntExtra(BatteryManager.EXTRA_STATUS_SMARTBOOK,
                            BatteryManager.BATTERY_STATUS_UNKNOWN)
                        : intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                            BatteryManager.BATTERY_STATUS_UNKNOWN);
                }
                /// @}
                else
                    mBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN);
                final int oldPlugType = mPlugType;
                mPlugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 1);

                /// M: [SystemUI] Support Smartbook Feature. @{
                /// M: Support Laptop Battery Status.
                if (SIMHelper.isMtkSmartBookSupport()) {
//                    Xlog.d(TAG, "mIsLaptopBatteryPresent = " + mIsLaptopBatteryPresent
//                        + "mBatteryStatus = " + mBatteryStatus);
                    if (mIsLaptopBatteryPresent) {
                        if (mBatteryStatus == BatteryManager.BATTERY_STATUS_CHARGING
                            || mBatteryStatus == BatteryManager.BATTERY_STATUS_FULL) {
                            mPlugType = BatteryManager.BATTERY_PLUGGED_AC;
                        } else {
                            mPlugType = 0;
                        }
                    }
                }
                /// @}

                final int oldInvalidCharger = mInvalidCharger;
                mInvalidCharger = intent.getIntExtra(BatteryManager.EXTRA_INVALID_CHARGER, 0);

                final boolean plugged = mPlugType != 0;
                final boolean oldPlugged = oldPlugType != 0;

                int oldBucket = findBatteryLevelBucket(oldBatteryLevel);
                int bucket = findBatteryLevelBucket(mBatteryLevel);

                if (false) {
                    Slog.d(TAG, "buckets   ....." + mLowBatteryAlertCloseLevel
                            + " .. " + mLowBatteryReminderLevels[0]
                            + " .. " + mLowBatteryReminderLevels[1]);
                    Slog.d(TAG, "level          " + oldBatteryLevel + " --> " + mBatteryLevel);
                    Slog.d(TAG, "status         " + oldBatteryStatus + " --> " + mBatteryStatus);
                    Slog.d(TAG, "plugType       " + oldPlugType + " --> " + mPlugType);
                    Slog.d(TAG, "invalidCharger " + oldInvalidCharger + " --> " + mInvalidCharger);
                    Slog.d(TAG, "bucket         " + oldBucket + " --> " + bucket);
                    Slog.d(TAG, "plugged        " + oldPlugged + " --> " + plugged);
                }

                mWarnings.update(mBatteryLevel, bucket, mScreenOffTime);
                if (oldInvalidCharger == 0 && mInvalidCharger != 0) {
//                    Slog.d(TAG, "showing invalid charger warning");
                    mWarnings.showInvalidChargerWarning();
                    return;
                } else if (oldInvalidCharger != 0 && mInvalidCharger == 0) {
                    mWarnings.dismissInvalidChargerWarning();
                } else if (mWarnings.isInvalidChargerWarningShowing()) {
                    // if invalid charger is showing, don't show low battery
                    return;
                }

                if (!plugged
                        && (bucket < oldBucket || oldPlugged)
                        && mBatteryStatus != BatteryManager.BATTERY_STATUS_UNKNOWN
                        && bucket < 0) {
                    // only play SFX when the dialog comes up or the bucket changes
                    final boolean playSound = bucket != oldBucket || oldPlugged;
                    mWarnings.showLowBatteryWarning(playSound);
                } else if (plugged || (bucket > oldBucket && bucket > 0)) {
                    mWarnings.dismissLowBatteryWarning();
                } else {
                    mWarnings.updateLowBatteryWarning();
                }
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenOffTime = SystemClock.elapsedRealtime();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenOffTime = -1;
            } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                mWarnings.userSwitched();
            } else if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(action)) {
                updateSaverMode();
            } else if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGING.equals(action)) {
                setSaverMode(intent.getBooleanExtra(PowerManager.EXTRA_POWER_SAVE_MODE, false));
            } else if (action.equals("android.intent.action.ACTION_SHUTDOWN_IPO")) {
//                Xlog.d(TAG, "Intent android.intent.action.ACTION_SHUTDOWN_IPO mBatteryLevel = "+ mBatteryLevel);
                /// M: Support show low battery dialog in IPO boot.
                mBatteryLevel = 100;
                mWarnings.dismissLowBatteryWarning();
                mDialogHandler.sendEmptyMessage(DISMISS_PLUGOUT_DIALOG);
            }
            ///M:Add for plug out dialog @{
            else if(action.equals(Intent.ACTION_POWER_DISCONNECTED)){
                if (DEBUG) Slog.d(TAG, "Intent.ACTION_POWER_DISCONNECTED mBootCompleted=" + mBootCompleted);
                if((mBootCompleted || "1".equals(SystemProperties.get("sys.boot_completed")))
                   && !(isPowerOffTest() || isObdTest()) && !mDialogHandler.hasMessages(SHOW_PLUGOUT_DIALOG)){
                     if (DEBUG) Slog.d(TAG, "Intent.ACTION_POWER_DISCONNECTED SHOW_PLUGOUT_DIALOG");
                     //mDialogHandler.sendEmptyMessage(SHOW_PLUGOUT_DIALOG);
                     mDialogHandler.sendEmptyMessageDelayed(SHOW_PLUGOUT_DIALOG, 5000);
                     if (mDelayHandler.hasMessages(CAN_PLAY_CHARGING_SOUND)) {
                         mDelayHandler.removeMessages(CAN_PLAY_CHARGING_SOUND);
                     }
                     mDelayHandler.sendEmptyMessageDelayed(CAN_PLAY_CHARGING_SOUND, 5000);
                     SystemProperties.set("sys.no.charging.sound","1");
                }

            }else if(action.equals(Intent.ACTION_POWER_CONNECTED)){
                if (DEBUG) Slog.d(TAG, "Intent.ACTION_POWER_CONNECTED DISMISS_PLUGOUT_DIALOG");
                mDialogHandler.sendEmptyMessage(DISMISS_PLUGOUT_DIALOG);
                mDialogHandler.removeMessages(SHOW_PLUGOUT_DIALOG);

            }else if(action.equals(Intent.ACTION_BOOT_COMPLETED)){
                mBootCompleted = true;
                SystemProperties.set("persist.shutdown.uncharger","-1");
            }else if(action.equals(Intent.ACTION_SHUTDOWN)){
                mBootCompleted = false;
                mDialogHandler.sendEmptyMessage(DISMISS_PLUGOUT_DIALOG);
            } else if(action.equals(ACTION_IPO_BOOT)) {
                SystemProperties.set("sys.suspend.ipo","0");
            }///@}
            else {
                Slog.w(TAG, "unknown intent: " + intent);
            }
        }
    };

    // PQLJ chenpengfei add 20160504 begin
    private boolean isPowerOffTest() {
        ActivityManager am= (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningTaskInfo> runningTasks = am.getRunningTasks(1);
        ComponentName comName = runningTasks.get(0).topActivity;
        Xlog.d(TAG, ">>>>>>>>>>>>>>>>> comName:"+comName.getClassName());
        if ("com.reallytek.wg.PowerOffActivity".equals(comName.getClassName())) {
            return true;
        } else {
            return false;
        }
    }
    // PQLJ chenpengfei add 20160504 end

    private boolean isObdTest() {
        ActivityManager am= (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningTaskInfo> runningTasks = am.getRunningTasks(1);
        ComponentName comName = runningTasks.get(0).topActivity;
        Xlog.d(TAG, ">>>>>>>>>>>>>>>>> comName:"+comName.getClassName());
        if ("com.reallytek.wg.ObdTestActivity".equals(comName.getClassName())) {
            return true;
        } else {
            return false;
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.print("mLowBatteryAlertCloseLevel=");
        pw.println(mLowBatteryAlertCloseLevel);
        pw.print("mLowBatteryReminderLevels=");
        pw.println(Arrays.toString(mLowBatteryReminderLevels));
        pw.print("mBatteryLevel=");
        pw.println(Integer.toString(mBatteryLevel));
        pw.print("mBatteryStatus=");
        pw.println(Integer.toString(mBatteryStatus));
        pw.print("mPlugType=");
        pw.println(Integer.toString(mPlugType));
        pw.print("mInvalidCharger=");
        pw.println(Integer.toString(mInvalidCharger));
        pw.print("mScreenOffTime=");
        pw.print(mScreenOffTime);
        if (mScreenOffTime >= 0) {
            pw.print(" (");
            pw.print(SystemClock.elapsedRealtime() - mScreenOffTime);
            pw.print(" ago)");
        }
        pw.println();
        pw.print("soundTimeout=");
        pw.println(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.LOW_BATTERY_SOUND_TIMEOUT, 0));
        pw.print("bucket: ");
        pw.println(Integer.toString(findBatteryLevelBucket(mBatteryLevel)));
        mWarnings.dump(pw);
    }

    public interface WarningsUI {
        void update(int batteryLevel, int bucket, long screenOffTime);
        void showSaverMode(boolean mode);
        void dismissLowBatteryWarning();
        void showLowBatteryWarning(boolean playSound);
        void dismissInvalidChargerWarning();
        void showInvalidChargerWarning();
        void updateLowBatteryWarning();
        boolean isInvalidChargerWarningShowing();
        void dump(PrintWriter pw);
        void userSwitched();
    }
     ///M:Add for plug out dialog @{
    private void showPlugoutDialog(boolean show,boolean updateMsg,String text,String title){
        if(show){
            if(plugOutDialog != null){
                mDialogHandler.removeMessages(SHOW_PLUGOUT_DIALOG);
                plugOutDialog.dismiss();
                plugOutDialog = null;
            }
            return;
        }

        if(updateMsg && (plugOutDialog == null)){
            if (DEBUG) Slog.d(TAG, "showPlugoutDialog message=" + text + " title=" + title);
            plugOutDialog = new AlertDialog.Builder(mContext)
                .setTitle(title)
                .setMessage(text)
                .setPositiveButton(com.android.internal.R.string.yes,new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //do sth
                        if (plugOutDialog != null) {
                            mDialogHandler.removeMessages(SHOW_PLUGOUT_DIALOG);
                            plugOutDialog.dismiss();
                            plugOutDialog = null;
                            mDialogHandler.sendEmptyMessage(DIALOG_REQUEST_SHUTDOWN);

                        }
                    }
                } )
                .setNegativeButton(com.android.internal.R.string.no,new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //do sth
                        if (plugOutDialog != null) {
                            mDialogHandler.removeMessages(SHOW_PLUGOUT_DIALOG);
                            plugOutDialog.dismiss();
                            plugOutDialog = null;
                        }
                    }
                } ).create();

            plugOutDialog.setCancelable(false);//blocking back key
            plugOutDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            plugOutDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            if (!plugOutDialog.isShowing()) {
                plugOutDialog.show();
            }

        }else if(updateMsg && plugOutDialog!=null){
            plugOutDialog.setMessage( text);

        }


    }

    private Handler mDelayHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
              switch (msg.what) {
                case CAN_PLAY_CHARGING_SOUND:
                    SystemProperties.set("sys.no.charging.sound","0");
                    mDelayHandler.removeMessages(CAN_PLAY_CHARGING_SOUND);
                    break;
              }
        }

    };

    private Handler mDialogHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
              switch(msg.what){
                case SHOW_PLUGOUT_DIALOG:
                    if(time >0){
                        boolean isCarCharger = false;
                        try {
                            isCarCharger = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.POWER_SUPPLY, 1) == 1;
                        } catch (NullPointerException ex) {
                            isCarCharger = false;
                        }
                        if (isCarCharger) {
                            if (mPlugType == 0) {
                                // strFormt = mContext.getResources().getString(R.string.show_dialog_message_shutdown);
                                strFormt = mContext.getResources().getString(R.string.show_dialog_message_suspend);
                            } else {
                                strFormt = mContext.getResources().getString(R.string.show_dialog_message_suspend);
                            }
                            title = mContext.getResources().getString(R.string.show_dialog_title);
                        } else {
                            strFormt = mContext.getResources().getString(R.string.show_dialog_message_suspend);
                            if (mPlugType == 0)
                                title = mContext.getResources().getString(R.string.show_dialog_title);
                            else
                                title = mContext.getResources().getString(R.string.show_dialog_title_acc);
                        }
                        message = String.format(strFormt,time--);
                        showPlugoutDialog(false,true,message,title);
                        mDialogHandler.sendEmptyMessageDelayed(SHOW_PLUGOUT_DIALOG,1000);
                    }else{
                        mDialogHandler.sendEmptyMessage(DIALOG_REQUEST_SHUTDOWN);
                        showPlugoutDialog(true,false,message,title);
                        time = 10;
                    }

                    break;
                case DISMISS_PLUGOUT_DIALOG:
                    showPlugoutDialog(true,false,message,title);
                    time = 10;
                    break;
                case DIALOG_REQUEST_SHUTDOWN:
                    shutDownRequest();
                    break;
              }

        }

    };

    private void shutDownRequest(){
        if(mContext == null)return;
        if("1".equals(SystemProperties.get("persist.shutdown.period"))){
            return ;
        }else{
            if (DEBUG) Slog.d(TAG, "shutDownRequest from systemui");
            SystemProperties.set("sys.suspend.ipo","1");
            Intent intent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
            intent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivityAsUser(intent, UserHandle.CURRENT);
            SystemProperties.set("persist.shutdown.uncharger","1");
            SystemProperties.set("persist.manual.poweroff","0");
        }
    }
    ///@}
}

