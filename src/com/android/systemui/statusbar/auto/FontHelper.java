package com.android.systemui.statusbar.auto;

import java.util.HashMap;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class FontHelper {
    private static final String TAG = "FontHelper";
    public static final String DIN_CONDENSED_BOLD = "fonts/DIN_Condensed_Bold.ttf";
    public static final String MYINGHEI_18030_H = "fonts/MYingHei_18030-H.ttf";
    public static final String MYINGHEI_18030_M = "fonts/MYingHei_18030-M.ttf";
    public static HashMap<String, Typeface> mTypefaces = new HashMap<String, Typeface>();
    public static void applyFont(Context context, View root, String fontName){
        try {
            if (root instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) root;
                for (int i = 0; i < viewGroup.getChildCount(); i++)
                    applyFont(context, viewGroup.getChildAt(i), fontName);
            } else if (root instanceof TextView)
                ((TextView) root).setTypeface(getTypeface(context, fontName));
        } catch (Exception e) {
            Log.e(TAG, String.format("Error occured when trying to apply %s font for %s view", fontName, root));
            e.printStackTrace();
        }
    }
    public static Typeface getTypeface(Context context, String fontName){
        if(mTypefaces.containsKey(fontName)){
            return mTypefaces.get(fontName);
        }else{
            Typeface typeFaces = Typeface.createFromAsset(context.getAssets(), fontName);
            mTypefaces.put(fontName, typeFaces);
            return typeFaces;
        }
    }

    public static void applyFont(Context context, View root, String fontName, float alpha){
        try {
            if (root instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) root;
                for (int i = 0; i < viewGroup.getChildCount(); i++)
                    applyFont(context, viewGroup.getChildAt(i), fontName);
            } else if (root instanceof TextView){
                ((TextView) root).setTypeface(getTypeface(context, fontName));
                root.setAlpha(alpha);
            }
        } catch (Exception e) {
            Log.e(TAG, String.format("Error occured when trying to apply %s font for %s view", fontName, root));
            e.printStackTrace();
        }
    }
}
