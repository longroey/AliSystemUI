package com.android.systemui.statusbar.auto;
import com.android.systemui.R;

import android.content.Context;
import android.widget.TextView;

public class HudInfoHelper {
    static final int[] iconArray = new int[] { R.drawable.sou2, R.drawable.sou3,
            R.drawable.sou4, R.drawable.sou5, R.drawable.sou6, R.drawable.sou7,
            R.drawable.sou8, R.drawable.sou9, R.drawable.sou10,
            R.drawable.sou11, R.drawable.sou12, R.drawable.sou13,
            R.drawable.sou14, R.drawable.sou15, R.drawable.sou16,
            R.drawable.sou17, R.drawable.sou18, R.drawable.sou19,
            R.drawable.sou20, R.drawable.sou21, R.drawable.sou22,
            R.drawable.sou23, R.drawable.sou24, R.drawable.sou25,
            R.drawable.sou26, R.drawable.sou27 };
    static final int[] iconDmArray = new int[] { R.drawable.dm_sou2, R.drawable.dm_sou3,
            R.drawable.dm_sou4, R.drawable.dm_sou5, R.drawable.dm_sou6, R.drawable.dm_sou7,
            R.drawable.dm_sou8, R.drawable.dm_sou9, R.drawable.dm_sou10,
            R.drawable.dm_sou11, R.drawable.dm_sou12, R.drawable.dm_sou13,
            R.drawable.dm_sou14, R.drawable.dm_sou15, R.drawable.dm_sou16,
            R.drawable.dm_sou17, R.drawable.dm_sou18, R.drawable.dm_sou19,
            R.drawable.dm_sou20, R.drawable.dm_sou21, R.drawable.dm_sou22,
            R.drawable.dm_sou23, R.drawable.dm_sou24, R.drawable.dm_sou25,
            R.drawable.dm_sou26, R.drawable.dm_sou27 };

    public static int getHudIcon(int hudId, boolean driveMode){
        int[] directIcon = driveMode ? iconDmArray : iconArray;
        if(hudId - 2 >= 0)
            return directIcon[hudId - 2];
        return -1;
    }


    public static String formatDistance(Context context, int distance){
        return formatDistance(context, distance, false);
    }

    public static String formatDistance(Context context, int distance, boolean simple){
        StringBuilder sb = new StringBuilder();
        if(distance >= 1000){
            //m to km xx.xx
            sb.append((float) (Math.round((float) distance / 1000 * 10)) / 10);
            String km = simple ? "km" : context.getResources().getString(R.string.kilometer);
            sb.append(km);
        }else{
            sb.append(distance);
            String m = simple ? "m" : context.getResources().getString(R.string.meter);
            sb.append(m);
        }
        return sb.toString();
    }
    public static String formatTime(Context context, int time){
        return formatTime(context, time, false);
    }
    public static String formatTime(Context context, int time, boolean simple){//s
        StringBuilder sb = new StringBuilder();
        String hour = simple ? "h" : context.getResources().getString(R.string.hour);
        String min = simple ? "min" : context.getResources().getString(R.string.minute);
        if(time >= 3600){
            //s to h min.
//            sb.append((float) Math.round((float) time / 3600 * 100) / 100);
            int hh = time / 3600;
            sb.append(hh);
            sb.append(hour);
            int left = time % 3600;
            if(left / 60 > 0 ){
                int mm = left / 60;
                sb.append(mm);
                sb.append(min);
            }
        }else{
            //s to min
            sb.append(time / 60);
            sb.append(min);
        }
        return sb.toString();
    }
}
