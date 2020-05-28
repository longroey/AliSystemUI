package com.android.systemui.statusbar.auto;

import android.net.Uri;

public class NotificationConstant {
    /**
     * send this broadcast notify systemui update UI.
     */
    public static final String NOTIFICATION_ACTION = "com.yunos.updatenotification";
    /**
     * send this broadcast notify systemui update record status icon.
     */
    public static final String RECORD_STATUS_ACTION = "com.yunos.recordstatus";
    /**
     * send this broadcast notify systemui update drive mode UI.
     */
    public static final String DRIVEMODE_STATUS_ACTION = "com.yunos.drivemodestatus";
    /**
     * send this broadcast notify systemui update phone state.
     */
    public static final String PHONE_STATE_ACTION = "com.yunos.phonestate";
    /**
     * send this broadcast notify systemui status bar update navigator hud.
     */
    public static final String NAVIGATOR_HUD_CHANGED = "com.yunos.navigatorhudchanged";
    /**
     * package of systemui, it receive this (update notification) broadcast.
     */
    public static final String NOTIFICATION_RECEIVE_PACKAGE = "com.android.systemui";
    /**
     * key of notification type.
     */
    public static final String KEY_NOTIFICATION_TYPE = "key_notification_type";
    /**
     * key of notification priority.
     */
    public static final String KEY_NOTIFICATION_PRIORITY = "key_notification_priority";
    /**
     * key of notification parameter.
     */
    public static final String KEY_NOTIFICATION_PARAMS = "key_notification_params";
    /**
     * key of notification state.
     */
    public static final String KEY_NOTIFICATION_STATE = "key_notification_state";
    /**
     * key of notification debounce.
     */
    public static final String KEY_NOTIFICATION_DEBOUNCE = "key_notification_debounce";
    /**
     * key of notification debounce.
     */
    public static final String KEY_NOTIFICATION_KEEP = "key_notification_keep";
    /**
     * key of record status
     */
    public static final String KEY_RECORD_STATUS = "key_record_status";
    /**
     * key of drive mode status
     */
    public static final String KEY_DRIVEMODE_STATUS = "key_drivemode_status";
    /**
     * key of phone status
     */
    public static final String KEY_PHONE_STATE = "key_phone_state";
    /**
     * key of show talking bar
     */
    public static final String KEY_SHOW_TALKING_BAR = "key_show_talking_bar";
    /**
     * key of navigator hud state
     */
    public static final String KEY_NAVIGATOR_HUD_STATE = "key_navigator_hud_state";
    /**
     * key of navigator hud icon
     */
    public static final String KEY_NAVIGATOR_HUD_ICON = "key_navigator_hud_icon";
    /**
     * key of segment remain distance
     */
    public static final String KEY_NAVIGATOR_SEG_REMAIN_DIS = "key_navigator_seg_remain_distance";
    /**
     * key of phone hold time long (ms)
     */
    public static final String KEY_PHONE_START_TIME = "key_phone_hold_time";
    /**
     * navigator type, send from navigator app.
     */
    public static final int NOTIFICATION_TYPE_NAVIGATOR = 0;
    /**
     * road info type, send from ali road info app.
     */
    public static final int NOTIFICATION_TYPE_ROADINFO = 1;
    /**
     * adas info type, send from dvr app.
     */
    public static final int NOTIFICATION_TYPE_ADAS = 2;
    /**
     * account type, send from account app.
     */
    public static final int NOTIFICATION_TYPE_ACCOUNT = 3;
    /**
     * start record
     */
    public static final int RECORD_STATUS_START = 0;
    /**
     * stop record
     */
    public static final int RECORD_STATUS_STOP = 1;
    /**
     * drivemode on
     */
    public static final int DRIVEMODE_ON = 0;
    /**
     * drivemode off
     */
    public static final int DRIVEMODE_OFF = 1;
    /**
     * update state.
     */
    public static final int STATE_UPDATE = 0;
    /**
     * cancel state.
     */
    public static final int STATE_CANCEL = 1;
    /**
     * hide talking bar
     */
    public static final int HIDE_TALKING_BAR = 0;
    /**
     * show talking bar
     */
    public static final int SHOW_TALKING_BAR = 1;
    /**
     * phone status idle
     */
    public static final int PHONE_STATE_IDLE = 0;
    /**
     * phone status offhook
     */
    public static final int PHONE_STATE_OFFHOOK = 1;
    /**
     * phone status comming call
     */
    public static final int PHONE_STATE_COMMING_CALL = 2;
    /**
     * phone status going call
     */
    public static final int PHONE_STATE_GOING_CALL = 3;
    /**
     * default priority, can be overlay by other notification. such as speed
     * info.
     */
    public static final int PRIOROTY_LOW = 0;
    /**
     * high priority, update immediately
     */
    public static final int PRIOROTY_HIGH = 1;

