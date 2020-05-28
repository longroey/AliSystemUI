package com.android.systemui.statusbar.auto.compositecard;

import org.json.JSONException;
import org.json.JSONObject;

import com.android.systemui.ExpandHelper;
import com.android.systemui.statusbar.DragDownHelper.DragDownCallback;
import com.android.systemui.statusbar.auto.NotificationConstant;
import com.android.systemui.statusbar.ExpandableView;
import com.android.systemui.statusbar.phone.PhoneStatusBar;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings.Global;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.systemui.R;

public class NotificationStackFrameLayout extends FrameLayout implements
        ExpandHelper.Callback {
    NavigatorLayout mNaviLayout;
    RoadInfoLayout mRoadInfoLayout;
    AccountLayout mAccountLayout;
    ADASLayout mADASLayout;
    ParceableLayout mActiveLayout;
    SpeedLimitFloatView mSpeedLimitFloatView;
    int mLastActiveIndex;
    int mKeepingType = -1;
    private Context mContext;
    private static final boolean DEBUG = true;
    private static final String GLOBAL_ROADINFO_STATE = "watch_dog";
//    private static final float SPEED_FACTOR = 3.6f;// m/s to km/h
    private static boolean mKeepAccount = true;
    private static final int MSG_KEEP_NOTIFICATION = 1000;
    static final int MSG_KEEP_ADAS_NOTIFICATION = 1001;
    private boolean mIsKeeping = false;
    private boolean mIsADASKeeping = false;
    int mADASKeepingType = -1;
    static final int MAG_DEBOUNCE_UPDATE_UI = 1;
    private static final String TAG = "NotificationStackFrameLayout";
    Runnable mDebounceRunnable;
    int mDebounceType;
    LocationManager locationManager;
    AnimationDrawable mRoadAnimator;
    AnimationDrawable mDmRoadAnimator;
    private ImageView mRoadBackgroundView;
    private boolean animatorRunning;
    private PhoneStatusBar mBar;
    private int mCurrentSpeed;
    Handler mDebounceHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MAG_DEBOUNCE_UPDATE_UI:
                int type = msg.arg1;
                if(type == mDebounceType){
                    mDebounceRunnable.run();
                }
                break;

            default:
                break;
            }
        }

    };
    // public NotificationStackFrameLayout(Context context) {
    // super(context);
    // }


    public NotificationStackFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
