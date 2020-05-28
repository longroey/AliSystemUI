package com.android.systemui.statusbar.auto;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.systemui.R;
import com.android.systemui.utils.AliUserTrackUtil;

public class NavigatorStatusBarView extends LinearLayout implements View.OnClickListener{
    private static final int INVALID_TYPE = -1;
    private static final String TAG = "NavigatorStatusBarView";
    ImageView mHudView;
    TextView mSegRemainDistanceView;
//    TextView mSegRemainDistanceUnitView;
    int mHudIcon = -1;
    int mSegDistance = -1;
    int mHudState = -1;
    boolean mDriveMode = false;
    View mContent;
    private static final int MSG_HIDE_ME = 1;
    private static final long HIDE_ME_TIMEOUT = 5000;
    private Handler mHander = new Handler(){
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
            case MSG_HIDE_ME:
                Log.d(TAG, "not receive any navigator info, hide me !");
                hideMe();
                break;
            default:
                break;
            }
        };
    };
    public NavigatorStatusBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
//        setOnClickListener(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSegRemainDistanceView = (TextView)findViewById(R.id.seg_remain_distance);
        FontHelper.applyFont(getContext(), mSegRemainDistanceView, FontHelper.DIN_CONDENSED_BOLD);
        mHudView = (ImageView)findViewById(R.id.hud_icon);
        mContent = findViewById(R.id.content);
        mSegRemainDistanceView.setOnClickListener(this);
        mHudView.setOnClickListener(this);
//        mSegRemainDistanceUnitView = (TextView)findViewById(R.id.seg_remain_distance_unit);
//        FontHelper.applyFont(getContext(), mSegRemainDistanceUnitView, FontHelper.DIN_CONDENSED_BOLD);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        //transparent event
        Log.d(TAG, "transparent event, onInterceptTouchEvent return false");
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d(TAG, "transparent event, onTouchEvent return false");
        return false;
    }
    @Override
    public void onClick(View v) {
         Intent intent = new Intent();
         intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
         intent.setComponent(new ComponentName("com.autonavi.amapautolite", "com.autonavi.auto.MainMapActivity"));
         getContext().startActivity(intent);
         AliUserTrackUtil.ctrlClicked("Navigator_Statusbar", "Quick_Navigator");
         hideMe();
    }

    private void hideMe(){
        setVisibility(View.INVISIBLE);
        cancelHideMsgIfHas();
    }

    private void showMe(){
        setVisibility(View.VISIBLE);
      //keep show 5s, if not receive other msg.
        cancelHideMsgIfHas();
        sendHideMsg();
    }

    private void cancelHideMsgIfHas(){
        if(mHander.hasMessages(MSG_HIDE_ME)){
            mHander.removeMessages(MSG_HIDE_ME);
        }
    }

    private void sendHideMsg(){
        mHander.sendEmptyMessageDelayed(MSG_HIDE_ME, HIDE_ME_TIMEOUT);
    }

    public void updateNavigatorStatus(int state, int hudIcon, int segDis){
        mHudIcon = hudIcon;
        mSegDistance = segDis;
        mHudState = state;
        updateView();
    }

    private void checkHudState(){
        if(mDriveMode){
            hideMe();
        }else{
            if(mHudState == NotificationConstant.NAVIGATOR_STATE_SHOW){
                showMe();
            } else {
                hideMe();
            }
        }
    }

    public void switchDriveMode(boolean driveMode){
        mDriveMode = driveMode;
        checkHudState();
    }

//    public void updateNavigatorStatus(NavigatorStatus status){
//        mHudIcon = status.hudIcon;
//        mSegDistance = status.segRemainDistance;
//    }

    public void updateBackground(boolean opque){
        if(opque){
            //setBackgroundColor(0x00000000);
            mContent.setBackground(null);
        }else{
//            setBackgroundColor(0xFF000000);
            mContent.setBackgroundResource(R.drawable.system_bar_background);
        }
    }

    private void updateView() {
        if(mHudIcon == INVALID_TYPE && mHudState != NotificationConstant.NAVIGATOR_STATE_SHOW){
            //show default hud icon (arrow icon).
//            mHudView.setImageResource(R.drawable.);
            mSegRemainDistanceView.setText("");
        }else{
            int hudDrawableId = HudInfoHelper.getHudIcon(mHudIcon, false);
            if(hudDrawableId != -1){
                mHudView.setImageResource(hudDrawableId);
                mSegRemainDistanceView.setText(HudInfoHelper.formatDistance(getContext(), mSegDistance, true));
            }
        }
        checkHudState();
    }

    public class NavigatorStatus{
        public int hudIcon;
        public String nextRoad;
        public int cameraType;
        public int limitSpeed;
        public int segRemainDistance;
        public NavigatorStatus(){

        }
    }
}
