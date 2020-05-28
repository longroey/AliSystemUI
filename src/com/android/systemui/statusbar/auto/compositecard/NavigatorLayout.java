package com.android.systemui.statusbar.auto.compositecard;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.systemui.R;
import com.android.systemui.statusbar.auto.FontHelper;
import com.android.systemui.statusbar.auto.NotificationConstant;

public class NavigatorLayout extends ParceableLayout{
    int[] iconArray = new int[] { R.drawable.sou2, R.drawable.sou3,
            R.drawable.sou4, R.drawable.sou5, R.drawable.sou6, R.drawable.sou7,
            R.drawable.sou8, R.drawable.sou9, R.drawable.sou10,
            R.drawable.sou11, R.drawable.sou12, R.drawable.sou13,
            R.drawable.sou14, R.drawable.sou15, R.drawable.sou16,
            R.drawable.sou17, R.drawable.sou18, R.drawable.sou19,
            R.drawable.sou20, R.drawable.sou21, R.drawable.sou22,
            R.drawable.sou23, R.drawable.sou24, R.drawable.sou25,
            R.drawable.sou26, R.drawable.sou27 };
    int[] iconDmArray = new int[] { R.drawable.dm_sou2, R.drawable.dm_sou3,
            R.drawable.dm_sou4, R.drawable.dm_sou5, R.drawable.dm_sou6, R.drawable.dm_sou7,
            R.drawable.dm_sou8, R.drawable.dm_sou9, R.drawable.dm_sou10,
            R.drawable.dm_sou11, R.drawable.dm_sou12, R.drawable.dm_sou13,
            R.drawable.dm_sou14, R.drawable.dm_sou15, R.drawable.dm_sou16,
            R.drawable.dm_sou17, R.drawable.dm_sou18, R.drawable.dm_sou19,
            R.drawable.dm_sou20, R.drawable.dm_sou21, R.drawable.dm_sou22,
            R.drawable.dm_sou23, R.drawable.dm_sou24, R.drawable.dm_sou25,
            R.drawable.dm_sou26, R.drawable.dm_sou27 };
    private TextView mNextRoadView;
    private TextView mRemainDistanceView;
    private TextView mRouteRemainTime;
    private TextView mSegRemainDistanceView;
    private ImageView mDirectIconView;
    private ImageView mDirectIconMiniView;
    private TextView mSegRemainDistanceMiniView;
    private View mNavigator;
    private View mMiniNavigator;
    private View mShadowView;
    private int mIconId;
    public NavigatorLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mNextRoadView = (TextView) findViewById(R.id.road_name);
        FontHelper.applyFont(getContext(), mNextRoadView, FontHelper.MYINGHEI_18030_H, 0.7f);
        mRemainDistanceView = (TextView) findViewById(R.id.remain_distance);
        FontHelper.applyFont(getContext(), mRemainDistanceView, FontHelper.DIN_CONDENSED_BOLD, 0.7f);
        mRouteRemainTime = (TextView) findViewById(R.id.remain_time);
        FontHelper.applyFont(getContext(), mRouteRemainTime, FontHelper.DIN_CONDENSED_BOLD, 0.7f);
//        mLimitSpeedView = (TextView) findViewById(R.id.speed_limit_text);
        mDirectIconView = (ImageView) findViewById(R.id.direction_icon);
        mDirectIconMiniView = (ImageView) findViewById(R.id.direction_icon_mini);
        mSegRemainDistanceView = (TextView)findViewById(R.id.seg_remain_distance);
        FontHelper.applyFont(getContext(), mSegRemainDistanceView, FontHelper.DIN_CONDENSED_BOLD);
        mSegRemainDistanceMiniView = (TextView)findViewById(R.id.seg_remain_distance_mini);
        FontHelper.applyFont(getContext(), mSegRemainDistanceMiniView, FontHelper.DIN_CONDENSED_BOLD, 0.7f);
        mNavigator = findViewById(R.id.navigator_content);
        mMiniNavigator = findViewById(R.id.navigator_content_mini);
        mShadowView = findViewById(R.id.shadow_view);
    }

    @Override
    public void parseParams(String param) {
        if(param == null || param.isEmpty()){
            Log.d("NavigatorLayout", "param is empty return. " 
                    + (param == null) + "  param.isEmpty() = " + param.isEmpty());
            return;
        }
        try {
            JSONObject o = new JSONObject(param);
            String nextRoad = o.optString(NotificationConstant.NAVI_NEXT_ROAD_NAME);
            int routeRemDis = o.optInt(NotificationConstant.NAVI_REMAIN_DIS);
            int routeRemTime = o.optInt(NotificationConstant.NAVI_REMAIN_TIME);
            int segRemDis = o.optInt(NotificationConstant.NAVI_SEG_REMAIN_DIS);
            mIconId = o.optInt(NotificationConstant.NAVI_ICON, -1);
            updateDirectIcon();
            mNextRoadView.setText(nextRoad);
            mRemainDistanceView.setText(formatDistance(routeRemDis));
            mRouteRemainTime.setText(formatTime(routeRemTime));
            mSegRemainDistanceView.setText(formatDistance(segRemDis));
            mSegRemainDistanceMiniView.setText(formatDistance(segRemDis));
            int cameraType = o.optInt(NotificationConstant.NAVI_CAMERA_TYPE, -1);
            int cameraDis = o.optInt(NotificationConstant.NAVI_CAMERA_DIST);
            int cameraSpeed = o.optInt(NotificationConstant.NAVI_CAMERA_SPEED, -1);
            int type = o.optInt(NotificationConstant.NAVI_TYPE);
            getRoot().updateSpeedLimitFloatView(cameraSpeed, cameraType);
            updateLayout4MiniState();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    private String formatDistance(int distance){
        StringBuilder sb = new StringBuilder();
        if(distance >= 1000){
            //m to km xx.xx
            sb.append((float) (Math.round((float) distance / 1000 * 100)) / 100);
            sb.append("km");
        }else{
            sb.append(distance);
            sb.append("m");
        }
        return sb.toString();
    }
    private String formatTime(int time){//s
        StringBuilder sb = new StringBuilder();
        if(time >= 3600){
            //s to h min.
//            sb.append((float) Math.round((float) time / 3600 * 100) / 100);
            int hh = time / 3600;
            sb.append(hh);
            sb.append("h");
            int left = time % 3600;
            if(left / 60 > 0 ){
                int mm = left / 60;
                sb.append(mm);
                sb.append("min");
            }
        }else{
            //s to min
            sb.append(time / 60);
            sb.append("min");
        }
        return sb.toString();
    }

    private void updateDirectIcon() {
        if (mIconId == -1) {
            return;
        }
        if ((mIconId - 2) < iconArray.length && mIconId >= 2) {
            int[] directIcon = isDriveMode() ? iconDmArray : iconArray;
            mDirectIconView.setImageResource(directIcon[mIconId - 2]);
            // mini layout
            mDirectIconMiniView.setImageResource(directIcon[mIconId - 2]);
        }
    }

    private void updateLayout4MiniState() {
        if (isMini()) {
            mMiniNavigator.setVisibility(View.VISIBLE);
            mNavigator.setVisibility(View.INVISIBLE);
            mShadowView.setVisibility(View.INVISIBLE);
        } else {
            mNavigator.setVisibility(View.VISIBLE);
            mMiniNavigator.setVisibility(View.INVISIBLE);
            mShadowView.setVisibility(View.VISIBLE);
        }
    }

    protected void switchToMini(boolean mini) {
        super.switchToMini(mini);// set mini type
        updateLayout4MiniState();
    }
    @Override
    protected void onDriveModeChanged(boolean mode) {
        updateDirectIcon();
    }
}
