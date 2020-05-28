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

import android.animation.LayoutTransition;

import android.content.Context;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.systemui.recent.RecentsPanelView.RecentsScrollView;
import com.android.systemui.recent.RecentsPanelView.TaskDescriptionAdapter;
import com.android.systemui.recent.RecentsPanelView.ViewHolder;
import com.android.systemui.utils.AliUserTrackUtil;
import com.android.systemui.utils.MemoryUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import android.view.WindowManager;

public class RecentsHorizontalScrollView extends HorizontalSuperScrollView
        implements RecentsSwipeHelper.Callback,
        RecentsPanelView.RecentsScrollView {
    private static final String TAG = "RecentsHorizontalScrollView";//RecentsPanelView.TAG;
    private static final boolean DEBUG = RecentsPanelView.DEBUG;
    private LinearLayout mLinearLayout;
    private TaskDescriptionAdapter mAdapter;
    private RecentsCallback mCallback;
    protected int mLastScrollPosition;
    private RecentsSwipeHelper mSwipeHelper;
    private FadedEdgeDrawHelper mFadedEdgeDrawHelper;
    private HashSet<View> mRecycledViews;
    private int mNumItemsInOneScreenful;
    private Runnable mOnScrollListener;
    private static final int MAX_X_OVERSCROLL_DISTANCE = 80;
    protected int mMaxXOverscrollDistance;
    private int mScreenWidth;
    /*YUNOS BEGIN*/
    private int mScreenHeight;    
    /*YUNOS END*/
    
    public RecentsHorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
        float densityScale = getResources().getDisplayMetrics().density;
        float pagingTouchSlop = ViewConfiguration.get(getContext())
                .getScaledPagingTouchSlop();
        mSwipeHelper = new RecentsSwipeHelper(RecentsSwipeHelper.Y, this,
                densityScale, pagingTouchSlop);
        mFadedEdgeDrawHelper = FadedEdgeDrawHelper.create(context, attrs, this,
                false);
        mRecycledViews = new HashSet<View>();
        WindowManager wm = (WindowManager) getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(displayMetrics);
        mScreenWidth = displayMetrics.widthPixels;
        /*YUNOS BEGIN*/
        mScreenHeight=displayMetrics.heightPixels;    
        /*YUNOS END*/
        initBounceDistance();
        setEdgeTransparent();
        mItemWidth = (int) getResources().getDimensionPixelSize(
                R.dimen.status_bar_recents_item_width);
    }

    private void initBounceDistance() {
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        mMaxXOverscrollDistance = (int) (metrics.density * MAX_X_OVERSCROLL_DISTANCE);
    }

    private void setEdgeTransparent() {
        try {
            Class<?> c = (Class<?>) Class
                    .forName(HorizontalSuperScrollView.class.getName());
            Field egtField = c.getDeclaredField("mEdgeGlowLeft");
            Field egbBottom = c.getDeclaredField("mEdgeGlowRight");
            egtField.setAccessible(true);
            egbBottom.setAccessible(true);
            Object egtObject = egtField.get(this);
            Object egbObject = egbBottom.get(this);
            Class<?> cc = (Class<?>) Class.forName(egtObject.getClass()
                    .getName());
            Field mGlow = cc.getDeclaredField("mGlow");
            mGlow.setAccessible(true);
            mGlow.set(egtObject, new ColorDrawable(Color.TRANSPARENT));
            mGlow.set(egbObject, new ColorDrawable(Color.TRANSPARENT));
            Field mEdge = cc.getDeclaredField("mEdge");
            mEdge.setAccessible(true);
            mEdge.set(egtObject, new ColorDrawable(Color.TRANSPARENT));
            mEdge.set(egbObject, new ColorDrawable(Color.TRANSPARENT));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected boolean overScrollBy(int deltaX, int deltaY, int scrollX,
            int scrollY, int scrollRangeX, int scrollRangeY,
            int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY,
                scrollRangeX, scrollRangeY, mMaxXOverscrollDistance,
                maxOverScrollY, isTouchEvent);
    }

    public void setMinSwipeAlpha(float minAlpha) {
        mSwipeHelper.setMinAlpha(minAlpha);
    }

    private int scrollPositionOfMostRecent() {
        return 0;
    }

    private void addToRecycledViews(View v) {
        if (mRecycledViews.size() < mNumItemsInOneScreenful) {
            mRecycledViews.add(v);
        }
    }

    public View findViewForTask(int persistentTaskId) {
        for (int i = 0; i < mLinearLayout.getChildCount(); i++) {
            View v = mLinearLayout.getChildAt(i);
            RecentsPanelView.ViewHolder holder = (RecentsPanelView.ViewHolder) v
                    .getTag();
            if (holder.taskDescription.persistentTaskId == persistentTaskId) {
                return v;
            }
        }
        return null;
    }

    public void removeAllViewsInLayout() {
        int count = mLinearLayout.getChildCount();
        View[] refView = new View[count];
        RecentsPanelView.ViewHolder holder;
        // child in mLinearLayout may be dismissed when we use, so we store it
        // before wo clear all
        for (int i = 0; i < count; i++)
            refView[i] = mLinearLayout.getChildAt(i);
        ArrayList<View> animationViews = new ArrayList<View>();
        if (refView.length <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            holder = (RecentsPanelView.ViewHolder) refView[i].getTag();
            if (holder == null || holder.taskDescription == null) {
                return;
            }
            String packageName = holder.taskDescription.packageName;
            if (!MemoryUtil.isTaskLocked(getContext(), packageName)) {
                int scrollX = getScrollX();
                // if (refView[i].getVisibility() == View.VISIBLE) {
                int left = refView[i].getLeft() - scrollX;
                int right = refView[i].getRight() - scrollX;
                if (left > 0 && left < mScreenWidth
                        || (right > 0 && right < mScreenWidth)) {
                    animationViews.add(refView[i]);
                    if (animationViews.size() >= numItemsInOneScreenful()) {
                        break;
                    }
                }
            }
        }
        if (animationViews.size() < 1) {
            removeAllViewsOnAnimationEnd();
        }
        mSwipeHelper.startDeleteAnimation(animationViews);
    }

    public void removeAllViewsOnAnimationEnd() {
        int count = mLinearLayout.getChildCount();
        View[] refView = new View[count];
        RecentsPanelView.ViewHolder holder;
        // child in mLinearLayout may be dismissed when we use, so we store it
        // before wo clear all
        for (int i = 0; i < count; i++)
            refView[i] = mLinearLayout.getChildAt(i);
        if (refView.length <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            holder = (RecentsPanelView.ViewHolder) refView[i].getTag();
            if (holder == null || holder.taskDescription == null) {
                return;
            }
            String packageName = holder.taskDescription.packageName;
            if (!MemoryUtil.isTaskLocked(getContext(), packageName)) {
                final View animView = getChildContentView(refView[i]);
                animView.setLayerType(View.LAYER_TYPE_NONE, null);
                onChildDismissedAnimationEnd(refView[i]);
            }
        }
        // update();
    }

    private void update() {
        for (int i = 0; i < mLinearLayout.getChildCount(); i++) {
            View v = mLinearLayout.getChildAt(i);
            addToRecycledViews(v);
            mAdapter.recycleView(v);
        }
        LayoutTransition transitioner = getLayoutTransition();
        setLayoutTransition(null);

        mLinearLayout.removeAllViews();
        Iterator<View> recycledViews = mRecycledViews.iterator();
        for (int i = 0; i < mAdapter.getCount(); i++) {
            View old = null;
            if (recycledViews.hasNext()) {
                old = recycledViews.next();
                recycledViews.remove();
                old.setVisibility(VISIBLE);
            }

            final View view = mAdapter.getView(i, old, mLinearLayout);

            if (mFadedEdgeDrawHelper != null) {
                mFadedEdgeDrawHelper.addViewCallback(view);
            }

            OnTouchListener noOpListener = new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            };

            view.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    // mCallback.dismiss();
                }
            });
            // We don't want a click sound when we dimiss recents
            view.setSoundEffectsEnabled(false);

            OnClickListener launchAppListener = new OnClickListener() {
                public void onClick(View v) {
                    mCallback.handleOnClick(view);
                }
            };

            RecentsPanelView.ViewHolder holder = (RecentsPanelView.ViewHolder) view
                    .getTag();
            final View thumbnailView = holder.thumbnailView;
            View lockView = holder.lockView;
            if (holder != null && holder.taskDescription != null && lockView!=null /*lurui*/) {
                String packageName = holder.taskDescription.packageName;
                boolean isLocked = MemoryUtil.isTaskLocked(getContext(),
                        packageName);
                if (isLocked)
                    lockView.setVisibility(View.VISIBLE);
                else
                    lockView.setVisibility(View.INVISIBLE);
            }
            // OnLongClickListener longClickListener = new OnLongClickListener()
            // {
            // public boolean onLongClick(View v) {
            // final View anchorView = view.findViewById(R.id.app_description);
            // mCallback.handleLongPress(view, anchorView, thumbnailView);
            // return true;
            // }
            // };
            thumbnailView.setClickable(true);
            thumbnailView.setOnClickListener(launchAppListener);
            // thumbnailView.setOnLongClickListener(longClickListener);

            // We don't want to dismiss recents if a user clicks on the app
            // title
            // (we also don't want to launch the app either, though, because the
            // app title is a small target and doesn't have great click
            // feedback)
            final View appTitle = view.findViewById(R.id.app_label);
            appTitle.setContentDescription(" ");
            appTitle.setOnTouchListener(noOpListener);
            mLinearLayout.addView(view);
        }

        setLayoutTransition(transitioner);

        // Scroll to end after initial layout.

        final OnGlobalLayoutListener updateScroll = new OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                mLastScrollPosition = scrollPositionOfMostRecent();
                scrollTo(mLastScrollPosition, 0);
                final ViewTreeObserver observer = getViewTreeObserver();
                if (observer.isAlive()) {
                    observer.removeOnGlobalLayoutListener(this);
                }
            }
        };
        getViewTreeObserver().addOnGlobalLayoutListener(updateScroll);
    }

    @Override
    public void removeViewInLayout(final View view) {
        dismissChild(view);
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (DEBUG)
            Log.v(TAG, "onInterceptTouchEvent()");

        return mSwipeHelper.onInterceptTouchEvent(ev)
                || super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        return mSwipeHelper.onTouchEvent(ev) || super.onTouchEvent(ev);
    }

    public boolean canChildBeDismissed(View v) {
        RecentsPanelView.ViewHolder holder = (RecentsPanelView.ViewHolder) v
                .getTag();
        boolean needBeProtected = false;
        if(holder != null){
            TaskDescription ad = holder.taskDescription;
            if (ad != null) {
                String packageName = ad.packageName;
                needBeProtected = packageName.equals("com.example.zuiserver");
            }
        }
        return !needBeProtected;
    }

    public void dismissChild(View v) {
        mSwipeHelper.dismissChild(v, 0);
    }

    public void onChildDismissedAnimationEnd(View v) {
        addToRecycledViews(v);
        mLinearLayout.removeView(v);
        mCallback.handleSwipe(v);
        // Restore the alpha/translation parameters to what they were before
        // swiping
        // (for when these items are recycled)
        View contentView = getChildContentView(v);
        contentView.setAlpha(1f);
        contentView.setTranslationY(0);
    }

    public void onChildDismissed(View v) {
        addToRecycledViews(v);
        /* YUNOS_BEGIN */
        // ##modules(Systemui recent): usertrack
        // ##date: 2013-12.26 author: yulong.hyl@alibaba-inc.com
        RecentsPanelView.ViewHolder viewHolder = (RecentsPanelView.ViewHolder) v
                .getTag();
        int position = viewHolder.position;
        TaskDescription ad = viewHolder.taskDescription;
        if (ad != null) {
            String packageName = ad.packageName;
//            String kvs = "packageName= " + packageName + " position= "
//                    + position + " action= " + "slideUp";
            Map<String, String> lMap = new HashMap<String, String>();
            lMap.put("packageName", packageName);
            lMap.put("position", position + "");
            lMap.put("action", "slideUp");
            AliUserTrackUtil.ctrlClicked("Multitask", "imageview", lMap);

        }
        /* YUNOS_END */
        mLinearLayout.removeView(v);
        mCallback.handleSwipe(v);
        // Restore the alpha/translation parameters to what they were before
        // swiping
        // (for when these items are recycled)
        View contentView = getChildContentView(v);
        contentView.setAlpha(1f);
        contentView.setTranslationY(0);
    }

    public void onBeginDrag(View v) {
        // We do this so the underlying ScrollView knows that it won't get
        // the chance to intercept events anymore
        requestDisallowInterceptTouchEvent(true);
    }

    public void onDragCancelled(View v) {
        RecentsPanelView.ViewHolder viewHolder = (RecentsPanelView.ViewHolder) v
                .getTag();
        View lockView = viewHolder.lockView;
        TaskDescription ad = viewHolder.taskDescription;
        boolean isNeedToProtected;
        if (ad != null) {
            String packageName = ad.packageName;
            isNeedToProtected = packageName.equals("com.example.zuiserver");
            if(isNeedToProtected){
                //lockView.setVisibility(View.VISIBLE);
                //MemoryUtil.lockTask(getContext(), packageName);
                //Toast.makeText(getContext(), R.string.notification_process_protected, 300).show();
                return;
            }
        }
        //lockView.setVisibility(reverseViewVisibility(lockView, viewHolder));
    }

    private int reverseViewVisibility(View view,
            RecentsPanelView.ViewHolder viewHolder) {
        if (viewHolder == null || viewHolder.taskDescription == null) {
            return view.getVisibility();
        }
        String packageName = viewHolder.taskDescription.packageName;
        /* YUNOS_BEGIN */
        // ##modules(Systemui recent): usertrack
        // ##date: 2013-12.26 author: yulong.hyl@alibaba-inc.com
        int position = viewHolder.position;
        String action = "action";
        /* YUNOS_END */
        int viewVisibility = view.getVisibility();
        if (viewVisibility == View.VISIBLE) {
            viewVisibility = View.INVISIBLE;
            MemoryUtil.unlockTask(getContext(), packageName);
            action = "unlock";
        } else if (viewVisibility == View.INVISIBLE) {
            //viewVisibility = View.VISIBLE;
            //MemoryUtil.lockTask(getContext(), packageName);
            action = "lock";
        }
//        String kvs = "packageName= " + packageName + " position= " + position
//                + " action= " + action;
        Map<String, String> lMap = new HashMap<String, String>();
        lMap.put("packageName", packageName);
        lMap.put("position", position + "");
        lMap.put("action", action);
        AliUserTrackUtil.ctrlClicked("Multitask", "imageview", lMap);
        return viewVisibility;
    }

    public View getChildAtPosition(MotionEvent ev) {
        final float x = ev.getX() + getScrollX();
        final float y = ev.getY() + getScrollY();
        /* YUNOS_BEGIN */
        // ##modules(Systemui recent): add for sliding to center_horizontal
        // ##date: 2014-10.23 author: ruijie.lrj@alibaba-inc.com

        final int n = mLinearLayout.getChildCount();

        int itemHalfWidth=0;
        
        if(mScreenHeight==720)
        {
        	itemHalfWidth=(int) getResources().getDimensionPixelSize(
                 R.dimen.status_bar_recents_half_item_width_1280_720);
        }
        else if(mScreenHeight==400)
        {
        	itemHalfWidth=(int) getResources().getDimensionPixelSize(
                    R.dimen.status_bar_recents_half_item_width_1280_400);
        }
        int[] location_scroll = new int[2];  
        int[] location_linear = new int[2];  
        getLocationOnScreen(location_scroll); 
        mLinearLayout.getLocationOnScreen(location_linear); 
        int location_left=location_scroll[0]>location_linear[0]?location_linear[0]:location_scroll[0];
        
        /* YUNOS_END */
        View itemReturn=null;
        for (int i = 0; i < n; i++) {
            View item = mLinearLayout.getChildAt(i);
            final int[] location = new int[2];   
            item.getLocationOnScreen(location);
            
            Log.d("com.android.systemui.SwipeHelper"," nnn="+Integer.valueOf(n)+" x="+Float.valueOf(x)+" y="+Float.valueOf(y)
            +" ihw="+Integer.valueOf(itemHalfWidth)+" l="+Integer.valueOf(item.getLeft())
             +" r="+Integer.valueOf(item.getRight())+" t="+Integer.valueOf(item.getTop())
             +" b="+Integer.valueOf(item.getBottom())+" sl="+location[0]+" st="+location[1]+" slx="+Integer.valueOf(location_left));
 
            /* YUNOS_BEGIN */
            int left=location[0];
            int right=location[0]+item.getWidth();
            int top=location[1];
            int bottom=location[1]+item.getHeight();
            
            float x1=x+(float)location_left-(float)itemHalfWidth;
            
            Log.d("com.android.systemui.SwipeHelper"," x1="+Float.valueOf(x1)
            +" l="+Integer.valueOf(left - itemHalfWidth)
             +" r="+Integer.valueOf(right - itemHalfWidth));
            if (x1 >= left - itemHalfWidth
                    && x1 <right - itemHalfWidth
                    || (n == 1 && x1 >= left + itemHalfWidth && x1 < 
                            right + itemHalfWidth) && y >= top
                    && y < bottom) {
                //return item;
            	itemReturn=item;
            }
            /* YUNOS_END */
            /*
            if (x >= item.getLeft() - itemHalfWidth
                    && x < item.getRight() - itemHalfWidth
                    || (n == 1 && x >= item.getLeft() + itemHalfWidth && x < item
                            .getRight() + itemHalfWidth) && y >= item.getTop()
                    && y < item.getBottom()) {
                //return item;
            	itemReturn=item;
            }*/
        }
      //  return null;
        return itemReturn;
    }

    public View getChildContentView(View v) {
        return v.findViewById(R.id.recent_item);
    }

    @Override
    public void drawFadedEdges(Canvas canvas, int left, int right, int top,
            int bottom) {
        if (mFadedEdgeDrawHelper != null) {

            mFadedEdgeDrawHelper.drawCallback(canvas, left, right, top, bottom,
                    mScrollX, mScrollY, 0, 0, getLeftFadingEdgeStrength(),
                    getRightFadingEdgeStrength(), mPaddingTop);
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mOnScrollListener != null) {
            mOnScrollListener.run();
        }
    }

    public void setOnScrollListener(Runnable listener) {
        mOnScrollListener = listener;
    }

    @Override
    public int getVerticalFadingEdgeLength() {
        if (mFadedEdgeDrawHelper != null) {
            return mFadedEdgeDrawHelper.getVerticalFadingEdgeLength();
        } else {
            return super.getVerticalFadingEdgeLength();
        }
    }

    @Override
    public int getHorizontalFadingEdgeLength() {
        if (mFadedEdgeDrawHelper != null) {
            return mFadedEdgeDrawHelper.getHorizontalFadingEdgeLength();
        } else {
            return super.getHorizontalFadingEdgeLength();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setScrollbarFadingEnabled(true);
        mLinearLayout = (LinearLayout) findViewById(R.id.recents_linear_layout);
        final int leftPadding = getContext().getResources()
                .getDimensionPixelOffset(
                        R.dimen.status_bar_recents_thumbnail_left_margin);
        setOverScrollEffectPadding(leftPadding, 0);
    }

    @Override
    public void onAttachedToWindow() {
        if (mFadedEdgeDrawHelper != null) {
            mFadedEdgeDrawHelper.onAttachedToWindowCallback(mLinearLayout,
                    isHardwareAccelerated());
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        float densityScale = getResources().getDisplayMetrics().density;
        mSwipeHelper.setDensityScale(densityScale);
        float pagingTouchSlop = ViewConfiguration.get(getContext())
                .getScaledPagingTouchSlop();
        mSwipeHelper.setPagingTouchSlop(pagingTouchSlop);
    }

    private void setOverScrollEffectPadding(int leftPadding, int i) {
        // TODO Add to (Vertical)ScrollView
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Skip this work if a transition is running; it sets the scroll values
        // independently
        // and should not have those animated values clobbered by this logic
        LayoutTransition transition = mLinearLayout.getLayoutTransition();
        if (transition != null && transition.isRunning()) {
            return;
        }
        // Keep track of the last visible item in the list so we can restore it
        // to the bottom when the orientation changes.
        mLastScrollPosition = scrollPositionOfMostRecent();

        // This has to happen post-layout, so run it "in the future"
        post(new Runnable() {
            public void run() {
                // Make sure we're still not clobbering the transition-set
                // values, since this
                // runnable launches asynchronously
                LayoutTransition transition = mLinearLayout
                        .getLayoutTransition();
                if (transition == null || !transition.isRunning()) {
                    scrollTo(mLastScrollPosition, 0);
                }
            }
        });
    }

    public void setAdapter(TaskDescriptionAdapter adapter) {
        mAdapter = adapter;
        mAdapter.registerDataSetObserver(new DataSetObserver() {
            public void onChanged() {
                update();
            }

            public void onInvalidated() {
                update();
            }
        });
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(dm.widthPixels,
                MeasureSpec.AT_MOST);
        int childheightMeasureSpec = MeasureSpec.makeMeasureSpec(
                dm.heightPixels, MeasureSpec.AT_MOST);
        View child = mAdapter.createView(mLinearLayout);
        child.measure(childWidthMeasureSpec, childheightMeasureSpec);
        mNumItemsInOneScreenful = (int) FloatMath.ceil(dm.widthPixels
                / (float) child.getMeasuredWidth());
        addToRecycledViews(child);

        for (int i = 0; i < mNumItemsInOneScreenful - 1; i++) {
            addToRecycledViews(mAdapter.createView(mLinearLayout));
        }
    }

    public int numItemsInOneScreenful() {
        return mNumItemsInOneScreenful;
    }

    @Override
    public void setLayoutTransition(LayoutTransition transition) {
        // The layout transition applies to our embedded LinearLayout
        mLinearLayout.setLayoutTransition(transition);
    }

    public void setCallback(RecentsCallback callback) {
        mCallback = callback;
    }
}
