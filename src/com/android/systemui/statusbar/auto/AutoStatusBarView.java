package com.android.systemui.statusbar.auto;

import com.android.systemui.statusbar.phone.BarTransitions;
import com.android.systemui.statusbar.phone.PhoneStatusBar;

import android.content.Context;
import android.util.AttributeSet;
import android.util.EventLog;
import android.util.Log;

import com.android.systemui.EventLogTags;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;

public class AutoStatusBarView extends PanelBar {

	private static final boolean DEBUG_GESTURES = false;
	public PhoneStatusBar mBar;
	private AutoStatusBarTransitions mBarTransitions;
	PanelView mLastFullyOpenedPanel = null;

	public AutoStatusBarView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mBarTransitions = new AutoStatusBarTransitions(this);
	}

	public void setBar(PhoneStatusBar bar) {
		mBar = bar;
	}
        private boolean factoryModeOn(){
            return "true".equals(android.os.SystemProperties.get("sys.factory_mode_on","false"));
        }
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if(!mBar.isDeviceProvisioned() || factoryModeOn()){
			Log.e(TAG, "device not provisioned or factory mode, ignore AutoStatusBarView touch event");
			return true;
		}
		//add by sining.huang 2018.09.13 for control statusbar expand --start
		if (!mBar.getStatusBarEnable()) {
			return true;
		}
		//add by sining.huang 2018.09.13 for control statusbar expand --end
		boolean barConsumedEvent = mBar.interceptTouchEvent(event);

		if (DEBUG_GESTURES) {
			if (event.getActionMasked() != MotionEvent.ACTION_MOVE) {
				EventLog.writeEvent(EventLogTags.SYSUI_PANELBAR_TOUCH, event.getActionMasked(), (int) event.getX(),
						(int) event.getY(), barConsumedEvent ? 1 : 0);
			}
		}

		return barConsumedEvent || super.onTouchEvent(event);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		if(!mBar.isDeviceProvisioned()){
			Log.e(TAG, "device not provisioned, ignore AutoStatusBarView onInterceptTouchEvent");
			return true;
		}
		return mBar.interceptTouchEvent(event) || super.onInterceptTouchEvent(event);
	}
	public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }


	/**---------------override------**/
	@Override
    public void onPanelPeeked() {
        super.onPanelPeeked();
        mBar.makeExpandedVisible(false);
    }

    @Override
    public void onAllPanelsCollapsed() {
        super.onAllPanelsCollapsed();

        // Close the status bar in the next frame so we can show the end of the animation.
        postOnAnimation(new Runnable() {
            @Override
            public void run() {
                mBar.makeExpandedInvisible();
            }
        });
        mLastFullyOpenedPanel = null;
    }

    public void updateRecordStatus(boolean start){
    	Log.d(TAG, "updateRecordStatus start = " + start);
    }

    public void updateTalkingBar(int state, long time){
    	Log.d(TAG, "updateTalkingBar state = " + state + ", talking time = " + time);
    }
    /*

    @Override
    public void onPanelFullyOpened(PanelView openPanel) {
        super.onPanelFullyOpened(openPanel);
        if (openPanel != mLastFullyOpenedPanel) {
            openPanel.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }
        mLastFullyOpenedPanel = openPanel;
    }

    @Override
    public void onTrackingStarted(PanelView panel) {
        super.onTrackingStarted(panel);
        mBar.onTrackingStarted();
//        mScrimController.onTrackingStarted();
    }

    @Override
    public void onClosingFinished() {
        super.onClosingFinished();
        mBar.onClosingFinished();
    }

    @Override
    public void onTrackingStopped(PanelView panel, boolean expand) {
        super.onTrackingStopped(panel, expand);
        mBar.onTrackingStopped(expand);
    }

    @Override
    public void onExpandingFinished() {
        super.onExpandingFinished();
//        mScrimController.onExpandingFinished();
    }

    @Override
    public void panelExpansionChanged(PanelView panel, float frac, boolean expanded) {
        super.panelExpansionChanged(panel, frac, expanded);
//        mScrimController.setPanelExpansion(frac);
//        mBar.updateCarrierLabelVisibility(false);
    }
    */
}
