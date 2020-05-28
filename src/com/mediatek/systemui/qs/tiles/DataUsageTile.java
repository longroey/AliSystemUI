package com.mediatek.systemui.qs.tiles;

import android.content.ComponentName;
import android.content.Intent;

import com.android.systemui.qs.QSTile.SignalState;
import com.android.systemui.qs.tiles.CellularTile;
import com.android.systemui.R;

/**
 * Customize the DataUsageTile.
 *
 */
public class DataUsageTile extends CellularTile {
    private Icon mIcon;

    /**
     * Constructor.
     * @param host The QSTileHost.
     */
    public DataUsageTile(Host host) {
        super(host);
        mIcon = ResourceIcon.get(R.drawable.ic_qs_data_usage);
    }

    @Override
    public void setListening(boolean listening) {
    }

    @Override
    protected void handleUpdateState(SignalState state, Object arg) {
        state.visible = true;
        state.icon = mIcon;
        state.label = mContext.getString(R.string.data_usage);
        state.contentDescription = mContext.getString(R.string.data_usage);
    }
}