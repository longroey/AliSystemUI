package com.android.systemui.statusbar.auto.compositecard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

abstract class ParceableLayout extends FrameLayout {

    NotificationStackFrameLayout mRoot;
    protected boolean isMini;
    private boolean mDrivemode;
    public ParceableLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    abstract void parseParams(String param);

    protected void switchToMini(boolean mini){
        isMini = mini;
    }

    protected boolean isMini(){
        return isMini;
    }
    protected void setRoot(NotificationStackFrameLayout root){
        mRoot = root;
    }
    protected NotificationStackFrameLayout getRoot(){
        return mRoot;
    }
    protected void switchDriveMode(boolean drivemode){
        mDrivemode = drivemode;
        onDriveModeChanged(mDrivemode);
    }
    protected boolean isDriveMode(){
        return mDrivemode;
    }
    protected void onDriveModeChanged(boolean mode){
    }
    protected void hideLayout(){
        setVisibility(View.INVISIBLE);
    }
    protected void showLayout(){
        setVisibility(View.VISIBLE);
    }
}
