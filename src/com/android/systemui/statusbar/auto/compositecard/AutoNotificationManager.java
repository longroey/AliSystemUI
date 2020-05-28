package com.android.systemui.statusbar.auto.compositecard;

import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.auto.NotificationConstant;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.policy.KeyButtonView;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

public class AutoNotificationManager {
	Context mContext;
	HashMap<Integer, AutoNotification> mActiveNotification;
	NotificationStackFrameLayout mStackLayout;
	PhoneStatusBar mBar;
	private static final String NAVI_ACTION = "AUTONAVI_STANDARD_BROADCAST_SEND";
    private static final String KEY_TYPE = "KEY_TYPE";
    private static final float SPEED_FACTOR = 3.6f;// m/s to km/h
    private static final String SPEED_UNIT = "km/h";
	protected static final String TAG = "AutoNotificationManager";
	protected static final boolean DEBUG = true;
    boolean needShowNavigator = false;
    boolean mIsAutoRun = false;
    boolean mIsAutoForeground = false;
    boolean mIsAutoNavi = false;
    boolean mIsAutoSimulate = false;
    boolean mIsAutoTTS = false;
	boolean mListening = false;
    private LocationManager mLocationManager;
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DEBUG)
                Log.v(TAG, "onReceive: " + intent);
            String action = intent.getAction();
            if (NotificationConstant.NOTIFICATION_ACTION.equals(action)) {
                StringBuilder sb = new StringBuilder();
                int type = intent.getIntExtra(NotificationConstant.KEY_NOTIFICATION_TYPE, -1);
                int priority = intent.getIntExtra(NotificationConstant.KEY_NOTIFICATION_PRIORITY, 0);
                int state = intent.getIntExtra(NotificationConstant.KEY_NOTIFICATION_STATE, -1);
                String params = intent.getStringExtra(NotificationConstant.KEY_NOTIFICATION_PARAMS);
                int debounce = intent.getIntExtra(NotificationConstant.KEY_NOTIFICATION_DEBOUNCE, 0);
                int keep = intent.getIntExtra(NotificationConstant.KEY_NOTIFICATION_KEEP,
                        type != NotificationConstant.NOTIFICATION_TYPE_ACCOUNT ? 3000 : 0);
                String packageName = intent.getPackage();
                sb.append(", type = " + type);
                sb.append(", priority = " + priority);
                sb.append(", state = " + state);
                sb.append(", params = " + params);
                sb.append(", packageName = " + packageName);
                if (DEBUG)
                    Log.d(TAG, "intent extra = " + sb.toString());
                if (state == NotificationConstant.STATE_CANCEL) {
                    removeNotification(type, debounce);
                } else if (state == NotificationConstant.STATE_UPDATE) {
                    updateNotification(type, priority, params, debounce, keep);
                }
            } else if (action.equals(NAVI_ACTION)) {
                int key_type = intent.getIntExtra(KEY_TYPE, 10000);
                StringBuilder sb = new StringBuilder();
                int ntype = NotificationConstant.NOTIFICATION_TYPE_NAVIGATOR;
                int priority = NotificationConstant.PRIOROTY_HIGH;
                String param = "";
                switch (key_type) {
                /*
                 * GuideInfoExtraKey
                 */
                case 10001:
                    int type = intent.getIntExtra(AutoConstant.GuideInfoExtraKey.TYPE, -1);
                    String next_road_name = intent.getStringExtra(AutoConstant.GuideInfoExtraKey.NEXT_ROAD_NAME);
                    int icon = intent.getIntExtra(AutoConstant.GuideInfoExtraKey.ICON, -1);
                    int seg_remain_dis = intent.getIntExtra(AutoConstant.GuideInfoExtraKey.SEG_REMAIN_DIS, 0);
                    int route_remain_dis = intent.getIntExtra(AutoConstant.GuideInfoExtraKey.ROUTE_REMAIN_DIS, 0);
                    int route_remain_time = intent.getIntExtra(AutoConstant.GuideInfoExtraKey.ROUTE_REMAIN_TIME, 0);
                    String cur_road_name = intent.getStringExtra(AutoConstant.GuideInfoExtraKey.CUR_ROAD_NAME);
                    int limited_speed = intent.getIntExtra(AutoConstant.GuideInfoExtraKey.LIMITED_SPEED, 0);
                    int camera_type = intent.getIntExtra(AutoConstant.GuideInfoExtraKey.CAMERA_TYPE, -1);
                    int camera_dist = intent.getIntExtra(AutoConstant.GuideInfoExtraKey.CAMERA_DIST, 0);
                    int camera_speed = intent.getIntExtra(AutoConstant.GuideInfoExtraKey.CAMERA_SPEED, 0);
                    if (DEBUG)
                        Log.d(TAG,
                                "10001: type=" + type + " icon=" + icon + " next_road_name=" + next_road_name
                                        + " seg_remain_dis=" + seg_remain_dis + " route_remain_dis=" + route_remain_dis
                                        + " route_remain_time=" + route_remain_time + " cur_road_name=" + cur_road_name
                                        + " limited_speed=" + limited_speed + " camera_type=" + camera_type
                                        + " camera_dist=" + camera_dist + " camera_speed=" + camera_speed);
                    param = generateNavigatorParam(next_road_name, seg_remain_dis,
                            route_remain_dis, route_remain_time, limited_speed,
                            camera_type, camera_dist, camera_speed, icon, type);
                    if(param == null){
                        Log.d(TAG, "json error when hanlde broadcast " + NAVI_ACTION);
                        param = "";
                    }
                    break;
                /*
                 * autonavi state: 0 start run, 1 inited, 2 stop run; 3
                 * foreground, 4 background; 5 start compute route, 6 success, 7
                 * fail; 8 start navi, 9 stop; 10 start simulation navi, 11
                 * pause, 12 stop; 13 start tts, 14 stop;
                 */
                case 10019:
                    int state = intent.getIntExtra("EXTRA_STATE", -1);
                    needShowNavigator = !isShouldCurise(state);
                    if (DEBUG)
                        Log.d(TAG, "10019: state=" + state + " needShowNavigator=" + needShowNavigator);
                    break;
                default:
                    break;
                }
                if (needShowNavigator) {
                    updateNotification(ntype, priority, param, 0, 3000);
                } else {
                    removeNotification(ntype);
                }
            } else if (action.equals(NotificationConstant.DRIVEMODE_STATUS_ACTION)) {
                int on = intent.getIntExtra(NotificationConstant.KEY_DRIVEMODE_STATUS,
                        NotificationConstant.DRIVEMODE_OFF);
                //if in account mode, remove it.
                if(on == NotificationConstant.DRIVEMODE_ON)
                    removeNotification(NotificationConstant.NOTIFICATION_TYPE_ACCOUNT);
                switchDriveMode(on == NotificationConstant.DRIVEMODE_ON);

            } else if (action.equals(NotificationConstant.ACTION_ACCOUNT_BROADCAST_LOGIN)
                    || action.equals(NotificationConstant.ACTION_ACCOUNT_BROADCAST_LOGOUT)) {
                if (DEBUG)
                    Log.d(TAG, "notify account info update, action = " + action);
                notifyAccountInfoUpdate();
            } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                // check location service after boot completed
                checkLocationListener();
                // check account info
                notifyAccountInfoUpdate();
            }
        }
    };
    public AutoNotificationManager(Context context, NotificationStackFrameLayout layout, PhoneStatusBar bar) {
		mContext = context;
		mStackLayout = layout;
		mBar = bar;
		mActiveNotification = new HashMap<Integer, AutoNotification>();
		defaultAccountInfoInitial();
		mStackLayout.setStatusBar(mBar);
		checkLocationListener();
	}
    public void startListening(boolean listening){
    	if(mListening != listening){
    		if(listening){
    			IntentFilter filter = new IntentFilter();
    	    	filter.addAction(NAVI_ACTION);
    	    	filter.addAction(NotificationConstant.NOTIFICATION_ACTION);
    	    	filter.addAction(NotificationConstant.DRIVEMODE_STATUS_ACTION);
    	    	filter.addAction(NotificationConstant.ACTION_ACCOUNT_BROADCAST_LOGIN);
    	    	filter.addAction(NotificationConstant.ACTION_ACCOUNT_BROADCAST_LOGOUT);
    	    	filter.addAction(Intent.ACTION_BOOT_COMPLETED);
    	    	mContext.registerReceiverAsUser(mBroadcastReceiver, UserHandle.ALL, filter, null, null);
    		}else{
    			mContext.unregisterReceiver(mBroadcastReceiver);
    		}
			mListening = listening;
    	}
    }
    private boolean isShouldCurise(int state) {
        boolean ret = true;
        if (state == AutoConstant.AUTONAVI_STATE.STOP_RUN) {
                mIsAutoRun = false;
                mIsAutoForeground = false;
                //mIsCompute = false;
                mIsAutoNavi = false;
                mIsAutoSimulate = false;
                mIsAutoTTS = false;
        } else {
                mIsAutoRun = true;

                switch(state) {
                /*
                case AutoConstant.AUTONAVI_STATE.ENTER_FOREGROUND:
                        mIsAutoForeground = true;
                        break;
                case AutoConstant.AUTONAVI_STATE.ENTER_BACKGROUND:
                        mIsAutoForeground = false;
                        break;
                */
                /*case AutoConstant.AUTONAVI_STATE.START_COMPUTE:
                        mIsCompute = true;
                        break;
                case AutoConstant.AUTONAVI_STATE.COMPUTE_SUCCESS:
                case AutoConstant.AUTONAVI_STATE.COMPUTE_FAIL:
                        mIsCompute = false;
                        break;*/
                case AutoConstant.AUTONAVI_STATE.START_NAVI:
                        mIsAutoNavi = true;
                        break;
                case AutoConstant.AUTONAVI_STATE.STOP_NAVI:
                        mIsAutoNavi = false;
                        break;
                case AutoConstant.AUTONAVI_STATE.START_SIMULATION:
                        mIsAutoSimulate = true;
                        break;
                case AutoConstant.AUTONAVI_STATE.PAUSE_SIMULATION:
                        mIsAutoSimulate = !mIsAutoSimulate;
                        break;
                case AutoConstant.AUTONAVI_STATE.STOP_SIMULATION:
                        mIsAutoSimulate = false;
                        break;
                /*
                case AutoConstant.AUTONAVI_STATE.START_TTS:
                        mIsAutoTTS = true;
                        break;
                case AutoConstant.AUTONAVI_STATE.STOP_TTS:
                        mIsAutoTTS = false;
                        break;
                */
                        default:
                        break;
                }
        }

        if (/*!mIsOpenWatchDog || mIsAutoForeground || */mIsAutoNavi || mIsAutoSimulate /*|| mIsAutoTTS*/) {
            Log.d(TAG, "10019: mIsAutoNavi = " + mIsAutoNavi + ", mIsAutoSimulate = " + mIsAutoSimulate);
                ret = false;
        }
        return ret;
    }
    private String generateNavigatorParam(String next_road_name, int seg_remain_dis,
            int route_remain_dis, int route_remain_time, int limited_speed, int camera_type,
            int camera_dist, int camera_speed, int icon, int type){
        JSONObject o = new JSONObject();
        try {
            o.put(NotificationConstant.NAVI_NEXT_ROAD_NAME, next_road_name);
            o.put(NotificationConstant.NAVI_SEG_REMAIN_DIS, seg_remain_dis);
            o.put(NotificationConstant.NAVI_REMAIN_DIS, route_remain_dis);
            o.put(NotificationConstant.NAVI_REMAIN_TIME, route_remain_time);
            o.put(NotificationConstant.NAVI_LIMITED_SPEED, limited_speed);
            o.put(NotificationConstant.NAVI_CAMERA_TYPE, camera_type);
            o.put(NotificationConstant.NAVI_CAMERA_DIST, camera_dist);
            o.put(NotificationConstant.NAVI_CAMERA_SPEED, camera_speed);
            o.put(NotificationConstant.NAVI_ICON, icon);
            o.put(NotificationConstant.NAVI_TYPE, type);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
        return o.toString();
    }
    private void checkLocationListener() {
        if (mLocationManager != null) {
            Log.d(TAG, "initLocationListener location service is ready, do nothing!");
            return;
        }
        mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        if (mLocationManager != null) {
            GpsLocationListener mGpsStatusListener = new GpsLocationListener();
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, mGpsStatusListener);
        } else {
            Log.d(TAG, "initLocationListener Location service not ready!");
        }
    }
    private void updateSpeedInfo(int speed) {
        //update per 1s.
        int type = NotificationConstant.NOTIFICATION_TYPE_ROADINFO;
        int priority = NotificationConstant.PRIOROTY_LOW;
        JSONObject o = new JSONObject();
        try {
            o.put(NotificationConstant.ROAD_INFO_TYPE, NotificationConstant.ROAD_INFO_TYPE_SPEED_INFO);
            o.put(NotificationConstant.ROAD_INFO_SPEED, speed);
            o.put(NotificationConstant.ROAD_INFO_SPEED_UNIT, "km/h");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String params = o.toString();
        int debounce = 0;
        int keep = -1;
        updateNotification(type, priority, params, debounce, keep);
    }
    public int getSpeedCompensate(float speed){
        int compensate = 0;
        if(speed >= 10 && speed < 20){
            compensate = 2;
        }else if (speed >= 20 && speed < 30){
            compensate = 3;
        }else if(speed >= 30){
            compensate = 4;
        }
        return compensate;
    }
    public class GpsLocationListener implements LocationListener {
        public void onLocationChanged(Location location) {
            float gspeed = location.getSpeed();
            float currentSpeed = (float) (gspeed * SPEED_FACTOR + getSpeedCompensate(gspeed));
            if(currentSpeed < NotificationConstant.MIN_RUNNING_SPEED){
                //ignore little speed
                if(DEBUG)Log.d(TAG, "ignore little speed = " + currentSpeed);
                currentSpeed = 0;
            }
            updateSpeedInfo((int) currentSpeed);
        }

        public void onProviderDisabled(String provider) {
            Log.d(TAG, "ProviderDisabled : " + provider);
        }

        public void onProviderEnabled(String provider) {
            Log.d(TAG, "ProviderEnabled : " + provider);
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.d(TAG, "StatusChanged : " + provider + status);
        }
    }
	private void defaultAccountInfoInitial() {
		JSONObject o = new JSONObject();
		try {
			o.put(NotificationConstant.ACCOUNT_INFO_TYPE, NotificationConstant.ACCOUNT_INFO_TYPE_INITIAL);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		String param = o.toString(); 
		updateNotification(NotificationConstant.NOTIFICATION_TYPE_ACCOUNT, NotificationConstant.PRIOROTY_HIGH,
				param);
	}

	public void updateNotification(int type, int priority, String param) {
		updateNotification(type, priority, param, 0, 0);
	}

	public void updateNotification(int type, int priority, String param, long debounce, long keep) {
		if (mActiveNotification.get(type) == null) {
			AutoNotification newNotification = new AutoNotification(type, priority, param, debounce, keep);
			mActiveNotification.put(type, newNotification);
		}
		if (mStackLayout != null)
			mStackLayout.updateUI4Notification(type, priority, param, debounce, keep);
	}
	public void notifyAccountInfoUpdate(){
		if (mStackLayout != null)
			mStackLayout.notifyAccountInfoUpdate();
	}
	public void removeNotification(int type) {
		removeNotification(type, 0);
	}

	public void removeNotification(int type, long debounce) {
		mActiveNotification.remove(type);
		if (mStackLayout != null)
			mStackLayout.removeNotification(type, debounce);
	}
	
	public void switchDriveMode(boolean on){
		if (mStackLayout != null)
			mStackLayout.setDriveMode(on);
	}

	public class AutoNotification {
		public int type;
		public int priority;
		public String param;
		long debounce;
		long keep;
		public AutoNotification(int type, int priority, String param) {
			this.type = type;
			this.priority = priority;
			this.param = param;
		}
		public AutoNotification(int type, int priority, String param, long debounce, long keep) {
			this.type = type;
			this.priority = priority;
			this.param = param;
			this.debounce = debounce;
			this.keep = keep;
		}
	}
}
