package com.senliast.updatesmanagerextended;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Keeps local settings and backup formats, publishing rules through the modern service. */
public final class FrameworkBridge {
    private static final String TAG = "UpdatesManagerExtended";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Context context;
    private static SharedPreferences preferences;
    private static SharedPreferences groups;
    private static XposedService service;
    private static SharedPreferences remoteRules;
    private static volatile boolean ready;

    // SharedPreferences keeps weak references, so retain this listener for the process lifetime.
    private static final SharedPreferences.OnSharedPreferenceChangeListener SETTINGS_LISTENER =
            (prefs, key) -> {
                if (remoteRules != null) publishRules();
            };

    private FrameworkBridge() {}

    public static void initialize(Context appContext) {
        context = appContext.getApplicationContext();
        preferences = context.getSharedPreferences("Preferences", Context.MODE_PRIVATE);
        groups = context.getSharedPreferences("Groups", Context.MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(SETTINGS_LISTENER);
        groups.registerOnSharedPreferenceChangeListener(SETTINGS_LISTENER);

        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService boundService) {
                MAIN.post(() -> {
                    service = boundService;
                    remoteRules = null;
                    refresh();
                });
            }

            @Override
            public void onServiceDied(XposedService deadService) {
                MAIN.post(() -> {
                    if (service == deadService) {
                        service = null;
                        remoteRules = null;
                        setReady(false);
                    }
                });
            }
        });
    }

    public static boolean isAvailable() {
        return ready;
    }

    /** Recheck scope when returning from the framework manager and reconnect saved rules. */
    public static void refresh() {
        if (service == null) {
            setReady(false);
            return;
        }
        try {
            if (service.getApiVersion() < 102 || !service.getScope().contains("system")) {
                remoteRules = null;
                setReady(false);
                return;
            }
            if (remoteRules == null) {
                new MyPreferencesManager().upgradePreferencesDatabase();
                new SettingsSanitizer().sanitize();
                remoteRules = service.getRemotePreferences("Rules");
            }
            publishRules();
        } catch (RuntimeException e) {
            remoteRules = null;
            setReady(false);
            Log.e(TAG, "Cannot connect to the API 102 settings service", e);
        }
    }

    private static void publishRules() {
        try {
            // Publish global and group rules atomically, including deletions after imports.
            SharedPreferences.Editor editor = remoteRules.edit().clear()
                    .putString("moduleStatus", preferences.getString("moduleStatus", "disabled"))
                    .putLong("moduleStatusTime", preferences.getLong("moduleStatusTime", 0L))
                    .putString("groups_list", groups.getString("groups_list", null));
            setReady(editor.commit());
        } catch (RuntimeException e) {
            setReady(false);
            Log.e(TAG, "Cannot publish update rules to the framework", e);
        }
    }

    private static void setReady(boolean value) {
        if (ready == value) return;
        ready = value;
        Intent event = new Intent("com.senliast.updatesmanagerextended.BROADCAST");
        event.putExtra("type", "event");
        event.putExtra("event_name", "on_framework_state_changed");
        LocalBroadcastManager.getInstance(context).sendBroadcast(event);
    }
}
