package com.senliast.updatesmanagerextended;

import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** Modern libxposed entry point, injected only into system_server. */
public class XposedMain extends XposedModule {
    private static final String TAG = "UpdatesManagerExtended";
    private static final String HOOK_ID = "block_app_updates";
    private final AtomicBoolean ruleErrorLogged = new AtomicBoolean();

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        try {
            Class<?> sessionClass = Class.forName(
                    "com.android.server.pm.PackageInstallerSession", false, param.getClassLoader());
            Method verification = sessionClass.getDeclaredMethod(
                    "assertPackageConsistentLocked", String.class, String.class, long.class);
            Hooker interceptor = createInterceptor(verification);
            hook(verification).setId(HOOK_ID).intercept(interceptor);
            log(Log.INFO, TAG, "API 102 update verification hook installed in system_server");
        } catch (ReflectiveOperationException | RuntimeException e) {
            log(Log.ERROR, TAG, "Update verification hook unavailable on this system", e);
        }
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        // The injected module owns no threads, listeners, native hooks, or external callbacks.
        // Keep its only hook installed until the new generation replaces it atomically.
        // No old-generation objects are passed through saved instance state.
        return true;
    }

    @Override
    public void onHotReloaded(HotReloadedParam param) {
        if (!param.isSystemServer()) {
            throw new IllegalStateException("Update verification must run in system_server");
        }
        List<HookHandle> previous = param.getOldHookHandles();
        HookHandle verification = null;
        for (HookHandle handle : previous) {
            if (HOOK_ID.equals(handle.getId()) && handle.getExecutable() instanceof Method) {
                verification = handle;
                break;
            }
        }
        if (verification == null) {
            // Lifecycle callbacks are not replayed, and no reliable system classloader is available.
            throw new IllegalStateException("No update verification hook to replace; restart the device");
        }
        try {
            // Resolve fields and preferences first. Preparation failures leave the old hook intact.
            Hooker interceptor = createInterceptor((Method) verification.getExecutable());
            verification.replaceHook(interceptor);
            for (HookHandle handle : previous) {
                if (handle != verification) handle.unhook();
            }
            log(Log.INFO, TAG, "Update verification hook hot-reloaded without restarting system_server");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot prepare the new update verification hook", e);
        }
    }

    private Hooker createInterceptor(Method verification) throws NoSuchFieldException {
        Field packageName = findField(verification.getDeclaringClass(), "mPackageName");
        Field installSource = findField(verification.getDeclaringClass(), "mInstallSource");
        SharedPreferences rules = getRemotePreferences("Rules");
        return chain -> {
            Object[] arguments = null;
            try {
                Object session = chain.getThisObject();
                String app = (String) packageName.get(session);
                Object sourceInfo = installSource.get(session);
                String source = sourceInfo == null ? null : (String)
                        findField(sourceInfo.getClass(), "mInstallerPackageName").get(sourceInfo);
                if (UpdateRules.shouldBlock(rules.getAll(), app, source,
                        System.currentTimeMillis())) {
                    arguments = chain.getArgs().toArray();
                    arguments[1] = "";
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                if (ruleErrorLogged.compareAndSet(false, true)) {
                    log(Log.ERROR, TAG, "Cannot evaluate update rules; allowing installation", e);
                }
            }
            // Installation failures must propagate once, including during hot reload.
            return arguments == null ? chain.proceed() : chain.proceed(arguments);
        };
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
