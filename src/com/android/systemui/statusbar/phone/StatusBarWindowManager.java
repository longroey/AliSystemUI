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
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.SystemProperties;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.android.keyguard.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.utils.ApplicationUtils;

/**
 * Encapsulates all logic for the status bar window state management.
 */
public class StatusBarWindowManager {
    private static final String TAG = "StatusBarWindowManager";
    private final Context mContext;
    private final WindowManager mWindowManager;
    private View mStatusBarView;
    private WindowManager.LayoutParams mLp;
    private WindowManager.LayoutParams mLpChanged;
    private int mBarWidth;
    private int mMinBarWidth;
    private int mBarHeight;
    private final boolean mKeyguardScreenRotation;
    private PhoneStatusBar mBar;

    private final State mCurrentState = new State();

    public StatusBarWindowManager(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mKeyguardScreenRotation = shouldEnableKeyguardScreenRotation();
    }

    private boolean shouldEnableKeyguardScreenRotation() {
        Resources res = mContext.getResources();
        return SystemProperties.getBoolean("lockscreen.rot_override", false)
                || res.getBoolean(R.bool.config_enableLockScreenRotation);
    }

    /**
     * Adds the status bar view to the window manager.
     *
     * @param statusBarView The view to add.
     * @param barHeight The height of the status bar in collapsed state.
     */
    public void add(View statusBarView, int barWidth, int minBarWidth, int barHeight, PhoneStatusBar bar) {

        // Now that the status bar window encompasses the sliding panel and its
        // translucent backdrop, the entire thing is made TRANSLUCENT and is
        // hardware-accelerated.
    	mBar = bar;
    	int width = !mBar.isDeviceProvisioned() ? minBarWidth : barWidth;
        mLp = new WindowManager.LayoutParams(
        		width,
                barHeight,
                WindowManager.LayoutParams.TYPE_STATUS_BAR,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.TRANSLUCENT);
        mLp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        mLp.gravity = Gravity.LEFT | Gravity.FILL_VERTICAL;
        mLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        mLp.setTitle("StatusBar");
        mLp.packageName = mContext.getPackageName();
        mStatusBarView = statusBarView;
        mBarHeight = barHeight;
        mBarWidth = barWidth;
        mMinBarWidth = minBarWidth;
        mWindowManager.addView(mStatusBarView, mLp);
        mLpChanged = new WindowManager.LayoutParams();
        mLpChanged.copyFrom(mLp);
    }

    private void applyKeyguardFlags(State state) {
        if (state.keyguardShowing) {
            mLpChanged.flags |= WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
            mLpChanged.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_KEYGUARD;
        } else {
            mLpChanged.flags &= ~WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
            mLpChanged.privateFlags &= ~WindowManager.LayoutParams.PRIVATE_FLAG_KEYGUARD;
        }
    }

