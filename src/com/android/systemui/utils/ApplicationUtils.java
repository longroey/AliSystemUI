package com.android.systemui.utils;
import android.content.Context;
import android.content.res.Resources;
import android.os.SystemProperties;

public class ApplicationUtils {

	public enum Style {
		Drawer,
		Tab
	}
	public static Style getDefaultStyle() {
		return Style.Tab;
	}
    
    /* YUNOS BEGIN */
    // ##module(StatusBar)
    // ##date:2014/4/3 ##author:sunchen.sc@alibaba-inc.com##BugID:107078
    // Check the device support Gfx Accel or not.
    public static final boolean SUPPORT_WIDE_SCREEN = "true".equals(SystemProperties.get("ro.yunos.support.wide_screen", "false"));
    public static boolean isGfxAccelSupport() {
        return !Resources.getSystem().getBoolean(com.android.internal.R.bool.config_avoidGfxAccel);
    }
    /* YUNOS END */
    public static float px2sp(Context context, float pxValue) {
        final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        return (pxValue / fontScale + 0.5f);
    }
}
