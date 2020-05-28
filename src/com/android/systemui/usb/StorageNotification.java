/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 Google Inc.
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

package com.android.systemui.usb;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;
import com.android.systemui.SystemUI;

import java.util.HashSet;
import android.os.SystemProperties;
import android.hardware.usb.UsbManager;
import android.app.ActivityManager;


public class StorageNotification extends SystemUI {
    private static final String TAG = "StorageNotification";
    private static final boolean DEBUG = true;

    private static final boolean POP_UMS_ACTIVITY_ON_CONNECT = true;

    /**
     * The notification that is shown when a USB mass storage host
     * is connected.
     * <p>
     * This is lazily created, so use {@link #setUsbStorageNotification()}.
     */
    private Notification mUsbStorageNotification;

    /**
     * The notification that is shown when the following media events occur:
     *     - Media is being checked
     *     - Media is blank (or unknown filesystem)
     *     - Media is corrupt
     *     - Media is safe to unmount
     *     - Media is missing
     * <p>
     * This is lazily created, so use {@link #setMediaStorageNotification()}.
     */
    private Notification   mMediaStorageNotification;
    private Notification   mMediaStorageNotificationForPrimary;
    private Notification   mMediaStorageNotificationForExtStorage;
    private Notification   mMediaStorageNotificationForExtUsbOtg;
    private boolean        mUmsAvailable;
    private StorageManager mStorageManager;
    private StorageNotificationEventListener mListener;
    private HashSet        mUsbNotifications;
    private String         mLastState;
    private boolean        mLastConnected;
    private boolean        mAlarmBootOff = false;
    private boolean        mIsLastVisible = false;

    private static int notifyid = 0;
    private Handler        mAsyncEventHandler;

