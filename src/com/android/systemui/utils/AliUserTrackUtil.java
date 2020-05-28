package com.android.systemui.utils;

import java.util.HashMap;
import java.util.Map;

import com.aliyun.ams.ta.TA;

public class AliUserTrackUtil {
    public static final int CLICK_EVENT_ID = 2101;
    public static final String BUTTON_PREFIX = "Button-";
    public static void ctrlClicked(String pageName, String component) {
        if(!component.startsWith(BUTTON_PREFIX)){
            //add button prefix.
            component = BUTTON_PREFIX.concat(component);
        }
        TA.getInstance().getDefaultTracker().commitEvent(pageName, CLICK_EVENT_ID, component, null, null, null);
    }
    
    public static void ctrlClicked(String pageName, String component, int on) {
        Map<String, String> lMap = new HashMap<String, String>();
        lMap.put("switch", on + "");
        if(!component.startsWith(BUTTON_PREFIX)){
            //add button prefix.
            component = BUTTON_PREFIX.concat(component);
        }
        TA.getInstance().getDefaultTracker().commitEvent(pageName, CLICK_EVENT_ID, component, null, null, lMap);
    }
    
//    public static void ctrlClicked(String pageName, String component, String value) {
//        Map<String, String> lMap = new HashMap<String, String>();
//        lMap.put("extra", value);
//        if(!component.startsWith(BUTTON_PREFIX)){
//            //add button prefix.
//            component = BUTTON_PREFIX.concat(component);
//        }
//        TA.getInstance().getDefaultTracker().commitEvent(pageName, CLICK_EVENT_ID, component, null, null, lMap);
//    }
    
    public static void ctrlClicked(String pageName, String component, Map<String,String> lMap) {
        if(!component.startsWith(BUTTON_PREFIX)){
            //add button prefix.
            component = BUTTON_PREFIX.concat(component);
        }
        TA.getInstance().getDefaultTracker().commitEvent(pageName, CLICK_EVENT_ID, component, null, null, lMap);
    }

    public static void pageEnter(String pageName) {
        TA.getInstance().getDefaultTracker().pageEnter(pageName);
    }

    public static void pageLeave(String pageName) {
        TA.getInstance().getDefaultTracker().pageLeave(pageName);
    }
}
