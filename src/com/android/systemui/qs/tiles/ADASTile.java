package com.android.systemui.qs.tiles;

import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTile.Host;
import com.android.systemui.qs.QSTile.ResourceIcon;
import com.android.systemui.utils.AliUserTrackUtil;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings.Global;
import android.util.Log;

import com.android.systemui.qs.QSTile.AnimationIcon;
import com.android.systemui.qs.QSTile.BooleanState;
import com.android.systemui.R;

public class ADASTile extends QSTile<QSTile.BooleanState> {

    private final GlobalSetting mSetting;
    private boolean mListening;
    private static final String GLOBAL_ADAS_STATE = Global.ADAS;//"adas_enable"
    private static final int MSG_DEBOUNCE_SET_ADAS = 1001;
    private static final int DEBOUNCE_TIME = 1000;
    private static final String ADAS_SET_ACTION = "com.aliyun.DVRapp.ACTION";
    private static final String KEY_SET_ADAS = "setAdasKey";
    public ADASTile(Host host) {
        super(host);
        mSetting = new GlobalSetting(mContext, mHandler, /*Global.FM*/GLOBAL_ADAS_STATE) {
            @Override
            protected void handleValueChanged(int value) {
                if (DEBUG) {
                     Log.d(TAG, "handleValueChanged: " + value);
                }
                handleRefreshState(value);
            }
        };
    }
    private Handler mHandler = new Handler(){
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
            case MSG_DEBOUNCE_SET_ADAS:
                int on = msg.arg1;
                setADASOn(on);
                break;
            default:
                break;
            }
        }
    };
    private void setADASOn(int on){
        Intent intent = new Intent(ADAS_SET_ACTION);
        intent.putExtra(KEY_SET_ADAS, on);
        mContext.sendBroadcast(intent);
        updateSettings(on);
    }
    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        mSetting.setListening(listening);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        int on = mState.value ? 0 : 1;//toggle state.
        if(mHandler.hasMessages(MSG_DEBOUNCE_SET_ADAS)){
            mHandler.removeMessages(MSG_DEBOUNCE_SET_ADAS);
        }
        Message msg = mHandler.obtainMessage(MSG_DEBOUNCE_SET_ADAS);
        msg.arg1 = on;
        mHandler.sendMessageDelayed(msg, DEBOUNCE_TIME);
        AliUserTrackUtil.ctrlClicked("Controlcenter","ADAS", on);
    }
    private void updateSettings(int on) {
        Global.putInt(mContext.getContentResolver(), /*Settings.Global.FM*/GLOBAL_ADAS_STATE, on);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer)arg : mSetting.getValue();
        final boolean modeOn = value != 0;
        if (DEBUG) {
            Log.d(TAG, "handleUpdateState: " + modeOn + ", " + value + ", " + arg);
        }
        state.value = modeOn;
        state.visible = true;
        state.label = mContext.getString(R.string.qs_label_adas);
        if (modeOn) {
            state.icon = ResourceIcon.get(R.drawable.control_adas_on_bg);;
//            state.contentDescription =  mContext.getString(
//                    R.string.accessibility_quick_settings_airplane_on);
        } else {
            state.icon = ResourceIcon.get(R.drawable.control_adas_off_bg);;
//            state.contentDescription =  mContext.getString(
//                    R.string.accessibility_quick_settings_airplane_off);
        }
    }

}
