
package com.android.systemui.statusbar.auto;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.android.systemui.R;

public class AutoTimerView extends LinearLayout{
    static final int SECONDS_PER_DAY = 24*60*60;//s
    static final int SECONDS_PER_HOUR = 60*60;//s
    static final int SECONDS_PER_MINUTES = 60;//s
    private long mTimeStart;
    private long mTimeEnd;
    private boolean stopInvalidate;
    boolean isOn;
//    BitmapDrawable mLight;
    private long mInvalidateDuration;
//    ImageView light;
    TextView timer;
    public AutoTimerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }
    private void initView(Context context) {
//        mLight = (BitmapDrawable)context.getResources().getDrawable(R.drawable.icon_redlight);
        setInvalidateDuration(1000);
    }
    protected void onFinishInflate(){
//        light = (ImageView)findViewById(R.id.light);
        timer = (TextView)findViewById(R.id.timer);
        FontHelper.applyFont(getContext(), timer, FontHelper.DIN_CONDENSED_BOLD);
    }

    public void pause() {
        isOn = false;
        stopInvalidate = true;
//        light.setVisibility(INVISIBLE);
    }
//    public void setRecordTime(String time){
//    	timer.setText(time);
//    }
    public void start() {
    	start(-1);
    }

	public void start(long startTime) {
		mTimeStart = startTime == -1 ? System.currentTimeMillis() : startTime;
		stopInvalidate = false;
		isOn = true;
		post(invalidateRunnable);
    }
    public void stop(){
    	isOn = false;
        stopInvalidate = true;
    	timer.setText("00:00");//reset timer.
    }
    protected void onDetachedFromWindow() {
        stopInvalidate = true;
    };

    private void setInvalidateDuration(long duration){
        mInvalidateDuration = duration;
    }

    Runnable invalidateRunnable = new Runnable() {
        @Override
        public void run() {
            if(!stopInvalidate){
//                light.setVisibility(isOn ? View.VISIBLE : View.INVISIBLE);
//                isOn = !isOn;
                mTimeEnd = System.currentTimeMillis() - mTimeStart;
                String text = formatTime(mTimeEnd);
                timer.setText(text);
                invalidate();
                postDelayed(invalidateRunnable, mInvalidateDuration);
            }
        }
    };
    
    public static String formatTime(long time) {
        time /= 1000;
        if(time >= SECONDS_PER_DAY){
            time %= SECONDS_PER_DAY;
        }
//        long hour = mTimeStart / SECONDS_PER_HOUR;
//        long minute = (mTimeStart - hour*SECONDS_PER_HOUR) / SECONDS_PER_MINUTES ;
//        long second = (mTimeStart - hour*SECONDS_PER_HOUR) % SECONDS_PER_MINUTES;
//        return String.format("%02d:%02d:%02d", hour, minute, second);
        long minute = time / SECONDS_PER_MINUTES;
        long second = time % SECONDS_PER_MINUTES;
        return String.format("%02d:%02d", minute, second);
    }
}
