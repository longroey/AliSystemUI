package com.android.systemui.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

public class ImageUtil {
    public static Drawable zoomDrawable(Drawable drawable, int w, int h) {  
        int width = drawable.getIntrinsicWidth();  
        int height = drawable.getIntrinsicHeight();  
        Log.d("tianImageUtil", "zoomDrawable old width: " + width + " height: " + height);
        Log.d("tianImageUtil", "zoomDrawable new width: " + w + " height: " + h);
        Bitmap oldbmp = drawableToBitmap(drawable);
        if (oldbmp == null) {
            return null;
        }
        Matrix matrix = new Matrix();  
        float scaleWidth = ((float) w / width);  
        float scaleHeight = ((float) h / height);  
        matrix.postScale(scaleWidth, scaleHeight);  
        Bitmap newbmp = Bitmap.createBitmap(oldbmp, 0, 0, width, height,  
                matrix, true);
        if (newbmp == null) {
            return null;
        }
        oldbmp.recycle();
        return new BitmapDrawable(null, newbmp);  
    }  
    
    private static Bitmap drawableToBitmap(Drawable drawable) {  
        int width = drawable.getIntrinsicWidth();  
        int height = drawable.getIntrinsicHeight();  
        Bitmap.Config config = drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888  
                : Bitmap.Config.RGB_565;  
        Bitmap bitmap = Bitmap.createBitmap(width, height, config);  
        if (bitmap == null) {
            return null;
        }
        Canvas canvas = new Canvas(bitmap);  
        drawable.setBounds(0, 0, width, height);  
        drawable.draw(canvas);  
        return bitmap;  
    }
}
