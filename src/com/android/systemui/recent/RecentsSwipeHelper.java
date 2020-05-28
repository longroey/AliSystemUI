/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui.recent;

import java.util.ArrayList;

import com.android.systemui.Gefingerpoken;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.RectF;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.LinearInterpolator;
import com.android.systemui.R;

public class RecentsSwipeHelper implements Gefingerpoken {
    static final String TAG = "com.android.systemui.SwipeHelper";
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_INVALIDATE = false;
    private static final boolean SLOW_ANIMATIONS = false; // DEBUG;
    private static final boolean CONSTRAIN_SWIPE = true;
    private static final boolean FADE_OUT_DURING_SWIPE = true;
    private static final boolean DISMISS_IF_SWIPED_FAR_ENOUGH = true;

    public static final int X = 0;
    public static final int Y = 1;

    private static LinearInterpolator sLinearInterpolator = new LinearInterpolator();

    private float SWIPE_ESCAPE_VELOCITY = 100f; // dp/sec
    private int DEFAULT_ESCAPE_ANIMATION_DURATION = 200; // ms
    private int MAX_ESCAPE_ANIMATION_DURATION = 400; // ms
    private int MAX_DISMISS_VELOCITY = 2000; // dp/sec
    private static final int SNAP_ANIM_LEN = SLOW_ANIMATIONS ? 1000 : 150; // ms

    public static float ALPHA_FADE_START = 0f; // fraction of thumbnail width
                                               // where fade starts
    static final float ALPHA_FADE_END = 0.5f; // fraction of thumbnail width
                                              // beyond which alpha->0
    private float mMinAlpha = 0f;

    private float mPagingTouchSlop;
    private Callback mCallback;
    private Handler mHandler;
    private int mSwipeDirection;
    private VelocityTracker mVelocityTracker;

    private float mInitialTouchPos;
    private boolean mDragging;
    private View mCurrView;
    private View mCurrAnimView;
    private boolean mCanCurrViewBeDimissed;
    private float mDensityScale;

    private boolean mLongPressSent;
    private View.OnLongClickListener mLongPressListener;
    private Runnable mWatchLongPress;
    private long mLongPressTimeout;

    private int mActivePointerId = MotionEvent.INVALID_POINTER_ID;

    public RecentsSwipeHelper(int swipeDirection, Callback callback, float densityScale,
            float pagingTouchSlop) {
        mCallback = callback;
        mHandler = new Handler();
        mSwipeDirection = swipeDirection;
        mVelocityTracker = VelocityTracker.obtain();
        mDensityScale = densityScale;
        mPagingTouchSlop = pagingTouchSlop;

        mLongPressTimeout = (long) (ViewConfiguration.getLongPressTimeout() * 1.5f); // extra
                                                                                     // long-press!
    }

    public void setLongPressListener(View.OnLongClickListener listener) {
        mLongPressListener = listener;
    }

    public void setDensityScale(float densityScale) {
        mDensityScale = densityScale;
    }

    public void setPagingTouchSlop(float pagingTouchSlop) {
        mPagingTouchSlop = pagingTouchSlop;
    }

    private float getPos(MotionEvent ev) {
        return mSwipeDirection == X ? ev.getX() : ev.getY();
    }

    private float getPos(MotionEvent ev, int id) {
        return mSwipeDirection == X ? ev.getX(id) : ev.getY(id);
    }

    private float getTranslation(View v) {
        return mSwipeDirection == X ? v.getTranslationX() : v.getTranslationY();
    }

    private float getVelocity(VelocityTracker vt) {
        return mSwipeDirection == X ? vt.getXVelocity() : vt.getYVelocity();
    }

