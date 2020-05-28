package com.android.systemui.statusbar.auto;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import com.android.systemui.R;
import com.android.systemui.utils.AliUserTrackUtil;

public class TalkingStatusBarView extends FrameLayout implements View.OnClickListener{
    AutoTimerView mTimerView;
    TextView mStatusView;
    View mBreathView;
    private static final String ACTION_STATUS_BAR_CLICK = "com.yunos.intent.STATUS_BAR_CLICK";
    private static final String ACTION_PHONECALL_START = "com.yunos.bluetooth4car.intent.PHONECALL_START";
    private static final String EXTRAS_CALL_EVENT_SHOW_FORCE = "call_event_show_force";
    ArrayList<TalkingBarStateListener> mStateListeners = new ArrayList<TalkingBarStateListener>();
    public TalkingStatusBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnClickListener(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTimerView = (AutoTimerView)findViewById(R.id.auto_timer);
        mStatusView = (TextView)findViewById(R.id.auto_phone_status);
        mBreathView = findViewById(R.id.breath_view);
        FontHelper.applyFont(getContext(), mStatusView, FontHelper.MYINGHEI_18030_M);
    }

    public void startTimer(long startTime){
        mTimerView.setVisibility(View.VISIBLE);
        mTimerView.start(startTime);
    }

    public void startTimer(){
        mTimerView.setVisibility(View.VISIBLE);
        mTimerView.start();
    }

    public void stopTimer(){
        mTimerView.stop();
    }

    public void showTimerView(boolean show){
        mTimerView.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }
    private void showTimer(boolean show, long time){
        if(show){
            startTimer(time);
            showTimerView(true);
        }else{
            stopTimer();
            showTimerView(false);
        }
    }
    public void setStatus(int status, int showTalkingBar, long time){
        boolean show = showTalkingBar == NotificationConstant.SHOW_TALKING_BAR;
        switch (status) {
        case NotificationConstant.PHONE_STATE_OFFHOOK:
            mStatusView.setText(R.string.state_connected);
            break;
        case NotificationConstant.PHONE_STATE_COMMING_CALL:
            mStatusView.setText(R.string.state_comming_call);
            break;
        case NotificationConstant.PHONE_STATE_GOING_CALL:
            mStatusView.setText(R.string.state_going_call);
            break;
        case NotificationConstant.PHONE_STATE_IDLE:
        default:
            mStatusView.setText("");
            break;
        }
        if(showTalkingBar == NotificationConstant.SHOW_TALKING_BAR){
            //show talking bar
            startBreath(true);
            showMe();
            boolean showTimer = status == NotificationConstant.PHONE_STATE_OFFHOOK;
            showTimer(showTimer, time);
        }else{
            //hide talking bar
            startBreath(false);
            showTimer(false, -1);
            hideMe();
        }
    }
    public void registerTalkingbarStateListener(TalkingBarStateListener listener){
        if(listener != null){
            mStateListeners.add(listener);
        }
    }
    
    public void unregisterTalkingbarStateListener(TalkingBarStateListener listener){
        mStateListeners.remove(listener);
    }

    public void startBreath(boolean active){
        if(active){
            Animation breathAnim = AnimationUtils.loadAnimation(getContext(), R.anim.talking_bar_breath_animation);
            mBreathView.setAnimation(breathAnim);
        }else{
            mBreathView.clearAnimation();
        }
    }

    @Override
    public void onClick(View v) {
         Intent intent = new Intent();
         intent.setAction(ACTION_PHONECALL_START);
         intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
         intent.putExtra(EXTRAS_CALL_EVENT_SHOW_FORCE, true);
         getContext().startActivity(intent);
         AliUserTrackUtil.ctrlClicked("Call_Calling", "Calling_Hide");
         hideMe();
    }
    private void hideMe(){
        setVisibility(View.INVISIBLE);
        onChangeState(false);
    }

    private void onChangeState(boolean show){
        for(TalkingBarStateListener listener : mStateListeners){
            listener.onTalkingBarStateChange(show);
        }
    }
    private void showMe(){
        setVisibility(View.VISIBLE);
        onChangeState(true);
    }

    public void resetView(){
        startBreath(false);
        stopTimer();
        mStatusView.setText("");
    }
    public interface TalkingBarStateListener{
        /**
         * when show or hide, called this.
         * @param state
         */
        void onTalkingBarStateChange(boolean show);
    }

}
