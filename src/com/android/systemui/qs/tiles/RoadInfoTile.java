package com.android.systemui.qs.tiles;

import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTile.Host;
import com.android.systemui.utils.AliUserTrackUtil;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.provider.Settings.Global;
import android.util.Log;

import com.android.systemui.qs.QSTile.AnimationIcon;
import com.android.systemui.qs.QSTile.BooleanState;
import com.android.systemui.R;

public class RoadInfoTile extends QSTile<QSTile.BooleanState> {
    private static final String GLOBAL_ROADINFO_STATE = Global.WATCH_DOG;//"watch_dog";
    private final GlobalSetting mSetting;
//  private final ResourceIcon mEnable = ResourceIcon.get(R.drawable.control_safe_on);
//    private final ResourceIcon mDisable = ResourceIcon.get(R.drawable.control_safe_off);
    private boolean mListening;
    public RoadInfoTile(Host host) {
        super(host);
        mSetting = new GlobalSetting(mContext, mHandler, GLOBAL_ROADINFO_STATE) {
            @Override
            protected void handleValueChanged(int value) {
                if (DEBUG) {
                     Log.d(TAG, "handleValueChanged: " + value);
                }
                handleRefreshState(value);
            }
        };
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        int on = mState.value ? 1 : 0;
        AliUserTrackUtil.ctrlClicked("Controlcenter", "Edog", on);
        //add by wei.cao for setWatchDogState  begin
        //setRoadInfoSwitch(!mState.value);
        if (!mState.value) {
            mState.icon = ResourceIcon.get(R.drawable.control_safe_on_bg);

        } else {
            mState.icon = ResourceIcon.get(R.drawable.control_safe_off_bg);

        }
        setWatchDogState(!mState.value,mContext);
        //add by wei.cao for setWatchDogState  begin
    }
    private void setRoadInfoSwitch(boolean eanble) {
        Global.putInt(mContext.getContentResolver(), /*Settings.Global.FM*/GLOBAL_ROADINFO_STATE, eanble
                ? 1
                : 0);
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
        state.label = mContext.getString(R.string.qs_label_roadinfo);
        if (modeOn) {
            state.icon = ResourceIcon.get(R.drawable.control_safe_on_bg);
//            state.contentDescription =  mContext.getString(
//                    R.string.accessibility_quick_settings_airplane_on);

        } else {
            state.icon = ResourceIcon.get(R.drawable.control_safe_off_bg);
//            state.contentDescription =  mContext.getString(
//                    R.string.accessibility_quick_settings_airplane_off);
        }



    }
     public void setListening(boolean listening) {
            if (mListening == listening) return;
            mListening = listening;
            mSetting.setListening(listening);
        }

    //add by wei.cao for setWatchDogState  begin
    private void setWatchDogState(boolean isChecked,Context mContext){
        Log.d(TAG, "-------systemUI--:" + isChecked);
        if(isChecked){
            Intent intent = new Intent("AUTONAVI_STANDARD_BROADCAST_RECV");
            intent.putExtra("KEY_TYPE", 10064);
            intent.putExtra("EXTRA_TYPE", 0);
            intent.putExtra("EXTRA_OPERA", 0);
            mContext.sendBroadcast(intent);

            Intent intent_bg_dog = new Intent("AUTONAVI_STANDARD_BROADCAST_RECV");
            intent_bg_dog.putExtra("KEY_TYPE", 12006);
            intent_bg_dog.putExtra("EXTRA_SETTING_TYPE", 1);
            intent_bg_dog.putExtra("EXTRA_SETTING_RESULT", true);
            mContext.sendBroadcast(intent_bg_dog);
            Log.d(TAG, "setWatchDogState:" + isChecked);
        }else{
            Intent intent = new Intent("AUTONAVI_STANDARD_BROADCAST_RECV");
            intent.putExtra("KEY_TYPE", 10064);
            intent.putExtra("EXTRA_TYPE", 0);
            intent.putExtra("EXTRA_OPERA", 1);
            mContext.sendBroadcast(intent);

            Intent intent_bg_dog = new Intent("AUTONAVI_STANDARD_BROADCAST_RECV");
            intent_bg_dog.putExtra("KEY_TYPE", 12006);
            intent_bg_dog.putExtra("EXTRA_SETTING_TYPE", 1);
            intent_bg_dog.putExtra("EXTRA_SETTING_RESULT", false);
            mContext.sendBroadcast(intent_bg_dog);
            Log.d(TAG, "setWatchDogState:" + isChecked);
        }
    }
    //add by wei.cao for setWatchDogState  end

}
