package io.github.vay1314.updatesmanagerextendedmod;

import android.app.Application;
import android.content.Context;
import io.github.vay1314.updatesmanagerextendedmod.FrameworkBridge;

public class MyApplication extends Application {

    private static Context context;

    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        FrameworkBridge.initialize(context);
    }

    public static Context getAppContext() {
        return context;
    }
}
