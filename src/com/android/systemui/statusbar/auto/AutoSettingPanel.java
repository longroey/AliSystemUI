package com.android.systemui.statusbar.auto;

import com.android.systemui.qs.QSPanel;
import com.android.systemui.settings.BrightnessController;
import com.android.systemui.settings.QsVolumeController;
import com.android.systemui.settings.ToggleSlider;
import com.android.systemui.statusbar.GestureRecorder;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.phone.PhoneStatusBarView;
import com.android.systemui.utils.AliUserTrackUtil;
import com.android.systemui.volume.VolumeUI;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.EventLog;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.android.systemui.EventLogTags;

import com.android.systemui.R;

public class AutoSettingPanel extends PanelView implements View.OnClickListener{
    public static final boolean DEBUG_GESTURES = true;
    public static final String TAG = "AutoSettingPanel";
    Drawable mHandleBar;
    int mHandleBarHeight;
    View mHandleView;
    int mFingers;
    PhoneStatusBar mStatusBar;
    boolean mOkToFlip;

    private QSPanel mQsPanel;

    private ImageView mBrightnessIcon;
    private ToggleSlider mBrightnessTS;
    private ImageView mSettingButton;
    private ImageView mScreenOffButton;
    private BrightnessController mBrightnessController;
    private QsVolumeController mVolumeController;


    public AutoSettingPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setStatusBar(PhoneStatusBar bar) {
        mStatusBar = bar;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Resources resources = getContext().getResources();
        mHandleBar = resources.getDrawable(R.drawable.status_bar_close);
        mHandleBarHeight = resources.getDimensionPixelSize(R.dimen.close_handle_height);
        mHandleView = findViewById(R.id.holder_panel);
        mQsPanel = (QSPanel) findViewById(R.id.quick_settings_panel);
        mBrightnessController = new BrightnessController(getContext(),
                (ImageView) findViewById(R.id.brightness_icon),
                (TextView) findViewById(R.id.auto_light_label),
                (ToggleSlider)findViewById(R.id.brightness_slider));
        mVolumeController = new QsVolumeController(getContext(),
                (ImageView) findViewById(R.id.mute_icon),
                (TextView) findViewById(R.id.mute_label),
                (ToggleSlider)findViewById(R.id.volume_slider));
        mSettingButton = (ImageView)findViewById(R.id.setting_btn);
        mSettingButton.setOnClickListener(this);
        mScreenOffButton = (ImageView)findViewById(R.id.screen_off_btn);
        mScreenOffButton.setOnClickListener(this);
    }

    @Override
    public void fling(float vel, boolean always) {
        GestureRecorder gr = ((AutoStatusBarView) mBar).mBar.getGestureRecorder();
        if (gr != null) {
            gr.tag(
                "fling " + ((vel > 0) ? "open" : "closed"),
                "notifications,v=" + vel);
        }
        super.fling(vel, always);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.getText()
                    .add(getContext().getString(R.string.accessibility_desc_notification_shade));
            return true;
        }

        return super.dispatchPopulateAccessibilityEvent(event);
    }

    // We draw the handle ourselves so that it's always glued to the bottom of the window.
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            final int pt = getPaddingTop();
            final int pb = getPaddingBottom();
//            mHandleBar.setBounds(pl, 0, getWidth() - pr, (int) mHandleBarHeight);
            mHandleBar.setBounds(0, pt, (int) mHandleBarHeight, getHeight() - pb);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
