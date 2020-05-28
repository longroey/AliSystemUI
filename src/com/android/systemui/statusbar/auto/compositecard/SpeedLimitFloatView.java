package com.android.systemui.statusbar.auto.compositecard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.systemui.R;
import com.android.systemui.statusbar.auto.FontHelper;

public class SpeedLimitFloatView extends ParceableLayout {
	int mCameraType;
	int mLimitSpeed;
	boolean mOverSpeed;
	TextView mLimitSpeedView;
	ImageView mCameraIconView;
	public static final int CAMERA_TYPE_LIMIT = 0;
	public static final int CAMERA_TYPE_OTHER = 1;

	public SpeedLimitFloatView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		mLimitSpeedView = (TextView) findViewById(R.id.speed_limit_text);
		FontHelper.applyFont(getContext(), mLimitSpeedView, FontHelper.DIN_CONDENSED_BOLD);
		mCameraIconView = (ImageView) findViewById(R.id.camera_limit_icon);
	}

	/**
	 * short for setCameraType(type, -1, false);
	 * 
	 * @param type
	 */
	public void setCameraType(int type) {
		setCameraType(type, -1, false);
	}

	public void setCameraType(int type, int limitSpeed, boolean overspeed) {
		mLimitSpeed = limitSpeed;
		mOverSpeed = overspeed;
		mCameraType = type;
		updateCameraIconView();
		updateLimitSpeedView();
	}

	private int getCameraIconResId(int type) {
		int resId = 0;
		switch (type) {
		case 0:
		default:
			resId = isDriveMode() ? R.drawable.drivemode_drive_camera : R.drawable.home_drive_camera;
			break;
		}
		return resId;
	}

	private void updateLimitSpeedView() {
		int iconRes = 0;
		int color = -1;
		if (mOverSpeed) {
			iconRes = isDriveMode() 
					? R.drawable.drivemode_drive_speedlimit_highlight
					: R.drawable.home_drive_speedlimit_highlight;
			color = 0xFFFF213F;
		} else {
			iconRes = isDriveMode() 
					? R.drawable.drivemode_drive_speedlimit
					: R.drawable.home_drive_speedlimit;
			color = 0xFFD0D0D0;
		}
		mLimitSpeedView.setBackgroundResource(iconRes);
		mLimitSpeedView.setTextColor(color);
		if (mLimitSpeed <= 0) {// -1 or 0
			// not show limit speed view
			mLimitSpeedView.setVisibility(View.INVISIBLE);
		} else {
			// show limit speed view
			mLimitSpeedView.setVisibility(View.VISIBLE);
			mLimitSpeedView.setText(mLimitSpeed + "");
		}
	}

	private void updateCameraIconView() {
		mCameraIconView.setBackgroundResource(getCameraIconResId(mCameraType));
	}

	@Override
	void parseParams(String param) {
	}

	protected void switchDriveMode(boolean drivemode) {
		super.switchDriveMode(drivemode);
		updateCameraIconView();
		updateLimitSpeedView();
	}
}
