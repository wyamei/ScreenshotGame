package com.ym.screenshotgame;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;

/**
 * Created by WYM on 2016/9/5.
 */
public class BitmapTools {
    
    public static Bitmap makeThumbnailFromScreenshot(String imgPath){
        Bitmap bitmap = null;

        try {
            bitmap = BitmapFactory.decodeFile(imgPath);
        } catch (OutOfMemoryError var5) {
            Log.d("ScreenshotMonitor", "OutOfMemoryError,获取截屏图片失败");
        }

        if(bitmap == null) {
            Log.d("ScreenshotMonitor", "screenshotBitmap 生成失败! Thumbnail ");
            return null;
        } else {
            Bitmap resizeBmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight() / 2);
            bitmap.recycle();
            if(resizeBmp == null) {
                Log.d("ScreenshotMonitor", "resizeBmp 生成失败!");
                return null;
            } else {
                Matrix matrix = new Matrix();
                matrix.postScale(0.5F, 0.5F);
                Bitmap thumbnailBitmap = Bitmap.createBitmap(resizeBmp, 0, 0, resizeBmp.getWidth(), resizeBmp.getHeight(), matrix, true);
                if(thumbnailBitmap == null) {
                    Log.d("ScreenshotMonitor", "thumbnailBitmap 生成失败!");
                    return null;
                } else {
                    resizeBmp.recycle();
                    return thumbnailBitmap;
                }
            }
        }
    }

    public static Bitmap getScreenshotBitmap(String imgPath){
        Bitmap bitmap = null;

        try {
            bitmap = BitmapFactory.decodeFile(imgPath);
        } catch (OutOfMemoryError var5) {
            Log.d("ScreenshotMonitor", "OutOfMemoryError,获取截屏图片失败");
        }
        return bitmap;
    }
}
