package com.ym.screenshotgame;

import android.app.Activity;
import android.app.Application;
import android.database.ContentObserver;
import android.os.Bundle;
import android.util.Log;

import java.util.HashMap;

/**
 * Created by WYM on 2016/8/30.
 */
public class MonitorLifeCallback implements Application.ActivityLifecycleCallbacks {

    HashMap<String, ContentObserver> mappings = new HashMap();

    public MonitorLifeCallback() {
    }

    public void addMapping(String name, ContentObserver observer) {
        this.mappings.put(name, observer);
    }

    public boolean exist(String name) {
        return this.mappings.containsKey(name);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {

    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {
        String name = "";
        if(activity != null) {
            name = activity.getClass().getName();
        }

        if(this.mappings.containsKey(name)) {
            Log.d("ScreenshotDetector", "unregister " + name);
            activity.getContentResolver().unregisterContentObserver((ContentObserver)this.mappings.get(name));
            this.mappings.remove(name);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

    }
}
