package com.android.systemui.qs.tiles;

import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTile.Host;
import com.android.systemui.qs.QSTile.BooleanState;

public class AutoLightTile extends QSTile<QSTile.BooleanState> {

	public AutoLightTile(Host host) {
		super(host);
	}

	@Override
	public void setListening(boolean listening) {
		
	}

	@Override
	protected BooleanState newTileState() {
		return new BooleanState();
	}

	@Override
	protected void handleClick() {
		
	}

	@Override
	protected void handleUpdateState(BooleanState state, Object arg) {
		
	}

}
