package com.android.systemui.simlock;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.app.Dialog;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.PhoneStatusBar;

import java.io.PrintWriter;
import java.text.NumberFormat;

public class SimLockScreenWarnings implements SimLockScreen.Warnings {
    private static final String TAG = "SimLockScreenWarnings";
    private static final boolean DEBUG = true;

    private final Context mContext;
    private final PowerManager mPowerManager;
    private final AudioManager mAudioManager;
    private final WifiManager mWifiManager;
    private final ConnectivityManager mConnectivityManager;
    private final Handler mHandler = new Handler();
    private final Receiver mReceiver = new Receiver();
    private SimLockDialog mSimLockDialog;

    public static final int ICC_PUK_RETRY_COUNT_ZERO = 100;
    public static final int ICC_PIN_RETRY_COUNT_ZERO = 101;
    public static final int ICC_IDENTIFY = 102;
    public static final int ICC_EXPIRED = 103;
    public static final int ICC_ILLEGAL = 104;
    /// {@See IccCardConstants#State}
    /**
     * SIM card state: Unknown. Signifies that the SIM is in transition
     * between states. For example, when the user inputs the SIM pin
     * under PIN_REQUIRED state, a query for sim status returns
     * this state before turning to SIM_STATE_READY.
     */
    private static final int ICC_UNKNOWN = 0;
    /** SIM card state: no SIM card is available in the device */
    private static final int ICC_ABSENT = 1;
    /** SIM card state: Locked: requires the user's SIM PIN to unlock */
    private static final int ICC_PIN_REQUIRED = 2;
    /** SIM card state: Locked: requires the user's SIM PUK to unlock */
    private static final int ICC_PUK_REQUIRED = 3;
    /** SIM card state: Locked: requires a network PIN to unlock */
    private static final int ICC_NETWORK_LOCKED = 4;
    /** SIM card state: Ready */
    private static final int ICC_READY = 5;
    /** SIM card state: SIM Card is NOT READY */
    private static final int ICC_NOT_READY = 6;
    /** SIM card state: SIM Card Error, permanently disabled */
    private static final int ICC_PERM_DISABLED = 7;
    /** SIM card state: SIM Card Error, present but faulty */
    private static final int ICC_CARD_IO_ERROR = 8;


    public SimLockScreenWarnings(Context context) {
        mContext = context;
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public void requestShowDialog(int simState) {
        if (DEBUG) {
            Log.d(TAG, "requestShowDialog: " + simState);
        }
        if (simState == ICC_READY) {
            if (mSimLockDialog != null) {
                mSimLockDialog.dismiss();
                mSimLockDialog = null;
                resumeFeature();
                mReceiver.unInit();
            }
        } else {
            if (mSimLockDialog == null) {
                mSimLockDialog = new SimLockDialog(mContext);
                mSimLockDialog.setCancelable(false);
                mSimLockDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SIMLOCK);
                mSimLockDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                pauseFeature();
                mReceiver.init();
            }
            mSimLockDialog.update(simState);
            if (!isShowing()) {
                mSimLockDialog.show();
            }
            //mPowerManager.wakeUp(SystemClock.uptimeMillis());
            //mPowerManager.setBacklightOffForWfd(false);
        }
    }


    @Override
    public boolean isShowing() {
        if (mSimLockDialog != null) {
            return mSimLockDialog.isShowing();
        }
        return false;
    }

    private boolean mHotspotState;
    private int mFMState;
    private void pauseFeature() {
        //pause volume
        mAudioManager.setMasterMute(true, ~AudioManager.FLAG_SHOW_UI);

        //pause TTS
        Settings.Global.putInt(mContext.getContentResolver(), "voice_wakeup_switch", 0);
        Intent txzIntent = new Intent("com.txznet.txz.wakeup.disable");
        txzIntent.putExtra("txz", true);
        mContext.sendBroadcast(txzIntent);

        //pause dvr
        Intent dvrIntent = new Intent("com.android.systemui.SIMLOCK");
        dvrIntent.putExtra("SIM_LOCK_STATE", 1);
        mContext.sendBroadcast(dvrIntent);

        //pause FM transmission
        mFMState = Settings.Global.getInt(mContext.getContentResolver(), "fm_enable", 0);
        if (mFMState == 1) {
           Settings.Global.putInt(mContext.getContentResolver(), "fm_enable", 0);
        }

        //pause hotspot
        if (isHotspotSupported()) {
            mHotspotState = isHotspotEnabled();
            setHotspotEnabled(false);
        }
    }

    private void resumeFeature() {
        //resume hotspot
        if (isHotspotSupported()) {
            setHotspotEnabled(mHotspotState);
        }

        //resume FM transmission
        Settings.Global.putInt(mContext.getContentResolver(), "fm_enable", mFMState);

        //resume dvr
        Intent dvrIntent = new Intent("com.android.systemui.SIMLOCK");
        dvrIntent.putExtra("SIM_LOCK_STATE", 0);
        mContext.sendBroadcast(dvrIntent);

        //resume TTS
        Settings.Global.putInt(mContext.getContentResolver(), "voice_wakeup_switch", 1);
        Intent txzIntent = new Intent("com.txznet.txz.wakeup.enable");
        mContext.sendBroadcast(txzIntent);

        //resume volume
        mAudioManager.setMasterMute(false, ~AudioManager.FLAG_SHOW_UI);
    }

    private boolean isHotspotSupported() {
        final boolean isSecondaryUser = ActivityManager.getCurrentUser() != UserHandle.USER_OWNER;
        return !isSecondaryUser && mConnectivityManager.isTetheringSupported();
    }

