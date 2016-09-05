package com.ym.screenshotgame;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeoutException;

/**
 * Created by WYM on 2016/8/30.
 */
public class ScreenshotMonitor {
    public static final String TAG = "ScreenshotDetector";
    private static final String EXTERNAL_CONTENT_URI_MATCHER =
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString();
    private static final String[] PROJECTION = new String[]{
            MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
    };
    private static final String SORT_ORDER = MediaStore.Images.Media.DATE_ADDED + " DESC";
    private static final long DEFAULT_DETECT_WINDOW_SECONDS = 10;

    private static ScreenshotMonitor captor;

    private MonitorLifeCallback lifeCallback;

    private boolean isPermissionGranted = true;

    private Handler mMainHandler;

    private ScreenshotMonitor() {
        //注册生命周期，在所有activity onPaused 中处理
        lifeCallback = new MonitorLifeCallback();
        Application app = new Application();
        app.registerActivityLifecycleCallbacks(lifeCallback);

        PackageManager manager = app.getPackageManager();
        //该应用使用有READ_EXTERNAL_STORAGE权限
        isPermissionGranted = (PackageManager.PERMISSION_GRANTED == manager.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, app.getPackageName()));
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    public void destroy() {
        Application app = new Application();
        app.unregisterActivityLifecycleCallbacks(lifeCallback);
        captor = null;
    }

    /**
     * 单例
     * @return
     */
    public static ScreenshotMonitor getInstance() {
        if (captor == null) {
            synchronized (ScreenshotMonitor.class) {
                if (captor == null) {
                    captor = new ScreenshotMonitor();
                }
            }
        }
        return captor;
    }

    public static void monitor(Activity activity, final ScreenshotCallback callback) {
        getInstance().start(activity, callback);
    }

    public void start(Activity activity, final ScreenshotCallback callback) {
        if (!isPermissionGranted) {
            Log.d(TAG, "权限不足，不能够监听截屏!");
        } else {
            if (lifeCallback.exist(activity.getClass().getName())) {
                Log.d(TAG, "已经注册过监听事件，不再重复注册!");
            } else {
                registerObserver(activity, callback);
            }
        }
    }

    /**
     * 注册观察者
     * 每个activity只能注册一个观察者，使用lifeCallback.addMapping()辅助判断
     * @param activity
     * @param callback
     */
    private void registerObserver(final Activity activity, final ScreenshotCallback callback) {
        final WeakReference<Activity> activityWeakReference = new WeakReference<Activity>(activity);
        final ContentResolver contentResolver = activity.getContentResolver();
        //当截屏存储目录中数据发生变化时，触发
        final ContentObserver contentObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                Log.d(TAG, "onChange: " + selfChange + ", " + uri.toString());

                Activity host = activityWeakReference.get();
                if (host == null || host.isFinishing()) {
                    Log.d(TAG, "页面已经销毁，不需要毁掉!");
                } else {
                    if (uri.toString().matches(EXTERNAL_CONTENT_URI_MATCHER)) {//根据uri匹配
                        Cursor cursor = null;
                        try {
                            cursor = contentResolver.query(uri, PROJECTION, null, null,
                                    SORT_ORDER);
                            if (cursor != null && cursor.moveToFirst()) {
                                String path = cursor.getString(
                                        cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                                long dateAdded = cursor.getLong(cursor.getColumnIndex(
                                        MediaStore.Images.Media.DATE_ADDED));
                                long currentTime = System.currentTimeMillis() / 1000;
                                Log.d(TAG, "path: " + path + ", dateAdded: " + dateAdded +
                                        ", currentTime: " + currentTime);
                                //最新的一条数据符合截屏图片的特征，则认为是截屏图片
                                if (matchPath(path) && matchTime(currentTime, dateAdded)) {
                                    //读取截屏图片进行操作
                                    ScreenshotChecker checker = new ScreenshotChecker(activityWeakReference, mMainHandler, callback, path);
                                    checker.call();
                                }
                            }
                        } catch (Throwable e) {
                            Log.d(TAG, "open cursor fail");
                            callback.onError(e);
                        } finally {
                            if (cursor != null) {
                                cursor.close();
                            }
                        }
                    }
                }
                super.onChange(selfChange, uri);
            }
        };

