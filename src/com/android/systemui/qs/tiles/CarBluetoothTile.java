package com.android.systemui.qs.tiles;

import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTile.Host;
import com.android.systemui.qs.QSTile.ResourceIcon;
import com.android.systemui.utils.AliUserTrackUtil;
import android.content.ContentValues;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings.Global;
import android.util.Log;

import com.android.systemui.qs.QSTile.AnimationIcon;
import com.android.systemui.qs.QSTile.BooleanState;
import com.android.systemui.R;

public class CarBluetoothTile extends QSTile<QSTile.BooleanState> {
    //public static final String READ_PERMISSION = "com.aliyun.permission.READ_BLUETOOTH_STATE";
    //public static final String WRITE_PERMISSION = "com.aliyun.permission.WRITE_BLUETOOTH_STATE";
    public static final String AUTHOURITY ="com.yunos.ext";//"com.aliyun.bluetooth_phone";
    public static final String TABLE_NAME = "state";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_VALUE = "value";
    public static final String ACTION_CAR_BLUETOOTH_SETTING = "com.aliyun.bluetoothphone.intent.BLUETOOTH_SETTINGS";//"com.yunos.intent.BLUETOOTH_SETTINGS";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHOURITY + "/" + TABLE_NAME);
    public static final String KEY_ENABLE = "bt_enable";
    public static final String ACTION_BT_POWER = "com.yunos.intent.BT_POWER";
    public static final String EXTRA_POWER_OP = "power";
    //private final GlobalSetting mSetting;
    private int mBluetoothState = -1;
    private boolean mListening;
//    private static final String GLOBAL_CAR_BT_STATE = Global.BT_CLIENT_ON;//"bt_client_on"
    ContentObserver mBtObserver = new ContentObserver(null) {
        public void onChange(boolean selfChange) {
            int oldState = mBluetoothState;
            int updateState = getUpdateBtState();
            /*if (DEBUG) */Log.d(TAG, "updateContentResolver oldState = " + oldState
                    + ", updateState = " + updateState);
            if(updateState != oldState){
                handleRefreshState(mBluetoothState);
            }
        };
    };
    public CarBluetoothTile(Host host) {
        super(host);
//        mSetting = new GlobalSetting(mContext, mHandler, /*Global.FM*/GLOBAL_CAR_BT_STATE) {
//            @Override
//            protected void handleValueChanged(int value) {
//                if (DEBUG) {
//                     Log.d(TAG, "handleValueChanged: " + value);
//                }
//                handleRefreshState(value);
//            }
//        };
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
//        mSetting.setListening(listening);
        if(listening){
            mContext.getContentResolver().registerContentObserver(CONTENT_URI, true, mBtObserver);
            getUpdateBtState();
            handleRefreshState(mBluetoothState);
        }else{
            mContext.getContentResolver().unregisterContentObserver(mBtObserver);
        }
    }
    private int getUpdateBtState(){
        Cursor cursor = mContext.getContentResolver()
        		.query(Uri.withAppendedPath(CONTENT_URI, KEY_ENABLE), null,null, null, null);
        if(cursor != null){
            try {
                while (cursor.moveToNext()) {
                    mBluetoothState = cursor.getInt(cursor.getColumnIndex(COLUMN_VALUE));
                }
            } catch (Exception e){
                e.printStackTrace();
            } finally {
                cursor.close();
            }
        }
        Log.d(TAG, "getUpdateBtState mBluetoothState = " + mBluetoothState);
        return mBluetoothState;
    }

    private void updateContentResolver(int on){
        Intent intent = new Intent(ACTION_BT_POWER);
        intent.putExtra(EXTRA_POWER_OP,on);
        mContext.sendBroadcast(intent);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        AliUserTrackUtil.ctrlClicked("Controlcenter", "CarBluetooth", mBluetoothState);
        setCarBluetoothSwitch(mBluetoothState == 0 ? 1 : 0);
    }
    @Override
    protected void handleLongClick() {
        super.handleLongClick();
        mHost.startSettingsActivity(new Intent(ACTION_CAR_BLUETOOTH_SETTING));
    }
    private void setCarBluetoothSwitch(int on) {
        updateContentResolver(on);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer)arg : mBluetoothState;
        final boolean modeOn = value != 0;
        if (DEBUG) {
            Log.d(TAG, "handleUpdateState: " + modeOn + ", " + value + ", " + arg);
        }
        state.value = modeOn;
        state.visible = true;
        state.label = mContext.getString(R.string.qs_label_car_bluetooth);
        if (modeOn) {
            state.icon = ResourceIcon.get(R.drawable.control_buletooth_on_bg);
//            state.contentDescription =  mContext.getString(
//                    R.string.accessibility_quick_settings_airplane_on);
        } else {
            state.icon = ResourceIcon.get(R.drawable.control_buletooth_off_bg);
//            state.contentDescription =  mContext.getString(
//                    R.string.accessibility_quick_settings_airplane_off);
        }
    }

}