    private boolean isHotspotEnabled() {
        return mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED;
    }

    private void setHotspotEnabled(boolean enabled) {
        //final ContentResolver cr = mContext.getContentResolver();
        // Call provisioning app which is called when enabling Tethering from Settings
        if (enabled) {
            int wifiState = mWifiManager.getWifiState();
            if ((wifiState == WifiManager.WIFI_STATE_ENABLING) ||
                    (wifiState == WifiManager.WIFI_STATE_ENABLED)) {
                mWifiManager.setWifiEnabled(false);
                //Settings.Global.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 1);
            }
            mWifiManager.setWifiApEnabled(null, true);
        } else {
            mWifiManager.setWifiApEnabled(null, false);
            //if (Settings.Global.getInt(cr, Settings.Global.WIFI_SAVED_STATE, 0) == 1) {
            //    mWifiManager.setWifiEnabled(true);
            //    Settings.Global.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 0);
            //}
        }
    }

    private final class Receiver extends BroadcastReceiver {

        public void init() {
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.intent.action.ACTION_SHUTDOWN_IPO");
            filter.addAction(Intent.ACTION_SHUTDOWN);
            mContext.registerReceiver(this, filter, null, mHandler);
        }

        public void unInit() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DEBUG) Log.d(TAG, "onReceive, action:" + action);
            if (action.equals("android.intent.action.ACTION_SHUTDOWN_IPO")
                        || action.equals(Intent.ACTION_SHUTDOWN)) {
                if (mSimLockDialog != null) {
                    if (DEBUG) Log.d(TAG, "receive shutdown broadcast, should resume feature.");
                    mSimLockDialog.dismiss();
                    mSimLockDialog = null;
                }
                if (isHotspotSupported()) {
                    setHotspotEnabled(mHotspotState);
                }
                Settings.Global.putInt(mContext.getContentResolver(), "fm_enable", mFMState);
            }
        }
    };

    private class SimLockDialog extends Dialog {
        private Context context;
        private TextView simLockTile;
        private TextView simLockMsg;

        public SimLockDialog(Context context) {
            this(context, com.android.internal.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        }

        public SimLockDialog(Context context, int theme) {
            super(context, theme);
            this.context = context;
            setContentView(R.layout.simlock_dialog);
            simLockTile = (TextView) this.findViewById(R.id.simlock_title);
            simLockMsg = (TextView) this.findViewById(R.id.simlock_msg);
        }

        private void update(int simState) {
            switch (simState) {
                case ICC_ABSENT:
                    synchronized (this) {
                        if (DEBUG) Log.d(TAG, "update, ICC_ABSENT we need to show no sim.");
                        simLockTile.setText(context.getResources().getString(R.string.no_sim_card_title));
                        simLockMsg.setText(context.getResources().getString(R.string.illegal_or_no_sim_card_msg));
                    }
                    break;
                case ICC_PIN_REQUIRED:
                case ICC_PUK_REQUIRED:
                case ICC_NETWORK_LOCKED:
                case ICC_PUK_RETRY_COUNT_ZERO:
                case ICC_PIN_RETRY_COUNT_ZERO:
                case ICC_ILLEGAL:
                    synchronized (this) {
                        if (DEBUG) Log.d(TAG, "update, we need to show the simlock.");
                        simLockTile.setText(context.getResources().getString(R.string.illegal_sim_card_title));
                        simLockMsg.setText(context.getResources().getString(R.string.illegal_or_no_sim_card_msg));
                    }
                    break;
                case ICC_IDENTIFY:
                    synchronized (this) {
                        if (DEBUG) Log.d(TAG, "update, sim card is identifing...");
                        simLockTile.setText(context.getResources().getString(R.string.identify_sim_card_title));
                        simLockMsg.setText(context.getResources().getString(R.string.identify_sim_card_msg));
                    }
                    break;
                case ICC_EXPIRED:
                    synchronized (this) {
                        if (DEBUG) Log.d(TAG, "update, sim card is expired.");
                        simLockTile.setText(context.getResources().getString(R.string.sim_card_expired_title));
                        simLockMsg.setText(context.getResources().getString(R.string.sim_card_expired_msg));
                    }
                    break;
                case ICC_PERM_DISABLED:
                    synchronized (this) {
                        if (DEBUG) Log.d(TAG, "update, sim card permanently disabled.");
                        simLockTile.setText(context.getResources().getString(R.string.sim_card_perm_disabled_title));
                        simLockMsg.setText(context.getResources().getString(R.string.sim_card_perm_disabled_msg));
                    }
                    break;
                case ICC_CARD_IO_ERROR:
                    synchronized (this) {
                        if (DEBUG) Log.d(TAG, "update, sim card io error.");
                        simLockTile.setText(context.getResources().getString(R.string.sim_card_io_error_title));
                        simLockMsg.setText(context.getResources().getString(R.string.sim_card_io_error_msg));
                    }
                    break;
                case ICC_UNKNOWN:
                case ICC_NOT_READY:
                default:
                    if (DEBUG) Log.v(TAG, "Ignoring state: " + simState);
                    break;
            }
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event){
            switch(keyCode){
                case KeyEvent.KEYCODE_POWER:
                    new Thread() {
                        public void run() {
                            try {
                                Instrumentation inst = new Instrumentation();
                                inst.sendPointerSync(MotionEvent.obtain(SystemClock.uptimeMillis(),
                                        SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 0, 0, 0));
                                inst.sendPointerSync(MotionEvent.obtain(SystemClock.uptimeMillis(),
                                        SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 0, 0, 0));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }.start();
                    return true;
                default:
                    break;
            }
            return false;
        }

    }

}