    private void adjustScreenOrientation(State state) {
        if (state.isKeyguardShowingAndNotOccluded()) {
            if (mKeyguardScreenRotation) {
                mLpChanged.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_USER;
            } else {
                mLpChanged.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
            }
        } else {
            mLpChanged.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
    }

    private void applyFocusableFlag(State state) {
        if (state.isKeyguardShowingAndNotOccluded() && state.keyguardNeedsInput
                && state.bouncerShowing) {
            mLpChanged.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            mLpChanged.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        } else if (state.isKeyguardShowingAndNotOccluded() || state.statusBarFocusable) {
            mLpChanged.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            mLpChanged.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        } else {
            mLpChanged.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            mLpChanged.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
    }

    private void applyWidth(State state) {
        boolean expanded = state.isKeyguardShowingAndNotOccluded() || state.statusBarExpanded
                || state.keyguardFadingAway || state.bouncerShowing;
        boolean mStackLayoutExpanded = state.stackLayoutExpanded;
        Log.d(TAG, ", expanded = " + expanded
                + "applyWidth state = " + state
                + ", state.isKeyguardShowingAndNotOccluded() = " + state.isKeyguardShowingAndNotOccluded()
                + ", state.statusBarExpanded = " + state.statusBarExpanded
                + ", state.keyguardFadingAway = " + state.keyguardFadingAway
                + ", state.bouncerShowing = " + state.bouncerShowing
                + ", mBarHeight = " + mBarHeight 
                + ", mStackLayoutExpanded = " + mStackLayoutExpanded);
        if (expanded) {
            mLpChanged.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mLpChanged.width = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            mLpChanged.height = mBarHeight;
            if(!mBar.isDeviceProvisioned()){
            	mLpChanged.width = mMinBarWidth;
            }else{
                mLpChanged.width = mStackLayoutExpanded ? mBarWidth : mMinBarWidth;
            }
        }
    }

    private void applyFitsSystemWindows(State state) {
        mStatusBarView.setFitsSystemWindows(!state.isKeyguardShowingAndNotOccluded());
    }

    private void applyUserActivityTimeout(State state) {
        if (state.isKeyguardShowingAndNotOccluded()
                && state.statusBarState == StatusBarState.KEYGUARD
                && !state.qsExpanded) {
            mLpChanged.userActivityTimeout = state.keyguardUserActivityTimeout;
        } else {
            mLpChanged.userActivityTimeout = -1;
        }
    }

    private void applyInputFeatures(State state) {
        if (state.isKeyguardShowingAndNotOccluded()
                && state.statusBarState == StatusBarState.KEYGUARD
                && !state.qsExpanded) {
            mLpChanged.inputFeatures |=
                    WindowManager.LayoutParams.INPUT_FEATURE_DISABLE_USER_ACTIVITY;
        } else {
            mLpChanged.inputFeatures &=
                    ~WindowManager.LayoutParams.INPUT_FEATURE_DISABLE_USER_ACTIVITY;
        }
    }

    private void apply(State state) {
        applyKeyguardFlags(state);
        applyFocusableFlag(state);
        adjustScreenOrientation(state);
        applyWidth(state);
        applyUserActivityTimeout(state);
        applyInputFeatures(state);
        applyFitsSystemWindows(state);
        if (mLp.copyFrom(mLpChanged) != 0) {
            mWindowManager.updateViewLayout(mStatusBarView, mLp);
        }
    }

    public WindowManager.LayoutParams getWindowLayoutParams() {
        return mLp;
    }

    public WindowManager getWindowManager () {
        return mWindowManager;
    }

    public void setKeyguardShowing(boolean showing) {
        mCurrentState.keyguardShowing = showing;
        apply(mCurrentState);
    }

    public void setKeyguardOccluded(boolean occluded) {
        mCurrentState.keyguardOccluded = occluded;
        apply(mCurrentState);
    }

    public void setKeyguardNeedsInput(boolean needsInput) {
        mCurrentState.keyguardNeedsInput = needsInput;
        apply(mCurrentState);
    }

    public void setStatusBarExpanded(boolean expanded) {
        mCurrentState.statusBarExpanded = expanded;
        mCurrentState.statusBarFocusable = expanded;
        apply(mCurrentState);
    }
    public void setStackLayoutExpanded(boolean expanded) {
        mCurrentState.stackLayoutExpanded = expanded;
        apply(mCurrentState);
    }

    public void setStatusBarFocusable(boolean focusable) {
        mCurrentState.statusBarFocusable = focusable;
        apply(mCurrentState);
    }

    public void setKeyguardUserActivityTimeout(long timeout) {
        mCurrentState.keyguardUserActivityTimeout = timeout;
        apply(mCurrentState);
    }

    public void setBouncerShowing(boolean showing) {
        mCurrentState.bouncerShowing = showing;
        apply(mCurrentState);
    }

    public void setKeyguardFadingAway(boolean keyguardFadingAway) {
        mCurrentState.keyguardFadingAway = keyguardFadingAway;
        apply(mCurrentState);
    }

    public void setQsExpanded(boolean expanded) {
        mCurrentState.qsExpanded = expanded;
        apply(mCurrentState);
    }

    /**
     * @param state The {@link StatusBarState} of the status bar.
     */
    public void setStatusBarState(int state) {
        mCurrentState.statusBarState = state;
        apply(mCurrentState);
    }

    private static class State {
        boolean keyguardShowing;
        boolean keyguardOccluded;
        boolean keyguardNeedsInput;
        boolean statusBarExpanded;
        boolean statusBarFocusable;
        long keyguardUserActivityTimeout;
        boolean bouncerShowing;
        boolean keyguardFadingAway;
        boolean qsExpanded;
        boolean stackLayoutExpanded = ApplicationUtils.SUPPORT_WIDE_SCREEN;//if not wide screen default not show

        /**
         * The {@link BaseStatusBar} state from the status bar.
         */
        int statusBarState;

        private boolean isKeyguardShowingAndNotOccluded() {
            return keyguardShowing && !keyguardOccluded;
        }
    }
}