    private ObjectAnimator createTranslationAnimation(View v, float newPos) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(v, mSwipeDirection == X ? "translationX"
                : "translationY", newPos);
        return anim;
    }

    private float getPerpendicularVelocity(VelocityTracker vt) {
        return mSwipeDirection == X ? vt.getYVelocity() : vt.getXVelocity();
    }

    private void setTranslation(View v, float translate) {
        if (mSwipeDirection == X) {
            v.setTranslationX(translate);
        } else {
            v.setTranslationY(translate);
        }
    }

    private float getSize(View v) {
        return mSwipeDirection == X ? v.getMeasuredWidth() : v.getMeasuredHeight();
    }

    public void setMinAlpha(float minAlpha) {
        mMinAlpha = minAlpha;
    }

    private float getAlphaForOffset(View view) {
        float viewSize = getSize(view);
        final float fadeSize = ALPHA_FADE_END * viewSize;
        float result = 1.0f;
        float pos = getTranslation(view);
        if (pos >= viewSize * ALPHA_FADE_START) {
            result = 1.0f - (pos - viewSize * ALPHA_FADE_START) / fadeSize;
        } else if (pos < viewSize * (1.0f - ALPHA_FADE_START)) {
            result = 1.0f + (viewSize * ALPHA_FADE_START + pos) / fadeSize;
        }
        return Math.max(mMinAlpha, result);
    }

    private void updateAlphaFromOffset(View animView, boolean dismissable) {
        if (FADE_OUT_DURING_SWIPE && dismissable) {
            View parentView = (View) animView.getParent();
            RecentsPanelView.ViewHolder holder = (RecentsPanelView.ViewHolder) parentView.getTag();
            if (null != holder.taskDescription
                    && "fm.xiami.yunos".equals(holder.taskDescription.packageName)) {
                float alpha = getAlphaForOffset(animView);
                View view = ((ViewGroup) ((ViewGroup) animView).getChildAt(0)).getChildAt(0);
                if (alpha != 0f && alpha != 1f) {
                    // aliyun.aml.RenderThread.globalThread().setPaused(true);
                    // aliyun.aml.RenderThread.globalThread().signal();
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                } else {
                    view.setLayerType(View.LAYER_TYPE_NONE, null);
                    // aliyun.aml.RenderThread.globalThread().setPaused(false);
                    // aliyun.aml.RenderThread.globalThread().signal();
                }
                animView.setAlpha(getAlphaForOffset(animView));
            } else {
                float alpha = getAlphaForOffset(animView);
                if (alpha != 0f && alpha != 1f) {
                    animView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    // aliyun.aml.RenderThread.globalThread().setPaused(true);
                    // aliyun.aml.RenderThread.globalThread().signal();
                } else {
                    // aliyun.aml.RenderThread.globalThread().setPaused(false);
                    // aliyun.aml.RenderThread.globalThread().signal();
                    animView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
                animView.setAlpha(getAlphaForOffset(animView));
            }
        }
        invalidateGlobalRegion(animView);
    }

    // invalidate the view's own bounds all the way up the view hierarchy
    public static void invalidateGlobalRegion(View view) {
        invalidateGlobalRegion(view,
                new RectF(view.getLeft(), view.getTop(), view.getRight(), view.getBottom()));
    }

    // invalidate a rectangle relative to the view's coordinate system all the
    // way up the view
    // hierarchy
    public static void invalidateGlobalRegion(View view, RectF childBounds) {
        // childBounds.offset(view.getTranslationX(), view.getTranslationY());
        if (DEBUG_INVALIDATE)
            Log.v(TAG, "-------------");
        while (view.getParent() != null && view.getParent() instanceof View) {
            view = (View) view.getParent();
            view.getMatrix().mapRect(childBounds);
            view.invalidate((int) Math.floor(childBounds.left), (int) Math.floor(childBounds.top),
                    (int) Math.ceil(childBounds.right), (int) Math.ceil(childBounds.bottom));
            if (DEBUG_INVALIDATE) {
                Log.v(TAG,
                        "INVALIDATE(" + (int) Math.floor(childBounds.left) + ","
                                + (int) Math.floor(childBounds.top) + ","
                                + (int) Math.ceil(childBounds.right) + ","
                                + (int) Math.ceil(childBounds.bottom));
            }
        }
    }

    public void removeLongPressCallback() {
        if (mWatchLongPress != null) {
            mHandler.removeCallbacks(mWatchLongPress);
            mWatchLongPress = null;
        }
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
				Log.d(TAG,"onInterceptTouchEvent="+Integer.valueOf(action));
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDragging = false;
                mLongPressSent = false;
                mCurrView = mCallback.getChildAtPosition(ev);
                Log.d(TAG,"onInterceptTouchEvent,v="+mCurrView);
                mVelocityTracker.clear();
                if (mCurrView != null) {
                    mCurrAnimView = mCallback.getChildContentView(mCurrView);
                    mCanCurrViewBeDimissed = mCallback.canChildBeDismissed(mCurrView);
                    mVelocityTracker.addMovement(ev);
                    mInitialTouchPos = getPos(ev);

                    if (mLongPressListener != null) {
                        if (mWatchLongPress == null) {
                            mWatchLongPress = new Runnable() {
                                @Override
                                public void run() {
                                    if (mCurrView != null && !mLongPressSent) {
                                        mLongPressSent = true;
                                        mCurrView
                                                .sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                                        mLongPressListener.onLongClick(mCurrView);
                                    }
                                }
                            };
                        }
                        mHandler.postDelayed(mWatchLongPress, mLongPressTimeout);
                    }

                }
                break;

            case MotionEvent.ACTION_MOVE:
                 Log.d(TAG,"onInterceptTouchEvent,v="+mCurrView+" longpress="+Boolean.valueOf(mLongPressSent));
                if (mCurrView != null && !mLongPressSent) {
                    mVelocityTracker.addMovement(ev);
                    float pos = getPos(ev);
                    float delta = pos - mInitialTouchPos;
                    Log.d(TAG,"onInterceptTouchEvent,pos="+Float.valueOf(pos)+" inipos="+Float.valueOf(mInitialTouchPos));
                    if (Math.abs(delta) > mPagingTouchSlop) {
                        mCallback.onBeginDrag(mCurrView);
                        mDragging = true;
                        mActivePointerId = ev.getPointerId(0);
                        mInitialTouchPos = getPos(ev) - getTranslation(mCurrAnimView);

                        removeLongPressCallback();
                    }
                }

                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDragging = false;
                mCurrView = null;
                mCurrAnimView = null;
                mLongPressSent = false;
                mActivePointerId = MotionEvent.INVALID_POINTER_ID;
                removeLongPressCallback();
                break;
        }
        Log.d(TAG,"onInterceptTouchEvent="+Boolean.valueOf(mDragging));
        return mDragging;
    }

    /**
     * @param view The view to be dismissed
     * @param velocity The desired pixels/second speed at which the view should
     *            move
     */
    public void dismissChild(final View view, float velocity) {
        final View animView = mCallback.getChildContentView(view);
        final boolean canAnimViewBeDismissed = mCallback.canChildBeDismissed(view);
        float newPos;

        if (velocity < 0 || (velocity == 0 && getTranslation(animView) < 0)
                // if we use the Menu to dismiss an item in landscape, animate
                // up
                || (velocity == 0 && getTranslation(animView) == 0 && mSwipeDirection == Y)) {
            newPos = -getSize(animView);
        } else {
            newPos = getSize(animView);
        }
        int duration = MAX_ESCAPE_ANIMATION_DURATION;
        if (velocity != 0) {
            duration = Math
                    .min(duration,
                            (int) (Math.abs(newPos - getTranslation(animView)) * 1000f / Math
                                    .abs(velocity)));
        } else {
            duration = DEFAULT_ESCAPE_ANIMATION_DURATION;
        }

        animView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ObjectAnimator anim = createTranslationAnimation(animView, newPos);
        anim.setInterpolator(sLinearInterpolator);
        anim.setDuration(duration);
        anim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                mCallback.onChildDismissed(view);
                animView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });
        anim.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                updateAlphaFromOffset(animView, canAnimViewBeDismissed);
            }
        });
        anim.start();
    }

    public void dismissChild(final View view, float velocity, final boolean isClearAll,
            final int index, final ArrayList<View> views) {
        final View animView = mCallback.getChildContentView(view);
        final boolean canAnimViewBeDismissed = mCallback.canChildBeDismissed(view);
        float newPos;

        if (velocity < 0 || (velocity == 0 && getTranslation(animView) < 0)
                // if we use the Menu to dismiss an item in landscape, animate
                // up
                || (velocity == 0 && getTranslation(animView) == 0 && mSwipeDirection == Y)) {
            newPos = -getSize(animView);
        } else {
            newPos = getSize(animView);
        }
        int duration = MAX_ESCAPE_ANIMATION_DURATION;
        if (velocity != 0) {
            duration = Math
                    .min(duration,
                            (int) (Math.abs(newPos - getTranslation(animView)) * 1000f / Math
                                    .abs(velocity)));
        } else {
            duration = DEFAULT_ESCAPE_ANIMATION_DURATION;
        }

        animView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ObjectAnimator anim = createTranslationAnimation(animView, newPos);
        anim.setInterpolator(sLinearInterpolator);
        anim.setDuration(duration);
        anim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                if (isClearAll) {
                    int next = index + 1;
                    if (next < views.size())
                        dismissChild(views.get(next), 0, true, next, views);
                    else
                        mCallback.removeAllViewsOnAnimationEnd();
                } else {
                    mCallback.onChildDismissed(view);
                }
                animView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });
        anim.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                updateAlphaFromOffset(animView, canAnimViewBeDismissed);
            }
        });
        anim.start();
    }

    public void snapChild(final View view, float velocity, final float delta) {
        final View animView = mCallback.getChildContentView(view);
        final boolean canAnimViewBeDismissed = mCallback.canChildBeDismissed(animView);
        ObjectAnimator anim = createTranslationAnimation(animView, 0);
        int duration = SNAP_ANIM_LEN;
        anim.setDuration(duration);
        anim.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                if (delta < 0) {
                    updateAlphaFromOffset(animView, canAnimViewBeDismissed);
                }
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animator) {
                if (delta < 0) {
                    updateAlphaFromOffset(animView, canAnimViewBeDismissed);
                }
            }
        });
        anim.start();
    }

    public void startDeleteAnimation(ArrayList<View> views) {
        if (views.size() < 1)
            return;
        dismissChild(views.get(0), 0, true, 0, views);
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (mLongPressSent) {
            return true;
        }

        if (!mDragging) {
            // We are not doing anything, make sure the long press callback
            // is not still ticking like a bomb waiting to go off.
            removeLongPressCallback();
            return false;
        }

        mVelocityTracker.addMovement(ev);
        final int action = ev.getAction();
        Log.d(TAG,"onTouchEvent="+Integer.valueOf(action));
        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
            case MotionEvent.ACTION_MOVE:
                if (mCurrView != null) {
                    final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                    if (activePointerIndex == -1) {
                        Log.e(TAG, "Invalid pointerId=" + mActivePointerId + " in onTouchEvent");
                        break;
                    }
                    float delta = getPos(ev, activePointerIndex) - mInitialTouchPos;
                    // don't let items that can't be dismissed be dragged more
                    // than
                    // maxScrollDistance
                    if (CONSTRAIN_SWIPE && !mCallback.canChildBeDismissed(mCurrView)) {
                        float size = getSize(mCurrAnimView);
                        float maxScrollDistance = 0.15f * size;
                        if (Math.abs(delta) >= size) {
                            delta = delta > 0 ? maxScrollDistance : -maxScrollDistance;
                        } else {
                            delta = maxScrollDistance
                                    * (float) Math.sin((delta / size) * (Math.PI / 2));
                        }
                    }
                    setTranslation(mCurrAnimView, delta);
                    if (delta < 0) {
                        updateAlphaFromOffset(mCurrAnimView, mCanCurrViewBeDimissed);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mCurrView != null) {
                    float maxVelocity = MAX_DISMISS_VELOCITY * mDensityScale;
                    mVelocityTracker.computeCurrentVelocity(1000 /* px/sec */, maxVelocity);
                    float escapeVelocity = SWIPE_ESCAPE_VELOCITY * mDensityScale;
                    float velocity = getVelocity(mVelocityTracker);
                    float perpendicularVelocity = getPerpendicularVelocity(mVelocityTracker);

                    // Decide whether to dismiss the current view
                    boolean childSwipedFarEnough = DISMISS_IF_SWIPED_FAR_ENOUGH
                            && Math.abs(getTranslation(mCurrAnimView)) > 0.4 * getSize(mCurrAnimView);
                    boolean childSwipedFastEnough = (Math.abs(velocity) > escapeVelocity)
                            && (Math.abs(velocity) > Math.abs(perpendicularVelocity))
                            && (velocity > 0) == (getTranslation(mCurrAnimView) > 0);

                    final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                    mActivePointerId = MotionEvent.INVALID_POINTER_ID;
                    if (activePointerIndex == -1) {// getTranslation
                        snapChild(mCurrView, 0, getTranslation(mCurrAnimView));
                        Log.e(TAG, "Invalid pointerId=" + mActivePointerId + " in onTouchEvent");
                        break;
                    }

                    float delta = getPos(ev) - mInitialTouchPos;
                    boolean dismissChild = mCallback.canChildBeDismissed(mCurrView)
                            && (childSwipedFastEnough || childSwipedFarEnough) && (delta < 0);
										Log.d(TAG,"onTouchEvent="+Boolean.valueOf(dismissChild));
                    if (dismissChild) {
                        // flingadingy
                        dismissChild(mCurrView, childSwipedFastEnough ? velocity : 0f);
                    } else {
                        // snappity
                        float sizeIcon = getSize(mCurrAnimView.findViewById(R.id.app_icon));
                        if (delta > 0.5 * sizeIcon) {
                            mCallback.onDragCancelled(mCurrView);
                        }
                        snapChild(mCurrView, velocity, delta);
                    }
                }
                break;
        }
        return true;
    }

    public interface Callback {
        View getChildAtPosition(MotionEvent ev);

        View getChildContentView(View v);

        boolean canChildBeDismissed(View v);

        void onBeginDrag(View v);

        void onChildDismissed(View v);

        void onDragCancelled(View v);

        void removeAllViewsOnAnimationEnd();
    }
}