    public static final String NOTIFICATION_TYPE = "notification_type";
    /* adas constant start */
    public static final String ADAS_FRONT_WARNING = "front_warning";
    public static final String ADAS_LEFT_CLAMPING = "left_clamping";
    public static final String ADAS_RIGHT_CLAMPING = "right_clamping";
    /* adas constant end */

    /* road info constant start */

    /**
     * road info type,
     */
    public static final String ROAD_INFO_TYPE = "road_info_type";
    /**
     * int
     */
    public static final int ROAD_INFO_TYPE_SPEED_INFO = 0;
    /**
     * int
     */
    public static final int ROAD_INFO_TYPE_CAMERA_INFO = 1;
    /**
     * int, car's speed
     */
    public static final String ROAD_INFO_SPEED = "road_info_speed";
    /**
     * String, speed unit km/h
     */
    public static final String ROAD_INFO_SPEED_UNIT = "road_info_speed_unit";
    /**
     * int, car direction, north 0 degrees, clockwise to increase
     */
    public static final String ROAD_INFO_CARDIR = "road_info_cardir";
    /**
     * int, camera or road limit speed
     */
    public static final String ROAD_INFO_LIMIT_SPEED = "road_info_limit_speed";
    /**
     * int, remain distance
     */
    public static final String ROAD_INFO_REMAIN_DISTANCE = "road_info_remain_distance";
    /**
     * int, camera type
     */
    public static final String ROAD_INFO_CAMERA_TYPE = "road_info_camera_type";
    /**
     * int, min running speed.
     * if > this, roadrunning animator start, initial account type removed.
     */
    public static final int MIN_RUNNING_SPEED = 8;//5; determined by gps accuracy.
    /* road info constant end */

    /* account constant start */
    /*------------------------Account Send Notification To SystemUI------------------------*/
    /*
     * default
     */
    public static final String ACCOUNT_INFO_TYPE = "account_info_type";
    /**
     * initial type, called when boot up.
     */
    public static final int ACCOUNT_INFO_TYPE_INITIAL = 0;
    /**
     * setting type, called when setting application is at front.
     */
    public static final int ACCOUNT_INFO_TYPE_FROM_SETTING = 1;
    /**
     * setting title
     */
    public static final String ACCOUNT_INFO_SETTING_TITLE = "account_info_setting_title";
    /*
     * int, EYUNOS_INITIAL 203 / EYUNOS_LOGOUT 202 / EYUNOS_SUCCESS 200
     */
    public static final String ACCOUNT_INFO_STATE = "account_info_state";
    /**
     * account initial state, not login
     */
    public static final int ACCOUNT_STATE_EYUNOS_INITIAL = 203;
    /**
     * account logout state
     */
    public static final int ACCOUNT_STATE_EYUNOS_LOGOUT = 202;
    /**
     * account login state
     */
    public static final int ACCOUNT_STATE_EYUNOS_SUCCESS = 200;
    /*
     * url
     */
    public static final String ACCOUNT_INFO_ICON = "account_info_icon";
    /*
     * String
     */
    public static final String ACCOUNT_INFO_NAME = "account_info_name";
    /*
     * String, good morning / good afternoon / good evening ...
     */
    public static final String ACCOUNT_INFO_WELCOME = "account_info_welcome";


