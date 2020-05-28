/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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
 * limitations under the License.
 */

package com.android.systemui.settings;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import com.android.systemui.utils.AliUserTrackUtil;

public class BrightnessController implements ToggleSlider.Listener {
    private static final String TAG = "StatusBar.BrightnessController";
    private static final boolean SHOW_AUTOMATIC_ICON = false;

    /**
     * {@link android.provider.Settings.System#SCREEN_AUTO_BRIGHTNESS_ADJ} uses the range [-1, 1].
     * Using this factor, it is converted to [0, BRIGHTNESS_ADJ_RESOLUTION] for the SeekBar.
     */
    private static final float BRIGHTNESS_ADJ_RESOLUTION = 100;
    private static final long DEBOUNCE_TIME = 200;
    private final int mMinimumBacklight;
    private final int mMaximumBacklight;

    private final Context mContext;
    private final ImageView mIcon;
    private final TextView mLabel;
    private final ToggleSlider mControl;
    private final boolean mAutomaticAvailable;
    private final IPowerManager mPower;
    private final CurrentUserTracker mUserTracker;
    private final Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_UPDATE_BRIGHT_PROVIDER:
                asyncUpdateBrightnessValue(msg.arg1);
                break;
            default:
                break;
            }
        }
    };
    private final BrightnessObserver mBrightnessObserver;

    private ArrayList<BrightnessStateChangeCallback> mChangeCallbacks =
            new ArrayList<BrightnessStateChangeCallback>();

    private boolean mAutomatic;
    private boolean mListening;
    private boolean mExternalChange;
    static final int MSG_UPDATE_BRIGHT_PROVIDER = 1000;

    public interface BrightnessStateChangeCallback {
        public void onBrightnessLevelChanged();
    }

    /** ContentObserver to watch brightness **/
    private class BrightnessObserver extends ContentObserver {

        private final Uri BRIGHTNESS_MODE_URI =
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE);
        private final Uri BRIGHTNESS_URI =
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS);
        private final Uri BRIGHTNESS_ADJ_URI =
                Settings.System.getUriFor(Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ);

        public BrightnessObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (selfChange) return;
            try {
                mExternalChange = true;
                if (BRIGHTNESS_MODE_URI.equals(uri)) {
                    updateMode();
                    updateSlider();
                } else if (BRIGHTNESS_URI.equals(uri) && !mAutomatic) {
                    updateSlider();
                } else if (BRIGHTNESS_ADJ_URI.equals(uri) && mAutomatic) {
                    updateSlider();
                } else {
                    updateMode();
                    updateSlider();
                }
                for (BrightnessStateChangeCallback cb : mChangeCallbacks) {
                    cb.onBrightnessLevelChanged();
                }
            } finally {
                mExternalChange = false;
            }
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.unregisterContentObserver(this);
            cr.registerContentObserver(
                    BRIGHTNESS_MODE_URI,
                    false, this, UserHandle.USER_ALL);
            cr.registerContentObserver(
                    BRIGHTNESS_URI,
                    false, this, UserHandle.USER_ALL);
            cr.registerContentObserver(
                    BRIGHTNESS_ADJ_URI,
                    false, this, UserHandle.USER_ALL);
        }

        public void stopObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.unregisterContentObserver(this);
        }

    }
    public BrightnessController(Context context, ImageView icon, ToggleSlider control) {
        this(context, icon, null, control);
    }
    public BrightnessController(Context context, ImageView icon, TextView label, ToggleSlider control) {
        mContext = context;
        mLabel = label;
        mIcon = icon;
        if(SHOW_AUTOMATIC_ICON){
            mIcon.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {
                    int on = mAutomatic ? 1 : 0;
                    AliUserTrackUtil.ctrlClicked("ControlCenter", "Click_Bright", on);
                    setMode(mAutomatic ? Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                            : Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
                    //Log.d(TAG, "---------onClick brightness icon mAutomatic = " + mAutomatic);
                    updateMode();
                    //Log.d(TAG, "---------update mode mAutomatic = " + mAutomatic);
                }
            });
        }else{
            mIcon.setClickable(false);
            mIcon.setFocusable(false);
        }


        mControl = control;
        mUserTracker = new CurrentUserTracker(mContext) {
            @Override
            public void onUserSwitched(int newUserId) {
                updateMode();
                updateSlider();
            }
        };
        mBrightnessObserver = new BrightnessObserver(mHandler);

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mMinimumBacklight = pm.getMinimumScreenBrightnessSetting();
        mMaximumBacklight = pm.getMaximumScreenBrightnessSetting();

        mAutomaticAvailable = context.getResources().getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available);
        mPower = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
    }

    private void asyncUpdateBrightnessValue(final int val){
        AsyncTask.execute(new Runnable() {
            public void run() {
                Settings.System.putIntForUser(mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, val,
                        UserHandle.USER_CURRENT);
                Log.d(TAG, "run asyncUpdateBrightnessValue val = " + val);
            }
        });
    }

    public void addStateChangedCallback(BrightnessStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    public boolean removeStateChangedCallback(BrightnessStateChangeCallback cb) {
        return mChangeCallbacks.remove(cb);
    }

    @Override
    public void onInit(ToggleSlider control) {
        // Do nothing
    }

    public void registerCallbacks() {
        if (mListening) {
            return;
        }

        mBrightnessObserver.startObserving();
        mUserTracker.startTracking();

        // Update the slider and mode before attaching the listener so we don't
        // receive the onChanged notifications for the initial values.
        updateMode();
        updateSlider();

        mControl.setOnChangedListener(this);
        mListening = true;
    }

    /** Unregister all call backs, both to and from the controller */
    public void unregisterCallbacks() {
        if (!mListening) {
            return;
        }

        mBrightnessObserver.stopObserving();
        mUserTracker.stopTracking();
        mControl.setOnChangedListener(null);
        mListening = false;
    }

    @Override
    public void onChanged(ToggleSlider view, boolean tracking, boolean automatic, int value) {
        updateIcon(mAutomatic);
        if (mExternalChange) return;
        Map<String, String> lmap = new HashMap<String, String>();
        lmap.put("brightness", value + "");
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currentTime = df.format(new Date(System.currentTimeMillis()));
        lmap.put("localtime", currentTime);
        AliUserTrackUtil.ctrlClicked("ControlCenter", "Drag_Brightness",lmap);
        //if(tracking){//start tracking
            if(mAutomatic){
                setMode(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Log.d(TAG, "start tracking change to manual mode! ");
            }
        //}
        //if (!mAutomatic) {
            final int val = value + mMinimumBacklight;
            setBrightness(val);
        //    if (!tracking) {
                mHandler.removeMessages(MSG_UPDATE_BRIGHT_PROVIDER);
                Message msg = mHandler.obtainMessage();
                msg.what = MSG_UPDATE_BRIGHT_PROVIDER;
                msg.arg1 = val;
                mHandler.sendMessageDelayed(msg,DEBOUNCE_TIME);
        //    }
       /* } else {
            final float adj = value / (BRIGHTNESS_ADJ_RESOLUTION / 2f) - 1;
            setBrightnessAdj(adj);
            if (!tracking) {
                AsyncTask.execute(new Runnable() {
                    public void run() {
                        Settings.System.putFloatForUser(mContext.getContentResolver(),
                                Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ, adj,
                                UserHandle.USER_CURRENT);
                    }
                });
            }
        }*/

        for (BrightnessStateChangeCallback cb : mChangeCallbacks) {
            cb.onBrightnessLevelChanged();
        }
    }

    private void setMode(int mode) {
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE, mode,
                mUserTracker.getCurrentUserId());
    }

    private void setBrightness(int brightness) {
        try {
            mPower.setTemporaryScreenBrightnessSettingOverride(brightness);
        } catch (RemoteException ex) {
        }
    }

    private void setBrightnessAdj(float adj) {
        try {
            mPower.setTemporaryScreenAutoBrightnessAdjustmentSettingOverride(adj);
        } catch (RemoteException ex) {
        }
    }

    private void updateIcon(boolean automatic) {
        if (mIcon != null) {
            mIcon.setImageResource(automatic && SHOW_AUTOMATIC_ICON ?
                    com.android.systemui.R.drawable.control_light_auto_bg :
                    com.android.systemui.R.drawable.control_light_bg);
        }
        if(mLabel != null){
            mLabel.setText(automatic && SHOW_AUTOMATIC_ICON ?
                     com.android.systemui.R.string.qs_label_auto_light
                     : com.android.systemui.R.string.qs_label_light);
        }
    }

    /** Fetch the brightness mode from the system settings and update the icon */
    private void updateMode() {
        if (mAutomaticAvailable) {
            int automatic;
            automatic = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                    UserHandle.USER_CURRENT);
            mAutomatic = automatic != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
            updateIcon(mAutomatic);
        } else {
            mControl.setChecked(false);
            updateIcon(false /*automatic*/);
            //M: [ALPS01748251] hide the Toggle slider
            mControl.hideToggle();
        }
    }

    /** Fetch the brightness from the system settings and update the slider */
    private void updateSlider() {
        if (mAutomatic) {
            float value = Settings.System.getFloatForUser(mContext.getContentResolver(),
                    Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ, 0,
                    UserHandle.USER_CURRENT);
            mControl.setMax((int) BRIGHTNESS_ADJ_RESOLUTION);
            mControl.setValue((int) ((value + 1) * BRIGHTNESS_ADJ_RESOLUTION / 2f));
            mControl.setEnabled(false);
        } else {
            int value;
            value = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, mMaximumBacklight,
                    UserHandle.USER_CURRENT);
            mControl.setMax(mMaximumBacklight - mMinimumBacklight);
            mControl.setValue(value - mMinimumBacklight);
            mControl.setEnabled(true);
        }
    }

}
