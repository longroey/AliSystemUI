package com.android.systemui.qs.tiles;

import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTile.Host;
import com.android.systemui.qs.QSTile.ResourceIcon;
import com.android.systemui.utils.AliUserTrackUtil;
import android.provider.Settings.Global;
import android.util.Log;

import com.android.systemui.qs.QSTile.AnimationIcon;
import com.android.systemui.qs.QSTile.BooleanState;
import com.android.systemui.R;

public class DrivingModeTile extends QSTile<QSTile.BooleanState> {

	private final GlobalSetting mSetting;
	private boolean mListening;
	private static final String GLOBAL_DRIVE_MODE_STATE = Global.DRIVE_MODE;//"drive_mode";
	public DrivingModeTile(Host host) {
		super(host);
		mSetting = new GlobalSetting(mContext, mHandler, /*Global.FM*/GLOBAL_DRIVE_MODE_STATE) {
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
		int on = mState.value ? 1 : 0;
		AliUserTrackUtil.ctrlClicked("Controlcenter", "Driving_Mode", on);
		setDrivingMode(!mState.value);
	}
	private void setDrivingMode(boolean eanble) {
        Global.putInt(mContext.getContentResolver(), /*Settings.Global.FM*/GLOBAL_DRIVE_MODE_STATE, eanble
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
        state.label = mContext.getString(R.string.qs_label_driving_mode);
        if (modeOn) {
            state.icon = ResourceIcon.get(R.drawable.control_drivemode_on_bg);
//            state.contentDescription =  mContext.getString(
//                    R.string.accessibility_quick_settings_airplane_on);
        } else {
            state.icon = ResourceIcon.get(R.drawable.control_drivemode_off_bg);
//            state.contentDescription =  mContext.getString(
//                    R.string.accessibility_quick_settings_airplane_off);
        }
	}

}