    /*------------------------Account Receive Notification------------------------*/
    /*
     * send notification to account
     */
    public static final String ACCOUNT_RECEIVE_ACTION = "com.yunos.account.ACCOUNT_BROADCAST_RECV";
    /**
     * receive broadcast from account login.
     */
    public static final String ACTION_ACCOUNT_BROADCAST_LOGIN = "com.aliyun.xiaoyunmi.action.AYUN_LOGIN_BROADCAST";
    /**
     * receive broadcast from account logout.
     */
    public static final String ACTION_ACCOUNT_BROADCAST_LOGOUT = "com.aliyun.xiaoyunmi.action.DELETE_ACCOUNT";
    /*
     * account package name
     */
    public static final String ACCOUNT_RECEIVE_PACKAGE = "com.yunos.account";
    /*
     * account action type
     */
    public static final String KEY_ACCOUNT_CODE = "KEY_ACCOUNT_CODE";
    /*
     * when settings is at foreground, notify account to show in SystemUI
     */
    public static final int ACCOUNT_TYPE_SHOW_IN_SYSTEMUI = 1001;
    /*
     * when settings isn't at foreground, notify account to hide in SystemUI
     */
    public static final int ACCOUNT_TYPE_HIDE_IN_SYSTEMUI = 1002;
    /*
     * SystemUI notify account login
     */
    public static final int ACCOUNT_TYPE_LOGIN = 1003;
    /*
     * SystemUI notify account logout and reserve account
     */
    public static final int ACCOUNT_TYPE_LOGOUT_RESERVE = 1004;
    /*
     * SystemUI notify account logout and delete account
     */
    public static final int ACCOUNT_TYPE_LOGOUT_DELETE = 1005;
    /*
     * JSON
     */
    public static final String KEY_ACCOUNT_PARAMS = "KEY_ACCOUNT_PARAMS";
    /* account constant end */

    /* navigation constant start */
    /**
     * String
     */
    public static final String NAVI_NEXT_ROAD_NAME = "next_road_name";
    /**
     * String
     */
    public static final String NAVI_SEG_REMAIN_DIS = "seg_remain_dis";
    /**
     * int
     */
    public static final String NAVI_REMAIN_DIS = "route_remain_dis";
    /**
     * int
     */
    public static final String NAVI_REMAIN_TIME = "route_remain_time";
    /**
     * int
     */
    public static final String NAVI_LIMITED_SPEED = "limited_speed";
    /**
     * int
     */
    public static final String NAVI_CAMERA_TYPE = "camera_type";
    /**
     * int
     */
    public static final String NAVI_CAMERA_DIST = "camera_dist";
    /**
     * int
     */
    public static final String NAVI_CAMERA_SPEED = "camera_speed";
    /**
     * int
     */
    public static final String NAVI_TYPE = "type";
    /**
     * int
     */
    public static final String NAVI_ICON = "icon";
    /**
     * int
     */
    public static final int NAVIGATOR_STATE_SHOW = 0;
    /**
     * int
     */
    public static final int NAVIGATOR_STATE_HIDE = 1;
    /* navigation constant end */

    /* gao de api constant start*/
    /**
     * gao de main action
     */
    public static final String GAODE_MAIN_ACTION = "android.intent.action.VIEW";
    /**
     * company Uri
     */
    public static final Uri GAODE_COMPANY_URI = Uri.parse("androidauto://navi2SpecialDest?sourceApplication=appname&dest=crop");
    /**
     * home Uri
     */
    public static final Uri GAODE_HOME_URI = Uri.parse("androidauto://navi2SpecialDest?sourceApplication=appname&dest=home");
    /**
     * gao de default category
     */
    public static final String GAODE_DEFAULT_CATEGORY = "android.intent.category.DEFAULT";
    /**
     * gaode receive package
     */
    public static final String GAODE_RECEIVE_PACAKGE = "com.autonavi.amapautolite";
    /* gao de api constant end*/
}
