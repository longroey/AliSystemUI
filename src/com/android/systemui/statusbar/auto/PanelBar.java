/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.statusbar.auto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;

import com.android.systemui.utils.AliUserTrackUtil;

public class PanelBar extends FrameLayout {
    public static final boolean DEBUG = true;
    public static final String TAG = PanelBar.class.getSimpleName();
    public static final void LOG(String fmt, Object... args) {
        if (!DEBUG) return;
        Log.v(TAG, String.format(fmt, args));
    }

    public static final int STATE_CLOSED = 0;
    public static final int STATE_OPENING = 1;
    public static final int STATE_OPEN = 2;

    // peikai.zpk add
    private BroadcastReceiver mScreenOffReceiver;
    private static final String ACTION_SCREENOFF_GESTURECONTROL = "com.yunos.gesturecontrol.SCREENOFF";

    PanelHolder mPanelHolder;
    ArrayList<PanelView> mPanels = new ArrayList<PanelView>();
    PanelView mTouchingPanel;
    private int mState = STATE_CLOSED;
    private boolean mTracking;
    private static final int MSG_AUTO_COLLAPSE = 0;
    private static final long AUTO_COLLAPSE_TIMEOUT = 15000;
    private Handler mHandler = new Handler(){
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
            case MSG_AUTO_COLLAPSE:
                if(mState != STATE_CLOSED)
                    collapseAllPanels(true);
                break;

            default:
                break;
            }
        }
    };
    float mPanelExpandedFractionSum;

    public void go(int state) {
        if (DEBUG) LOG("go state: %d -> %d", mState, state);
        mState = state;
    }

    public PanelBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    public void addPanel(PanelView pv) {
        mPanels.add(pv);
        pv.setBar(this);
    }

    public void setPanelHolder(PanelHolder ph) {
        if (ph == null) {
            Log.e(TAG, "setPanelHolder: null PanelHolder", new Throwable());
            return;
        }
        ph.setBar(this);
        mPanelHolder = ph;
        final int N = ph.getChildCount();
        for (int i=0; i<N; i++) {
            final View v = ph.getChildAt(i);
            if (v != null && v instanceof PanelView) {
                addPanel((PanelView) v);
            }
        }
    }

    public float getBarHeight() {
        return getMeasuredHeight();
    }

    public PanelView selectPanelForTouch(MotionEvent touch) {
        final int N = mPanels.size();
        return mPanels.get((int)(N * touch.getX() / getMeasuredWidth()));
    }

    public boolean panelsEnabled() {
        return true;
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Allow subclasses to implement enable/disable semantics
        if (!panelsEnabled()) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                Log.v(TAG, String.format("onTouch: all panels disabled, ignoring touch at (%d,%d)",
                        (int) event.getX(), (int) event.getY()));
            }
            return false;
        }

        // figure out which panel needs to be talked to here
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            final PanelView panel = selectPanelForTouch(event);
            if (panel == null) {
                // panel is not there, so we'll eat the gesture
                Log.v(TAG, String.format("onTouch: no panel for touch at (%d,%d)",
                        (int) event.getX(), (int) event.getY()));
                mTouchingPanel = null;
                return true;
            }
            boolean enabled = panel.isEnabled();
            if (DEBUG) LOG("PanelBar.onTouch: state=%d ACTION_DOWN: panel %s %s", mState, panel,
                    (enabled ? "" : " (disabled)"));
            if (!enabled) {
                // panel is disabled, so we'll eat the gesture
                Log.v(TAG, String.format(
                        "onTouch: panel (%s) is disabled, ignoring touch at (%d,%d)",
                        panel, (int) event.getX(), (int) event.getY()));
                mTouchingPanel = null;
                return true;
            }
            startOpeningPanel(panel);
        }
        final boolean result = mTouchingPanel != null
                ? mTouchingPanel.onTouchEvent(event)
                : true;
        return result;
    }

    // called from PanelView when self-expanding, too
    public void startOpeningPanel(PanelView panel) {
        if (DEBUG) LOG("startOpeningPanel: " + panel);
        mTouchingPanel = panel;
        mPanelHolder.setSelectedPanel(mTouchingPanel);
        for (PanelView pv : mPanels) {
            if (pv != panel) {
                pv.collapse();
            }
        }
    }

    public void panelExpansionChanged(PanelView panel, float frac) {
        boolean fullyClosed = true;
        PanelView fullyOpenedPanel = null;
        if (DEBUG) LOG("panelExpansionChanged: start state=%d panel=%s", mState, panel.getName());
        mPanelExpandedFractionSum = 0f;
        for (PanelView pv : mPanels) {
            final boolean visible = pv.getVisibility() == View.VISIBLE;
            // adjust any other panels that may be partially visible
            if (pv.getExpandedWidth() > 0f) {
                if (mState == STATE_CLOSED) {
                    go(STATE_OPENING);
                    onPanelPeeked();
                }
                fullyClosed = false;
                final float thisFrac = pv.getExpandedFraction();
                mPanelExpandedFractionSum += (visible ? thisFrac : 0);
                if (DEBUG) LOG("panelExpansionChanged:  -> %s: f=%.1f", pv.getName(), thisFrac);
                if (panel == pv) {
                    if (thisFrac == 1f) fullyOpenedPanel = panel;
                }
            }
            if (pv.getExpandedWidth() > 0f) {
                if (!visible) pv.setVisibility(View.VISIBLE);
            } else {
                if (visible) pv.setVisibility(View.GONE);
            }
        }
        mPanelExpandedFractionSum /= mPanels.size();
        if (fullyOpenedPanel != null && !mTracking) {
            go(STATE_OPEN);
            onPanelFullyOpened(fullyOpenedPanel);
        } else if (fullyClosed && !mTracking && mState != STATE_CLOSED) {
            go(STATE_CLOSED);
            onAllPanelsCollapsed();
        }

        if (DEBUG) LOG("panelExpansionChanged: end state=%d [%s%s ]", mState,
                (fullyOpenedPanel!=null)?" fullyOpened":"", fullyClosed?" fullyClosed":"");
    }

    public void collapseAllPanels(boolean animate) {
        boolean waiting = false;
        for (PanelView pv : mPanels) {
            if (animate && !pv.isFullyCollapsed()) {
                pv.collapse();
                waiting = true;
            } else {
                pv.setExpandedFraction(0); // just in case
                pv.setVisibility(View.GONE);
                pv.cancelPeek();
            }
        }
        if (DEBUG) LOG("collapseAllPanels: animate=%s waiting=%s", animate, waiting);
        if (!waiting && mState != STATE_CLOSED) {
            // it's possible that nothing animated, so we replicate the termination
            // conditions of panelExpansionChanged here
            go(STATE_CLOSED);
            onAllPanelsCollapsed();
        }
    }

    public void onPanelPeeked() {
        if (DEBUG) LOG("onPanelPeeked");
    }

    public void onAllPanelsCollapsed() {
        if (DEBUG) LOG("onAllPanelsCollapsed");
        setListening(false);
        AliUserTrackUtil.pageLeave("Page_ControlCenter");

        // peikai.zpk add this
        unRegisterScreenoffReceiver();
    }

    public void onPanelFullyOpened(PanelView openPanel) {
        if (DEBUG) LOG("onPanelFullyOpened");
        setListening(true);
        startDebounceCollapsed();
        AliUserTrackUtil.pageEnter("Page_ControlCenter");

        // peikai.zpk add this
        registerScreentOffReceiver();
    }

    private void registerScreentOffReceiver() {
        if (DEBUG) {
            LOG("registerScreentOffReceiver, mScreenOffReceiver = " + mScreenOffReceiver);
        }

        if (null == mScreenOffReceiver) {
            mScreenOffReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (ACTION_SCREENOFF_GESTURECONTROL.equals(intent.getAction())) {
                        PanelBar.this.collapseAllPanels(false);
                    }
                }
            };

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_SCREENOFF_GESTURECONTROL);
            this.getContext().registerReceiver(mScreenOffReceiver, intentFilter);
        }
    }

    private void unRegisterScreenoffReceiver() {
        if (DEBUG) {
            LOG("unRegisterScreenoffReceiver, mScreenOffReceiver = " + mScreenOffReceiver);
        }

        if (null != mScreenOffReceiver) {
            this.getContext().unregisterReceiver(mScreenOffReceiver);
            mScreenOffReceiver = null;
        }
    }
    
    void startDebounceCollapsed() {
        if(mHandler.hasMessages(MSG_AUTO_COLLAPSE)){
            mHandler.removeMessages(MSG_AUTO_COLLAPSE);
        }
        mHandler.sendEmptyMessageDelayed(MSG_AUTO_COLLAPSE, AUTO_COLLAPSE_TIMEOUT);
    }
    
    void stopDebounceCollapsed(){
        if(mHandler.hasMessages(MSG_AUTO_COLLAPSE)){
            mHandler.removeMessages(MSG_AUTO_COLLAPSE);
        }
    }

    void setListening(boolean listening) {
        for(PanelView pv : mPanels){
            pv.setListening(listening);
        }
    }

    public void onTrackingStarted(PanelView panel) {
        mTracking = true;
        stopDebounceCollapsed();
        if (DEBUG && panel != mTouchingPanel) {
            LOG("shouldn't happen: onTrackingStarted(%s) != mTouchingPanel(%s)",
                    panel, mTouchingPanel);
        }
    }

    public void onTrackingStopped(PanelView panel) {
        mTracking = false;
        startDebounceCollapsed();
        panelExpansionChanged(panel, panel.getExpandedFraction());
    }
}
