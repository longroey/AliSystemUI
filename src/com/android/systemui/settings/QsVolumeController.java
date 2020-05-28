package com.android.systemui.settings;

import com.android.systemui.settings.BrightnessController.BrightnessStateChangeCallback;
import com.android.systemui.utils.AliUserTrackUtil;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.System;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import com.android.systemui.R;

public class QsVolumeController implements ToggleSlider.Listener, Handler.Callback{
    private static final String TAG = "QsVolumeController";
    private static final int MSG_SET_STREAM_VOLUME = 0;
    private static final int MSG_SET_STREAM_MUTE = 1;
    private static final int MSG_UPDATE_UI = 99;
    private static final int FLAG_NOT_SHOW_UI = ~AudioManager.FLAG_SHOW_UI;
    ImageView mIcon;
    ToggleSlider mControl;
    TextView mLabel;
    Context mContext;
    private AudioManager mAudioManager;
    private int mStreamType;
    private boolean mListening;
    private boolean mMute;
    private int mLastProgress;
    private VolumeObserver mObserver;
    private Handler mHandler = new Handler(){
        public void handleMessage(Message msg) {
            //handled by other.
            switch (msg.what) {
            case MSG_UPDATE_UI:
                updateMode();
                 updateSlider();
                break;

            default:
                break;
            }
        };
    };
    public QsVolumeController(Context context, ImageView icon, TextView label, ToggleSlider control){
        this(context, icon, label, control, AudioManager.STREAM_MUSIC);
    }
    public QsVolumeController(Context context, ImageView icon, TextView label, ToggleSlider control, int streamType) {
        mContext = context;
        mIcon = icon;
        mLabel = label;
        mControl = control;
        mControl.setFocusable(false);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mStreamType = streamType;
        mThread = new HandlerThread(TAG + ".CallbackHandler");
        mThread.start();
        mThreadHandler = new Handler(mThread.getLooper(), this);
        mObserver = new VolumeObserver(mHandler);
        mIcon.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                boolean mute = isMuted();
                int on = mute ? 1 : 0;
                AliUserTrackUtil.ctrlClicked("ControlCenter", "Mute", on);
                setMute(!mute);
            }
        });
    }
    private Handler mThreadHandler;
    private HandlerThread mThread;
    @Override
    public void onInit(ToggleSlider v) {

    }

    @Override
    public void onChanged(ToggleSlider v, boolean tracking, boolean checked, int value) {
        boolean mute = isMuted();
        Map<String, String> lmap = new HashMap<String, String>();
        lmap.put("volume", value + "");
        AliUserTrackUtil.ctrlClicked("ControlCenter", "Drag_Volume", lmap);
        if(mute && value == 0){
            // not set volume to audio manager.
            Log.d(TAG, "not set volume to audio manager in mute mode.");
            return;
        }
        postSetVolume(value);
    }
    void postSetVolume(int value) {
        // Do the volume changing separately to give responsive UI
        mLastProgress = value;
        mThreadHandler.removeMessages(MSG_SET_STREAM_VOLUME);
        mThreadHandler.sendMessage(mThreadHandler.obtainMessage(MSG_SET_STREAM_VOLUME));
    }

    public void registerCallbacks() {
        if (mListening) {
            return;
        }

        mObserver.startObserving();
//        mUserTracker.startTracking();

        // Update the slider and mode before attaching the listener so we don't
        // receive the onChanged notifications for the initial values.
        updateMode();
        updateSlider();

        mControl.setOnChangedListener(this);
        mListening = true;
    }

    private void updateSlider() {
        if(!isMuted()){
            mControl.setMax(mAudioManager.getStreamMaxVolume(mStreamType));
            mLastProgress = mAudioManager.getStreamVolume(mStreamType);
            mControl.setValue(mLastProgress);
        }else{
            mControl.setValue(0);//just for show 0 value
        }
    }
    private void updateMode() {
        if(isMuted()){
            mIcon.setImageResource(R.drawable.control_volume_off_bg);
            if(mLabel != null)
                mLabel.setText(R.string.qs_label_mute_volume);
        }else{
            mIcon.setImageResource(R.drawable.control_volume_bg);
            if(mLabel != null)
                mLabel.setText(R.string.qs_label_volume);
        }
    }
    private void setMute(boolean mute){
        mThreadHandler.removeMessages(MSG_SET_STREAM_MUTE);
        Message msg = mThreadHandler.obtainMessage(MSG_SET_STREAM_MUTE);
        msg.arg1 = mute ? 1 : 0;
        mThreadHandler.sendMessage(msg);
    }

    /** Unregister all call backs, both to and from the controller */
    public void unregisterCallbacks() {
        if (!mListening) {
            return;
        }

        mObserver.stopObserving();
//        mUserTracker.stopTracking();
        mControl.setOnChangedListener(null);
        mListening = false;
    }
    private boolean isMuted(){
        return mAudioManager.isMasterMute();
//        return mAudioManager.isStreamMute(mStreamType);
    }
    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
        case MSG_SET_STREAM_VOLUME:
            /*YUNOS BEGIN*/
            //adjust volume together
            //mAudioManager.setStreamVolume(mStreamType, mLastProgress, 0);
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mLastProgress, 0);
            mAudioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, mLastProgress, 0);
            mAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, mLastProgress, 0);
            /*YUNOS END*/
            if(isMuted() && mLastProgress > 0){
                mAudioManager.setMasterMute(false, FLAG_NOT_SHOW_UI);
            }
//            AliUserTrackUtils.click("SoundSettings", "Volume", "volume_set", mLastProgress+"");
            break;
        case MSG_SET_STREAM_MUTE:
            boolean mute = msg.arg1 == 1;
            mAudioManager.setMasterMute(mute, FLAG_NOT_SHOW_UI);
//            mAudioManager.setStreamMute(mStreamType, mute);
//            AliUserTrackUtils.click("SoundSettings", "Volume", "volume_set", mLastProgress+"");
            break;
        default:
            Log.e(TAG, "invalid message: " + msg.what);
        }
        //send to main thread to update UI.
        mHandler.sendEmptyMessage(MSG_UPDATE_UI);
        return true;
    }

    class VolumeObserver extends ContentObserver{

        public VolumeObserver(Handler handler) {
            super(handler);
        }

         @Override
            public void onChange(boolean selfChange) {
                 super.onChange(selfChange);
                 Log.d(TAG, "VolumeObserver onChange selfChange = " + selfChange);
                 updateMode();
                 updateSlider();
            }

            public void startObserving() {
                final ContentResolver cr = mContext.getContentResolver();
//                cr.unregisterContentObserver(this);
                cr.registerContentObserver(
                        System.getUriFor(System.VOLUME_SETTINGS[mStreamType]),
                        false, this);
                cr.registerContentObserver(
                        System.getUriFor(System.VOLUME_MASTER_MUTE),
                        false, this);
                Log.d(TAG, "VolumeObserver startObserving----");
            }

            public void stopObserving() {
                final ContentResolver cr = mContext.getContentResolver();
                cr.unregisterContentObserver(this);
                Log.d(TAG, "VolumeObserver stopObserving----");
            }
    }

}