	 //M:ALPS01925477,add for multiUser,"USB connected" notification will not show when the current user is not owner. 
	private int currentUserId = 0;
    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action != null) {
                if (action.equals("android.intent.action.ACTION_SHUTDOWN_IPO")) {
                    // Slog.d(TAG, "onReceive [ACTION_SHUTDOWN_IPO] - [Clear mUsbNotifications]");
                	mUsbNotifications.clear();
                }

                if (action.equals("android.intent.action.normal.boot.done")) {
                    final boolean connected = mStorageManager.isUsbMassStorageConnected();
                    // Slog.d(TAG, "onReceive [ACTION_NORMAL_BOOT] - connected: " + connected);
                	mAlarmBootOff = true;
                    mListener.onUsbMassStorageConnectionChanged(connected);
                }
		//M:ALPS01925477,add for multiUser,"USB connected" notification will not show when the current user is not owner.
		if(action.equals(Intent.ACTION_USER_SWITCHED)){
					if(ActivityManager.getCurrentUser()!=0){
						// Slog.d(TAG, "onReceive [ACTION_USER_SWITCHED] - CurrentUser=: " + ActivityManager.getCurrentUser());
						mListener.onUsbMassStorageConnectionChanged(false);
					}else{
						// Slog.d(TAG, "onReceive [ACTION_USER_SWITCHED] - CurrentUser=: " + ActivityManager.getCurrentUser());
						final boolean connected = mStorageManager.isUsbMassStorageConnected();
						 mListener.onUsbMassStorageConnectionChanged(connected);
					}
					currentUserId = ActivityManager.getCurrentUser();
		}else{
					// Slog.d(TAG, "onReceive [ACTION_USER_SWITCHED] - no=: " + ActivityManager.getCurrentUser());
		}
		///@}
            }
        }
    };

    private class StorageNotificationEventListener extends StorageEventListener {
        public void onUsbMassStorageConnectionChanged(final boolean connected) {
            mAsyncEventHandler.post(new Runnable() {
                @Override
                public void run() {
                   /// onUsbMassStorageConnectionChangedAsync(connected);
                   //M:ALPS01925477,add for multiUser,"USB connected" notification will not show when the current user is not owner.
					if(currentUserId!=0){
						onUsbMassStorageConnectionChangedAsync(false);
					}else{
						onUsbMassStorageConnectionChangedAsync(connected);
					}
			///@}
                }
            });
        }
        public void onStorageStateChanged(final String path,
                final String oldState, final String newState) {
            mAsyncEventHandler.post(new Runnable() {
                @Override
                public void run() {
                    onStorageStateChangedAsync(path, oldState, newState);
                }
            });
        }
    }

    @Override
    public void start() {
        mStorageManager = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
        final boolean connected = mStorageManager.isUsbMassStorageConnected();

		/* find any storage is unmountable. If yes, show it. */
        String st = "";
        String path = "";
        StorageVolume[] volumes = mStorageManager.getVolumeList();

        if (volumes != null) {
            for (int i = 0; i < volumes.length; i++) {
                if (volumes[i].allowMassStorage() && !volumes[i].isEmulated()) {
                    path = volumes[i].getPath();
                    st = mStorageManager.getVolumeState(path);
                }
            }
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.normal.boot.done");
        filter.addAction("android.intent.action.ACTION_SHUTDOWN_IPO");
	//M:ALPS01925477,add for multiUser,"USB connected" notification will not show when the current user is not owner.@{				
	filter.addAction(Intent.ACTION_USER_SWITCHED);
	///@}
        mContext.registerReceiver(mIntentReceiver, filter);

        if (DEBUG) Log.d(TAG, String.format( "Startup with UMS connection %s (media state %s)",
                mUmsAvailable, st));

        HandlerThread thr = new HandlerThread("SystemUI StorageNotification");
        thr.start();
        mAsyncEventHandler = new Handler(thr.getLooper());
        mUsbNotifications = new HashSet();
        mLastState = Environment.MEDIA_MOUNTED;
        mLastConnected = false;

        mListener = new StorageNotificationEventListener();
        mListener.onUsbMassStorageConnectionChanged(connected);
        for (int i=0; i<volumes.length; i++) {
            String sharePath = volumes[i].getPath();
            String shareState = mStorageManager.getVolumeState(sharePath);
            if (shareState != null) {
                Log.d(TAG, "onStorageStateChanged - sharePath: " + sharePath + " shareState: " + shareState);
                if (shareState.equals(Environment.MEDIA_UNMOUNTABLE) ||
                    shareState.equals(Environment.MEDIA_NOFS)) {
                    mListener.onStorageStateChanged(sharePath, shareState, shareState);
                }
            }
        }
        mStorageManager.registerListener(mListener);
    }

    private void onUsbMassStorageConnectionChangedAsync(boolean connected) {
        mUmsAvailable = connected;
        /*
         * Even though we may have a UMS host connected, we the SD card
         * may not be in a state for export.
         */
        int allowedShareNum = 0;
        String st = "";
        String path = "";
        StorageVolume[] volumes = mStorageManager.getVolumeList();

        if (volumes != null) {
            for (int i = 0; i < volumes.length; i++) {
                if (volumes[i].allowMassStorage() && !volumes[i].isEmulated()) {
                    path = volumes[i].getPath();
                    st = mStorageManager.getVolumeState(path);
                    if (!(st.equals(Environment.MEDIA_REMOVED) || st.equals(Environment.MEDIA_CHECKING)|| st.equals(Environment.MEDIA_BAD_REMOVAL))) {
                        /* got a truly sharable volume */
                        allowedShareNum++;
                    }
                }
            }
        }

        if(connected && allowedShareNum == 0){
            /* only changed connceted here */
            // Slog.d(TAG, "change connected from true -> false");
            connected = false;
        }

        if (st != null) {
            if (DEBUG) Log.i(TAG, String.format("UMS connection changed to %s (media state %s), (path %s)",
                    connected, st, path));

            // Slog.d(TAG, "onUsbMassStorageConnectionChangedAsync - mLastState: " + mLastState + ", st: " + st + ", mLastConnected: " + mLastConnected+ ", connected: " + connected + ", path: " + path);
            if (!connected) {
                mUsbNotifications.clear();
                updateUsbMassStorageNotification(connected);
                // Slog.d(TAG, "onUsbMassStorageConnectionChangedAsync - Disconnect");
            } else {
                String mCurrentFunctions = SystemProperties.get("sys.usb.config", "none");
                if (containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_MASS_STORAGE)) {
                    // Slog.d(TAG, "onUsbMassStorageConnectionChangedAsync - Connect - UMS");
                    if (mLastState.equals(st) && mLastConnected == connected && !mAlarmBootOff) {
                        // Slog.d(TAG, "onUsbMassStorageConnectionChangedAsync - Connect - UMS - Ignore");
                        return;
                    }
                    updateUsbMassStorageNotification(connected);
                } else {
                    // Slog.d(TAG, "onUsbMassStorageConnectionChangedAsync - Connect - MTP");
                    setUsbStorageNotification(0, 0, 0, false, false, null);
                    mLastConnected = connected;
                }
            }
        }
        mLastConnected = connected;
        // Slog.d(TAG, "onUsbMassStorageConnectionChangedAsync - mLastConnected: " + mLastConnected);
    }

    private static boolean containsFunction(String functions, String function) {
        int index = functions.indexOf(function);

        if (index < 0) return false;
        if (index > 0 && functions.charAt(index - 1) != ',') return false;
        int charAfter = index + function.length();
        if (charAfter < functions.length() && functions.charAt(charAfter) != ',') return false;
        return true;
    }
    private void onStorageStateChangedAsync(String path, String oldState, String newState) {
        if (DEBUG) Log.i(TAG, String.format(
                "Media {%s} state changed from {%s} -> {%s}", path, oldState, newState));
        mLastState = newState;

        if (newState.equals(Environment.MEDIA_SHARED)) {
            /*
             * Storage is now shared. Modify the UMS notification
             * for stopping UMS.
             */
            // Slog.d(TAG, "onStorageStateChangedAsync - [MEDIA_SHARED]");
            Intent intent = new Intent();
            intent.setClass(mContext, com.android.systemui.usb.UsbStorageActivity.class);
            PendingIntent pi = PendingIntent.getActivity(mContext, 0, intent, 0);
            setUsbStorageNotification(
                    com.android.internal.R.string.usb_storage_stop_notification_title,
                    com.android.internal.R.string.usb_storage_stop_notification_message,
                    com.android.internal.R.drawable.stat_sys_warning, false, true, pi);

            // Slog.d(TAG, "Cancel all MediaStorageNotifications");
            NotificationManager notificationManager = (NotificationManager) mContext
                  .getSystemService(Context.NOTIFICATION_SERVICE);
            if (mMediaStorageNotificationForPrimary != null) {
              notificationManager.cancelAsUser(null, mMediaStorageNotificationForPrimary.icon, UserHandle.CURRENT);
            }
            if (mMediaStorageNotificationForExtStorage != null) {
              notificationManager.cancelAsUser(null, mMediaStorageNotificationForExtStorage.icon, UserHandle.CURRENT);
            }
            if (mMediaStorageNotificationForExtUsbOtg != null) {
              notificationManager.cancelAsUser(null, mMediaStorageNotificationForExtUsbOtg.icon, UserHandle.CURRENT);       
            }
        } else if (newState.equals(Environment.MEDIA_CHECKING)) {
            /*
             * Storage is now checking. Update media notification and disable
             * UMS notification.
             */
            // Slog.d(TAG, "onStorageStateChangedAsync - [MEDIA_CHECKING]");
            setMediaStorageNotification(path, false,
                    com.android.internal.R.string.ext_media_checking_notification_title,
                    com.android.internal.R.string.ext_media_checking_notification_message,
                    com.android.internal.R.drawable.stat_notify_sdcard_prepare, true, false, null);
        } else if (newState.equals(Environment.MEDIA_MOUNTED)) {
            /*
             * Storage is now mounted. Dismiss any media notifications,
             * and enable UMS notification if connected.
             */
            // Slog.d(TAG, "onStorageStateChangedAsync - [MEDIA_MOUNTED]");
            setMediaStorageNotification(path, false, 0, 0, 0, false, false, null);
            updateUsbMassStorageNotification(mUmsAvailable);
        } else if (newState.equals(Environment.MEDIA_UNMOUNTED)) {
            /*
             * Storage is now unmounted. We may have been unmounted
             * because the user is enabling/disabling UMS, in which case we don't
             * want to display the 'safe to unmount' notification.
             */
            // Slog.d(TAG, "onStorageStateChangedAsync - [MEDIA_UNMOUNTED]");
            if (!mStorageManager.isUsbMassStorageEnabled()) {
                // Slog.d(TAG, "onStorageStateChangedAsync - [MEDIA_UNMOUNTED]  !mStorageManager.isUsbMassStorageEnabled()");
                if (oldState.equals(Environment.MEDIA_SHARED)) {
                    /*
                     * The unmount was due to UMS being enabled. Dismiss any
                     * media notifications, and enable UMS notification if connected
                     */
                    // Slog.d(TAG, "onStorageStateChangedAsync - [MEDIA_UNMOUNTED]  MEDIA_SHARED");
                    setMediaStorageNotification(path, false, 0, 0, 0, false, false, null);
                    //updateUsbMassStorageNotification(mUmsAvailable);
                } else {
                    /*
                     * Show safe to unmount media notification, and enable UMS
                     * notification if connected.
                     */
                    if (Environment.isExternalStorageRemovable()) {
                         setMediaStorageNotification(path, false,
                                com.android.internal.R.string.ext_media_safe_unmount_notification_title,
                                com.android.internal.R.string.ext_media_safe_unmount_notification_message,
                                com.android.internal.R.drawable.stat_notify_sdcard, true, true, null);
                    } else {
                    // Slog.d(TAG, "onStorageStateChangedAsync - [MEDIA_UNMOUNTED]  !isExternalStorageRemovable");
                        // This device does not have removable storage, so
                        // don't tell the user they can remove it.
                        setMediaStorageNotification(path, false, 0, 0, 0, false, false, null);
                    }
                    // Slog.d(TAG, "onStorageStateChangedAsync - [MEDIA_UNMOUNTED]  !MEDIA_SHARED");
                    //updateUsbMassStorageNotification(mUmsAvailable);
                }
            } else {
                /*
                 * The unmount was due to UMS being enabled. Dismiss any
                 * media notifications, and disable the UMS notification
                 */
                // Slog.d(TAG, "onStorageStateChangedAsync - [MEDIA_UNMOUNTED]  mStorageManager.isUsbMassStorageEnabled()");
                setMediaStorageNotification(path, false, 0, 0, 0, false, false, null);
            }
        } else if (newState.equals(Environment.MEDIA_NOFS)) {
            /*
             * Storage has no filesystem. Show blank media notification,
             * and enable UMS notification if connected.
             */
            // Slog.d(TAG, "onStorageStateChangedAsync - [MEDIA_NOFS]");
            Intent intent = new Intent();
            intent.setClass(mContext, com.android.internal.app.ExternalMediaFormatActivity.class);
            intent.putExtra("PATH", path);
            PendingIntent pi = PendingIntent.getActivity(mContext, notifyid++, intent, 0);

            setMediaStorageNotification(path, false,
                    com.android.internal.R.string.ext_media_nofs_notification_title,
                    com.android.internal.R.string.ext_media_nofs_notification_message,
                    com.android.internal.R.drawable.stat_notify_sdcard_usb, true, false, pi);
            updateUsbMassStorageNotification(mUmsAvailable);
            /*YUNOS BEGIN*/
            //## BUGID:8715930:support user to format exfat && NTFS 
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            /*YUNOS END*/
        } else if (newState.equals(Environment.MEDIA_UNMOUNTABLE)) {
            /*
             * Storage is corrupt. Show corrupt media notification,
             * and enable UMS notification if connected.
             */
            // Slog.d(TAG, "onStorageStateChangedAsync - [MEDIA_UNMOUNTABLE]");
            Intent intent = new Intent();
            intent.setClass(mContext, com.android.internal.app.ExternalMediaFormatActivity.class);
            intent.putExtra("PATH", path);
            PendingIntent pi = PendingIntent.getActivity(mContext, notifyid++, intent, 0);

            setMediaStorageNotification(path, false,
                    com.android.internal.R.string.ext_media_unmountable_notification_title,
                    com.android.internal.R.string.ext_media_unmountable_notification_message,
                    com.android.internal.R.drawable.stat_notify_sdcard_usb, true, false, pi);
            updateUsbMassStorageNotification(mUmsAvailable);
        } else if (newState.equals(Environment.MEDIA_REMOVED)) {
            /*
             * Storage has been removed. Show nomedia media notification,
             * and disable UMS notification regardless of connection state.
             */
            // Slog.d(TAG, "onStorageStateChangedAsync - [MEDIA_REMOVED]");
            setMediaStorageNotification(path, false,
                    com.android.internal.R.string.ext_media_nomedia_notification_title,
                    com.android.internal.R.string.ext_media_nomedia_notification_message,
                    com.android.internal.R.drawable.stat_notify_sdcard_usb,
                    true, true, null);
            updateUsbMassStorageNotification(false);
        } else if (newState.equals(Environment.MEDIA_BAD_REMOVAL)) {
            /*
             * Storage has been removed unsafely. Show bad removal media notification,
             * and disable UMS notification regardless of connection state.
             */
            // Slog.d(TAG, "onStorageStateChangedAsync - [MEDIA_BAD_REMOVAL]");
            setMediaStorageNotification(path, true,
                    com.android.internal.R.string.ext_media_badremoval_notification_title,
                    com.android.internal.R.string.ext_media_badremoval_notification_message,
                    com.android.internal.R.drawable.stat_sys_warning,
                    true, true, null);
            updateUsbMassStorageNotification(false);
        } else {
            Log.w(TAG, String.format("Ignoring unknown state {%s}", newState));
        }
    }

    /*
    * Check how many storages can be shared on the device.
    * It seems that the device supported SHARED SD need to check.
    */
    boolean isAbleToShare() {
        int allowedShareNum = 0;
        StorageVolume[] volumes = mStorageManager.getVolumeList();
        if (volumes != null) {
            // Slog.d(TAG, "isAbleToShare - length:" + volumes.length);
            for (int i = 0; i < volumes.length; i++) {
                // Slog.d(TAG, "isAbleToShare - allowMassStorage:" + volumes[i].allowMassStorage() + "isEmulated:" + volumes[i].isEmulated());
                if (volumes[i].allowMassStorage() && !volumes[i].isEmulated()) {
                    String path = volumes[i].getPath();
                    String st = mStorageManager.getVolumeState(path);
                    if (st != null) {
                        // Slog.d(TAG, String.format("isAbleToShare - %s @ %s", path, st));
                        /* Only count the number of the storage can be shared */
                        if ( !st.equals(Environment.MEDIA_UNMOUNTABLE) && !st.equals(Environment.MEDIA_NOFS) &&
                            !st.equals(Environment.MEDIA_REMOVED) && !st.equals(Environment.MEDIA_BAD_REMOVAL) ) {
                            allowedShareNum++;
                        }
                    }
                }
            }
        }
        // Slog.d(TAG, "isAbleToShare - allowedShareNum:" + allowedShareNum);
        if (allowedShareNum == 0)
            return false;
        else
            return true;
    }

    /**
     * Update the state of the USB mass storage notification
     */
    void updateUsbMassStorageNotification(boolean available) {

        boolean isStorageCanShared = isAbleToShare();
        // Slog.d(TAG, "updateUsbMassStorageNotification - isStorageCanShared=" + isStorageCanShared + ",available=" + available);
        if( !mStorageManager.isUsbMassStorageEnabled() || mLastState.equals(Environment.MEDIA_BAD_REMOVAL) ) {
            /* Show "USB Connected" notification, if the system want it and there is more than one storage can be shared. */
            /* Like SHARED SD, there is an internal storage, but that can not be shared. So don't show notification. */
            if (available && isStorageCanShared) {
                // Slog.d(TAG, "updateUsbMassStorageNotification - [true]");
            Intent intent = new Intent();
            intent.setClass(mContext, com.android.systemui.usb.UsbStorageActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent pi = PendingIntent.getActivity(mContext, 0, intent, 0);
            setUsbStorageNotification(
                    com.android.internal.R.string.usb_storage_notification_title,
                    com.android.internal.R.string.usb_storage_notification_message,
                    com.android.internal.R.drawable.stat_sys_data_usb,
                    false, true, pi);
            } else if(!available && !isStorageCanShared || !mUmsAvailable) {
                /* Cancel "USB Connected" notification, if the system want to cancel it and there is no storage can be shared. */
                /* Like SD hot-plug, remove the external SD card, but still one storage can be shared. So don't cancel the notification. */
                // Slog.d(TAG, "updateUsbMassStorageNotification - [false]");
                setUsbStorageNotification(0, 0, 0, false, false, null);
            } else {
                // Slog.d(TAG, "updateUsbMassStorageNotification - Cannot as your wish!");
                /* When there is no partition to share (NOFS/BAD/...), invisible this notification */
            Intent intent = new Intent();
            intent.setClass(mContext, com.android.systemui.usb.UsbStorageActivity.class);

            PendingIntent pi = PendingIntent.getActivity(mContext, 0, intent, 0);
            setUsbStorageNotification(
                    com.android.internal.R.string.usb_storage_stop_notification_title,
                    com.android.internal.R.string.usb_storage_stop_notification_message,
                    com.android.internal.R.drawable.stat_sys_warning, false, false, pi);
            }
            mLastConnected = available;
        } else {
            // Slog.d(TAG, "updateUsbMassStorageNotification - UMS Enabled");
        }
    }

    /**
     * Sets the USB storage notification.
     */
    private synchronized void setUsbStorageNotification(int titleId, int messageId, int icon,
            boolean sound, boolean visible, PendingIntent pi) {

        // Slog.d(TAG, String.format("setUsbStorageNotification - visible: {%s}", visible));
        // Slog.d(TAG, "setUsbStorageNotification - mIsLastVisible: " + mIsLastVisible);
        if (!visible && mUsbStorageNotification == null) {
            return;
        }

        NotificationManager notificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            return;
        }

        if (visible) {
            Resources r = Resources.getSystem();
            CharSequence title = r.getText(titleId);
            CharSequence message = r.getText(messageId);

            if (mUsbStorageNotification == null) {
                mUsbStorageNotification = new Notification();
                mUsbStorageNotification.icon = icon;
                mUsbStorageNotification.when = 0;
                mUsbStorageNotification.priority = Notification.PRIORITY_MIN;
            }

            if (sound) {
                mUsbStorageNotification.defaults |= Notification.DEFAULT_SOUND;
            } else {
                mUsbStorageNotification.defaults &= ~Notification.DEFAULT_SOUND;
            }

            mUsbStorageNotification.flags = Notification.FLAG_ONGOING_EVENT;

            mUsbStorageNotification.tickerText = title;

			String bootReason = SystemProperties.get("sys.boot.reason");
			boolean alarmBoot = (bootReason != null && bootReason.equals("1")) ? true : false;

           	// Slog.d(TAG, "setUsbStorageNotification - alarmBoot: " + alarmBoot);

			if (alarmBoot) {
                // Slog.d(TAG, "setUsbStorageNotification - [Show Notification After AlarmBoot]");
				return;
			}

           	// Slog.d(TAG, "setUsbStorageNotification - count of mUsbNotifications: " + mUsbNotifications.size());
            if (!mUsbNotifications.contains(title.toString())) {
                mUsbNotifications.clear();
                mUsbNotifications.add(title.toString());
                // Slog.d(TAG, String.format("setUsbStorageNotification - [Add] title: {%s} to HashSet", title.toString()));
            } else {
                // Slog.d(TAG, String.format("setUsbStorageNotification - [Hashset contains] visible: {%s}", visible));
                if (mIsLastVisible) {
                    // Slog.d(TAG, "setUsbStorageNotification - same and visible, return");
                    return;
                }
            }

            if (pi == null) {
                Intent intent = new Intent();
                pi = PendingIntent.getBroadcastAsUser(mContext, 0, intent, 0,
                        UserHandle.CURRENT);
            }
            mUsbStorageNotification.color = mContext.getResources().getColor(
                    com.android.internal.R.color.system_notification_accent_color);
            mUsbStorageNotification.setLatestEventInfo(mContext, title, message, pi);
            mUsbStorageNotification.visibility = Notification.VISIBILITY_PUBLIC;
            mUsbStorageNotification.category = Notification.CATEGORY_SYSTEM;

            final boolean adbOn = 1 == Settings.Global.getInt(
                mContext.getContentResolver(),
                Settings.Global.ADB_ENABLED,
                0);

            if (POP_UMS_ACTIVITY_ON_CONNECT && !adbOn) {
                // Pop up a full-screen alert to coach the user through enabling UMS. The average
                // user has attached the device to USB either to charge the phone (in which case
                // this is harmless) or transfer files, and in the latter case this alert saves
                // several steps (as well as subtly indicates that you shouldn't mix UMS with other
                // activities on the device).
                //
                // If ADB is enabled, however, we suppress this dialog (under the assumption that a
                // developer (a) knows how to enable UMS, and (b) is probably using USB to install
                // builds or use adb commands.
                if (Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.DEVICE_PROVISIONED, 0) == 0) {
                    // Slog.d(TAG, "Device not provisioned, skipping showing full screen UsbStorageActivity");
                    mUsbStorageNotification.fullScreenIntent = null;
                } else {
                    // ALPS01823077
                    //mUsbStorageNotification.fullScreenIntent = pi;
                    mUsbStorageNotification.fullScreenIntent = null;
                    // ALPS01823077
                }
            } else {
                mUsbStorageNotification.fullScreenIntent = null;
            }
        }

        final int notificationId = mUsbStorageNotification.icon;
        if (visible) {
            notificationManager.notifyAsUser(null, notificationId, mUsbStorageNotification,
                    UserHandle.ALL);
            mIsLastVisible = true;
        } else {
            notificationManager.cancelAsUser(null, notificationId, UserHandle.ALL);
            mIsLastVisible = false;
        }
    }

    private synchronized boolean getMediaStorageNotificationDismissable() {
        if ((mMediaStorageNotification != null) &&
            ((mMediaStorageNotification.flags & Notification.FLAG_AUTO_CANCEL) ==
                    Notification.FLAG_AUTO_CANCEL))
            return true;

        return false;
    }

    /**
     * Sets the media storage notification.
     */
    private synchronized void setMediaStorageNotification(String path, boolean sound, int titleId, int messageId, int icon, boolean visible,
                                                          boolean dismissable, PendingIntent pi) {

        // Slog.d(TAG, String.format("setMediaStorageNotification path:%s", path));

        if ("/storage/sdcard0".equals(path) || "/storage/emulated/0".equals(path)) {
            if (mMediaStorageNotificationForPrimary == null) {
                mMediaStorageNotificationForPrimary = new Notification();
                mMediaStorageNotificationForPrimary.when = 0;
            }
            mMediaStorageNotification = mMediaStorageNotificationForPrimary;
        } else if ("/storage/sdcard1".equals(path)) {
            if (mMediaStorageNotificationForExtStorage == null) {
                mMediaStorageNotificationForExtStorage = new Notification();
                mMediaStorageNotificationForExtStorage.when = 0;
            }
            mMediaStorageNotification = mMediaStorageNotificationForExtStorage;
        } else {
            if (mMediaStorageNotificationForExtUsbOtg == null) {
                mMediaStorageNotificationForExtUsbOtg = new Notification();
                mMediaStorageNotificationForExtUsbOtg.when = 0;
            }
            mMediaStorageNotification = mMediaStorageNotificationForExtUsbOtg;
        }

        if (!visible && mMediaStorageNotification == null) {
            return;
        }

        NotificationManager notificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            return;
        }

        if (mMediaStorageNotification != null && visible) {
            /*
             * Dismiss the previous notification - we're about to
             * re-use it.
             */
            final int notificationId = mMediaStorageNotification.icon;
            notificationManager.cancel(notificationId);
        }

        if (visible) {
            Resources r = Resources.getSystem();
            String title = r.getText(titleId).toString();
            String message = r.getText(messageId).toString();

            StorageVolume volume = null;
            String volumeDescription = "";
            StorageVolume[] Volumes = mStorageManager.getVolumeList();
             if (SystemProperties.get("ro.mtk_multi_patition").equals("1") && (path.indexOf("usbotg") != -1)) {
                volumeDescription = Resources.getSystem().getString(com.android.internal.R.string.storage_external_usb);
            } else {
                if (Volumes != null) {
                    for (int i = 0; i < Volumes.length; i++) {
                        if(Volumes[i].getPath().equals(path)) {
                            volume = Volumes[i];
                            break;
                        }
                    }
                }

                if (volume == null) {
                    Slog.e(TAG, String.format(
                        "Can NOT find volume by name {%s}", path));
                    return;
                } else {
                    volumeDescription = volume.getDescription(mContext);
                }
            }

            String sd_string = Resources.getSystem().getText(com.android.internal.R.string.storage_sd_card).toString();
            title = title.replace(sd_string, volumeDescription);
            message = message.replace(sd_string, volumeDescription);

            if (mMediaStorageNotification == null) {
                mMediaStorageNotification = new Notification();
                mMediaStorageNotification.when = 0;
            }


            if (sound) {
                mMediaStorageNotification.defaults |= Notification.DEFAULT_SOUND;
            } else {
                mMediaStorageNotification.defaults &= ~Notification.DEFAULT_SOUND;
            }


            if (dismissable) {
                mMediaStorageNotification.flags = Notification.FLAG_AUTO_CANCEL;
            } else {
                mMediaStorageNotification.flags = Notification.FLAG_ONGOING_EVENT;
            }

            mMediaStorageNotification.tickerText = title;
            if (pi == null) {
                Intent intent = new Intent();
                pi = PendingIntent.getBroadcastAsUser(mContext, 0, intent, 0,
                        UserHandle.CURRENT);
            }

            mMediaStorageNotification.icon = icon;
            mMediaStorageNotification.color = mContext.getResources().getColor(
                    com.android.internal.R.color.system_notification_accent_color);
            mMediaStorageNotification.setLatestEventInfo(mContext, title, message, pi);
            mMediaStorageNotification.visibility = Notification.VISIBILITY_PUBLIC;
            mMediaStorageNotification.category = Notification.CATEGORY_SYSTEM;
        }

        final int notificationId = mMediaStorageNotification.icon;
        if (visible) {
             notificationManager.notifyAsUser(null, notificationId,
                    mMediaStorageNotification, UserHandle.CURRENT);
        } else {
            notificationManager.cancelAsUser(null, notificationId, UserHandle.CURRENT);
        }
    }
}
