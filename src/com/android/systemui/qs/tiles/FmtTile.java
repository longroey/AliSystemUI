package com.android.systemui.qs.tiles;

import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTile.Host;
import com.android.systemui.qs.QSTile.ResourceIcon;
import com.android.systemui.utils.AliUserTrackUtil;

import android.content.Intent;
import android.provider.Settings.Global;
import android.util.Log;

import com.android.systemui.qs.QSTile.AnimationIcon;
import com.android.systemui.qs.QSTile.BooleanState;
import com.android.systemui.R;

public class FmtTile extends QSTile<QSTile.BooleanState> {
	private final GlobalSetting mSetting;
	private boolean mListening;
	private static final String ACTION_FMT_SETTINGS = "android.intent.action.fm_settings";
	private static final String GLOBAL_FM_STATE = Global.FM;//"fm_enable";
	public FmtTile(Host host) {
		super(host);
		mSetting = new GlobalSetting(mContext, mHandler, /*Global.FM*/GLOBAL_FM_STATE) {
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
		AliUserTrackUtil.ctrlClicked("Controlcenter", "FM", on);
		setFmSwitch(!mState.value);
	}
	@Override
	protected void handleLongClick() {
		super.handleLongClick();
    	mHost.startSettingsActivity(new Intent(ACTION_FMT_SETTINGS));
	}
	private void setFmSwitch(boolean eanble) {
        Global.putInt(mContext.getContentResolver(), /*Settings.Global.FM*/GLOBAL_FM_STATE, eanble
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
        state.label = mContext.getString(R.string.qs_label_fmt);
        if (modeOn) {
            state.icon = ResourceIcon.get(R.drawable.control_fm_on_bg);
//            state.contentDescription =  mContext.getString(
//                    R.string.accessibility_quick_settings_airplane_on);
        } else {
            state.icon = ResourceIcon.get(R.drawable.control_fm_off_bg);
//            state.contentDescription =  mContext.getString(
//                    R.string.accessibility_quick_settings_airplane_off);
        }
	}

}
