package com.android.systemui.statusbar.auto.compositecard;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.systemui.R;
import com.android.systemui.statusbar.auto.FontHelper;
import com.android.systemui.statusbar.auto.NotificationConstant;

public class RoadInfoLayout extends ParceableLayout{

    private TextView mSpeedInfoView;
    private TextView mSpeedUnitView;
    private TextView mSpeedInfoMiniView;
    private TextView mSpeedUnitMiniView;
    private TextView mCameraRemainDistance;
    private View mSpeedLayout;
    private View mMiniSpeedLayout;
    private int mRoadInfoType;
    private ImageView mCarView;
//    private int mSpeed;
    private int mRemainDistance;
    private int mCurrentSpeed;
    private int mCurLimitSpeed;
    private long mCurCameraTime;
    private static final int MSG_CHECK_SPEED_INFO = 0;
    private static final int MSG_DEBOUNCE_SHOW_NO_LIMIT_SPEED = 1;
    private static final long CHECK_SPEED_TIMEOUT = 5000;
    private static final long DEBOUNCE_SHOW_NO_LIMIT_SPEED_TIME = 500;
    private static final String TAG = "RoadInfoLayout";
    private Handler mHandler = new Handler(){
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
            case MSG_CHECK_SPEED_INFO:
                resetSpeedInfo();
                break;
            case MSG_DEBOUNCE_SHOW_NO_LIMIT_SPEED:
                updateSpeedLimitFloatView(msg.arg1, msg.arg2);
                break;
            default:
                break;
            }
        }
    };
    public RoadInfoLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSpeedInfoView = (TextView) findViewById(R.id.speed_info);
        FontHelper.applyFont(getContext(), mSpeedInfoView, FontHelper.DIN_CONDENSED_BOLD);
        mSpeedInfoMiniView = (TextView) findViewById(R.id.speed_info_mini);
        FontHelper.applyFont(getContext(), mSpeedInfoMiniView, FontHelper.DIN_CONDENSED_BOLD, 0.7f);
        mSpeedUnitView = (TextView) findViewById(R.id.speed_unit);
        FontHelper.applyFont(getContext(), mSpeedUnitView, FontHelper.MYINGHEI_18030_M, 0.5f);
        mSpeedUnitMiniView = (TextView) findViewById(R.id.speed_unit_mini);
        FontHelper.applyFont(getContext(), mSpeedUnitMiniView, FontHelper.MYINGHEI_18030_M, 0.5f);
        mCameraRemainDistance = (TextView) findViewById(R.id.camera_remain_distance);
        FontHelper.applyFont(getContext(), mCameraRemainDistance, FontHelper.DIN_CONDENSED_BOLD);
        mSpeedLayout = findViewById(R.id.speed_layout);
        mMiniSpeedLayout = findViewById(R.id.speed_layout_mini);
        mCarView = (ImageView) findViewById(R.id.icon_car);
    }
    @Override
    public void parseParams(String param) {
        try {
            JSONObject o = new JSONObject(param);
            mRoadInfoType = o.optInt(NotificationConstant.ROAD_INFO_TYPE, -1);
            int speed = o.optInt(NotificationConstant.ROAD_INFO_SPEED);
            String speedUnit = o
                    .optString(NotificationConstant.ROAD_INFO_SPEED_UNIT);
            if(mRoadInfoType == NotificationConstant.ROAD_INFO_TYPE_SPEED_INFO){
                mSpeedInfoView.setText(speed + "");
                mSpeedInfoMiniView.setText(speed + "");
                mSpeedUnitView.setText(speedUnit);
                mSpeedUnitMiniView.setText(speedUnit);
                getRoot().onSpeedInfoChanged(speed);
            } else {
                //update limit float view.
                int limitSpeed = o
                        .optInt(NotificationConstant.ROAD_INFO_LIMIT_SPEED, -1);
                int cameraType = o
                        .optInt(NotificationConstant.ROAD_INFO_CAMERA_TYPE, -1);
                mRemainDistance = o
                        .optInt(NotificationConstant.ROAD_INFO_REMAIN_DISTANCE, -1);
                if(limitSpeed <= 0){
                    //debounce show no speed limit view;
                    debounceShowNoLimitView(limitSpeed, cameraType);
                }else{
                    //cancel debounce if has.
                    cancelDebounceShowIfHas();
                    updateSpeedLimitFloatView(limitSpeed, cameraType);
                }
//                if(mRemainDistance != -1){
//                    mCameraRemainDistance.setText(mRemainDistance + "m");
//                    mCameraRemainDistance.setVisibility(View.VISIBLE);
//                }else{
//                    mCameraRemainDistance.setVisibility(View.INVISIBLE);
//                }
                mCameraRemainDistance.setVisibility(View.INVISIBLE);
            }
//                int carDir = o
//                        .optInt(NotificationConstant.ROAD_INFO_CARDIR);
            updateLayout4MiniState();
            checkSpeedTimeout();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    private void checkSpeedTimeout(){
        //ignore gps lost speed info.
        if(mHandler.hasMessages(MSG_CHECK_SPEED_INFO)){
            mHandler.removeMessages(MSG_CHECK_SPEED_INFO);
        }
        mHandler.sendEmptyMessageDelayed(MSG_CHECK_SPEED_INFO, CHECK_SPEED_TIMEOUT);
    }
    private void debounceShowNoLimitView(int limit, int camera){
        cancelDebounceShowIfHas();
        Message msg = mHandler.obtainMessage(MSG_DEBOUNCE_SHOW_NO_LIMIT_SPEED);
        msg.arg1 = limit;
        msg.arg2 = camera;
        mHandler.sendMessageDelayed(msg,DEBOUNCE_SHOW_NO_LIMIT_SPEED_TIME);
    }
    private void cancelDebounceShowIfHas(){
        if(mHandler.hasMessages(MSG_DEBOUNCE_SHOW_NO_LIMIT_SPEED)){
            mHandler.removeMessages(MSG_DEBOUNCE_SHOW_NO_LIMIT_SPEED);
        }
    }
    private void updateSpeedLimitFloatView(int limit, int camera){
        getRoot().updateSpeedLimitFloatView(limit, camera);
    }
    private void resetSpeedInfo(){
        updateSpeedInfo(0);
        getRoot().onSpeedInfoChanged(0);
        Log.d(TAG, "gps lost, reset speed info. ");
    }
    public void updateSpeedInfo(int speed){
//        mSpeed = speed;
        mSpeedInfoView.setText(speed + "");
        mSpeedInfoMiniView.setText(speed + "");
    }
    private void updateLayout4MiniState(){
        if(isMini()){
            mMiniSpeedLayout.setVisibility(View.VISIBLE);
            mSpeedLayout.setVisibility(View.INVISIBLE);
            showCarView(false);
        }else{
            mSpeedLayout.setVisibility(View.VISIBLE);
            mMiniSpeedLayout.setVisibility(View.INVISIBLE);
            showCarView(true);
//            showCarView(mRemainDistance== -1 ? true : false);
        }
    }
    protected void switchToMini(boolean mini){
        super.switchToMini(mini);//set mini type
        updateLayout4MiniState();
    }
    @Override
    protected void onDriveModeChanged(boolean mode) {
        mCarView.setImageResource(mode ? R.drawable.drivemode_drive_car : R.drawable.home_drive_car);
    }
    public void showCarView(boolean show){
        if(show){
            mCarView.setVisibility(View.VISIBLE);
        }else{
            mCarView.setVisibility(View.INVISIBLE);
        }
    }
}