        // 给contentResolver注册观察者，根据uri匹配，
        // 这样当uri匹配的contentResolver发生变化的时候，会通知contentObjserver
        contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, contentObserver);

        //将contentObserver添加到map中，用于判断一个activity只有一个contentObserver
        lifeCallback.addMapping(activity.getClass().getName(), contentObserver);
    }

    private static boolean matchPath(String path) {
        return path.toLowerCase().contains("screenshot") || path.contains("截屏") ||
                path.contains("截图");
    }

    private static boolean matchTime(long currentTime, long dateAdded) {
        return Math.abs(currentTime - dateAdded) <= DEFAULT_DETECT_WINDOW_SECONDS;
    }

    /**
     * 截屏回调接口
     */
    public interface ScreenshotCallback {

        void onCompleted(String path);

        void onError(Throwable e);
    }

    /**
     * 截屏图片检查
     * 截屏完成后，在截屏图片写入到android系统中时
     * 执行获取截屏图片的操作，可能会出现获取图片不完全的情况，
     * 采用循环判断获取的方式，直到获取到完整的图片
     */
    static class ScreenshotChecker implements Runnable {

        private static final int MAX_COUNT = 5;
        private static final int INTERVAL = 200;

        private WeakReference<Activity> host;
        private ScreenshotCallback callback;
        private String path;
        private Handler executor;

        private int failureCount = 0;

        //在小米上非常变态，有时候文件虽然存在，但是还没有写全...特殊处理一下
        private long beforeScreenshotSize = -1;

        public ScreenshotChecker(WeakReference<Activity> host, Handler executor, ScreenshotCallback callback, String path) {
            this.host = host;
            this.callback = callback;
            this.path = path;
            this.executor = executor;
        }

        /**
         *
         */
        public void call() {
            File screenshot = new File(path);
            if (screenshot.exists() && screenshot.length() > 1024) {
                beforeScreenshotSize = screenshot.length();
            }
            executor.post(this);
        }

        /**
         * Starts executing the active part of the class' code. This method is
         * called when a thread is started that has been created with a class which
         * implements {@code Runnable}.
         */
        @Override
        public void run() {
            //检查文件是否存在
            File screenshot = new File(path);
            //* Returns 0 if the file does not exist.
            long size = screenshot.length();
            Log.d(TAG, "before size : " + beforeScreenshotSize + " after size: " + size);
            if (!screenshot.exists()) {
                //文件失败
                if (failureCount < MAX_COUNT) {
                    failureCount++;
                    executor.postDelayed(this, INTERVAL);
                } else {
                    //尝试几次都失败，回调失败
                    if (callback != null && needCallback()) {
                        callback.onError(new TimeoutException(String.format("等待%d次后，图片依然不存在!", MAX_COUNT)));
                    }
                }
            } else if (beforeScreenshotSize != size) {
                //文件失败
                if (failureCount < MAX_COUNT) {
                    beforeScreenshotSize = size;
                    failureCount++;
                    executor.postDelayed(this, INTERVAL);
                } else {
                    //尝试几次都失败，回调失败
                    if (callback != null && needCallback()) {
                        callback.onError(new TimeoutException(String.format("等待%d次后，图片依然不完整!", MAX_COUNT)));
                    }
                }
            } else {
                //文件存在，一切正常
                if (callback != null && needCallback()) {
                    callback.onCompleted(path);
                }
            }
        }

        public boolean needCallback() {
            Activity activity = host.get();
            return activity != null && !activity.isFinishing();
        }
    }


}