//         setChildrenDrawingOrderEnabled(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mNaviLayout = (NavigatorLayout) findViewById(R.id.navigator_layout);
        mRoadInfoLayout = (RoadInfoLayout) findViewById(R.id.road_info_layout);
        mAccountLayout = (AccountLayout) findViewById(R.id.account_layout);
        mADASLayout = (ADASLayout) findViewById(R.id.adas_layout);
        mNaviLayout.setRoot(this);
        mRoadInfoLayout.setRoot(this);
        mAccountLayout.setRoot(this);
        mADASLayout.setRoot(this);
        mSpeedLimitFloatView = (SpeedLimitFloatView) findViewById(R.id.speed_limit_float_view);
        mRoadBackgroundView = (ImageView)findViewById(R.id.background_animator);
        mNaviLayout.hideLayout();
        mRoadInfoLayout.hideLayout();
        mADASLayout.hideLayout();
        //mAccountLayout.hideLayout();
        mDmRoadAnimator = (AnimationDrawable) mContext.getResources().getDrawable(R.drawable.drivemode_road_running_bg);
        mRoadAnimator = (AnimationDrawable) mContext.getResources().getDrawable(R.drawable.road_running_bg);
    }

    public void updateUI4Notification(int type, int priority, String param) {
        updateUI4Notification(type, priority, param, 0, 0);
    }
    public void updateUI4Notification(final int type, final int priority, final String param, long debounce, long keep) {
//        updatefistBootAccountKeepState(type, param);
//        if (mKeepAccount) {
//            return;
//        }
//        if (isNotificationKeeping()) {
//            return;
//        }
        mDebounceRunnable = new Runnable() {
            @Override
            public void run() {
                setActiveLayout(type, param);
                requestLayout();
            }
        };
        if (debounce > 0) {
            if (mDebounceHandler.hasMessages(MAG_DEBOUNCE_UPDATE_UI))
                mDebounceHandler.removeMessages(MAG_DEBOUNCE_UPDATE_UI);
            Message msg = mDebounceHandler.obtainMessage(MAG_DEBOUNCE_UPDATE_UI);
            msg.arg1 = type;
            mDebounceHandler.sendMessageDelayed(msg, debounce);
            mDebounceType = type;
        }else{
            mDebounceRunnable.run();
        }
        if(keep > 0){
            if((type == NotificationConstant.NOTIFICATION_TYPE_ADAS)){
                //handle adas notification specially. //not replaced by other type.
                keepADASNotification(type,keep);
            }else{
                keepNotification(type, keep);
            }
        }
    }
    public void notifyAccountInfoUpdate(){
        if(mAccountLayout != null)
            ((AccountLayout) mAccountLayout).notifyAccountInfoUpdate();
    }
    private boolean isNotificationKeeping() {
        return mIsKeeping;
    }
    private void keepADASNotification(int type, long time){
        mIsADASKeeping = true;
        if(mHandler.hasMessages(MSG_KEEP_ADAS_NOTIFICATION))
            mHandler.removeMessages(MSG_KEEP_ADAS_NOTIFICATION);
        Message msg = mHandler.obtainMessage(MSG_KEEP_ADAS_NOTIFICATION);
        msg.arg1 = type;
        mADASKeepingType = type;
        mHandler.sendMessageDelayed(msg, time);
    }
    private void keepNotification(int type, long time) {
        mIsKeeping = true;
        if(mHandler.hasMessages(MSG_KEEP_NOTIFICATION))
            mHandler.removeMessages(MSG_KEEP_NOTIFICATION);
        Message msg = mHandler.obtainMessage(MSG_KEEP_NOTIFICATION);
        msg.arg1 = type;
        mKeepingType = type;
        mHandler.sendMessageDelayed(msg, time);
    }

    protected Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_KEEP_NOTIFICATION:
                mIsKeeping = false;
                //remove it, if not removed.
                removeNotification(msg.arg1);
                mKeepingType = -1;
                break;
            case MSG_KEEP_ADAS_NOTIFICATION:
                mIsADASKeeping = false;
                //remove it, if not removed.
                removeNotification(msg.arg1);
                mADASKeepingType = -1;
            }
        }
    };

    private void removeInitialAccountIfHas() {
        mAccountLayout.cancelAccountInitial();
//        removeNotification(NotificationConstant.NOTIFICATION_TYPE_ACCOUNT);
    }

    public void switch2AccountInfoInitial() {
        mAccountLayout.resetAccountInitial();
    }
    boolean checkOverSpeed(int limitSpeed){
        return mCurrentSpeed >= limitSpeed;
    }

    public void onSpeedInfoChanged(int speed) {
        mCurrentSpeed = speed;
        if (speed >= NotificationConstant.MIN_RUNNING_SPEED) {
            removeInitialAccountIfHas();
            startRoadRunning(true);
        } else {
            switch2AccountInfoInitial();
            removeNotification(NotificationConstant.NOTIFICATION_TYPE_ROADINFO, 0);
            //called checkSpeedLayoutVisibility, if all layout invisible.
            startRoadRunning(false);
        }
    }
    public int getCurrentSpeed(){
        return mCurrentSpeed;
    }
    private void setActiveLayout(int type, String param) {
        if(DEBUG)Log.d(TAG, "setActiveLayout type = " + type + ", mNaviLayout.isShown() = " + mNaviLayout.isShown());
        if (findLayoutByType(type) != null) {
            boolean updateBackground = false;
            if (NotificationConstant.NOTIFICATION_TYPE_ADAS == type) {
                switchToMini(true);
            }else if(NotificationConstant.NOTIFICATION_TYPE_NAVIGATOR == type){
                mRoadInfoLayout.hideLayout();
            }else if(NotificationConstant.NOTIFICATION_TYPE_ROADINFO == type){
                updateBackground = mNaviLayout.isShown();
                //conflict with navigator layout.
            }
            mActiveLayout = (ParceableLayout) findLayoutByType(type);
            mActiveLayout.parseParams(param);
            if(!updateBackground){
                mActiveLayout.showLayout();
            }
        }
    }

    public void showSpeedLimitFloat(boolean show){
        if(show){
            mSpeedLimitFloatView.setVisibility(View.VISIBLE);
        }else{
            mSpeedLimitFloatView.setVisibility(View.INVISIBLE);
        }
    }
    public void switchToMini(boolean mini){
        mNaviLayout.switchToMini(mini);
        mRoadInfoLayout.switchToMini(mini);
        //other layout not has mini type.
    }

    public void setDriveMode(boolean drivemode){
        mNaviLayout.switchDriveMode(drivemode);
        mRoadInfoLayout.switchDriveMode(drivemode);
        mADASLayout.switchDriveMode(drivemode);
        mAccountLayout.switchDriveMode(drivemode);
        mSpeedLimitFloatView.switchDriveMode(drivemode);
        updateAnimatorBackground(drivemode);
    }
    private void updateAnimatorBackground(boolean drivemode){
        AnimationDrawable select = drivemode ? mDmRoadAnimator : mRoadAnimator;
        mRoadBackgroundView.setImageDrawable(select);
        if(animatorRunning){
            select.start();
        }else{
            select.stop();
        }
    }
    public boolean isADASKeeping(){
        return mIsKeeping && mKeepingType == NotificationConstant.NOTIFICATION_TYPE_ADAS;
    }
    public void startRoadRunning(boolean start){
        if(start){
            ((AnimationDrawable)mRoadBackgroundView.getDrawable()).start();
        }else{
            ((AnimationDrawable)mRoadBackgroundView.getDrawable()).stop();
        }
        animatorRunning = start;
    }
    public void updateSpeedLimitFloatView(int limitSpeed, int cameraType){
        if(cameraType == -1){
            //not has camera
            Log.d(TAG, "updateSpeedLimitFloatView not has camera !");
            showSpeedLimitFloat(false);
        }else{
            showSpeedLimitFloat(true);
            mSpeedLimitFloatView.setCameraType(cameraType, limitSpeed, checkOverSpeed(limitSpeed));
        }
    }

    private ParceableLayout findLayoutByType(int type) {
        ParceableLayout found = null;
        switch (type) {
        case NotificationConstant.NOTIFICATION_TYPE_ROADINFO:
            found = mRoadInfoLayout;
            break;
        case NotificationConstant.NOTIFICATION_TYPE_ADAS:
            found = mADASLayout;
            break;
        case NotificationConstant.NOTIFICATION_TYPE_ACCOUNT:
            found = mAccountLayout;
            break;
        case NotificationConstant.NOTIFICATION_TYPE_NAVIGATOR:
            found = mNaviLayout;
            break;
        default:
            break;
        }
        return found;
    }

    public void removeNotification(int type) {
        removeNotification(type, 0);
    }

    public void removeNotification(final int type, long debounce) {
        mDebounceRunnable = new Runnable() {
            @Override
            public void run() {
                ParceableLayout found = findLayoutByType(type);
                if(DEBUG)Log.d(TAG, "removeNotification type = " + type + ", mNaviLayout.isShown() = " + mNaviLayout.isShown());
                if (found != null) {
                    found.hideLayout();
                    checkSpeedLayoutVisibility(type);
                    if(type == NotificationConstant.NOTIFICATION_TYPE_ADAS){
                        //revert other layout type.
                        switchToMini(false);
                    } else if (type == NotificationConstant.NOTIFICATION_TYPE_NAVIGATOR){
                        Log.d(TAG, "removeNotification not showSpeedLimitFloat type = " + type);
                        showSpeedLimitFloat(false);//not show float view
                    }
                }
            }

        };
        if (debounce != 0) {
            if (mDebounceHandler.hasMessages(MAG_DEBOUNCE_UPDATE_UI))
                mDebounceHandler.removeMessages(MAG_DEBOUNCE_UPDATE_UI);
            Message msg = mDebounceHandler.obtainMessage(MAG_DEBOUNCE_UPDATE_UI);
            msg.arg1 = type;
            mDebounceHandler.sendMessageDelayed(msg, debounce);
            mDebounceType = type;
        }else{
            mDebounceRunnable.run();
        }
    }
    //need keep one visible layout
    private void checkSpeedLayoutVisibility(int type) {
        boolean allInvisible = mRoadInfoLayout.getVisibility() != View.VISIBLE
                && mAccountLayout.getVisibility() != View.VISIBLE
                && mNaviLayout.getVisibility() != View.VISIBLE
                && mADASLayout.getVisibility() != View.VISIBLE;
        if(allInvisible){
            if(DEBUG)Log.d(TAG, "checkSpeedLayoutVisibility allInvisible type = " + type
                    + ", account layout initial = " + mAccountLayout.isInitial());
            if(mAccountLayout.isInitial()){
                mAccountLayout.showLayout();
            }else{
                //show roadinfo
                mRoadInfoLayout.showLayout();
            }
        }
    }
    /**
     * Change the position of child to a new location
     *
     * @param child
     *            the view to change the position for
     * @param newIndex
     *            the new index
     */
    public void changeViewPosition(View child, int newIndex) {

    }

    @Override
    public ExpandableView getChildAtRawPosition(float x, float y) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ExpandableView getChildAtPosition(float x, float y) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean canChildBeExpanded(View v) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setUserExpandedChild(View v, boolean userExpanded) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setUserLockedChild(View v, boolean userLocked) {
        // TODO Auto-generated method stub

    }

    @Override
    public void expansionStateChanged(boolean isExpanding) {
        // TODO Auto-generated method stub

    }

    public void cancelExpandHelper() {
        // TODO Auto-generated method stub
    }

    public void setStatusBar(PhoneStatusBar bar) {
        mBar = bar;
    }

    public boolean isDeviceProvisioned(){
        return mBar.isDeviceProvisioned();
    }
}