//        final int off = (int) (getHeight() - mHandleBarHeight - getPaddingBottom());
/*        final int off = (int) (getWidth() - mHandleBarHeight - getPaddingRight());
        canvas.translate(off, 0);
        mHandleBar.setState(mHandleView.getDrawableState());
        mHandleBar.draw(canvas);
        canvas.translate(-off, 0);*/
    }
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        Log.d(TAG, "AutoSettingPanel dispatchTouchEvent ----------------" + ev);
        if(ev.getActionMasked() == MotionEvent.ACTION_DOWN){
            mBar.stopDebounceCollapsed();
        }else if(ev.getActionMasked() == MotionEvent.ACTION_CANCEL || ev.getActionMasked() == MotionEvent.ACTION_UP){
            mBar.startDebounceCollapsed();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (DEBUG_GESTURES) {
            if (event.getActionMasked() != MotionEvent.ACTION_MOVE) {
                EventLog.writeEvent(EventLogTags.SYSUI_NOTIFICATIONPANEL_TOUCH,
                       event.getActionMasked(), (int) event.getX(), (int) event.getY());
            }
        }
        //if (PhoneStatusBar.SETTINGS_DRAG_SHORTCUT && mStatusBar.mHasFlipSettings) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mOkToFlip = getExpandedWidth() == 0;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (mOkToFlip) {
                        float miny = event.getY(0);
                        float maxy = miny;
                        for (int i=1; i<event.getPointerCount(); i++) {
                            final float y = event.getY(i);
                            if (y < miny) miny = y;
                            if (y > maxy) maxy = y;
                        }
                        if (maxy - miny < mHandleBarHeight) {
                            if (getMeasuredHeight() < mHandleBarHeight) {
//                                mStatusBar.switchToSettings();
                            } else {
//                                mStatusBar.flipToSettings();
                            }
                            mOkToFlip = false;
                        }
                    }
                    break;
            }
        //}
        return mHandleView.dispatchTouchEvent(event);
    }

    void setListening(boolean listening) {
        mQsPanel.setListening(listening);
        if(listening){
            mBrightnessController.registerCallbacks();
            mVolumeController.registerCallbacks();
        }else{
            mBrightnessController.unregisterCallbacks();
            mVolumeController.unregisterCallbacks();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
        case R.id.setting_btn:
            handleClickSettingBtn();
            break;
        case R.id.screen_off_btn:
            handleClickScreenOffBtn();
            break;
        default:
            break;
        }
    }

	private void handleClickScreenOffBtn() {
		Log.d(TAG, "handleClickScreenOffBtn");
		if(!isFullyExpanded()){
			Log.d(TAG, "handleClickScreenOffBtn have not fully expanded, ignore it");
			return;
		}
        sendPowerOffKey();
		collapse();
	}

    private void sendPowerOffKey(){
         /* Start the key-simulation in a thread
         * so we do not block the GUI. */
         new Thread(new Runnable() {
              public void run() {
                  sendKeyEvent(KeyEvent.KEYCODE_POWER);
              }
         }).start(); /* And start the Thread. */
    }


    private void handleClickSettingBtn() {
        if(!isFullyExpanded()){
            Log.d(TAG, "handleClickSettingBtn have not fully expanded, ignore it");
            return;
        }
        Intent intent = new Intent("android.settings.SETTINGS");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        getContext().startActivity(intent);
        collapse();
        AliUserTrackUtil.ctrlClicked("ControlCenter", "Settings");
    }
	/**  
	 * Send a single key event.  
	 *  
	 * @param event is a string representing the keycode of the key event you  
	 * want to execute.  
	 */  
	private void sendKeyEvent(int keyCode) {  
	    int eventCode = keyCode;  
	    long now = SystemClock.uptimeMillis();  
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, eventCode, 0);  
        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, eventCode, 0);  
        invokeKeyEvent(down);
        invokeKeyEvent(up);
        AliUserTrackUtil.ctrlClicked("ControlCenter", "Screen_Off");
	} 
	
    private void invokeKeyEvent(KeyEvent event){
        Class cl = InputManager.class;
        try {
            Method method = cl.getMethod("getInstance");
            Object result = method.invoke(cl);
            InputManager im = (InputManager) result;
            method = cl.getMethod("injectInputEvent", InputEvent.class, int.class);
            method.invoke(im, event, 2);
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }  catch (IllegalArgumentException e) {
           e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
