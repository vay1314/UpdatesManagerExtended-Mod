package com.senliast.updatesmanagerextended;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.HotReloadResult;

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
    public enum RuntimeState { ACTIVE, RELOADING, RESTART_REQUIRED }
    private static volatile RuntimeState runtimeState = RuntimeState.RESTART_REQUIRED;
    private static boolean reloadAttempted;
    private static long reloadDeadline;
    private static final Runnable RUNTIME_POLL = FrameworkBridge::refreshRuntime;

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
                    reloadAttempted = false;
                    reloadDeadline = 0L;
                    MAIN.removeCallbacks(RUNTIME_POLL);
                    refresh();
                });
            }

            @Override
            public void onServiceDied(XposedService deadService) {
                MAIN.post(() -> {
                    if (service == deadService) {
                        service = null;
                        remoteRules = null;
                        MAIN.removeCallbacks(RUNTIME_POLL);
                        setRuntimeState(RuntimeState.RESTART_REQUIRED);
                        setReady(false);
                    }
                });
            }
        });
    }

    public static boolean isAvailable() {
        return ready;
    }

    public static RuntimeState getRuntimeState() {
        return runtimeState;
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
                MAIN.removeCallbacks(RUNTIME_POLL);
                setRuntimeState(RuntimeState.RESTART_REQUIRED);
                setReady(false);
                return;
            }
            if (remoteRules == null) {
                new MyPreferencesManager().upgradePreferencesDatabase();
                new SettingsSanitizer().sanitize();
                remoteRules = service.getRemotePreferences("Rules");
            }
            publishRules();
            if (ready) refreshRuntime();
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
        notifyStateChanged();
    }

    private static void setRuntimeState(RuntimeState state) {
        if (runtimeState == state) return;
        runtimeState = state;
        notifyStateChanged();
    }

    private static void refreshRuntime() {
        MAIN.removeCallbacks(RUNTIME_POLL);
        if (service == null || !ready) return;
        try {
            HookedTarget system = null;
            for (HookedTarget target : service.getRunningTargets()) {
                if ("system_server".equals(target.getProcessName())) {
                    system = target;
                    break;
                }
            }
            if (system == null) {
                setRuntimeState(RuntimeState.RESTART_REQUIRED);
            } else if (system.getState() == HookedTarget.State.UP_TO_DATE) {
                reloadDeadline = 0L;
                setRuntimeState(RuntimeState.ACTIVE);
            } else if (system.getState() == HookedTarget.State.RELOADING) {
                waitForReload();
            } else if (system.getState() == HookedTarget.State.STALE && !reloadAttempted) {
                // Automatic update reload may not have started; request it once for this app process.
                reloadAttempted = true;
                XposedService requestingService = service;
                waitForReload();
                service.hotReloadModule(system, null, (target, result) -> MAIN.post(() -> {
                    if (service != requestingService) return;
                    if (result.status() == HotReloadResult.Status.SUCCEEDED
                            || result.status() == HotReloadResult.Status.IN_PROGRESS) {
                        refreshRuntime();
                    } else {
                        MAIN.removeCallbacks(RUNTIME_POLL);
                        setRuntimeState(RuntimeState.RESTART_REQUIRED);
                        Log.w(TAG, "Hot reload requires a restart: " + result.status()
                                + "; " + result.message());
                    }
                }));
            } else {
                setRuntimeState(RuntimeState.RESTART_REQUIRED);
            }
        } catch (RuntimeException e) {
            MAIN.removeCallbacks(RUNTIME_POLL);
            setRuntimeState(RuntimeState.RESTART_REQUIRED);
            Log.e(TAG, "Cannot determine or reload the running module", e);
        }
    }

    private static void waitForReload() {
        if (reloadDeadline == 0L) reloadDeadline = SystemClock.elapsedRealtime() + 30000L;
        if (SystemClock.elapsedRealtime() >= reloadDeadline) {
            setRuntimeState(RuntimeState.RESTART_REQUIRED);
        } else {
            setRuntimeState(RuntimeState.RELOADING);
            MAIN.postDelayed(RUNTIME_POLL, 1000L);
        }
    }

    private static void notifyStateChanged() {
        Intent event = new Intent("com.senliast.updatesmanagerextended.BROADCAST");
        event.putExtra("type", "event");
        event.putExtra("event_name", "on_framework_state_changed");
        LocalBroadcastManager.getInstance(context).sendBroadcast(event);
    }
}
