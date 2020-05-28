/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.qs.tiles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTileView;
import com.android.systemui.qs.SignalTileView;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.MobileDataController;
import com.android.systemui.statusbar.policy.NetworkController.MobileDataController.DataUsageInfo;
import com.android.systemui.utils.AliUserTrackUtil;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;
import com.mediatek.xlog.Xlog;

/** Quick settings tile: Cellular **/
public class CellularTile extends QSTile<QSTile.SignalState> {
    private static final Intent CELLULAR_SETTINGS = new Intent().setAction("android.settings.DATA_ROAMING_SETTINGS");

    private static final String TAG = "CellularTile";
    private static final boolean DBG = false;

    private final NetworkController mController;
    private final MobileDataController mDataController;
    private final CellularDetailAdapter mDetailAdapter;

    public CellularTile(Host host) {
        super(host);
        mController = host.getNetworkController();
        mDataController = mController.getMobileDataController();
        mDetailAdapter = new CellularDetailAdapter();
    }

    @Override
    protected SignalState newTileState() {
        return new SignalState();
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mController.addNetworkSignalChangedCallback(mCallback);
        } else {
            mController.removeNetworkSignalChangedCallback(mCallback);
        }
    }

    @Override
    public QSTileView createTileView(Context context) {
        return new SignalTileView(context);
    }

    @Override
    protected void handleClick() {
//        if (mDataController.isMobileDataSupported()) {
//            showDetail(true);
//        } else {
//            mHost.startSettingsActivity(CELLULAR_SETTINGS);
//        }
    	int value = -1;
        if (mDataController.isMobileDataSupported()) {
        	boolean enable = mDataController.isMobileDataEnabled();
        	value = enable ? 1 : 0;
            mDataController.setMobileDataEnabled(!enable);
			mContext.sendBroadcast(new Intent("android.intent.action.ANY_DATA_STATE"));//add by zhouguo at 20170616
        }else{
            Log.d(TAG, "handleClick sim card is not ready or not support mobile");
        }
        AliUserTrackUtil.ctrlClicked("Controlcenter", "Mobile_Data", value);
    }
    
    @Override
    protected void handleLongClick() {
    	super.handleLongClick();
    	if (mDataController.isMobileDataSupported()){
    		mHost.startSettingsActivity(CELLULAR_SETTINGS);
    	}
    }


    @Override
    protected void handleUpdateState(SignalState state, Object arg) {
        state.visible = mController.hasMobileDataFeature();
        if (!state.visible) return;
        final CallbackInfo cb = (CallbackInfo) arg;
        if (cb == null) return;
        final Resources r = mContext.getResources();
        final int iconId = cb.noSim || cb.airplaneModeEnabled ? R.drawable.control_data_disable
                : !cb.dataEnabled  ? R.drawable.control_data_off_bg
                : R.drawable.control_data_on_bg;
        state.icon = ResourceIcon.get(iconId);
        if(DBG){
            Log.d(TAG, "handleUpdateState cb = " + cb.toString());
            Log.d(TAG, "handleUpdateState iconId = " + iconId);
        }
        state.isOverlayIconWide = cb.isDataTypeIconWide;
        state.autoMirrorDrawable = !cb.noSim;
//        state.overlayIconId = cb.enabled && (cb.dataTypeIconId > 0) ? cb.dataTypeIconId : 0;
        state.filter = iconId != R.drawable.ic_qs_no_sim;
//        state.activityIn = cb.dataEnabled && cb.activityIn;
//        state.activityOut = cb.dataEnabled && cb.activityOut;
        final boolean enabled = mDataController.isMobileDataSupported()
                && !cb.noSim && !cb.airplaneModeEnabled && cb.dataEnabled;
        final boolean dataConnected = enabled && mDataController.isMobileDataEnabled()
                && (cb.mobileSignalIconId > 0) && (cb.enabledDesc != null);
        state.label = r.getString(R.string.qs_label_mobile_data);
//        state.label = cb.noSim || cb.airplaneModeEnabled ? r.getString(R.string.qs_label_mobile_data) : r.getString(R.string.qs_label_mobile_data);
        state.connected = enabled && dataConnected;

        final String signalContentDesc = cb.dataEnabled && (cb.mobileSignalIconId > 0)
                ? cb.signalContentDescription
                : r.getString(R.string.accessibility_no_signal);
        final String dataContentDesc = cb.dataEnabled && (cb.dataTypeIconId > 0) && !cb.wifiEnabled
                ? cb.dataContentDescription
                : r.getString(R.string.accessibility_no_data);
        state.contentDescription = r.getString(
                R.string.accessibility_quick_settings_mobile,
                signalContentDesc, dataContentDesc,
                state.label);
    }

    // Remove the period from the network name
    public static String removeTrailingPeriod(String string) {
        if (string == null) return null;
        final int length = string.length();
        if (string.endsWith(".")) {
            return string.substring(0, length - 1);
        }
        return string;
    }

    private static final class CallbackInfo {
        boolean dataEnabled;
        boolean wifiEnabled;
        boolean wifiConnected;
        boolean airplaneModeEnabled;
        int mobileSignalIconId;
        String signalContentDescription;
        int dataTypeIconId;
        String dataContentDescription;
        boolean activityIn;
        boolean activityOut;
        String enabledDesc;
        boolean noSim;
        boolean isDataTypeIconWide;
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("CallbackInfo: ");
            sb.append(", enabled : " + dataEnabled);
            sb.append(", wifiEnabled : " + wifiEnabled);
            sb.append(", wifiConnected : " + wifiConnected);
            sb.append(", airplaneModeEnabled : " + airplaneModeEnabled);
            sb.append(", mobileSignalIconId : " + mobileSignalIconId);
            sb.append(", signalContentDescription : " + signalContentDescription);
            sb.append(", dataTypeIconId : " + dataTypeIconId);
            sb.append(", dataContentDescription : " + dataContentDescription);
            sb.append(", activityIn : " + activityIn);
            sb.append(", activityOut : " + activityOut);
            sb.append(", enabledDesc : " + enabledDesc);
            sb.append(", noSim : " + noSim);
            sb.append(", isDataTypeIconWide : " + isDataTypeIconWide);
            return sb.toString();
        }
    }

    private final NetworkSignalChangedCallback mCallback = new NetworkSignalChangedCallback() {
        private final CallbackInfo mInfo = new CallbackInfo();

        @Override
        public void onWifiSignalChanged(boolean enabled, boolean connected, int wifiSignalIconId,
                boolean activityIn, boolean activityOut,
                String wifiSignalContentDescriptionId, String description) {
            mInfo.wifiEnabled = enabled;
            mInfo.wifiConnected = connected;
            refreshState(mInfo);
        }

        @Override
        public void onMobileDataSignalChanged(boolean enabled,
                int mobileSignalIconId,
                String mobileSignalContentDescriptionId, int dataTypeIconId,
                boolean activityIn, boolean activityOut,
                String dataTypeContentDescriptionId, String description,
                boolean isDataTypeIconWide) {
            if(DBG)
                Log.d(TAG, "onMobileDataSignalChanged enabled = " + enabled
                + ", mDataController.isMobileDataEnabled() = " + mDataController.isMobileDataEnabled());
//get enable value from telephony manager, it is correct.
            mInfo.dataEnabled = mDataController.isMobileDataEnabled();
            mInfo.mobileSignalIconId = mobileSignalIconId;
            mInfo.signalContentDescription = mobileSignalContentDescriptionId;
            mInfo.dataTypeIconId = dataTypeIconId;
            mInfo.dataContentDescription = dataTypeContentDescriptionId;
            mInfo.activityIn = activityIn;
            mInfo.activityOut = activityOut;
            mInfo.enabledDesc = description;
            mInfo.isDataTypeIconWide = isDataTypeIconWide;
            if (DBG) {
                Xlog.d(TAG, "onMobileDataSignalChanged info.enabled = " + mInfo.dataEnabled +
                    " mInfo.mobileSignalIconId = " + mInfo.mobileSignalIconId +
                    " mInfo.signalContentDescription = " + mInfo.signalContentDescription +
                    " mInfo.dataTypeIconId = " + mInfo.dataTypeIconId +
                    " mInfo.dataContentDescription = " + mInfo.dataContentDescription +
                    " mInfo.activityIn = " + mInfo.activityIn +
                    " mInfo.activityOut = " + mInfo.activityOut +
                    " mInfo.enabledDesc = " + mInfo.enabledDesc +
                    " mInfo.isDataTypeIconWide = " + mInfo.isDataTypeIconWide);
            }
            refreshState(mInfo);
        }

        @Override
        public void onNoSimVisibleChanged(boolean visible) {
            mInfo.noSim = visible;
            if (mInfo.noSim) {
                // Make sure signal gets cleared out when no sims.
                mInfo.mobileSignalIconId = 0;
                mInfo.dataTypeIconId = 0;
                // Show a No SIMs description to avoid emergency calls message.
                mInfo.dataEnabled = false;
                mInfo.enabledDesc = mContext.getString(
                        R.string.keyguard_missing_sim_message_short);
                mInfo.signalContentDescription = mInfo.enabledDesc;

                Xlog.d(TAG, "NoSim");
            }
            refreshState(mInfo);
        }

        @Override
        public void onAirplaneModeChanged(boolean enabled) {
            mInfo.airplaneModeEnabled = enabled;
            refreshState(mInfo);
        }

        public void onMobileDataEnabled(boolean enabled) {
            if(DBG)
                Log.d(TAG, "onMobileDataEnabled enabled = " + enabled);
            mDetailAdapter.setMobileDataEnabled(enabled);
            mInfo.dataEnabled = enabled;
            refreshState(mInfo);
        }
    };

    private final class CellularDetailAdapter implements DetailAdapter {

        @Override
        public int getTitle() {
            return R.string.quick_settings_cellular_detail_title;
        }

        @Override
        public Boolean getToggleState() {
            return mDataController.isMobileDataSupported()
                    ? mDataController.isMobileDataEnabled()
                    : null;
        }

        @Override
        public Intent getSettingsIntent() {
            return CELLULAR_SETTINGS;
        }

        @Override
        public void setToggleState(boolean state) {
            mDataController.setMobileDataEnabled(state);
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            final DataUsageDetailView v = (DataUsageDetailView) (convertView != null
                    ? convertView
                    : LayoutInflater.from(mContext).inflate(R.layout.data_usage, parent, false));
            final DataUsageInfo info = mDataController.getDataUsageInfo();
            if (info == null) return v;
            v.bind(info);
            return v;
        }

        public void setMobileDataEnabled(boolean enabled) {
            fireToggleStateChanged(enabled);
        }
    }
}
