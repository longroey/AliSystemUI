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

import android.net.Uri;

public class Constants {
    static final int MAX_ESCAPE_ANIMATION_DURATION = 500; // in ms
    static final int SNAP_BACK_DURATION = 250; // in ms
    static final int ESCAPE_VELOCITY = 100; // speed of item required to
                                            // "curate" it in dp/s
    public static float ALPHA_FADE_START = 0.8f; // fraction of thumbnail width
                                                 // where fade starts
    static final float ALPHA_FADE_END = 0.5f; // fraction of thumbnail width
                                              // beyond which alpha->0

    // for recent task icons
    public static final String ALI_PACKAGE_NAME_PHONE = "com.android.phone";
    public static final String ALI_PACKAGE_NAME_INCALLUI = "com.android.incallui";
    public static final String ALI_PACKAGE_NAME_INCALLUI_MAIN = "com.android.incallui.InCallActivity";
    public static final String ALI_PACKAGE_NAME_PHONE_MAIN = "com.yunos.alicontacts.activities.DialtactsContactsActivity";
    public static final String ALI_PACKAGE_NAME_CONTACT = "com.yunos.alicontacts";
    public static final String ALI_PACKAGE_NAME_CONTACT_MAIN = "com.yunos.alicontacts.activities.PeopleActivity2";
    public static final String ALI_PACKAGE_NAME_SMS = "com.yunos.alicontacts";
    public static final String ALI_PACKAGE_NAME_SMS_MAIN = "com.yunos.alimms.ui.ConversationList";
    public static final String ALI_PACKAGE_NAME_SECURITYCENTER = "com.aliyun.SecurityCenter";
    //use ushell for homeshell activity.
    public static final String ALI_PACKAGE_NAME_HOMESHELL = "com.aliyun.ushell";
    /* YUNOS_BEGIN */
    // ##modules(Systemui recent): RecentTasks consistent with SecurityCenter
    // experience
    // ##date: 2014.1.11 author: yulong.hyl@alibaba-inc.com
    public static final String REQUEST_ACCELERATION = "aliyun.intent.action.REQUEST_ACCELERATION";
    public static final Uri CONTENT_URI_ACC_USER_WHITE_APP = Uri
            .parse("content://com.aliyun.provider.secure/pkg_acc_user_white_apps");
    public static final String PACKAGE_NAME = "packagename";
    /* YUNOS_END */
}
