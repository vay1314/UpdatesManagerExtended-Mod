package com.senliast.updatesmanagerextended;

import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** Modern libxposed entry point, injected only into system_server. */
public class XposedMain extends XposedModule {
    private static final String TAG = "UpdatesManagerExtended";
    private final AtomicBoolean ruleErrorLogged = new AtomicBoolean();

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        try {
            Class<?> sessionClass = Class.forName(
                    "com.android.server.pm.PackageInstallerSession", false, param.getClassLoader());
            Method verification = sessionClass.getDeclaredMethod(
                    "assertPackageConsistentLocked", String.class, String.class, long.class);
            Field packageName = findField(sessionClass, "mPackageName");
            Field installSource = findField(sessionClass, "mInstallSource");
            SharedPreferences rules = getRemotePreferences("Rules");

            hook(verification).setId("block_app_updates").intercept(chain -> {
                Object[] arguments = null;
                try {
                    Object session = chain.getThisObject();
                    String app = (String) packageName.get(session);
                    Object sourceInfo = installSource.get(session);
                    String source = sourceInfo == null ? null : (String)
                            findField(sourceInfo.getClass(), "mInstallerPackageName").get(sourceInfo);
                    if (UpdateRules.shouldBlock(rules.getAll(), app, source,
                            System.currentTimeMillis())) {
                        // Preserve the original blocking mechanism: force verification to fail.
                        arguments = chain.getArgs().toArray();
                        arguments[1] = "";
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // Settings or ROM incompatibilities must not disrupt system installation.
                    if (ruleErrorLogged.compareAndSet(false, true)) {
                        log(Log.ERROR, TAG, "Cannot evaluate update rules; allowing installation", e);
                    }
                }
                // Keep proceed outside the catch: an installation failure must propagate once.
                return arguments == null ? chain.proceed() : chain.proceed(arguments);
            });
            log(Log.INFO, TAG, "API 102 update verification hook installed in system_server");
        } catch (ReflectiveOperationException | RuntimeException e) {
            log(Log.ERROR, TAG, "Update verification hook unavailable on this system", e);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // OEMs may move fields into a superclass.
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
}
