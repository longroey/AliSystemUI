package com.android.systemui.statusbar.auto.compositecard;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.provider.Settings.Global;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.android.systemui.statusbar.auto.NotificationConstant;

import com.android.systemui.R;
public class ADASLayout extends ParceableLayout{
    ImageView mADASIcon;
    private Context mContext;
    private long mCurrentTTSTime = -1;
    private static final String GLOBAL_ADAS_STATE = "adas_enable";
    private static final String TAG = "ADASLayout";
    private String mADASType;
    public ADASLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mADASIcon = (ImageView)findViewById(R.id.adas_icon);
    }

    @Override
    public void parseParams(String param) {
        try {
            JSONObject o = new JSONObject(param);
            mADASType = o.optString(NotificationConstant.NOTIFICATION_TYPE);
            updateAdasIconResource();
            //sendTtsMessage();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    private void updateAdasIconResource(){
        if (NotificationConstant.ADAS_FRONT_WARNING.equals(mADASType)) {
            mADASIcon.setImageResource(
                    isDriveMode() ? R.drawable.dm_adas_front_warning : R.drawable.adas_front_warning);
        } else if (NotificationConstant.ADAS_LEFT_CLAMPING.equals(mADASType)) {
            mADASIcon.setImageResource(
                    isDriveMode() ? R.drawable.dm_adas_left_clamping : R.drawable.adas_left_clamping);
        } else if (NotificationConstant.ADAS_RIGHT_CLAMPING.equals(mADASType)) {
            mADASIcon.setImageResource(
                    isDriveMode() ? R.drawable.dm_adas_right_clamping : R.drawable.adas_right_clamping);
        }
    }
    @Override
    protected void onDriveModeChanged(boolean mode) {
        updateAdasIconResource();
    }
    private void sendTtsMessage() {
        //check whether tts is allowed by.
        if(checkTtsNotAllowed() || checkDebounceTTS())
            return;
        playsound();
        mCurrentTTSTime = System.currentTimeMillis();
    }
    private void playsound(){
        int rawId = getRawId();
        if(rawId == -1){
            return;
        }
        SoundPool pool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        final int sourceid = pool.load(mContext, rawId, 0);
        pool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                // TODO Auto-generated method stub
                AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                float audioMaxVolumn = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                float volumnCurrent = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                float volumnRatio = volumnCurrent / audioMaxVolumn;
                soundPool.play(sourceid, volumnRatio, volumnRatio, 0, 0, 1);
                Log.d(TAG, "onLoadComplete playsound ------ volumnRatio = " + volumnRatio);
            }
        });
    }
    private int getRawId(){
      int rawId = -1;
      if (NotificationConstant.ADAS_FRONT_WARNING.equals(mADASType)) {
          rawId = R.raw.adas_front;
      } else if (NotificationConstant.ADAS_LEFT_CLAMPING.equals(mADASType)) {
          rawId = R.raw.adas_crimping;
      } else if (NotificationConstant.ADAS_RIGHT_CLAMPING.equals(mADASType)) {
          rawId = R.raw.adas_crimping;
      }
      return rawId;
    }
    private boolean checkDebounceTTS() {
        if(mCurrentTTSTime == -1){
            return false;
        }
        long lastTTSTime = mCurrentTTSTime;
        long current = System.currentTimeMillis();
        Log.d(TAG, "checkDebounceTTS current = " + current
                + ", lastTTSTime = " + lastTTSTime
                + ", pastTime = " + (current - lastTTSTime)
                + ", debounceTime = " + getDebounceTTSTime());
        return current - lastTTSTime <=  getDebounceTTSTime();
    }

    private long getDebounceTTSTime(){
        long time = 0;
        if(getRoot().getCurrentSpeed() <= 10){
            time = 1000;
        }else if(getRoot().getCurrentSpeed() >= 30){
            time = 100;
        }else{
            time = 500;
        }
        return time;
    }

    private boolean checkTtsNotAllowed() {
        //if need be improve, use observer?
        return Global.getInt(mContext.getContentResolver(), GLOBAL_ADAS_STATE, 1) == 0;
        /*else if(type == NotificationConstant.NOTIFICATION_TYPE_ROADINFO){
            return Global.getInt(mContext.getContentResolver(), GLOBAL_ROADINFO_STATE, 1) == 0;
        }*/
    }
}
